// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferReader;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferWriter;
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.DataOutputStream;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.DataInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This class is just an 'instance holder' -- actual implementation is an {@link FSRecordsImpl} instance,
 * all methods delegate to it.
 * <p>
 * Current policy: avoid use of this class outside of VFS impl code, inside VFS impl code migrate to use
 * the {@link FSRecordsImpl} _instance_ obtained by {@link #connect(List)}/{@link #getInstance()}.
 * <p>
 * This is very low-level API, intended to be used only by VFS implementation code only -- mainly
 * {@link PersistentFSImpl}. Inside VFS implementation all the calls should go through the instance
 * obtained by {@link #connect(List)}. Current usages of static methods should be gradually migrated.
 * {@link FSRecords#getInstance()} method could be used to help with migration.
 * <p>
 * At the end I plan to convert {@link FSRecordsImpl} a regular {@link com.intellij.openapi.components.Service},
 * with a usual .getInstance() method.
 */
@ApiStatus.Internal
public final class FSRecords {
  static final Logger LOG = Logger.getInstance(FSRecords.class);
  static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(30));

  static final boolean BACKGROUND_VFS_FLUSH = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);

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

  /** Holds stacktrace of the disconnect call */
  private static volatile Exception disconnectLocationStackTrace;


  /** @return path to the directory there all VFS files are located */
  public static @NotNull String getCachesDir() {
    String dir = System.getProperty("caches_dir");
    return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
  }

  private FSRecords() {
    throw new AssertionError("Not for instantiation");
  }

  //========== lifecycle: =====================================================

  static synchronized FSRecordsImpl connect(@NotNull List<ConnectionInterceptor> connectionInterceptors) throws UncheckedIOException {
    return connect(connectionInterceptors, FSRecordsImpl.getDefaultErrorHandler());
  }

  static synchronized FSRecordsImpl connect(@NotNull List<ConnectionInterceptor> connectionInterceptors,
                                            @NotNull FSRecordsImpl.ErrorHandler errorHandler) throws UncheckedIOException {
    FSRecordsImpl _impl = FSRecordsImpl.connect(Path.of(getCachesDir()), connectionInterceptors, errorHandler);
    impl = _impl;
    return _impl;
  }

  static synchronized void disconnect() {
    FSRecordsImpl _impl = impl;
    if (_impl != null) {
      _impl.dispose();
      impl = null;
      disconnectLocationStackTrace = new Exception("VFS dispose stacktrace");
    }
  }


  private static @NotNull FSRecordsImpl implOrFail() {
    FSRecordsImpl _impl = impl;
    if (_impl == null || _impl.isDisposed()) {
      throw alreadyDisposed();
    }

    return _impl;
  }

  /** @throws AlreadyDisposedException if VFS is disposed (or not yet initialized) */
  public static @NotNull FSRecordsImpl getInstance() throws AlreadyDisposedException {
    return implOrFail();
  }


  //========== FS records-as-a-whole properties: ==============================

  public static int getVersion() {
    return implOrFail().getVersion();
  }

  public static long getCreationTimestamp() {
    return implOrFail().getCreationTimestamp();
  }

  //========== modifications counters: ========================================

  static int getLocalModCount() {
    return implOrFail().getLocalModCount();
  }

  @TestOnly
  static int getPersistentModCount() {
    return implOrFail().getPersistentModCount();
  }

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

  public static int createRecord() {
    return implOrFail().createRecord();
  }

  static void deleteRecordRecursively(int fileId) {
    implOrFail().deleteRecordRecursively(fileId);
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


  //========== FS roots manipulation: ========================================

  @TestOnly
  static int @NotNull [] listRoots() {
    return impl.listRoots();
  }

  static int findOrCreateRootRecord(@NotNull String rootUrl) {
    return implOrFail().findOrCreateRootRecord(rootUrl);
  }

  static void loadRootData(int id,
                           @NotNull String path,
                           @NotNull NewVirtualFileSystem fs) {
    implOrFail().loadRootData(id, path, fs);
  }

  static void deleteRootRecord(int fileId) {
    implOrFail().deleteRootRecord(fileId);
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

  static boolean mayHaveChildren(int fileId) {
    return implOrFail().mayHaveChildren(fileId);
  }

  /**
   * @return child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
   */
  @NotNull
  static ListResult list(int parentId) {
    return implOrFail().list(parentId);
  }

  public static @NotNull @Unmodifiable List<CharSequence> listNames(int parentId) {
    return implOrFail().listNames(parentId);
  }

  static boolean wereChildrenAccessed(int fileId) {
    return impl.wereChildrenAccessed(fileId);
  }

  // Perform operation on children and save the list atomically:
  // Obtain fresh children and try to apply `childrenConvertor` to the children of `parentId`.
  // If everything is still valid (i.e. no one changed the list in the meantime), commit.
  // Failing that, repeat pessimistically: retry converter inside write lock for fresh children and commit inside the same write lock
  static @NotNull ListResult update(@NotNull VirtualFile parent,
                                    int parentId,
                                    @NotNull Function<? super ListResult, ListResult> childrenConvertor) {
    return implOrFail().update(parent, parentId, childrenConvertor);
  }

  static void moveChildren(int fromParentId,
                           int toParentId) {
    implOrFail().moveChildren(fromParentId, toParentId);
  }


  //========== symlink manipulation: ========================================

  static @Nullable String readSymlinkTarget(int fileId) {
    return implOrFail().readSymlinkTarget(fileId);
  }

  static void storeSymlinkTarget(int fileId,
                                 @Nullable String symlinkTarget) {
    implOrFail().storeSymlinkTarget(fileId, symlinkTarget);
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

  public static int getParent(int fileId) {
    return implOrFail().getParent(fileId);
  }

  static void setParent(int id,
                        int parentId) {
    implOrFail().setParent(id, parentId);
  }

  //MAYBE RC: this method is used to look up files by name, but with future non-strict enumerator this
  //          approach becomes 'non-strict' also: nameId returned could be the new nameId, never used
  //          before in any file record, even though the name was already registered for some file(s)
  static int getNameId(@NotNull String name) {
    return implOrFail().getNameId(name);
  }

  public static @NotNull String getName(int fileId) {
    return implOrFail().getName(fileId);
  }

  static @NotNull CharSequence getNameSequence(int fileId) {
    return implOrFail().getNameSequence(fileId);
  }

  static CharSequence getNameByNameId(int nameId) {
    return implOrFail().getNameByNameId(nameId);
  }

  static void setName(int fileId,
                      @NotNull String name,
                      int oldNameId) {
    implOrFail().setName(fileId, name, oldNameId);
  }

  static void setFlags(int fileId,
                       @PersistentFS.Attributes int flags) {
    implOrFail().setFlags(fileId, flags);
  }

  static @PersistentFS.Attributes int getFlags(int fileId) {
    return implOrFail().getFlags(fileId);
  }

  @ApiStatus.Internal
  public static boolean isDeleted(int fileId) {
    return implOrFail().isDeleted(fileId);
  }

  static long getLength(int fileId) {
    return implOrFail().getLength(fileId);
  }

  static void setLength(int fileId,
                        long length) {
    implOrFail().setLength(fileId, length);
  }

  /**
   * @return nameId > 0
   */
  static int updateRecordFields(int fileId,
                                int parentId,
                                @NotNull FileAttributes attributes,
                                @NotNull String name,
                                boolean overwriteMissed) {
    return implOrFail().updateRecordFields(fileId, parentId, attributes, name, overwriteMissed);
  }


  static long getTimestamp(int fileId) {
    return implOrFail().getTimestamp(fileId);
  }

  static void setTimestamp(int fileId,
                           long value) {
    implOrFail().setTimestamp(fileId, value);
  }

  static int getModCount(int fileId) {
    return impl.getModCount(fileId);
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

  //========== file content accessors: ======================================

  static int acquireFileContent(int fileId) {
    return implOrFail().acquireFileContent(fileId);
  }

  static @Nullable DataInputStream readContent(int fileId) {
    return implOrFail().readContent(fileId);
  }

  static @NotNull DataInputStream readContentById(int contentId) {
    return implOrFail().readContentById(contentId);
  }

  static void releaseContent(int contentId) {
    implOrFail().releaseContent(contentId);
  }

  static int getContentId(int fileId) {
    return implOrFail().getContentRecordId(fileId);
  }

  @TestOnly
  static byte[] getContentHash(int fileId) {
    return implOrFail().getContentHash(fileId);
  }

  static @NotNull DataOutputStream writeContent(int fileId,
                                                boolean fixedSize) {
    return implOrFail().writeContent(fileId, fixedSize);
  }

  static void writeContent(int fileId,
                           @NotNull ByteArraySequence bytes,
                           boolean fixedSize) {
    implOrFail().writeContent(fileId, bytes, fixedSize);
  }

  static int storeUnlinkedContent(byte[] bytes) {
    return implOrFail().storeUnlinkedContent(bytes);
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

  /** @deprecated please use {@link #invalidateCaches(String)} instead -> provide explicit reason for invalidate caches */
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

  /**
   * @return human-readable description of file fileId -- as much information as VFS now contains
   */
  public static @NotNull String describeAlreadyCreatedFile(int fileId,
                                                           int nameId) {
    return implOrFail().describeAlreadyCreatedFile(fileId, nameId);
  }

  @TestOnly
  public static void checkFilenameIndexConsistency() {
    FSRecordsImpl _impl = impl;
    if (_impl != null && !_impl.isDisposed()) {
      _impl.checkFilenameIndexConsistency();
    }
  }

  @NotNull
  private static AlreadyDisposedException alreadyDisposed() {
    AlreadyDisposedException alreadyDisposed = new AlreadyDisposedException("VFS is already disposed");
    if (disconnectLocationStackTrace != null) {
      alreadyDisposed.addSuppressed(disconnectLocationStackTrace);
    }
    return alreadyDisposed;
  }
}
