// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.Processor;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This class is just an 'instance holder' -- actual implementation is an {@link FSRecordsImpl} instance,
 * all methods delegate to it.
 * This is very low-level API, intended to be used only by VFS implementation code only -- mainly
 * {@link PersistentFSImpl}. Inside VFS implementation all the calls should go through the instance
 * obtained by {@link #connect()}. Current usages of static methods should be gradually migrated.
 * {@link FSRecords#getInstance()} method intended to help with migration.
 * <p>
 * Avoid use of this class outside of VFS impl code, inside VFS impl code migrate to use the
 * {@link FSRecordsImpl} _instance_. Instance should be obtained by {@link #connect()} somewhere
 * up the stack, and passed through everywhere needed. Static {@link #getInstance()} method helps
 * transition to use instance in cases there calling code doesn't supply apt {@link FSRecordsImpl} instance.
 * <p>
 * If you want a new and independent {@link FSRecordsImpl} instance (e.g. for tests) -- use
 * {@link FSRecordsImpl#connect(Path)} methods. {@link FSRecords#connect()} methods install 'default'
 * shared instance available for everyone via {@link FSRecords#getInstance()}.
 * <p>
 * At the end I plan to convert {@link FSRecordsImpl} a regular {@link com.intellij.openapi.components.Service},
 * with a usual .getInstance() method.
 */
@ApiStatus.Internal
public final class FSRecords {
  static final Logger LOG = Logger.getInstance(FSRecords.class);
  static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(30));

  /** Not a constant value but just key for a value, because could be changed (see TurbochargedSharedIndexes) */
  public static final String IDE_USE_FS_ROOTS_DATA_LOADER = "idea.fs.roots.data.loader";


  /**
   * Initially recordId=0 was used as a storage header record, hence fileId=0 was reserved.
   * New storages still reserve id=0, even though they usually separate the header from
   * records explicitly -- because it is consistent with id=0 being used as NULL in other parts
   * of app, e.g. in DataEnumerator
   */
  public static final int NULL_FILE_ID = 0;

  /**
   * fileId of artificial 'directory' all FS roots are attached to as children. This fs-record
   * is special in a few ways -- e.g. it has CHILDREN storage format different from regular
   * directories (see {@link PersistentFSTreeAccessor#findOrCreateRootRecord(String)}
   */
  public static final int ROOT_FILE_ID = NULL_FILE_ID + 1;
  public static final int MIN_REGULAR_FILE_ID = ROOT_FILE_ID + 1;


  /** singleton instance */
  private static volatile FSRecordsImpl impl;

  /** @return path to the directory there all VFS files are located */
  public static @NotNull Path getCacheDir() {
    String dir = System.getProperty("caches_dir");
    return dir == null ? Path.of(PathManager.getSystemPath(), "caches") : Path.of(dir);
  }

  private FSRecords() {
    throw new AssertionError("Not for instantiation");
  }

  //========== lifecycle: =====================================================

  /**
   * This method creates new {@link FSRecordsImpl} instance, and <b>set it as default instance, available
   * through {@link #getInstance()}</b>.
   * If you want 'private' VFS instance -- e.g. for testing -- use {@link FSRecordsImpl#connect(Path, FSRecordsImpl.ErrorHandler)}
   * method(s) instead, because this method changes global state, hence could affect the tests following current.
   */
  public static synchronized FSRecordsImpl connect() throws UncheckedIOException {
    return connect(FSRecordsImpl.getDefaultErrorHandler());
  }

  /**
   * This method creates new {@link FSRecordsImpl} instance, and set it as default instance, available
   * through {@link #getInstance()}.
   * If you want 'private' VFS instance -- e.g. for testing -- use {@link FSRecordsImpl#connect(Path, FSRecordsImpl.ErrorHandler)}
   * method(s) instead, because this method changes global state, hence could affect the tests following current.
   */
  public static synchronized FSRecordsImpl connect(@NotNull FSRecordsImpl.ErrorHandler errorHandler) throws UncheckedIOException {
    FSRecordsImpl oldImpl = impl;
    if (oldImpl != null && !oldImpl.isClosed()) {
      //MAYBE RC: provide reconnect()
      throw new IllegalStateException(
        "Can't connect default VFS instance -- default VFS instance is already set up" +
        " and not yet disposed. " +
        "Current instance: " + oldImpl
      );
    }
    FSRecordsImpl _impl = FSRecordsImpl.connect(getCacheDir(), errorHandler);
    impl = _impl;
    return _impl;
  }

  private static @NotNull FSRecordsImpl implOrFail() {
    FSRecordsImpl _impl = impl;
    if (_impl == null) {
      throw new ServiceNotReadyException("VFS instance is not initialized yet");
    }
    else if (_impl.isClosed()) {
      //guaranteed to fail, and provides diagnostic:
      _impl.checkNotClosed();
    }

    return _impl;
  }

  /**
   * @throws ServiceNotReadyException if VFS is not yet initialized (connected)
   * @throws AlreadyDisposedException if VFS is disposed
   * @throws com.intellij.openapi.progress.ProcessCanceledException (wrapping AlreadyDisposedException) if VFS is disposed, and
   * we're now running under an progress indicator or Job
   */
  public static @NotNull FSRecordsImpl getInstance() throws AlreadyDisposedException {
    return implOrFail();
  }

  static @Nullable FSRecordsImpl getInstanceIfCreatedAndNotDisposed() {
    FSRecordsImpl _impl = impl;
    return _impl == null || _impl.isClosed() ? null : _impl;
  }

  //========== FS records-as-a-whole properties: ==============================

  public static long getCreationTimestamp() {
    return getInstance().getCreationTimestamp();
  }

  //========== modifications counters: ========================================

  public static long getNamesIndexModCount() {
    return implOrFail().getInvertedNameIndexModCount();
  }


  //========== FS records persistence: ========================================

  @TestOnly
  static void force() {
    implOrFail().force();
  }

  @TestOnly
  static boolean isDirty() {
    return implOrFail().isDirty();
  }

  //========== record allocation/deletion: ====================================


  /** @deprecated Use FSRecords.getInstance().createRecord() instead */
  @Deprecated(forRemoval = true)
  public static int createRecord() {
    return implOrFail().createRecord();
  }

  /**
   * @return records (ids) freed in previous session, and not yet re-used in a current session.
   */
  public static @NotNull IntList getRemainFreeRecords() {
    return implOrFail().getRemainFreeRecords();
  }

  /**
   * @return records (ids) freed in current session.
   * Returns !empty list only in unit-tests -- outside of testing records freed in a current session are marked by REMOVED
   * flag, but not collected into free-list
   */
  public static @NotNull IntList getNewFreeRecords() {
    return implOrFail().getNewFreeRecords();
  }


  //========== directory/children manipulation: =============================

  static void loadDirectoryData(int id,
                                @NotNull VirtualFile parent,
                                @NotNull CharSequence path,
                                @NotNull NewVirtualFileSystem fs) {
    implOrFail().loadDirectoryData(id, parent, path, fs);
  }

  public static int @NotNull [] listIds(int fileId) {
    return implOrFail().listIds(fileId);
  }

  public static @NotNull @Unmodifiable List<CharSequence> listNames(int parentId) {
    return implOrFail().listNames(parentId);
  }

  //========== file name iterations: ========================================

  public static boolean processAllNames(@NotNull Processor<? super CharSequence> processor) {
    return implOrFail().processAllNames(processor);
  }

  public static boolean processFilesWithNames(@NotNull Set<String> names,
                                              @NotNull IntPredicate processor) {
    return implOrFail().processFilesWithNames(names, processor);
  }


  //========== file record fields accessors: ================================

  /** @deprecated replace with apt FSRecords.getInstance() instance method */
  @Deprecated(forRemoval = true)
  public static int getParent(int fileId) {
    return implOrFail().getParent(fileId);
  }

  @ApiStatus.Internal
  public static boolean isDeleted(int fileId) {
    return implOrFail().isDeleted(fileId);
  }

  /** @deprecated replace with apt FSRecords.getInstance() instance method */
  @Deprecated(forRemoval = true)
  static long getLength(int fileId) {
    return implOrFail().getLength(fileId);
  }

  /** @deprecated replace with apt FSRecords.getInstance() instance method */
  @Deprecated(forRemoval = true)
  static void setLength(int fileId,
                        long length) {
    implOrFail().setLength(fileId, length);
  }

  /** @deprecated replace with apt FSRecords.getInstance() instance method */
  @Deprecated(forRemoval = true)
  static long getTimestamp(int fileId) {
    return implOrFail().getTimestamp(fileId);
  }

  /** @deprecated replace with apt FSRecords.getInstance() instance method */
  @Deprecated(forRemoval = true)
  static void setTimestamp(int fileId,
                           long value) {
    implOrFail().setTimestamp(fileId, value);
  }

  //========== file attributes accessors: ===================================

  public static @Nullable AttributeInputStream readAttributeWithLock(int fileId,
                                                                     @NotNull FileAttribute attribute) {
    return implOrFail().readAttributeWithLock(fileId, attribute);
  }

  public static @NotNull AttributeOutputStream writeAttribute(int fileId,
                                                              @NotNull FileAttribute attribute) {
    return implOrFail().writeAttribute(fileId, attribute);
  }

  //'raw' (lambda + ByteBuffer instead of Input/OutputStream) attributes access: experimental

  public static boolean supportsRawAttributesAccess() {
    return implOrFail().supportsRawAttributesAccess();
  }

  /** BEWARE: ByteBuffer passed into a reader could have ByteOrder different from JVM-default BIG_ENDIAN! */
  @ApiStatus.Internal
  public static <R> @Nullable R readAttributeRawWithLock(int fileId,
                                                         @NotNull FileAttribute attribute,
                                                         ByteBufferReader<R> reader) {
    return implOrFail().readAttributeRaw(fileId, attribute, reader);
  }

  @ApiStatus.Internal
  public static void writeAttributeRaw(int fileId,
                                       FileAttribute attribute,
                                       ByteBufferWriter writer) {
    implOrFail().writeAttributeRaw(fileId, attribute, writer);
  }

  //========== aux: ========================================================

  /** Method creates 'VFS corruption marker', which forces VFS to rebuild on the next startup */
  public static void invalidateCaches(@NotNull String diagnosticMessage,
                                      @NotNull Throwable errorCause) {
    implOrFail().scheduleRebuild(diagnosticMessage, errorCause);
  }

  /**
   * With method create 'VFS corruption marker', which forces VFS to rebuild on next startup.
   * Contrary to the {@link #invalidateCaches(String, Throwable)} version, this method is not
   * considered a scenario as 'an error', but as a regular request -- e.g. no errors logged.
   */
  public static void invalidateCaches(@NotNull String diagnosticMessage) {
    implOrFail().scheduleRebuild(diagnosticMessage, null);
  }

  /**
   * @deprecated please use {@link #invalidateCaches(String)} instead -> provide explicit reason for invalidate caches
   * TODO RC: currently only third-party plugins keep using it
   */
  @ApiStatus.Obsolete
  @Deprecated
  public static void invalidateCaches() {
    invalidateCaches("No description given");
  }

  /**
   * Method is supposed to be called in a pattern like this:
   * <pre>
   * try{
   *  ...
   * }
   * catch(Throwable t){
   *   throw handeError(e);
   * }
   * </pre>
   * i.e. in a 'throw' statement -- to make clear that it will throw an exception.
   * Method made return RuntimeException specifically for that purpose: to be used
   * in a 'throw' statement, so the compiler understands it as a method exit point.
   */
  @Contract("_->fail")
  public static RuntimeException handleError(Throwable e) throws RuntimeException, Error {
    return implOrFail().handleError(e);
  }

  //========== diagnostic, sanity checks: ==================================

  @TestOnly
  public static void checkFilenameIndexConsistency() {
    FSRecordsImpl _impl = impl;
    if (_impl != null && !_impl.isClosed()) {
      _impl.checkFilenameIndexConsistency();
    }
  }
}
