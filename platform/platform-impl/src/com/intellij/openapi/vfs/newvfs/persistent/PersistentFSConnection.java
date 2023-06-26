// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.core.CoreBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.io.GentleFlusherBase;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.persistent.intercept.*;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.storage.CapacityAllocationPolicy;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.io.storage.RefCountingContentStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Indexes;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@ApiStatus.Internal
public final class PersistentFSConnection {
  private static final Logger LOG = Logger.getInstance(PersistentFSConnection.class);

  static final int RESERVED_ATTR_ID = 0;
  static final AttrPageAwareCapacityAllocationPolicy REASONABLY_SMALL = new AttrPageAwareCapacityAllocationPolicy();

  private static final boolean USE_GENTLE_FLUSHER = getBooleanProperty("vfs.flushing.use-gentle-flusher", true);

  /**
   * After how many errors ('corruptions') insist on restarting IDE? I.e. we schedule
   * VFS rebuild and _suggest_ restart IDE on the first error detected -- but it is
   * just a suggestion. After that many errors, we show a modal dialog and insist on
   * restart now, since that many errors severely affect IDE operation.
   */
  private static final int INSIST_TO_RESTART_AFTER_ERRORS_COUNT = getIntProperty("vfs.insist-to-restart-after-n-errors", 1000);


  private final IntList myFreeRecords;

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

  private final Closeable flushingTask;

  /** accessed under {@link #r}/{@link #w} */
  private final AtomicInteger corruptionsDetected = new AtomicInteger();

  PersistentFSConnection(@NotNull PersistentFSPaths paths,
                         @NotNull PersistentFSRecordsStorage records,
                         @NotNull ScannableDataEnumeratorEx<String> names,
                         @NotNull AbstractAttributesStorage attributes,
                         @NotNull RefCountingContentStorage contents,
                         @Nullable ContentHashEnumerator contentHashesEnumerator,
                         @NotNull SimpleStringPersistentEnumerator enumeratedAttributes,
                         @NotNull IntList freeRecords,
                         @NotNull List<ConnectionInterceptor> interceptors) throws IOException {
    if (!(names instanceof Forceable) || !(names instanceof Closeable)) {
      //RC: there is no simple way to specify type like DataEnumerator & Forceable & Closeable in java,
      //    hence the runtime check here (and in methods below calling Forceable/Closeable methods).
      //    This is needed only during transition period, while we're experimenting with plugging in
      //    different names impls -- after we'll decide which impl is the best, explicit type could be specified here
      throw new IllegalArgumentException("names(" + names + ") must implement Forceable & Closeable");
    }
    myRecords = wrapRecords(records, interceptors);
    myNames = names;
    myAttributesStorage = wrapAttributes(attributes, interceptors);
    myContents = wrapContents(contents, interceptors);
    myContentHashesEnumerator = contentHashesEnumerator;
    myPersistentFSPaths = paths;
    myFreeRecords = freeRecords;
    myEnumeratedAttributes = enumeratedAttributes;

    if (FSRecords.BACKGROUND_VFS_FLUSH) {
      //MAYBE RC: move the flushing up, to FSRecordsImpl?
      final ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();
      flushingTask = USE_GENTLE_FLUSHER ?
                     new GentleVFSFlusher(scheduler) :
                     new ClassicVFSFlusher(scheduler);
    }
    else {
      flushingTask = null;
    }
  }

  private static RefCountingContentStorage wrapContents(RefCountingContentStorage contents, List<ConnectionInterceptor> interceptors) {
    var contentInterceptors = interceptors.stream()
      .filter(ContentsInterceptor.class::isInstance)
      .map(ContentsInterceptor.class::cast)
      .toList();
    return InterceptorInjection.INSTANCE.injectInContents(contents, contentInterceptors);
  }

  private static AbstractAttributesStorage wrapAttributes(AbstractAttributesStorage attributes, List<ConnectionInterceptor> interceptors) {
    var attributesInterceptors = interceptors.stream()
      .filter(AttributesInterceptor.class::isInstance)
      .map(AttributesInterceptor.class::cast)
      .toList();
    return InterceptorInjection.INSTANCE.injectInAttributes(attributes, attributesInterceptors);
  }

  private static PersistentFSRecordsStorage wrapRecords(PersistentFSRecordsStorage records, List<ConnectionInterceptor> interceptors) {
    var recordsInterceptors = interceptors.stream()
      .filter(RecordsInterceptor.class::isInstance)
      .map(RecordsInterceptor.class::cast)
      .toList();
    return InterceptorInjection.INSTANCE.injectInRecords(records, recordsInterceptors);
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
    if (myNames != null && flushingTask != null) {
      if (myNames instanceof Forceable) {
        ((Forceable)myNames).force();
      }
      myAttributesStorage.force();
      myContents.force();
      if (myContentHashesEnumerator != null) {
        myContentHashesEnumerator.force();
      }
      writeConnectionState();
      myRecords.force();
    }
  }

