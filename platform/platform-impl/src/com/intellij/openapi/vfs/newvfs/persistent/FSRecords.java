// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferReader;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferWriter;
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.DataOutputStream;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.DataInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * This class is just an 'instance holder' -- actual implementation is an {@link FSRecordsImpl} instance,
 * all methods delegate to it.
 */
@ApiStatus.Internal
public final class FSRecords {
  static final Logger LOG = Logger.getInstance(FSRecords.class);

  static final boolean BACKGROUND_VFS_FLUSH = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);

  /** Not a constant value but just key for a value, because could be changed (see TurbochargedSharedIndexes) */
  public static final String IDE_USE_FS_ROOTS_DATA_LOADER = "idea.fs.roots.data.loader";


  /**
   * Initially recordId=0 was used as a storage header record, hence fileId=0 was reserved.
   * New storages still reserve id=0, even though they usually separate the header from
   * records explicitly -- because it is consistent with id=0 being used as NULL in other parts
   * of app, e.g. in DataEnumerator
   */
  static final int NULL_FILE_ID = 0;

  /**
   * fileId of artificial 'directory' all FS roots are attached to as children. This fs-record
   * is special in a few ways -- e.g. it has CHILDREN storage format different from regular
   * directories (see {@link PersistentFSTreeAccessor#findOrCreateRootRecord(String)}
   */
  static final int ROOT_FILE_ID = NULL_FILE_ID + 1;
  static final int MIN_REGULAR_FILE_ID = ROOT_FILE_ID + 1;


  /** singleton instance */
  private static volatile FSRecordsImpl impl;

  /** Holds stacktrace of the disconnect call */
  private static volatile Exception disconnectLocationStackTrace;


  /** @return path to the directory there all VFS files are located */
  public static @NotNull String getCachesDir() {
    final String dir = System.getProperty("caches_dir");
    return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
  }

  private FSRecords() {
    throw new AssertionError("Not for instantiation");
  }

  //========== lifecycle: =====================================================

  static synchronized void connect(final @NotNull VfsLog vfsLog) throws UncheckedIOException {
    impl = FSRecordsImpl.connect(Path.of(getCachesDir()), vfsLog);
  }

  static synchronized void dispose() {
    final FSRecordsImpl _impl = impl;
    if (_impl != null) {
      _impl.dispose();
      impl = null;
      disconnectLocationStackTrace = new Exception("VFS dispose stacktrace");
    }
  }

  @NotNull
  private static FSRecordsImpl implOrFail() {
    final FSRecordsImpl _impl = impl;
    if (_impl == null || _impl.isDisposed()) {
      throw alreadyDisposed();
    }

    return _impl;
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

  static void deleteRecordRecursively(final int fileId) {
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

  static int findRootRecord(final @NotNull String rootUrl) {
    return implOrFail().findRootRecord(rootUrl);
  }

  static void loadRootData(final int id,
                           final @NotNull String path,
                           final @NotNull NewVirtualFileSystem fs) {
    implOrFail().loadRootData(id, path, fs);
  }

  static void deleteRootRecord(final int fileId) {
    implOrFail().deleteRootRecord(fileId);
  }


  //========== directory/children manipulation: =============================

  static void loadDirectoryData(final int id,
                                final @NotNull VirtualFile parent,
                                final @NotNull CharSequence path,
                                final @NotNull NewVirtualFileSystem fs) {
    implOrFail().loadDirectoryData(id, parent, path, fs);
  }

  public static int @NotNull [] listIds(final int fileId) {
    return implOrFail().listIds(fileId);
  }

  static boolean mayHaveChildren(final int fileId) {
    return implOrFail().mayHaveChildren(fileId);
  }

  /**
   * @return child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
   */
  @NotNull
  static ListResult list(final int parentId) {
    return implOrFail().list(parentId);
  }

  public static @NotNull @Unmodifiable List<CharSequence> listNames(final int parentId) {
    return implOrFail().listNames(parentId);
  }

  static boolean wereChildrenAccessed(final int fileId) {
    return impl.wereChildrenAccessed(fileId);
  }

  // Perform operation on children and save the list atomically:
  // Obtain fresh children and try to apply `childrenConvertor` to the children of `parentId`.
  // If everything is still valid (i.e. no one changed the list in the meantime), commit.
  // Failing that, repeat pessimistically: retry converter inside write lock for fresh children and commit inside the same write lock
  static @NotNull ListResult update(final @NotNull VirtualFile parent,
                                    final int parentId,
                                    final @NotNull Function<? super ListResult, ListResult> childrenConvertor) {
    SlowOperations.assertSlowOperationsAreAllowed();
    return implOrFail().update(parent, parentId, childrenConvertor);
  }

  static void moveChildren(final int fromParentId,
                           final int toParentId) {
    implOrFail().moveChildren(fromParentId, toParentId);
  }

  //MAYBE RC: this method is better to be moved up, to VirtualFileSystem?
  static @Nullable VirtualFileSystemEntry findFileById(final int fileId,
                                                       final @NotNull VirtualDirectoryCache idToDirCache) {
    final FSRecordsImpl impl = implOrFail();

    //We climb up from fileId, collecting parentIds (=path), until find a parent which is cached in
    // idToDirCache. From that (grand)parent we climb down (findDescendantByIdPath) to fileId,
    // resolving every child via idToDirCache:
    class ParentFinder implements ThrowableComputable<Void, Exception> {
      private @Nullable IntList path;
      private VirtualFileSystemEntry foundParent;

      @Override
      public Void compute() {
        int currentId = fileId;
        while (true) {
          final int parentId = impl.getParent(currentId);
          if (path != null && (path.size() % 128 == 0 && path.contains(parentId))) {
            //circularity check is expensive: do it only once-in-a-while, as path became deep enough
            //  to start to suspect something may be wrong.
            LOG.error("Cyclic parent-child relations in the database: fileId = " + fileId + ": path=" + path);
            break;
          }
          foundParent = idToDirCache.getCachedDir(parentId);
          if (foundParent != null) {
            break;
          }
          if (parentId == NULL_FILE_ID) {
            //TODO RC: personally I think here we should throw exception. But (it seems)
            //    the method .findFileById() is used in an assumption it just returns null
            //    if 'incorrect' fileId is passed in? -- so I keep legacy behavior until I'll
            //    be able to understand it better, or fix calling code meaningfully
            final String currentFileName = getName(currentId);
            LOG.info(
              "file[" + fileId + "]: top parent (currentId: " + currentId + ", name: '" + currentFileName + "', parent: 0), " +
              "is still not in the idToDirCache. path: " + path);
            break;
          }

          currentId = parentId;
          if (path == null) {
            path = new IntArrayList();
          }
          path.add(currentId);
        }
        return null;
      }

      private @Nullable VirtualFileSystemEntry findDescendantByIdPath() {
        VirtualFileSystemEntry parent = foundParent;
        if (path != null) {
          for (int i = path.size() - 1; i >= 0; i--) {
            parent = findChild(parent, path.getInt(i));
          }
        }

        return findChild(parent, fileId);
      }

      private @Nullable VirtualFileSystemEntry findChild(VirtualFileSystemEntry parent, int childId) {
        if (!(parent instanceof VirtualDirectoryImpl)) {
          return null;
        }
        VirtualFileSystemEntry child = ((VirtualDirectoryImpl)parent).doFindChildById(childId);
        if (child instanceof VirtualDirectoryImpl) {
          LOG.assertTrue(childId == child.getId());
          VirtualFileSystemEntry old = idToDirCache.cacheDirIfAbsent(child);
          if (old != null) child = old;
        }
        return child;
      }
    }

    final ParentFinder finder = new ParentFinder();
    try {
      finder.compute();
    }
    catch (Throwable t) {
      throw impl.handleError(t);
    }
    final VirtualFileSystemEntry file = finder.findDescendantByIdPath();
    if (file != null) {
      LOG.assertTrue(file.getId() == fileId);
    }
    return file;
  }


  //========== symlink manipulation: ========================================

  static @Nullable String readSymlinkTarget(final int fileId) {
    return implOrFail().readSymlinkTarget(fileId);
  }

  static void storeSymlinkTarget(final int fileId,
                                 final @Nullable String symlinkTarget) {
    implOrFail().storeSymlinkTarget(fileId, symlinkTarget);
  }


  //========== file name iterations: ========================================

  public static boolean processAllNames(@NotNull Processor<? super CharSequence> processor) {
    return implOrFail().processAllNames(processor);
  }

  public static boolean processFilesWithNames(final @NotNull Set<String> names,
                                              final @NotNull IntPredicate processor) {
    return implOrFail().processFilesWithNames(names, processor);
  }


  //========== file record fields accessors: ================================

  public static int getParent(final int fileId) {
    return implOrFail().getParent(fileId);
  }

  static void setParent(final int id,
                        final int parentId) {
    implOrFail().setParent(id, parentId);
  }

  //TODO RC: this method is used to look up files by name, but this non-strict enumerator this approach
  //         becomes 'non-strict' also: nameId returned could be the new nameId, never used before, hence
  //         in any file record, even though name was already registered for some file(s)
  public static int getNameId(final @NotNull String name) {
    return implOrFail().getNameId(name);
  }

  public static @NotNull String getName(final int fileId) {
    return implOrFail().getName(fileId);
  }

  static @NotNull CharSequence getNameSequence(final int fileId) {
    return implOrFail().getNameSequence(fileId);
  }

  public static CharSequence getNameByNameId(final int nameId) {
    return implOrFail().getNameByNameId(nameId);
  }

  static void setName(final int fileId,
                      final @NotNull String name,
                      final int oldNameId) {
    implOrFail().setName(fileId, name, oldNameId);
  }

  static void setFlags(final int fileId,
                       final @PersistentFS.Attributes int flags) {
    implOrFail().setFlags(fileId, flags);
  }

  static @PersistentFS.Attributes int getFlags(final int fileId) {
    return implOrFail().getFlags(fileId);
  }

  @ApiStatus.Internal
  public static boolean isDeleted(final int fileId) {
    return implOrFail().isDeleted(fileId);
  }

  static long getLength(final int fileId) {
    return implOrFail().getLength(fileId);
  }

  static void setLength(final int fileId,
                        final long length) {
    implOrFail().setLength(fileId, length);
  }

  /**
   * @return nameId > 0
   */
  static int writeAttributesToRecord(final int fileId,
                                     final int parentId,
                                     final @NotNull FileAttributes attributes,
                                     final @NotNull String name,
                                     final boolean overwriteMissed) {
    return implOrFail().writeAttributesToRecord(fileId, parentId, attributes, name, overwriteMissed);
  }


  static long getTimestamp(final int fileId) {
    return implOrFail().getTimestamp(fileId);
  }

  static void setTimestamp(final int fileId,
                           final long value) {
    implOrFail().setTimestamp(fileId, value);
  }

  static int getModCount(final int fileId) {
    return impl.getModCount(fileId);
  }

  //========== file attributes accessors: ===================================

  public static @Nullable AttributeInputStream readAttributeWithLock(final int fileId,
                                                                     final @NotNull FileAttribute attribute) {
    return implOrFail().readAttributeWithLock(fileId, attribute);
  }

  public static @NotNull AttributeOutputStream writeAttribute(final int fileId,
                                                              final @NotNull FileAttribute attribute) {
    //TODO RC: we need to check fileId here, and throw exception if it is not valid
    return implOrFail().writeAttribute(fileId, attribute);
  }

  //'raw' (lambda + ByteBuffer instead of Input/OutputStream) attributes access: experimental

  public static boolean supportsRawAttributesAccess() {
    return implOrFail().supportsRawAttributesAccess();
  }

  @ApiStatus.Internal
  public static <R> @Nullable R readAttributeRawWithLock(final int fileId,
                                                         final @NotNull FileAttribute attribute,
                                                         final ByteBufferReader<R> reader) {
    return implOrFail().readAttributeRawWithLock(fileId, attribute, reader);
  }

  public static void writeAttributeRaw(final int fileId,
                                       final FileAttribute attribute,
                                       final ByteBufferWriter writer) {
    implOrFail().writeAttributeRaw(fileId, attribute, writer);
  }

  //========== file content accessors: ======================================

  static int acquireFileContent(final int fileId) {
    return implOrFail().acquireFileContent(fileId);
  }

  static @Nullable DataInputStream readContent(final int fileId) {
    return implOrFail().readContent(fileId);
  }

  static @NotNull DataInputStream readContentById(final int contentId) {
    return implOrFail().readContentById(contentId);
  }

  static void releaseContent(final int contentId) {
    implOrFail().releaseContent(contentId);
  }

  static int getContentId(final int fileId) {
    return implOrFail().getContentId(fileId);
  }

  @TestOnly
  static byte[] getContentHash(final int fileId) {
    return implOrFail().getContentHash(fileId);
  }

  static @NotNull DataOutputStream writeContent(final int fileId,
                                                final boolean readOnly) {
    return implOrFail().writeContent(fileId, readOnly);
  }

  static void writeContent(final int fileId,
                           final @NotNull ByteArraySequence bytes,
                           final boolean readOnly) {
    implOrFail().writeContent(fileId, bytes, readOnly);
  }

  static int storeUnlinkedContent(final byte[] bytes) {
    return implOrFail().storeUnlinkedContent(bytes);
  }

  //========== aux: ========================================================

  public static void invalidateCaches() {
    implOrFail().invalidateCaches();
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
   * i.e. in a 'throw' statement -- to make clear, it will throw an exception. Method made return
   * RuntimeException specifically for that purpose: to be used in a 'throw' statement, so compiler
   * understands it is as a method exit point.
   */
  @Contract("_->fail")
  public static RuntimeException handleError(final Throwable e) throws RuntimeException, Error {
    return implOrFail().handleError(e);
  }

  //========== diagnostic, sanity checks: ==================================


  static void checkSanity() {
    implOrFail().checkSanity();
  }

  /**
   * @return human-readable description of file fileId -- as much information as VFS now contains
   */
  public static @NotNull String describeAlreadyCreatedFile(final int fileId,
                                                           final int nameId) {
    return implOrFail().describeAlreadyCreatedFile(fileId, nameId);
  }

  @TestOnly
  public static void checkFilenameIndexConsistency() {
    final FSRecordsImpl _impl = impl;
    if (_impl != null && !_impl.isDisposed()) {
      _impl.checkFilenameIndexConsistency();
    }
  }

  @NotNull
  private static AlreadyDisposedException alreadyDisposed() {
    final AlreadyDisposedException alreadyDisposed = new AlreadyDisposedException("VFS is already disposed");
    if (disconnectLocationStackTrace != null) {
      alreadyDisposed.addSuppressed(disconnectLocationStackTrace);
    }
    return alreadyDisposed;
  }
}
