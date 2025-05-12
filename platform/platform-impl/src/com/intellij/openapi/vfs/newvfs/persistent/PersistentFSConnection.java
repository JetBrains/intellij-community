// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.core.CoreBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.GentleFlusherBase;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoveryInfo;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.ThreadSafeThrottler;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.io.storage.VFSContentStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Indexes;
import static com.intellij.util.SystemProperties.getIntProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.*;

@ApiStatus.Internal
public final class PersistentFSConnection {
  private static final Logger LOG = Logger.getInstance(PersistentFSConnection.class);

  /**
   * After how many errors ('corruptions') insist on restarting IDE? I.e. we schedule
   * VFS rebuild and _suggest_ restart IDE on the first error detected -- but it is
   * just a suggestion. After that many errors, we show a modal dialog and insist on
   * restart now, since that many errors severely affect IDE operation.
   */
  private static final int INSIST_TO_RESTART_AFTER_ERRORS_COUNT = getIntProperty("vfs.insist-to-restart-after-n-errors", 1000);


  private final @NotNull NotNullLazyValue<? extends IntList> freeRecords;

  private final @NotNull PersistentFSPaths persistentFSPaths;

  private final @NotNull VFSAttributesStorage attributesStorage;
  private final @NotNull VFSContentStorage contentStorage;

  private final @NotNull PersistentFSRecordsStorage records;

  private final @NotNull ScannableDataEnumeratorEx<String> namesEnumerator;
  /**
   * Enumerator for repeating strings used in attributes. Used to support
   * {@link AttributeInputStream#readEnumeratedString()}
   * {@link AttributeOutputStream#writeEnumeratedString(String)}
   */
  private final @NotNull SimpleStringPersistentEnumerator enumeratedAttributes;

  private volatile boolean closed = false;

  /** How many errors were detected (during the use) that are likely caused by VFS corruptions -- i.e. broken internal invariants */
  private final AtomicInteger corruptionsDetected = new AtomicInteger();

  private final @NotNull VFSRecoveryInfo recoveryInfo;

  PersistentFSConnection(@NotNull PersistentFSPaths paths,
                         @NotNull PersistentFSRecordsStorage records,
                         @NotNull ScannableDataEnumeratorEx<String> names,
                         @NotNull VFSAttributesStorage attributes,
                         @NotNull VFSContentStorage contents,
                         @NotNull SimpleStringPersistentEnumerator enumeratedAttributes,
                         @NotNull NotNullLazyValue<? extends IntList> freeRecords,
                         @NotNull VFSRecoveryInfo info) throws IOException {
    if (!(names instanceof Forceable) || !(names instanceof Closeable)) {
      //RC: there is no simple way to specify type like DataEnumerator & Forceable & Closeable in java,
      //    hence the runtime check here (and in methods below calling Forceable/Closeable methods).
      //    This is needed only during transition period, while we're experimenting with plugging in
      //    different names impls -- after we'll decide which impl is the best, explicit type could be specified here
      throw new IllegalArgumentException("names(" + names + ") must implement Forceable & Closeable");
    }
    this.records = records;
    namesEnumerator = names;
    attributesStorage = attributes;
    contentStorage = contents;
    persistentFSPaths = paths;
    this.freeRecords = freeRecords;
    this.enumeratedAttributes = enumeratedAttributes;
    recoveryInfo = info;
  }

  @VisibleForTesting
  public @NotNull DataEnumerator<String> attributesEnumerator() {
    return enumeratedAttributes;
  }

  int enumerateAttributeId(@NotNull String attributeId) {
    int enumeratedAttributeId = enumeratedAttributes.enumerate(attributeId);
    if (enumeratedAttributeId > VFSAttributesStorage.MAX_ATTRIBUTE_ID) {
      throw new IllegalStateException(
        "attribute[" + attributeId + "] assigned id[" + enumeratedAttributeId + "] which is above max " +
        VFSAttributesStorage.MAX_ATTRIBUTE_ID +
        ". Current list of attributes: " + enumeratedAttributes.dumpToString()
      );
    }
    return enumeratedAttributeId;
  }

