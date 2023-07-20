// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferReader;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferWriter;
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.ObjIntConsumer;
import java.util.zip.ZipException;

import static com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * This is an attempt to convert FSRecords into a self-containing _object_, not a set of static
 * methods.
 * The plan is: FSRecordsImpl is FSRecords, re-implemented as an object, with all fields being
 * instance fields. Old FSRecords become a single 'volatile FSRecordsImpl impl' holder, all the
 * methods delegated to it.
 * <p>
 * Benefits:
 * 1. Simplify state: since all fields are initialized in ctor and final -- there are much fewer
 * checks for 'ourConnection!=null' in the code -- .ourConnection can't be null since ctor
 * checks it.
 * 2. clearer separation API methods from implementation -- implementation methods are in FSRecordsImpl,
 * FSRecords contains only API
 * 3. Simplify testing/benchmarking: separate instance of FSRecordsImpl could be created for
 * test/benchmark, and thrown away afterward, without compromising the JVM-wide state.
 * 4. Simplify change of implementation: it is easy to extract interface from FSRecordsImpl, and
 * make another impl, and there is only a single place to switch between impls.
 * <p>
 * TODO RC: maybe FSRecordsImpl methods should throw exceptions, and try...catch{handleError} should
 * be in FSRecords?
 */
@ApiStatus.Internal
public final class FSRecordsImpl {
  private static final Logger LOG = Logger.getInstance(FSRecordsImpl.class);

  private static final boolean USE_CONTENT_HASHES = getBooleanProperty("idea.share.contents", true);
  static final boolean INLINE_ATTRIBUTES = getBooleanProperty("idea.inline.vfs.attributes", true);

  /**
   * If true -> use {@link CompactRecordsTable} for managing attributes record, instead of default {@link com.intellij.util.io.storage.RecordsTable}
   */
  static final boolean USE_SMALL_ATTR_TABLE = getBooleanProperty("idea.use.small.attr.table.for.vfs", true);

  static final boolean USE_FAST_NAMES_IMPLEMENTATION = getBooleanProperty("vfs.use-fast-names-storage", false);
  public static final boolean USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION =
    getBooleanProperty("vfs.use-streamlined-attributes-storage", true);
  public static final boolean USE_RAW_ACCESS_TO_READ_CHILDREN = getBooleanProperty("vfs.use-raw-access-to-read-children", false);

  private static final FileAttribute SYMLINK_TARGET_ATTRIBUTE = new FileAttribute("FsRecords.SYMLINK_TARGET");

  /**
   * Default VFS error handler: marks VFS as corrupted, schedules rebuild on next app startup, and rethrows
   * the error passed in
   */
  public static final ErrorHandler ON_ERROR_MARK_CORRUPTED_AND_SCHEDULE_REBUILD = (records, error) -> {
    records.connection.markAsCorruptedAndScheduleRebuild(error);
    ExceptionUtil.rethrow(error);
  };


  private final @NotNull PersistentFSConnection connection;
  private final @NotNull PersistentFSContentAccessor contentAccessor;
  private final @NotNull PersistentFSAttributeAccessor attributeAccessor;
  private final @NotNull PersistentFSTreeAccessor treeAccessor;
  private final @NotNull PersistentFSRecordAccessor recordAccessor;

  private final @NotNull ErrorHandler errorHandler;

  /** Additional information about how VFS was initialized */
  private final @NotNull PersistentFSConnector.InitializationResult initializationResult;

  /**
   * Right now invertedNameIndex looks like a property of PersistentFSConnection -- but this is only because now it
   * operates with fileId/nameId. Future index impls may work with name hashes instead of nameId -- say, because hash
   * is a better way to identify strings if nameId is not unique. Such a version of index will require a name itself,
   * as String, which is less available inside PersistentFSConnection.
   */
  private final @NotNull InvertedNameIndex invertedNameIndex;
  private final AtomicLong invertedNameIndexModCount = new AtomicLong();


  /** VFS implementation version */
  private final int currentVersion;


  private final FineGrainedIdLock updateLock = new FineGrainedIdLock();

  private volatile boolean disposed = false;

  /** Keep stacktrace of {@link #dispose()} call -- for better diagnostics of unexpected dispose */
  private volatile Exception disposedStackTrace = null;