  public boolean isDirty() {
    return myDirty || ((Forceable)myNames).isDirty() || myAttributesStorage.isDirty() || myContents.isDirty() || myRecords.isDirty() ||
           myContentHashesEnumerator != null && myContentHashesEnumerator.isDirty();
  }

  int corruptionsDetected() {
    return corruptionsDetected.get();
  }

  void closeFiles() throws IOException {
    if (flushingTask != null) {
      flushingTask.close();
    }

    writeConnectionState();
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

  //TODO RC: we use it to mark file record modified there something derived is modified -- i.e. children attribute
  //         or content. This looks suspicious to me: why we need to update _file_ record version in those cases?
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

  private void writeConnectionState() throws IOException {
    // no synchronization, it's ok to have race here
    if (myDirty) {
      myDirty = false;
      myRecords.setConnectionStatus(corruptionsDetected.get() > 0 ?
                                    PersistentFSHeaders.CORRUPTED_MAGIC :
                                    PersistentFSHeaders.SAFELY_CLOSED_MAGIC);
    }
  }

  int getAttributeId(@NotNull String attributeId) {
    int enumeratedAttributeId = myEnumeratedAttributes.enumerate(attributeId);
    if (enumeratedAttributeId > AbstractAttributesStorage.MAX_ATTRIBUTE_ID) {
      throw new IllegalStateException(
        "attribute[" + attributeId + "] assigned id[" + enumeratedAttributeId + "] which is above max " +
        AbstractAttributesStorage.MAX_ATTRIBUTE_ID +
        ". Current list of attributes: " + myEnumeratedAttributes.dumpToString()
      );
    }
    return enumeratedAttributeId;
  }

  void markAsCorruptedAndScheduleRebuild(@NotNull Throwable cause) throws RuntimeException, Error {
    try {
      int corruptions = corruptionsDetected.incrementAndGet();
      if (corruptions == 1) {
        scheduleVFSRebuild(cause.getMessage(), cause);
        if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
          showCorruptionNotification(/*insist: */ false);
        }
        doForce();//forces connectionStatus=CORRUPTED to be written on disk
      }
      else if (corruptions == INSIST_TO_RESTART_AFTER_ERRORS_COUNT) {
        if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
          showCorruptionNotification(/*insist: */ true);
        }
      }
    }
    catch (IOException ioException) {
      LOG.error(ioException);
    }
  }

  void scheduleVFSRebuild(@Nullable String message,
                          @Nullable Throwable errorCause) {
    final VFSCorruptedException corruptedException = new VFSCorruptedException(
      message == null ? "(No specific reason of corruption was given)" : message,
      errorCause
    );
    if (errorCause == null) {
      //Without 'errorCause' it is not an error, but, likely, an explicit 'invalidateCache' call:
      // no need to print stacktrace then, also no need for a WARN
      LOG.info("VFS is corrupted; Creating VFS corruption marker: " + message);
    }
    else {
      LOG.warn("VFS is corrupted; Creating VFS corruption marker", corruptedException);
    }

    try {
      final Path brokenMarker = myPersistentFSPaths.getCorruptionMarkerFile();
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (PrintStream stream = new PrintStream(out, false, UTF_8)) {
        stream.println("VFS files are corrupted and must be rebuilt from the scratch on next startup");
        corruptedException.printStackTrace(stream);
      }
      Files.write(
        brokenMarker,
        out.toByteArray(),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE
      );
    }
    catch (IOException ex) {// No luck:
      LOG.info("Can't create VFS corruption marker", ex);
    }
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

  private static void showCorruptionNotification(boolean insisting) {
    AnAction restartIdeAction = ActionManager.getInstance().getAction("RestartIde");
    NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches");
    if (insisting) {
      notificationGroup.createNotification(
          CoreBundle.message("vfs.corruption.notification.title"),
          CoreBundle.message("vfs.corruption.notification.text"),
          INFORMATION
        )
        .setImportant(true)
        .addAction(restartIdeAction)
        .notify(null);
    }
    else {
      notificationGroup.createNotification(
          CoreBundle.message("vfs.corruption.notification.insist.title"),
          CoreBundle.message("vfs.corruption.notification.insist.text"),
          ERROR
        )
        .addAction(restartIdeAction)
        .notify(null);
    }
  }

  /**
   * Legacy flushing implementation: do some basic precautions against contention, i.e. wait for a period without modifications
   */
  private class ClassicVFSFlusher implements Runnable, Closeable {

    /** How often, on average, flush each index to the disk */
    public static final long FLUSHING_PERIOD_MS = SECONDS.toMillis(5);

    private int lastModCount;
    private final Future<?> scheduledFuture;


    private ClassicVFSFlusher(final @NotNull ScheduledExecutorService scheduler) {
      this.scheduledFuture = scheduler.scheduleWithFixedDelay(this, FLUSHING_PERIOD_MS, FLUSHING_PERIOD_MS, MILLISECONDS);
    }

    @Override
    public void run() {
      if (lastModCount == myRecords.getGlobalModCount()) {
        if (isDirty() && !HeavyProcessLatch.INSTANCE.isRunning()) {
          try {
            doForce();
          }
          catch (IOException e) {
            markAsCorruptedAndScheduleRebuild(e);
            ExceptionUtil.rethrow(e);
          }
        }
      }
      lastModCount = myRecords.getGlobalModCount();
    }

    @Override
    public void close() {
      scheduledFuture.cancel(false);
    }
  }

  /**
   * Gentle flusher impl: uses storage lock .getQueueLength() to determine potential contention and limit it.
   * <p>
   * More details in a {@link GentleFlusherBase} javadocs
   */
  private class GentleVFSFlusher extends GentleFlusherBase {
    /** How often, on average, flush each index to the disk */
    private static final long FLUSHING_PERIOD_MS = SECONDS.toMillis(FlushingDaemon.FLUSHING_PERIOD_IN_SECONDS);


    private static final int MIN_CONTENTION_QUOTA = 2;
    private static final int INITIAL_CONTENTION_QUOTA = 16;
    private static final int MAX_CONTENTION_QUOTA = 32;


    private int lastModCount;

    private long lastSuccessfulFlushTimestampMs = 0;

    private GentleVFSFlusher(final @NotNull ScheduledExecutorService scheduler) {
      super("VFSFlusher",
            scheduler, FLUSHING_PERIOD_MS,
            MIN_CONTENTION_QUOTA, MAX_CONTENTION_QUOTA, INITIAL_CONTENTION_QUOTA,
            TelemetryManager.getMeter(Indexes)
      );
    }

    @Override
    protected boolean betterPostponeFlushNow() {
      //RC: Basically, we're trying to flush 'if idle': i.e. we don't want to issue a flush if
      //    somebody actively writes to VFS because flush will slow them down, if not stall
      //    them -- and (regular) flush is less important than e.g. a current UI task. So we
      //    attempt to flush only if there were _no updates_ in VFS since the last invocation
      //    of this method:
      final int currentModCount = myRecords.getGlobalModCount();
      if (lastModCount != currentModCount) {
        lastModCount = currentModCount;
        return true;
      }
      return false;
    }

    @Override
    protected FlushResult flushAsMuchAsPossibleWithinQuota(final /*InOut*/ IntRef contentionQuota) throws IOException {
      if (!isDirty()) {
        return FlushResult.NOTHING_TO_FLUSH_NOW;
      }

      if (System.currentTimeMillis() - lastSuccessfulFlushTimestampMs < FLUSHING_PERIOD_MS) {
        return FlushResult.NOTHING_TO_FLUSH_NOW;
      }

      int unspentContentionQuota = contentionQuota.get();
      try {
        unspentContentionQuota -= competingThreads();
        if (unspentContentionQuota < 0) {
          return FlushResult.HAS_MORE_TO_FLUSH;
        }

        //RC: code below is a copy of doFlush() method, but interleaved with contention quota checking:

        if (myNames instanceof Forceable) {
          ((Forceable)myNames).force();

          unspentContentionQuota -= competingThreads();
          if (unspentContentionQuota < 0) {
            return FlushResult.HAS_MORE_TO_FLUSH;
          }
        }

        myAttributesStorage.force();

        unspentContentionQuota -= competingThreads();
        if (unspentContentionQuota < 0) {
          return FlushResult.HAS_MORE_TO_FLUSH;
        }

        myContents.force();

        unspentContentionQuota -= competingThreads();
        if (unspentContentionQuota < 0) {
          return FlushResult.HAS_MORE_TO_FLUSH;
        }

        if (myContentHashesEnumerator != null) {
          myContentHashesEnumerator.force();

          unspentContentionQuota -= competingThreads();
          if (unspentContentionQuota < 0) {
            return FlushResult.HAS_MORE_TO_FLUSH;
          }
        }

        writeConnectionState();
        myRecords.force();

        unspentContentionQuota -= competingThreads();

        lastSuccessfulFlushTimestampMs = System.currentTimeMillis();
        return FlushResult.FLUSHED_ALL;
      }
      finally {
        contentionQuota.set(unspentContentionQuota);
      }
    }

    @Override
    public boolean hasSomethingToFlush() {
      return isDirty();
    }

    private static int competingThreads() {
      //TODO RC: this is a bit of a hack -- I rely on the implicit knowledge that now all storages use
      //         PagedFileStorage under the hood, and PFS uses StorageLockContext default instance. This
      //         is not true for other implementations of underlying storages: i.e. FilePageCacheLockFree,
      //         and/or memory-mapped file based. Hence, this estimation will gradually lose applicability
      //         as we will transition to those new storages implementation.

      final ReentrantReadWriteLock storageLock = StorageLockContext.defaultContextLock();

      if (storageLock.isWriteLocked()) {
        return storageLock.getQueueLength() + 1;
      }
      else {
        final int readers = storageLock.getReadLockCount();
        return storageLock.getQueueLength() + readers;
      }
    }
  }

  /** Created to make stacktraces easily recognizable in logs */
  private static class VFSCorruptedException extends Exception {
    VFSCorruptedException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