  @VisibleForTesting
  public @NotNull VFSContentStorage contents() {
    return contentStorage;
  }

  @VisibleForTesting
  public @NotNull VFSAttributesStorage attributes() {
    return attributesStorage;
  }

  @VisibleForTesting
  public @NotNull ScannableDataEnumeratorEx<String> names() {
    return namesEnumerator;
  }

  public @NotNull PersistentFSRecordsStorage records() {
    return records;
  }

  @NotNull IntList freeRecords() {
    synchronized (freeRecords) {
      return new IntArrayList(freeRecords.getValue());
    }
  }

  long creationTimestamp() throws IOException {
    return records.getTimestamp();
  }

  /**
   * @return id of record to re-use, or -1 if no records for reuse remain
   */
  int reserveFreeRecord() {
    if (!freeRecords.isComputed()) {
      //do not wait until all deleted records are collected -- just allocate new record at the
      // end of the file
      return -1;
    }
    synchronized (freeRecords) {
      IntList records = freeRecords.getValue();
      return records.isEmpty() ? -1 : records.removeInt(records.size() - 1);
    }
  }

  @TestOnly
  int persistentModCount() {
    return records.getGlobalModCount();
  }

  public boolean isDirty() {
    return records.isDirty()
           || ((Forceable)namesEnumerator).isDirty()
           || attributesStorage.isDirty()
           || contentStorage.isDirty();
  }

  @VisibleForTesting
  public void force() throws IOException {
    ((Forceable)namesEnumerator).force();//checked to be a Forceable in ctor
    attributesStorage.force();
    contentStorage.force();
    records.force();
  }