  private static int nextMask(int value,
                              int bits,
                              int prevMask) {
    assert value < (1 << bits) && value >= 0 : value;
    int mask = (prevMask << bits) | value;
    if (mask < 0) throw new IllegalStateException("Too many flags, int mask overflown");
    return mask;
  }

  private static int nextMask(boolean value,
                              int prevMask) {
    return nextMask(value ? 1 : 0, 1, prevMask);
  }

  private static int calculateVersion() {
    //bumped main version (59 -> 60) because of VfsDependentEnumerator removal, and filenames change
    final int mainVFSFormatVersion = 60;
    return nextMask(mainVFSFormatVersion + (PersistentFSRecordsStorageFactory.getRecordsStorageImplementation().ordinal()), /* acceptable range is [0..255] */ 8,
           nextMask(USE_CONTENT_HASHES,
           nextMask(IOUtil.useNativeByteOrderForByteBuffers(),
           nextMask(false, // feel free to re-use
           nextMask(INLINE_ATTRIBUTES,
           nextMask(getBooleanProperty(FSRecords.IDE_USE_FS_ROOTS_DATA_LOADER, false),
           nextMask(false, // feel free to re-use
           nextMask(USE_SMALL_ATTR_TABLE,
           nextMask(PersistentHashMapValueStorage.COMPRESSION_ENABLED,
           nextMask(FileSystemUtil.DO_NOT_RESOLVE_SYMLINKS,
           nextMask(ZipHandlerBase.getUseCrcInsteadOfTimestampPropertyValue(),
           nextMask(USE_FAST_NAMES_IMPLEMENTATION,
           nextMask(USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION, 0)))))))))))));
  }


  /**
   * Factory
   *
   * @param storagesDirectoryPath directory there to put all FS-records files ('caches' directory)
   */
  static FSRecordsImpl connect(@NotNull Path storagesDirectoryPath) throws UncheckedIOException {
    return connect(storagesDirectoryPath, Collections.emptyList());
  }

  static FSRecordsImpl connect(@NotNull Path storagesDirectoryPath,
                               @NotNull List<ConnectionInterceptor> connectionInterceptors) throws UncheckedIOException {
    return connect(storagesDirectoryPath, connectionInterceptors, ON_ERROR_MARK_CORRUPTED_AND_SCHEDULE_REBUILD);
  }

  static FSRecordsImpl connect(@NotNull Path storagesDirectoryPath,
                               @NotNull List<ConnectionInterceptor> connectionInterceptors,
                               @NotNull ErrorHandler errorHandler) throws UncheckedIOException {
    if (IOUtil.isSharedCachesEnabled()) {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.set(false);
    }
    try {
      int currentVersion = calculateVersion();
      InvertedNameIndex invertedNameIndex = new InvertedNameIndex();
      final PersistentFSConnector.InitializationResult initializationResult = PersistentFSConnector.connect(
        storagesDirectoryPath,
        currentVersion,
        USE_CONTENT_HASHES,
        invertedNameIndex,
        connectionInterceptors
      );
      PersistentFSConnection connection = initializationResult.connection;
      PersistentFSContentAccessor contentAccessor = new PersistentFSContentAccessor(USE_CONTENT_HASHES, connection);
      PersistentFSAttributeAccessor attributeAccessor = new PersistentFSAttributeAccessor(connection);
      PersistentFSTreeAccessor treeAccessor = attributeAccessor.supportsRawAccess() && USE_RAW_ACCESS_TO_READ_CHILDREN ?
                                              new PersistentFSTreeRawAccessor(attributeAccessor, connection) :
                                              new PersistentFSTreeAccessor(attributeAccessor, connection);
      PersistentFSRecordAccessor recordAccessor = new PersistentFSRecordAccessor(contentAccessor, attributeAccessor, connection);

      try {
        treeAccessor.ensureLoaded();

        return new FSRecordsImpl(
          connection,
          contentAccessor, attributeAccessor, treeAccessor, recordAccessor,
          invertedNameIndex,
          currentVersion,
          errorHandler,
          initializationResult
        );
      }
      catch (IOException e) {
        LOG.error(e);//because we need more details
        //FIXME throw handleError(e);
        throw new UncheckedIOException(e);
      }
    }
    finally {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.remove();
    }
  }


