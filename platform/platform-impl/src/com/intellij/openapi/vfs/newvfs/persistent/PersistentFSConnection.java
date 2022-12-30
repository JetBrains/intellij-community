// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.core.CoreBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.*;
import com.intellij.util.io.storage.CapacityAllocationPolicy;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.io.storage.RefCountingContentStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class PersistentFSConnection {
  private static final Logger LOG = Logger.getInstance(PersistentFSConnection.class);

  static final int RESERVED_ATTR_ID = FSRecords.bulkAttrReadSupport ? 1 : 0;
  static final AttrPageAwareCapacityAllocationPolicy REASONABLY_SMALL = new AttrPageAwareCapacityAllocationPolicy();
  private static final int FIRST_ATTR_ID_OFFSET = FSRecords.bulkAttrReadSupport ? RESERVED_ATTR_ID : 0;

  private final IntList myFreeRecords;
  @NotNull
  private final VfsDependentEnum myAttributesList;
  @NotNull
  private final PersistentFSPaths myPersistentFSPaths;

  @NotNull
  private final AbstractAttributesStorage myAttributesStorage;
  @NotNull
  private final RefCountingContentStorage myContents;
  @NotNull
  private final PersistentFSRecordsStorage myRecords;
  @Nullable
  private final ContentHashEnumerator myContentHashesEnumerator;
  private final ScannableDataEnumeratorEx<String> myNames;
  /**
   * Enumerator for repeating strings used in attributes. Used to support
   * {@link AttributeInputStream#readEnumeratedString()}
   * {@link AttributeOutputStream#writeEnumeratedString(String)}
   */
  private final @NotNull SimpleStringPersistentEnumerator myEnumeratedAttributes;

  private volatile boolean myDirty;
  /**
   * accessed under {@link #r}/{@link #w}
   */
  private final ScheduledFuture<?> myFlushingFuture;
  /**
   * accessed under {@link #r}/{@link #w}
   */
  private final AtomicBoolean myCorrupted = new AtomicBoolean();

  PersistentFSConnection(@NotNull PersistentFSPaths paths,
                         @NotNull PersistentFSRecordsStorage records,
                         @NotNull ScannableDataEnumeratorEx<String> names,
                         @NotNull AbstractAttributesStorage attributes,
                         @NotNull RefCountingContentStorage contents,
                         @Nullable ContentHashEnumerator contentHashesEnumerator,
                         @NotNull SimpleStringPersistentEnumerator enumeratedAttributes,
                         @NotNull IntList freeRecords,
                         boolean markDirty) throws IOException {
    if (!(names instanceof Forceable) || !(names instanceof Closeable)) {
      //RC: there is no simple way to specify type like DataEnumerator & Forceable & Closeable in java,
      //    hence the runtime check here (and in methods below calling Forceable/Closeable methods).
      //    This is needed only during transition period, while we're experimenting with plugging in
      //    different names impls -- after we'll decide which impl is the best, explicit type could be specified here
      throw new IllegalArgumentException("names(" + names + ") must implement Forceable & Closeable");
    }
    myRecords = records;
    myNames = names;
    myAttributesStorage = attributes;
    myContents = contents;
    myContentHashesEnumerator = contentHashesEnumerator;
    myPersistentFSPaths = paths;
    myFreeRecords = freeRecords;
    myEnumeratedAttributes = enumeratedAttributes;

    if (markDirty) {
      markDirty();
    }
    myAttributesList = new VfsDependentEnum(getPersistentFSPaths(), "attrib", 1);

    if (FSRecords.backgroundVfsFlush) {
      myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
        private int lastModCount;

        @Override
        public void run() {
          //TODO RC: use myDirty instead of myRecords.getGlobalModCount?
          if (lastModCount == myRecords.getGlobalModCount()) {
            flush();
          }
          lastModCount = myRecords.getGlobalModCount();
        }
      });
    }
    else {
      myFlushingFuture = null;
    }
  }

  @NotNull("Vfs must be initialized")
  SimpleStringPersistentEnumerator getEnumeratedAttributes() {
    return myEnumeratedAttributes;
  }

  @NotNull("Content hash enumerator must be initialized")
  ContentHashEnumerator getContentHashesEnumerator() {
    return myContentHashesEnumerator;
  }

  @NotNull("Vfs must be initialized")
  RefCountingContentStorage getContents() {
    return myContents;
  }

  @NotNull("Vfs must be initialized")
  AbstractAttributesStorage getAttributes() {
    return myAttributesStorage;
  }

  @NotNull("Vfs must be initialized")
  ScannableDataEnumeratorEx<String> getNames() {
    return myNames;
  }

  @NotNull("Vfs must be initialized")
  PersistentFSRecordsStorage getRecords() {
    return myRecords;
  }

  @NotNull
  IntList getFreeRecords() {
    synchronized (myFreeRecords) {
      return new IntArrayList(myFreeRecords);
    }
  }

  long getTimestamp() throws IOException {
    return myRecords.getTimestamp();
  }

  /**
   * @return id of record to re-use, or -1 if no records for reuse remain
   */
  int reserveFreeRecord() {
    synchronized (myFreeRecords) {
      return myFreeRecords.isEmpty() ? -1 : myFreeRecords.removeInt(myFreeRecords.size() - 1);
    }
  }

  void createBrokenMarkerFile(@Nullable Throwable reason) {
    final File brokenMarker = myPersistentFSPaths.getCorruptionMarkerFile();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (@SuppressWarnings("ImplicitDefaultCharsetUsage") PrintStream stream = new PrintStream(out)) {
      new Exception().printStackTrace(stream);
      if (reason != null) {
        stream.print("\nReason:\n");
        reason.printStackTrace(stream);
      }
    }
    LOG.info("Creating VFS corruption marker; Trace=\n" + out);

    try (@SuppressWarnings("ImplicitDefaultCharsetUsage") FileWriter writer = new FileWriter(brokenMarker)) {
      writer.write("These files are corrupted and must be rebuilt from the scratch on next startup");
    }
    catch (IOException ignored) {
    }  // No luck.
  }

  @TestOnly
  int getPersistentModCount() {
    return myRecords.getGlobalModCount();
  }

  void markDirty() throws IOException {
    if (!myDirty) {
      myDirty = true;
      myRecords.setConnectionStatus(PersistentFSHeaders.CONNECTED_MAGIC);
    }
  }

  int getModificationCount() {
    return myRecords.getGlobalModCount();
  }

  void doForce() throws IOException {
    // avoid NPE when close has already taken place
    if (myNames != null && myFlushingFuture != null) {
      if (myNames instanceof Forceable) {
        ((Forceable)myNames).force();
      }
      myAttributesStorage.force();
      myContents.force();
      if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
      markClean();      //TODO RC: shouldn't markClean() be _after_ myRecords.close()?
      myRecords.force();
    }
  }

  // must not be run under write lock to avoid other clients wait for read lock
  private void flush() {
    if (isDirty() && !HeavyProcessLatch.INSTANCE.isRunning()) {
      try {
        doForce();
      }
      catch (IOException e) {
        handleError(e);
        throw new RuntimeException(e);
      }
    }
  }

  public boolean isDirty() {
    return myDirty || ((Forceable)myNames).isDirty() || myAttributesStorage.isDirty() || myContents.isDirty() || myRecords.isDirty() ||
           myContentHashesEnumerator != null && myContentHashesEnumerator.isDirty();
  }

  void closeFiles() throws IOException {
    if (myFlushingFuture != null) {
      myFlushingFuture.cancel(false);
    }

    markClean(); //TODO RC: shouldn't markClean() be the last statement, after storages close?
    closeStorages(myRecords,
                  myNames,
                  myAttributesStorage,
                  myContentHashesEnumerator,
                  myContents);
  }

  @NotNull
  PersistentFSPaths getPersistentFSPaths() {
    return myPersistentFSPaths;
  }

  public void markRecordAsModified(int fileId) throws IOException {
    getRecords().markRecordAsModified(fileId);
    markDirty();
  }

  static void closeStorages(@Nullable PersistentFSRecordsStorage records,
                            @Nullable ScannableDataEnumeratorEx<String> names,
                            @Nullable AbstractAttributesStorage attributes,
                            @Nullable ContentHashEnumerator contentHashesEnumerator,
                            @Nullable RefCountingContentStorage contents) throws IOException {
    if (names instanceof Closeable) {//implies != null
      ((Closeable)names).close();
    }

    if (attributes != null) {
      attributes.close();
      //Disposer.dispose(attributes);
    }

    if (contents != null) {
      Disposer.dispose(contents);
    }

    if (contentHashesEnumerator != null) {
      contentHashesEnumerator.close();
    }

    if (records != null) {
      records.close();
    }
  }

  void markClean() throws IOException {
    // no synchronization, it's ok to have race here
    if (myDirty) {
      myDirty = false;
      myRecords.setConnectionStatus(myCorrupted.get()
                                    ? PersistentFSHeaders.CORRUPTED_MAGIC
                                    : PersistentFSHeaders.SAFELY_CLOSED_MAGIC);
    }
  }

  int getAttributeId(@NotNull String attId) throws IOException {
    // do not invoke FSRecords.requestVfsRebuild under read lock to avoid deadlock
    return myAttributesList.getIdRaw(attId) + FIRST_ATTR_ID_OFFSET;
  }

  @Contract("_->fail")
  void handleError(@NotNull Throwable e) throws RuntimeException, Error {
    // No need to forcibly mark VFS corrupted if it is already shut down
    try {
      if (myCorrupted.compareAndSet(false, true)) {
        createBrokenMarkerFile(e);
        if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
          showCorruptionNotification();
        }
        doForce();
      }
    }
    catch (IOException ioException) {
      LOG.error(ioException);
    }

    ExceptionUtil.rethrow(e);
  }

  static class AttrPageAwareCapacityAllocationPolicy extends CapacityAllocationPolicy {
    boolean myAttrPageRequested;

    @Override
    public int calculateCapacity(int requiredLength) {   // 20% for growth
      return Math.max(myAttrPageRequested ? 8 : 32, Math.min((int)(requiredLength * 1.2), (requiredLength / 1024 + 1) * 1024));
    }
  }

  /**
   * @param id - file id, name id, any other positive id
   */
  static void ensureIdIsValid(int id) {
    assert id > 0 : id;
  }

  private static void showCorruptionNotification() {
    NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
      .createNotification(CoreBundle.message("vfs.corruption.notification.title"),
                          CoreBundle.message("vfs.corruption.notification.text"),
                          NotificationType.INFORMATION)
      .addAction(ActionManager.getInstance().getAction("RestartIde"))
      .notify(null);
  }
}