  public boolean isClosed() {
    return closed;
  }

  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }

    force();

    try {//ensure async loading is finished:
      freeRecords.getValue();
    }
    catch (Throwable ex) {
      //not an issue on close, but could provide some insights
      LOG.info("Free records loading is failed", ex);
    }
    try {
      closeStorages(records,
                    namesEnumerator,
                    attributesStorage,
                    contentStorage);
    }
    finally {
      closed = true;
    }
  }

  public @NotNull PersistentFSPaths paths() {
    return persistentFSPaths;
  }

  int corruptionsDetected() {
    return corruptionsDetected.get();
  }

  public @NotNull VFSRecoveryInfo recoveryInfo() {
    return recoveryInfo;
  }

  static void closeStorages(@Nullable PersistentFSRecordsStorage records,
                            @Nullable ScannableDataEnumeratorEx<String> names,
                            @Nullable VFSAttributesStorage attributes,
                            @Nullable VFSContentStorage contents) throws IOException {
    if (names instanceof Closeable) {//implies != null
      ((Closeable)names).close();
    }

    if (attributes != null) {
      attributes.close();
    }

    if (contents != null) {
      contents.close();
    }

    if (records != null) {
      records.close();
    }
  }

  private final ThreadSafeThrottler corruptionNotificationThrottler = new ThreadSafeThrottler(5, MINUTES);

  void markAsCorruptedAndScheduleRebuild(@NotNull Throwable cause) throws RuntimeException, Error {
    try {
      int corruptions = corruptionsDetected.incrementAndGet();
      records.setErrorsAccumulated(corruptions);
      if (corruptions == 1) {
        //Persist ErrorsAccumulated.
        // No need to force() on each error -- we don't bother not persist exact count of errors,
        // but we do want to persist (errors > 0) transition:
        force();
      }
      corruptionNotificationThrottler.runThrottled(System.nanoTime(), () -> {
        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
          boolean insistRestart = (corruptions >= INSIST_TO_RESTART_AFTER_ERRORS_COUNT);
          showCorruptionNotification(insistRestart);
        }
      });
    }
    catch (IOException ioException) {
      LOG.error(ioException);
    }
  }

  @VisibleForTesting
  public static void scheduleVFSRebuild(@NotNull Path corruptionMarkerFile,
                                 @Nullable String message,
                                 @Nullable Throwable errorCause) {
    VFSCorruptedException corruptedException = new VFSCorruptedException(
      message == null ? "(No specific reason of corruption was given)" : message,
      errorCause
    );
    if (errorCause == null) {
      //Without 'errorCause' it is not an error, but, likely, an explicit 'invalidateCache' call:
      // no need to print stacktrace then, also no need for a WARN
      LOG.info("VFS rebuild is requested: creating VFS rebuild marker. Message: " + message);
    }
    else {
      LOG.warn("VFS is corrupted: creating VFS rebuild marker.", corruptedException);
    }

    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (PrintStream stream = new PrintStream(out, false, UTF_8)) {
        stream.println("VFS files are corrupted and must be rebuilt from the scratch on next startup");
        corruptedException.printStackTrace(stream);
      }
      Files.write(
        corruptionMarkerFile,
        out.toByteArray(),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE
      );
    }
    catch (IOException ex) {// No luck:
      LOG.info("Can't create VFS corruption marker", ex);
    }
  }

  @VisibleForTesting
  public void scheduleVFSRebuild(@Nullable String message,
                          @Nullable Throwable errorCause) {
    scheduleVFSRebuild(persistentFSPaths.getCorruptionMarkerFile(), message, errorCause);
  }


  /**
   * @param id - file id, name id, any other positive id
   */
  static void ensureIdIsValid(int id) {
    assert id > 0 : id;
  }

  /** @throws IndexOutOfBoundsException if fileId is outside already allocated file ids */
  void ensureFileIdIsValid(int fileId) throws IndexOutOfBoundsException {
    if (!records.isValidFileId(fileId)) {
      int maxAllocatedID = records.maxAllocatedID();
      throw new IndexOutOfBoundsException("fileId[" + fileId + "] is outside valid/allocated ids range [1.." + maxAllocatedID + "]");
    }
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

  static @NotNull Closeable startFlusher(@NotNull ScheduledExecutorService scheduler,
                                         @NotNull PersistentFSConnection connection,
                                         boolean gentleFlusher) {
    return gentleFlusher ?
           new GentleVFSFlusher(connection, scheduler) :
           new ClassicVFSFlusher(connection, scheduler);
  }

  /**
   * Legacy flushing implementation: do some basic precautions against contention, i.e. wait for a period without modifications
   */
  private static class ClassicVFSFlusher implements Runnable, Closeable {

    /** How often, on average, flush each index to the disk */
    public static final long FLUSHING_PERIOD_MS = SECONDS.toMillis(5);
    private final PersistentFSConnection connection;

    private int lastModCount;
    private final Future<?> scheduledFuture;


    private ClassicVFSFlusher(@NotNull PersistentFSConnection connection,
                              @NotNull ScheduledExecutorService scheduler) {
      this.scheduledFuture = scheduler.scheduleWithFixedDelay(this, FLUSHING_PERIOD_MS, FLUSHING_PERIOD_MS, MILLISECONDS);
      this.connection = connection;
    }

    @Override
    public void run() {
      if (lastModCount == connection.records.getGlobalModCount()) {
        if (connection.isDirty() && !HeavyProcessLatch.INSTANCE.isRunning()) {
          try {
            connection.force();
          }
          catch (AlreadyDisposedException | RejectedExecutionException e) {
            LOG.warn("Stop flushing: pool is shutting down or whole application is closing", e);
            scheduledFuture.cancel(false);
          }
          catch (Throwable t) {
            LOG.error("Unhandled exception during flush (reschedule regularly)", t);
          }
        }
      }
      lastModCount = connection.records.getGlobalModCount();
    }

    @Override
    public void close() {
      scheduledFuture.cancel(false);
    }
  }

  /**
   * Gentle flusher impl: uses storage lock .getQueueLength() to determine potential contention and limit it.
   * TODO RC: actually with most of the storages in VFS is memory-mapped-file-based now, this Flusher becomes
   *          almost equivalent to ClassicVFSFlusher -- but way more complex.
   * <p>
   * More details in a {@link GentleFlusherBase} javadocs
   */
  private static final class GentleVFSFlusher extends GentleFlusherBase {
    /** How often, on average, flush each index to the disk */
    private static final long FLUSHING_PERIOD_MS = SECONDS.toMillis(FlushingDaemon.FLUSHING_PERIOD_IN_SECONDS);


    private static final int MIN_CONTENTION_QUOTA = 2;
    private static final int INITIAL_CONTENTION_QUOTA = 16;
    private static final int MAX_CONTENTION_QUOTA = 32;

    private final PersistentFSConnection connection;

    private int lastModCount;

    private long lastSuccessfulFlushTimestampMs = 0;

    private GentleVFSFlusher(@NotNull PersistentFSConnection connection,
                             @NotNull ScheduledExecutorService scheduler) {
      super("VFSFlusher",
            scheduler, FLUSHING_PERIOD_MS,
            MIN_CONTENTION_QUOTA, MAX_CONTENTION_QUOTA, INITIAL_CONTENTION_QUOTA,
            TelemetryManager.getInstance().getMeter(Indexes)
      );
      this.connection = connection;
    }

    @Override
    protected boolean betterPostponeFlushNow() {
      //RC: Basically, we're trying to flush 'if idle': i.e. we don't want to issue a flush if
      //    somebody actively writes to VFS because flush will slow them down, if not stall
      //    them -- and (regular) flush is less important than e.g. a current UI task. So we
      //    attempt to flush only if there were _no updates_ in VFS since the last invocation
      //    of this method:
      int currentModCount = connection.records.getGlobalModCount();
      if (lastModCount != currentModCount) {
        lastModCount = currentModCount;
        return true;
      }
      return false;
    }

    @Override
    protected FlushResult flushAsMuchAsPossibleWithinQuota(/*InOut*/ IntRef contentionQuota) throws IOException {
      if (!connection.isDirty()) {
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

        if (connection.namesEnumerator instanceof Forceable) {
          ((Forceable)connection.namesEnumerator).force();

          unspentContentionQuota -= competingThreads();
          if (unspentContentionQuota < 0) {
            return FlushResult.HAS_MORE_TO_FLUSH;
          }
        }

        connection.attributesStorage.force();

        unspentContentionQuota -= competingThreads();
        if (unspentContentionQuota < 0) {
          return FlushResult.HAS_MORE_TO_FLUSH;
        }

        connection.contentStorage.force();

        unspentContentionQuota -= competingThreads();
        if (unspentContentionQuota < 0) {
          return FlushResult.HAS_MORE_TO_FLUSH;
        }

        connection.records.force();

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
      return connection.isDirty();
    }

    private static int competingThreads() {
      //FIXME RC: this is totally incorrect now: code relies on implicit knowledge that all storages use
      //          PagedFileStorage under the hood, and PFS uses StorageLockContext default instance. This
      //          is not true anymore, since VFS uses either FilePageCacheLockFree or mmapped-file based
      //          storages now:

      ReentrantReadWriteLock storageLock = StorageLockContext.defaultContextLock();

      if (storageLock.isWriteLocked()) {
        return storageLock.getQueueLength() + 1;
      }
      else {
        int readers = storageLock.getReadLockCount();
        return storageLock.getQueueLength() + readers;
      }
    }
  }

  /** Created to make stacktraces easily recognizable in logs */
  private static final class VFSCorruptedException extends Exception {
    VFSCorruptedException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