  private FSRecordsImpl(@NotNull PersistentFSConnection connection,
                        @NotNull PersistentFSContentAccessor contentAccessor,
                        @NotNull PersistentFSAttributeAccessor attributeAccessor,
                        @NotNull PersistentFSTreeAccessor treeAccessor,
                        @NotNull PersistentFSRecordAccessor recordAccessor,
                        @NotNull InvertedNameIndex invertedNameIndex,
                        int currentVersion,
                        @NotNull ErrorHandler errorHandler,
                        @NotNull PersistentFSConnector.InitializationResult initializationResult) {
    this.connection = connection;
    this.contentAccessor = contentAccessor;
    this.attributeAccessor = attributeAccessor;
    this.treeAccessor = treeAccessor;
    this.recordAccessor = recordAccessor;
    this.errorHandler = errorHandler;
    this.invertedNameIndex = invertedNameIndex;

    this.currentVersion = currentVersion;
    this.initializationResult = initializationResult;
  }

  //========== lifecycle: ========================================

  synchronized void dispose() {
    if (!disposed) {
      try {
        PersistentFSConnector.disconnect(connection);
      }
      catch (IOException e) {
        handleError(e);
      }

      invertedNameIndex.clear();
      disposed = true;
      disposedStackTrace = new Exception("FSRecordsImpl dispose stacktrace");
    }
  }

  boolean isDisposed() {
    return disposed;
  }

  private void checkNotDisposed() {
    if (disposed) {
      AlreadyDisposedException alreadyDisposed = new AlreadyDisposedException("VFS is already disposed");
      if (disposedStackTrace != null) {
        alreadyDisposed.addSuppressed(disposedStackTrace);
      }
      throw alreadyDisposed;
    }
  }


  //========== general FS records properties: ========================================

  int getVersion() {
    return currentVersion;
  }

