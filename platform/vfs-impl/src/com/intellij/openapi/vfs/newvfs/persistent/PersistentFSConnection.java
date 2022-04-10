// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.core.CoreBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import com.intellij.util.io.storage.CapacityAllocationPolicy;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.io.storage.RefCountingContentStorage;
import com.intellij.util.io.storage.Storage;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
  private final Storage myAttributes;
  @NotNull
  private final RefCountingContentStorage myContents;
  @NotNull
  private final PersistentFSRecordsStorage myRecords;
  @Nullable
  private final ContentHashEnumerator myContentHashesEnumerator;
  private final PersistentStringEnumerator myNames;
  private final @NotNull SimpleStringPersistentEnumerator myEnumeratedAttributes;

  private final AtomicInteger myLocalModificationCount;
  private volatile boolean myDirty;
  /**
   * accessed under {@link #r}/{@link #w}
   */
  private final ScheduledFuture<?> myFlushingFuture;
  /**
   * accessed under {@link #r}/{@link #w}
   */
  private boolean myCorrupted;

  PersistentFSConnection(@NotNull PersistentFSPaths paths,
                         @NotNull PersistentFSRecordsStorage records,
                         @NotNull PersistentStringEnumerator names,
                         @NotNull Storage attributes,
                         @NotNull RefCountingContentStorage contents,
                         @Nullable ContentHashEnumerator contentHashesEnumerator,
                         @NotNull SimpleStringPersistentEnumerator enumeratedAttributes,
                         @NotNull IntList freeRecords,
                         AtomicInteger localModificationCount,
                         boolean markDirty) throws IOException {
    myRecords = records;
    myNames = names;
    myAttributes = attributes;
    myContents = contents;
    myContentHashesEnumerator = contentHashesEnumerator;
    myPersistentFSPaths = paths;
    myFreeRecords = freeRecords;
    myLocalModificationCount = localModificationCount;
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
          if (lastModCount == myLocalModificationCount.get()) {
            flush();
          }
          lastModCount = myLocalModificationCount.get();
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
  Storage getAttributes() {
    return myAttributes;
  }

  @NotNull("Vfs must be initialized")
  PersistentStringEnumerator getNames() {
    return myNames;
  }

  @NotNull("Vfs must be initialized")
  PersistentFSRecordsStorage getRecords() {
    return myRecords;
  }

  @NotNull
  IntList getFreeRecords() {
    return myFreeRecords;
  }

  long getTimestamp() throws IOException {
    return myRecords.getTimestamp();
  }

  int getFreeRecord() {
    return myFreeRecords.isEmpty() ? 0 : myFreeRecords.removeInt(myFreeRecords.size() - 1);
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

  int getPersistentModCount() throws IOException {
    return myRecords.getGlobalModCount();
  }

  int incGlobalModCount() throws IOException {
    incLocalModCount();
    return myRecords.incGlobalModCount();
  }

  void markDirty() throws IOException {
    assert FSRecords.lock.isWriteLocked();
    if (!myDirty) {
      myDirty = true;
      myRecords.setConnectionStatus(PersistentFSHeaders.CONNECTED_MAGIC);
    }
  }

  void incLocalModCount() throws IOException {
    markDirty();
    //noinspection NonAtomicOperationOnVolatileField
    myLocalModificationCount.incrementAndGet();
  }

  int getLocalModificationCount() {
    return myLocalModificationCount.get();
  }

  void doForce() throws IOException {
    // avoid NPE when close has already taken place
    if (myNames != null && myFlushingFuture != null) {
      myNames.force();
      myAttributes.force();
      myContents.force();
      if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
      markClean();
      myRecords.force();
    }
  }

  // must not be run under write lock to avoid other clients wait for read lock
  private void flush() {
    if (isDirty() && !HeavyProcessLatch.INSTANCE.isRunning()) {
      FSRecords.readAndHandleErrors(() -> {
        doForce();
        return null;
      });
    }
  }

  public boolean isDirty() {
    return myDirty || myNames.isDirty() || myAttributes.isDirty() || myContents.isDirty() || myRecords.isDirty() ||
           myContentHashesEnumerator != null && myContentHashesEnumerator.isDirty();
  }

  void closeFiles() throws IOException {
    if (myFlushingFuture != null) {
      myFlushingFuture.cancel(false);
    }

    markClean();
    closeStorages(myRecords,
                  myNames,
                  myAttributes,
                  myContentHashesEnumerator,
                  myContents);
  }

  @NotNull
  PersistentFSPaths getPersistentFSPaths() {
    return myPersistentFSPaths;
  }

  public void incModCount(int fileId) throws IOException {
    int count = incGlobalModCount();
    getRecords().setModCount(fileId, count);
  }

  static void closeStorages(@Nullable PersistentFSRecordsStorage records,
                            @Nullable PersistentStringEnumerator names,
                            @Nullable Storage attributes,
                            @Nullable ContentHashEnumerator contentHashesEnumerator,
                            @Nullable RefCountingContentStorage contents) throws IOException {
    if (names != null) {
      names.close();
    }

    if (attributes != null) {
      Disposer.dispose(attributes);
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

  // either called from FlushingDaemon thread under read lock, or from handleError under write lock
  void markClean() throws IOException {
    assert FSRecords.lock.isWriteLocked() || FSRecords.lock.getReadHoldCount() != 0;
    if (myDirty) {
      myDirty = false;
      // writing here under read lock is safe because no-one else read or write at this offset (except at startup)
      myRecords.setConnectionStatus(myCorrupted
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
    assert FSRecords.lock.getReadHoldCount() == 0;

    // No need to forcibly mark VFS corrupted if it is already shut down
    try {
      FSRecords.write(() -> {
        if (!myCorrupted) {
          createBrokenMarkerFile(e);
          myCorrupted = true;
          if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
            showCorruptionNotification();
          }
          doForce();
        }
      });
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