  long getCreationTimestamp() {
    try {
      checkNotDisposed();
      return connection.getTimestamp();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  List<Throwable> initializationFailures() {
    return Collections.unmodifiableList(initializationResult.attemptsFailures);
  }

  boolean wasCreatedANew() {
    return initializationResult.storagesCreatedAnew;
  }

  long totalInitializationDuration(@NotNull TimeUnit unit) {
    return unit.convert(initializationResult.totalInitializationDurationNs, NANOSECONDS);
  }


  //========== modifications counters: ========================================

  long getInvertedNameIndexModCount() {
    return invertedNameIndexModCount.get();
  }

  int getLocalModCount() {
    checkNotDisposed();
    return connection.getModificationCount() + attributeAccessor.getLocalModificationCount();
  }

  @TestOnly
  int getPersistentModCount() {
    checkNotDisposed();
    return connection.getPersistentModCount();
  }

  //========== FS records persistence: ========================================

  @TestOnly
  void force() {
    try {
      checkNotDisposed();
      connection.doForce();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @TestOnly
  boolean isDirty() {
    checkNotDisposed();
    return connection.isDirty();
  }


  //========== record allocations: ========================================

  int createRecord() {
    try {
      return recordAccessor.createRecord();
    }
    catch (Exception e) {
      throw handleError(e);
    }
  }

  /**
   * @return records (ids) freed in previous session, and not yet re-used in a current session.
   */
  @NotNull IntList getRemainFreeRecords() {
    checkNotDisposed();
    return connection.getFreeRecords();
  }

  /**
   * @return records (ids) freed in current session.
   * Returns !empty list only in unit-tests -- outside of testing records freed in a current session are marked by REMOVED
   * flag, but not collected into free-list
   */
  @NotNull IntList getNewFreeRecords() {
    return recordAccessor.getNewFreeRecords();
  }

  void deleteRecordRecursively(int fileId) {
    checkNotDisposed();
    try {
      markAsDeletedRecursively(fileId);
      connection.markDirty();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  private void markAsDeletedRecursively(int fileId) throws IOException {
    IntList ids = new IntArrayList();
    ids.add(fileId);
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.getInt(i);
      ids.addElements(ids.size(), listIds(id));
    }
    // delete children first
    for (int i = ids.size() - 1; i >= 0; i--) {
      int id = ids.getInt(i);
      int nameId = connection.getRecords().getNameId(id);
      if (PersistentFS.isDirectory(getFlags(id))) {
        treeAccessor.deleteDirectoryRecord(id);
      }
      recordAccessor.markRecordAsDeleted(id);

      invertedNameIndex.updateFileName(id, NULL_NAME_ID, nameId);
    }
    invertedNameIndexModCount.incrementAndGet();
  }


  //========== FS roots manipulation: ========================================

  int @NotNull [] listRoots() {
    try {
      return treeAccessor.listRoots();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int findOrCreateRootRecord(@NotNull String rootUrl) {
    try {
      return treeAccessor.findOrCreateRootRecord(rootUrl);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void forEachRoot(@NotNull ObjIntConsumer<String> rootConsumer) {
    try {
      treeAccessor.forEachRoot((rootId, rootUrlId) -> {
        String rootUrl = getNameByNameId(rootUrlId).toString();
        rootConsumer.accept(rootUrl, rootId);
      });
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void loadRootData(int fileId,
                    @NotNull String path,
                    @NotNull NewVirtualFileSystem fs) {
    try {
      treeAccessor.loadRootData(fileId, path, fs);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void deleteRootRecord(int fileId) {
    try {
      treeAccessor.deleteRootRecord(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  //========== directory/children manipulation: ========================================

  void loadDirectoryData(int id,
                         @NotNull VirtualFile parent,
                         @NotNull CharSequence path,
                         @NotNull NewVirtualFileSystem fs) {
    try {
      treeAccessor.loadDirectoryData(id, parent, path, fs);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int @NotNull [] listIds(int fileId) {
    try {
      return treeAccessor.listIds(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean mayHaveChildren(int fileId) {
    try {
      return treeAccessor.mayHaveChildren(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /**
   * @return child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
   */
  @NotNull
  ListResult list(int parentId) {
    try {
      return treeAccessor.doLoadChildren(parentId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @NotNull @Unmodifiable List<CharSequence> listNames(int parentId) {
    return ContainerUtil.map(list(parentId).children, ChildInfo::getName);
  }

  boolean wereChildrenAccessed(int fileId) {
    try {
      return treeAccessor.wereChildrenAccessed(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /**
   * Perform operation on children and save the list atomically:
   * Obtain fresh children and try to apply `childrenConvertor` to the children of `parentId`.
   * If everything is still valid (i.e. no one changed the list in the meantime), commit.
   * Failing that, repeat pessimistically: retry converter inside write lock for fresh children and commit inside the same write lock
   */
  @NotNull ListResult update(@NotNull VirtualFile parent,
                             int parentId,
                             @NotNull Function<? super ListResult, ListResult> childrenConvertor) {
    assert parentId > 0 : parentId;
    ListResult children = list(parentId);
    ListResult result = childrenConvertor.apply(children);

    updateLock.lock(parentId);
    try {
      ListResult toSave;
      // optimization: if the children were never changed after list(), do not check for duplicates again
      if (result.childrenWereChangedSinceLastList()) {
        children = list(parentId);
        toSave = childrenConvertor.apply(children);
      }
      else {
        toSave = result;
      }
      // optimization: when converter returned unchanged children (see e.g. PersistentFSImpl.findChildInfo())
      // then do not save them back again unnecessarily
      if (!toSave.equals(children)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Update children for " + parent + " (id = " + parentId + "); old = " + children + ", new = " + toSave);
        }
        checkNotDisposed();
        connection.markRecordAsModified(parentId);
        updateSymlinksForNewChildren(parent, children, toSave);
        treeAccessor.doSaveChildren(parentId, toSave);
      }
      return toSave;
    }
    catch (ProcessCanceledException e) {
      // NewVirtualFileSystem.list methods can be interrupted now
      throw e;
    }
    catch (Throwable e) {
      throw handleError(e);
    }
    finally {
      updateLock.unlock(parentId);
    }
  }

  void moveChildren(int fromParentId,
                    int toParentId) {
    assert fromParentId > 0 : fromParentId;
    assert toParentId > 0 : toParentId;

    if (fromParentId == toParentId) return;

    int minId = Math.min(fromParentId, toParentId);
    int maxId = Math.max(fromParentId, toParentId);

    checkNotDisposed();
    updateLock.lock(minId);
    try {
      updateLock.lock(maxId);
      try {
        try {
          ListResult children = list(fromParentId);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Move children from " + fromParentId + " to " + toParentId + "; children = " + children);
          }

          connection.markRecordAsModified(toParentId);
          treeAccessor.doSaveChildren(toParentId, children);

          connection.markRecordAsModified(fromParentId);
          treeAccessor.doSaveChildren(fromParentId, new ListResult(Collections.emptyList(), fromParentId));
        }
        catch (ProcessCanceledException e) {
          // NewVirtualFileSystem.list methods can be interrupted now
          throw e;
        }
        catch (Throwable e) {
          throw handleError(e);
        }
      }
      finally {
        updateLock.unlock(maxId);
      }
    }
    finally {
      updateLock.unlock(minId);
    }
  }

  //========== symlink manipulation: ========================================

  private void updateSymlinksForNewChildren(@NotNull VirtualFile parent,
                                            @NotNull ListResult oldChildren,
                                            @NotNull ListResult newChildren) {
    // find children which are added to the list and call updateSymlinkInfoForNewChild() on them (once)
    ContainerUtil.processSortedListsInOrder(
      oldChildren.children, newChildren.children,
      Comparator.comparingInt(ChildInfo::getId),
      /*mergeEqualItems: */ true,
      (childInfo, isOldInfo) -> {
        if (!isOldInfo) {
          updateSymlinkInfoForNewChild(parent, childInfo);
        }
      });
  }

  private void updateSymlinkInfoForNewChild(@NotNull VirtualFile parent,
                                            @NotNull ChildInfo info) {
    int attributes = info.getFileAttributeFlags();
    if (attributes != -1 && PersistentFS.isSymLink(attributes)) {
      int id = info.getId();
      String symlinkTarget = info.getSymlinkTarget();
      storeSymlinkTarget(id, symlinkTarget);
      CharSequence name = info.getName();
      VirtualFileSystem fs = parent.getFileSystem();
      if (fs instanceof LocalFileSystemImpl) {
        String linkPath = parent.getPath() + '/' + name;
        ((LocalFileSystemImpl)fs).symlinkUpdated(id, parent, name, linkPath, symlinkTarget);
      }
    }
  }

  @Nullable String readSymlinkTarget(int fileId) {
    try (DataInputStream stream = readAttribute(fileId, SYMLINK_TARGET_ATTRIBUTE)) {
      if (stream != null) {
        try {
          String result = StringUtil.nullize(IOUtil.readUTF(stream));
          return result == null ? null : FileUtil.toSystemIndependentName(result);
        }
        catch (EOFException eof) {
          //EA-822669: collect detailed info for debug (TODO: remove after root cause found)
          try (DataInputStream attrStream = readAttribute(fileId, SYMLINK_TARGET_ATTRIBUTE)) {
            int size = attrStream.available();
            byte[] content = new byte[size];
            attrStream.readFully(content);
            throw handleError(
              new IOException("Can't read symLink from attribute[fileId:" + fileId + "][=" + IOUtil.toHexString(content) + "]", eof)
            );
          }
        }
      }
      return null;
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void storeSymlinkTarget(int fileId,
                          @Nullable String symlinkTarget) {
    try {
      checkNotDisposed();
      connection.markDirty();
      try (DataOutputStream stream = writeAttribute(fileId, SYMLINK_TARGET_ATTRIBUTE)) {
        IOUtil.writeUTF(stream, StringUtil.notNullize(symlinkTarget));
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  //========== file by name iterations: ========================================

  boolean processAllNames(@NotNull Processor<? super CharSequence> processor) {
    try {
      checkNotDisposed();
      return connection.getNames().processAllDataObjects(processor);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean processFilesWithNames(@NotNull Set<String> names,
                                @NotNull IntPredicate processor) {
    if (names.isEmpty()) return true;
    return invertedNameIndex.processFilesWithNames(names, processor);
  }


  //========== file record fields accessors: ========================================

  @PersistentFS.Attributes int getFlags(int fileId) {
    try {
      checkNotDisposed();
      return connection.getRecords().getFlags(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  boolean isDeleted(int fileId) {
    try {
      return recordAccessor.isDeleted(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int getModCount(int fileId) {
    try {
      checkNotDisposed();
      return connection.getRecords().getModCount(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  int getParent(int fileId) {
    try {
      checkNotDisposed();
      int parentId = connection.getRecords().getParent(fileId);
      if (parentId == fileId) {
        throw new IllegalStateException("Cyclic parent child relations in the database: fileId = " + fileId + " == parentId");
      }

      return parentId;
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setParent(int fileId,
                 int parentId) {
    if (fileId == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    try {
      checkNotDisposed();
      connection.getRecords().setParent(fileId, parentId);
      connection.markDirty();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  /**
   * TODO RC: this method is used to look up files by name, but with (future) non-strict enumerator
   * this approach becomes 'non-strict' also: nameId returned could be the new nameId, never used
   * before, hence in any file record, even though name was already registered for some file(s)
   */
  int getNameId(@NotNull String name) {
    try {
      checkNotDisposed();
      return connection.getNames().enumerate(name);
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  @NotNull String getName(int fileId) {
    return getNameSequence(fileId).toString();
  }

  @NotNull CharSequence getNameSequence(int fileId) {
    //TODO RC: I don't see any profit of CharSequence method since under the hood it is always a String
    try {
      checkNotDisposed();
      int nameId = connection.getRecords().getNameId(fileId);
      return nameId == NULL_NAME_ID ? "" : FileNameCache.getVFileName(nameId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int getNameIdByFileId(int fileId) {
    try {
      checkNotDisposed();
      return connection.getRecords().getNameId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  CharSequence getNameByNameId(int nameId) {
    assert nameId >= NULL_NAME_ID : "nameId(=" + nameId + ") must be positive";
    try {
      checkNotDisposed();
      return nameId == NULL_NAME_ID ? "" : connection.getNames().valueOf(nameId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setName(int fileId, @NotNull String name, int oldNameId) {
    try {
      checkNotDisposed();
      int nameId = getNameId(name);

      connection.getRecords().setNameId(fileId, nameId);
      connection.markDirty();

      invertedNameIndex.updateFileName(fileId, nameId, oldNameId);
      invertedNameIndexModCount.incrementAndGet();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setFlags(int fileId,
                @PersistentFS.Attributes int flags) {
    try {
      checkNotDisposed();
      if (connection.getRecords().setFlags(fileId, flags)) {
        connection.markDirty();
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  long getLength(int fileId) {
    try {
      checkNotDisposed();
      return connection.getRecords().getLength(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setLength(int fileId,
                 long len) {
    try {
      checkNotDisposed();
      if (connection.getRecords().setLength(fileId, len)) {
        connection.markDirty();
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  long getTimestamp(int fileId) {
    try {
      checkNotDisposed();
      return connection.getRecords().getTimestamp(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void setTimestamp(int fileId,
                    long value) {
    try {
      checkNotDisposed();
      if (connection.getRecords().setTimestamp(fileId, value)) {
        connection.markDirty();
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int getContentRecordId(int fileId) {
    try {
      return connection.getRecords().getContentRecordId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  SimpleStringPersistentEnumerator getEnumeratedAttributes() {
    return connection.getEnumeratedAttributes();
  }

  int getAttributeRecordId(int fileId) {
    try {
      return connection.getRecords().getAttributeRecordId(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  /**
   * @return nameId > 0
   */
  int updateRecordFields(int fileId,
                         int parentId,
                         @NotNull FileAttributes attributes,
                         @NotNull String name,
                         boolean overwriteMissed) {
    int nameId = getNameId(name);
    long timestamp = attributes.lastModified;
    long length = attributes.isDirectory() ? -1L : attributes.length;
    int flags = PersistentFSImpl.fileAttributesToFlags(attributes);

    try {
      fillRecord(fileId, timestamp, length, flags, nameId, parentId, overwriteMissed);
    }
    catch (IOException e) {
      throw handleError(e);
    }

    invertedNameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);
    invertedNameIndexModCount.incrementAndGet();

    return nameId;
  }

  void fillRecord(int fileId,
                  long timestamp,
                  long length,
                  int flags,
                  int nameId,
                  int parentId,
                  boolean overwriteMissed) throws IOException {
    checkNotDisposed();
    connection.getRecords().fillRecord(fileId, timestamp, length, flags, nameId, parentId, overwriteMissed);
    connection.markDirty();
  }


  //========== file attributes accessors: ========================================

  @Nullable AttributeInputStream readAttributeWithLock(int fileId,
                                                       @NotNull FileAttribute attribute) {
    //RC: attributeAccessor acquires lock anyway, no need for additional lock here
    try {
      return readAttribute(fileId, attribute);
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  /** must be called under r or w lock */
  private @Nullable AttributeInputStream readAttribute(int fileId,
                                                       @NotNull FileAttribute attribute) throws IOException {
    return attributeAccessor.readAttribute(fileId, attribute);
  }

  @NotNull AttributeOutputStream writeAttribute(int fileId,
                                                @NotNull FileAttribute attribute) {
    return attributeAccessor.writeAttribute(fileId, attribute);
  }

  //'raw' (lambda + ByteBuffer instead of Input/OutputStream) attributes access: experimental

  @ApiStatus.Internal
  boolean supportsRawAttributesAccess() {
    return attributeAccessor.supportsRawAccess();
  }

  @ApiStatus.Internal
  <R> @Nullable R readAttributeRawWithLock(int fileId,
                                           @NotNull FileAttribute attribute,
                                           ByteBufferReader<R> reader) {
    //RC: attributeAccessor acquires lock anyway, no need for additional lock here
    try {
      return attributeAccessor.readAttributeRaw(fileId, attribute, reader);
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  @ApiStatus.Internal
  void writeAttributeRaw(int fileId,
                         @NotNull FileAttribute attribute,
                         @NotNull ByteBufferWriter writer) {
    attributeAccessor.writeAttributeRaw(fileId, attribute, writer);
  }


  //========== file content accessors: ========================================

  @Nullable DataInputStream readContent(int fileId) {
    try {
      return contentAccessor.readContent(fileId);
    }
    catch (InterruptedIOException ie) {
      //RC: goal is to just bypass handleError(), which likely marks VFS corrupted,
      //    but thread interruption during _read_ doesn't corrupt anything
      throw new RuntimeException(ie);
    }
    catch (OutOfMemoryError oom) {
      throw oom;
    }
    catch (ZipException e) {
      // we use zip to compress content
      String fileName = getName(fileId);
      long length = getLength(fileId);
      IOException diagnosticException = new IOException(
        "Failed to decompress file's content for file. File name = " + fileName + ", length = " + length);
      diagnosticException.addSuppressed(e);
      throw handleError(diagnosticException);
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  @NotNull DataInputStream readContentById(int contentId) {
    try {
      return contentAccessor.readContentDirectly(contentId);
    }
    catch (InterruptedIOException ie) {
      //RC: goal is to just not go into handleError(), which likely marks VFS corrupted,
      //    but thread interruption during _read_ doesn't corrupt anything
      throw new RuntimeException(ie);
    }
    catch (OutOfMemoryError oom) {
      throw oom;
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  int acquireFileContent(int fileId) {
    try {
      return contentAccessor.acquireContentRecord(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  void releaseContent(int contentId) {
    try {
      contentAccessor.releaseContentRecord(contentId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @NotNull DataOutputStream writeContent(int fileId,
                                         boolean fixedSize) {
    return new DataOutputStream(contentAccessor.new ContentOutputStream(fileId, fixedSize)) {
      @Override
      public void close() {
        try {
          super.close();
          if (((PersistentFSContentAccessor.ContentOutputStream)out).isModified()) {
            checkNotDisposed();
            connection.markRecordAsModified(fileId);
          }
        }
        catch (IOException e) {
          throw handleError(e);
        }
      }
    };
  }

  void writeContent(int fileId,
                    @NotNull ByteArraySequence bytes,
                    boolean fixedSize) {
    try {
      contentAccessor.writeContent(fileId, bytes, fixedSize);
    }
    //TODO RC: catch and rethrow InterruptedIOException & OoMError as in readContent(),
    //         thus bypassing handleError() and VFS rebuild. But I'm not sure that writeContent
    //         is really safe against thread-interruption/OoM: i.e. it could be InterruptedException
    //         or OoM really left RefCountingContentStorage in a inconsistent state -- more
    //         thoughtful analysis (and likely a tests!) needed
    catch (Throwable t) {
      throw handleError(t);
    }
  }

  @TestOnly
  byte[] getContentHash(int fileId) {
    try {
      return contentAccessor.getContentHash(fileId);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  int storeUnlinkedContent(byte[] bytes) {
    try {
      return contentAccessor.allocateContentRecordAndStore(bytes);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }


  //========== aux: ========================================

  /**
   * With method create 'VFS corruption marker', which forces VFS to rebuild on next startup.
   * If cause argument is not null -- this scenario is considered an 'error', and warnings are
   * logged, if cause is null -- the scenario is considered not an 'error', but a regular
   * request -- e.g. no errors logged.
   * TODO rename to scheduleRebuild()?
   */
  void invalidateCaches(final @Nullable String diagnosticMessage,
                        final @Nullable Throwable cause) {
    checkNotDisposed();
    connection.scheduleVFSRebuild(diagnosticMessage, cause);
  }

  /**
   * Method is supposed to be called in a pattern like this:
   * <pre>
   * try{
   *  ...
   * }
   * catch(Throwable t){
   *   throw handleError(e);
   * }
   * </pre>
   * i.e. in a 'throw' statement -- to make clear, it will throw an exception. Method declared to
   * return RuntimeException specifically for that purpose: to be used in a 'throw' statement, so
   * the javac understands it is as a method exit point.
   */
  @Contract("_->fail")
  RuntimeException handleError(Throwable e) throws RuntimeException, Error {
    if (e instanceof ClosedStorageException || disposed) {
      // no connection means IDE is closing...
      AlreadyDisposedException alreadyDisposed = new AlreadyDisposedException("VFS already disposed");
      alreadyDisposed.addSuppressed(e);
      if (disposed && disposedStackTrace != null) {
        alreadyDisposed.addSuppressed(disposedStackTrace);
      }
      throw alreadyDisposed;
    }
    if (e instanceof ProcessCanceledException) {
      throw (ProcessCanceledException)e;
    }

    errorHandler.handleError(this, e);

    //errorHandler.handleError() _must_ throw some exception:
    throw new AssertionError("Bug: should be unreachable, since ErrorHandle must throw some exception", e);
  }

  //========== diagnostic, sanity checks: ========================================

  @TestOnly
  void checkFilenameIndexConsistency() {
    invertedNameIndex.checkConsistency();
  }

  void checkSanity() {
    try {
      recordAccessor.checkSanity();
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  @NotNull String describeAlreadyCreatedFile(int fileId,
                                             int nameId) {
    //RC: Actually, this method is better to be in VfsData class from there it is called.
    //    The only non-public method needed is .list(parentId) -- all other methods are
    //    open to be called from VfsData.
    int parentId = getParent(fileId);
    String description = "fileId=" + fileId +
                         "; nameId=" + nameId + "(" + FileNameCache.getVFileName(nameId) + ")" +
                         "; parentId=" + parentId;
    if (parentId > 0) {
      description += "; parent.name=" + getName(parentId)
                     + "; parent.children=" + list(parentId) + "; ";
    }
    return description;
  }

  public int corruptionsDetected(){
    return connection.corruptionsDetected();
  }
  //========== accessors for diagnostics & sanity checks: ========================


  PersistentFSConnection connection() {
    return connection;
  }

  PersistentFSContentAccessor contentAccessor() {
    return contentAccessor;
  }

  PersistentFSAttributeAccessor attributeAccessor() {
    return attributeAccessor;
  }

  PersistentFSTreeAccessor treeAccessor() {
    return treeAccessor;
  }

  PersistentFSRecordAccessor recordAccessor() {
    return recordAccessor;
  }

  public interface ErrorHandler {

    /**
     * Called when some of FSRecords method encounters an exception, it doesn't know how to process by itself.
     * The method should throw an exception: either rethrow original exception, or wrap it into something more
     * appropriate -- but could also have side effects: e.g. schedule VFS rebuild is the most obvious,
     * 'traditional' way of handling errors.
     */
    void handleError(@NotNull FSRecordsImpl records,
                     @NotNull Throwable error) throws Error, RuntimeException;
  }
}
