// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentHashMapValueStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

@ApiStatus.Internal
public final class FSRecords {

  static final Logger LOG = Logger.getInstance(FSRecords.class);

  public static final boolean useContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);
  static final boolean backgroundVfsFlush = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);
  static final boolean inlineAttributes = SystemProperties.getBooleanProperty("idea.inline.vfs.attributes", true);
  static final boolean bulkAttrReadSupport = SystemProperties.getBooleanProperty("idea.bulk.attr.read", false);
  static final boolean useCompressionUtil = SystemProperties.getBooleanProperty("idea.use.lightweight.compression.for.vfs", false);
  static final boolean useSmallAttrTable = SystemProperties.getBooleanProperty("idea.use.small.attr.table.for.vfs", true);
  static final boolean ourStoreRootsSeparately = SystemProperties.getBooleanProperty("idea.store.roots.separately", false);

  static volatile PersistentFSConnection ourConnection;
  static volatile PersistentFSContentAccessor ourContentAccessor;
  static volatile PersistentFSAttributeAccessor ourAttributeAccessor;
  static volatile PersistentFSTreeAccessor ourTreeAccessor;
  static volatile PersistentFSRecordAccessor ourRecordAccessor;

  private static int nextMask(int value, int bits, int prevMask) {
    assert value < (1<<bits) && value >= 0 : value;
    int mask = (prevMask << bits) | value;
    if (mask < 0) throw new IllegalStateException("Too many flags, int mask overflown");
    return mask;
  }
  private static int nextMask(boolean value, int prevMask) {
    return nextMask(value ? 1 : 0, 1, prevMask);
  }
  private static int nextMask(@SuppressWarnings("SameParameterValue") int versionValue, int prevMask) {
    return nextMask(versionValue, 8, prevMask);
  }
  static final int VERSION = nextMask(58,  // acceptable range is [0..255]
                             nextMask(useContentHashes,
                             nextMask(IOUtil.BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER,
                             nextMask(bulkAttrReadSupport,
                             nextMask(inlineAttributes,
                             nextMask(ourStoreRootsSeparately,
                             nextMask(useCompressionUtil,
                             nextMask(useSmallAttrTable,
                             nextMask(PersistentHashMapValueStorage.COMPRESSION_ENABLED,
                             nextMask(FileSystemUtil.DO_NOT_RESOLVE_SYMLINKS,
                             nextMask(ZipHandlerBase.USE_CRC_INSTEAD_OF_TIMESTAMP, 0)))))))))));

  private static final FileAttribute ourSymlinkTargetAttr = new FileAttribute("FsRecords.SYMLINK_TARGET");
  static final ReentrantReadWriteLock lock;
  private static final ReentrantReadWriteLock.ReadLock r;
  private static final ReentrantReadWriteLock.WriteLock w;

  static {
    lock = new ReentrantReadWriteLock();
    r = lock.readLock();
    w = lock.writeLock();
  }

  /**
   * @return nameId > 0
   */
  static int writeAttributesToRecord(int fileId, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    return writeAndHandleErrors(() -> {
      int nameId = setName(fileId, name);

      setTimestamp(fileId, attributes.lastModified);
      setLength(fileId, attributes.isDirectory() ? -1L : attributes.length);
      setFlags(fileId, PersistentFSImpl.fileAttributesToFlags(attributes));
      setParent(fileId, parentId);

      return nameId;
    });
  }

  @Contract("_->fail")
  static void requestVfsRebuild(@NotNull Throwable e) {
    handleError(e);
  }

  @NotNull
  public static String diagnosticsForAlreadyCreatedFile(int fileId, int nameId, @NotNull Object existingData) {
    invalidateCaches();
    int parentId = getParent(fileId);
    String msg = "File already created: fileId=" + fileId +
                 "; nameId=" + nameId + "(" + getNameByNameId(nameId) + ")" +
                 "; parentId=" + parentId +
                 "; existingData=" + existingData;
    if (parentId > 0) {
      msg += "; parent.name=" + getName(parentId);
      msg += "; parent.children=" + list(parentId);
    }
    return msg;
  }

  private FSRecords() { }

  @ApiStatus.Internal
  public static int getVersion() {
    return VERSION;
  }

  static void connect() {
    ourConnection = PersistentFSConnector.connect(getCachesDir(), getVersion(), useContentHashes);
    ourContentAccessor = new PersistentFSContentAccessor(useContentHashes);
    ourAttributeAccessor = new PersistentFSAttributeAccessor(bulkAttrReadSupport, inlineAttributes);
    ourTreeAccessor = new PersistentFSTreeAccessor(ourAttributeAccessor, ourStoreRootsSeparately);
    ourRecordAccessor = new PersistentFSRecordAccessor(ourContentAccessor, ourAttributeAccessor);
    try {
      ourTreeAccessor.ensureLoaded(ourConnection);
    }
    catch (IOException e) {
      handleError(e);
    }
  }

  private static String getCachesDir() {
    String dir = System.getProperty("caches_dir");
    return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
  }

  public static long getCreationTimestamp() {
    return readAndHandleErrors(() -> ourConnection.getTimestamp());
  }

  // todo: Address  / capacity store in records table, size store with payload
  public static int createRecord() {
    return writeAndHandleErrors(() -> ourRecordAccessor.createRecord(ourConnection));
  }

  static void deleteRecordRecursively(int id) {
    writeAndHandleErrors(() -> {
      incModCount(id);
      markAsDeletedRecursively(id);
    });
  }

  private static void markAsDeletedRecursively(final int id) {
    for (int subRecord : listIds(id)) {
      markAsDeletedRecursively(subRecord);
    }

    ourRecordAccessor.addToFreeRecordsList(id, ourConnection);
  }

  @TestOnly
  static int @NotNull [] listRoots() {
    return readAndHandleErrors(() -> ourTreeAccessor.listRoots(ourConnection));
  }

  @TestOnly
  static void force() {
    writeAndHandleErrors(ourConnection::doForce);
  }

  @TestOnly
  static boolean isDirty() {
    return readAndHandleErrors(ourConnection::isDirty);
  }

  @PersistentFS.Attributes
  static int getFlags(int id) {
    return readAndHandleErrors(() -> ourConnection.getRecords().doGetFlags(id));
  }

  static int findRootRecord(@NotNull String rootUrl) {
    return writeAndHandleErrors(() -> ourTreeAccessor.findOrCreateRootRecord(rootUrl, ourConnection, () -> createRecord()));
  }

  static void deleteRootRecord(int fileId) {
    writeAndHandleErrors(() -> ourTreeAccessor.deleteRootRecord(fileId, ourConnection));
  }

  static int @NotNull [] listIds(int fileId) {
    return readAndHandleErrors(() -> ourTreeAccessor.listIds(fileId, ourConnection));
  }

  static boolean mayHaveChildren(int fileId) {
    return readAndHandleErrors(() -> ourTreeAccessor.mayHaveChildren(fileId, ourConnection));
  }

  // returns child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
  @NotNull
  static ListResult list(int parentId) {
    return readAndHandleErrors(() -> ourTreeAccessor.doLoadChildren(parentId, ourConnection));
  }

  @NotNull
  public static List<CharSequence> listNames(int parentId) {
    return ContainerUtil.map(list(parentId).children, c -> c.getName());
  }

  static boolean wereChildrenAccessed(int id) {
    return readAndHandleErrors(() -> ourTreeAccessor.wereChildrenAccessed(id, ourConnection));
  }

  static <T> T readAndHandleErrors(@NotNull ThrowableComputable<T, ? extends Exception> action) {
    // otherwise DbConnection.handleError(e) (requires write lock) could fail
    if (lock.getReadHoldCount() != 0) {
      try {
        return action.compute();
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    r.lock();
    try {
      try {
        return action.compute();
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static <T> T writeAndHandleErrors(@NotNull ThrowableComputable<T, ?> action) {
    w.lock();
    try {
      return action.compute();
    }
    catch (Throwable e) {
      handleError(e);
      throw new RuntimeException(e);
    }
    finally {
      w.unlock();
    }
  }

  static void writeAndHandleErrors(@NotNull ThrowableRunnable<?> action) {
    w.lock();
    try {
      action.run();
    }
    catch (Throwable e) {
      handleError(e);
      throw new RuntimeException(e);
    }
    finally {
      w.unlock();
    }
  }

  static <T extends Exception> void write(@NotNull ThrowableRunnable<T> action) throws T {
    w.lock();
    try {
      action.run();
    }
    finally {
      w.unlock();
    }
  }

  // Perform operation on children and save the list atomically:
  // Obtain fresh children and try to apply `childrenConvertor` to the children of `parentId`.
  // If everything is still valid (i.e. no one changed the list in the meantime), commit.
  // Failing that, repeat pessimistically: retry converter inside write lock for fresh children and commit inside the same write lock
  @NotNull
  static ListResult update(@NotNull VirtualFile parent, int parentId, @NotNull Function<? super ListResult, ? extends ListResult> childrenConvertor) {
    assert parentId > 0: parentId;
    ListResult children = list(parentId);
    ListResult result = childrenConvertor.apply(children);

    w.lock();
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
        updateSymlinksForNewChildren(parent, children, toSave);
        ourTreeAccessor.doSaveChildren(parentId, toSave, ourConnection);
      }
      return toSave;
    }
    catch (ProcessCanceledException e) {
      // NewVirtualFileSystem.list methods can be interrupted now
      throw e;
    }
    catch (Throwable e) {
      handleError(e);
      ExceptionUtil.rethrow(e);
      return result;
    }
    finally {
      w.unlock();
    }
  }

  private static void updateSymlinksForNewChildren(@NotNull VirtualFile parent,
                                                   @NotNull ListResult oldChildren,
                                                   @NotNull ListResult newChildren) {
    // find children which are added to the list and call updateSymlinkInfoForNewChild() on them (once)
    ContainerUtil.processSortedListsInOrder(oldChildren.children, newChildren.children, Comparator.comparingInt(ChildInfo::getId), true,
                                            (childInfo, isOldInfo) -> {
                                              if (!isOldInfo) {
                                                updateSymlinkInfoForNewChild(parent, childInfo);
                                              }
                                            });
  }

  private static void updateSymlinkInfoForNewChild(@NotNull VirtualFile parent, @NotNull ChildInfo info) {
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

  static @Nullable String readSymlinkTarget(int id) {
    String result = readAndHandleErrors(() -> {
      try (DataInputStream stream = readAttribute(id, ourSymlinkTargetAttr)) {
        if (stream != null) return StringUtil.nullize(IOUtil.readUTF(stream));
      }
      return null;
    });
    return result != null ? FileUtil.toSystemIndependentName(result) : null;
  }

  static void storeSymlinkTarget(int id, @Nullable String symlinkTarget) {
    writeAndHandleErrors(() -> {
      ourConnection.markDirty();
      try (DataOutputStream stream = writeAttribute(id, ourSymlinkTargetAttr)) {
        IOUtil.writeUTF(stream, StringUtil.notNullize(symlinkTarget));
      }
    });
  }


  static int getLocalModCount() {
    return ourConnection.getLocalModificationCount(); // This is volatile, only modified under Application.runWriteAction() lock.
  }

  static int getPersistentModCount() {
    return readAndHandleErrors(ourConnection::getPersistentModCount);
  }

  private static void incModCount(int id) {
    ourConnection.incModCount(id);
  }

  public static int getParent(int id) {
    return readAndHandleErrors(() -> {
      final int parentId = ourConnection.getRecords().getParent(id);
      if (parentId == id) {
        LOG.error("Cyclic parent child relations in the database. id = " + id);
        return 0;
      }

      return parentId;
    });
  }

  @Nullable
  static VirtualFileSystemEntry findFileById(int id, @NotNull ConcurrentIntObjectMap<VirtualFileSystemEntry> idToDirCache) {
    class ParentFinder implements ThrowableComputable<Void, Exception> {
      @Nullable private IntList path;
      private VirtualFileSystemEntry foundParent;

      @Override
      public Void compute() {
        int currentId = id;
        while (true) {
          int parentId = ourConnection.getRecords().getParent(currentId);
          if (parentId == 0) {
            break;
          }
          if (parentId == currentId || path != null && path.size() % 128 == 0 && path.contains(parentId)) {
            LOG.error("Cyclic parent child relations in the database. id = " + parentId);
            break;
          }
          foundParent = idToDirCache.get(parentId);
          if (foundParent != null) {
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

      @Nullable
      private VirtualFileSystemEntry findDescendantByIdPath() {
        VirtualFileSystemEntry parent = foundParent;
        if (path != null) {
          for (int i = path.size() - 1; i >= 0; i--) {
            parent = findChild(parent, path.getInt(i));
          }
        }

        return findChild(parent, id);
      }

      @Nullable
      private VirtualFileSystemEntry findChild(VirtualFileSystemEntry parent, int childId) {
        if (!(parent instanceof VirtualDirectoryImpl)) {
          return null;
        }
        VirtualFileSystemEntry child = ((VirtualDirectoryImpl)parent).doFindChildById(childId);
        if (child instanceof VirtualDirectoryImpl) {
          VirtualFileSystemEntry old = idToDirCache.putIfAbsent(childId, child);
          if (old != null) child = old;
        }
        return child;
      }
    }

    ParentFinder finder = new ParentFinder();
    readAndHandleErrors(finder);
    return finder.findDescendantByIdPath();
  }

  @ApiStatus.Internal
  @NotNull
  public static IntList getRemainFreeRecords() {
    return readAndHandleErrors(() -> new IntArrayList(ourConnection.getFreeRecords()));
  }

  @ApiStatus.Internal
  @NotNull
  public static IntList getNewFreeRecords() {
    return readAndHandleErrors(() -> new IntArrayList(ourRecordAccessor.getNewFreeRecords()));
  }

  static void setParent(int id, int parentId) {
    if (id == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    writeAndHandleErrors(() -> {
      incModCount(id);
      ourConnection.getRecords().setParent(id, parentId);
    });
  }

  public static int getNameId(@NotNull String name) {
    return readAndHandleErrors(() -> ourConnection.getNames().enumerate(name));
  }

  public static String getName(int id) {
    return getNameSequence(id).toString();
  }

  @NotNull
  static CharSequence getNameSequence(int id) {
    return readAndHandleErrors(() -> doGetNameSequence(id));
  }

  @NotNull
  private static CharSequence doGetNameSequence(int id) throws IOException {
    int nameId = ourConnection.getRecords().getNameId(id);
    return nameId == 0 ? "" : FileNameCache.getVFileName(nameId, FSRecords::doGetNameByNameId);
  }

  public static String getNameByNameId(int nameId) {
    return readAndHandleErrors(() -> doGetNameByNameId(nameId));
  }

  private static String doGetNameByNameId(int nameId) throws IOException {
    assert nameId >= 0 : nameId;
    return nameId == 0 ? "" : ourConnection.getNames().valueOf(nameId);
  }

  /**
   * @return nameId
   */
  static int setName(int fileId, @NotNull String name) {
    return writeAndHandleErrors(() -> {
      incModCount(fileId);
      int nameId = ourConnection.getNames().enumerate(name);
      ourConnection.getRecords().setNameId(fileId, nameId);
      return nameId;
    });
  }

  static void setFlags(int id, @PersistentFS.Attributes int flags) {
    writeAndHandleErrors(() -> {
      incModCount(id);
      ourConnection.getRecords().setFlags(id, flags);
    });
  }

  static long getLength(int id) {
    return readAndHandleErrors(() -> ourConnection.getRecords().getLength(id));
  }

  static void setLength(int id, long len) {
    writeAndHandleErrors(() -> {
      PersistentFSRecordsStorage records = ourConnection.getRecords();
      if (records.putLength(id, len)) {
        incModCount(id);
      }
    });
  }

  static long getTimestamp(int id) {
    return readAndHandleErrors(() -> ourConnection.getRecords().getTimestamp(id));
  }

  static void setTimestamp(int id, long value) {
    writeAndHandleErrors(() -> {
      PersistentFSRecordsStorage records = ourConnection.getRecords();
      if (records.putTimeStamp(id, value)) {
        incModCount(id);
      }
    });
  }

  static int getModCount(int id) {
    return readAndHandleErrors(() -> ourConnection.getRecords().getModCount(id));
  }

  @Nullable
  static DataInputStream readContent(int fileId) {
    ThrowableComputable<DataInputStream, IOException> computable = readAndHandleErrors(() -> {
      return ourContentAccessor.readContent(fileId, ourConnection);
    });
    if (computable == null) return null;
    try {
      return computable.compute();
    }
    catch (OutOfMemoryError outOfMemoryError) {
      throw outOfMemoryError;
    }
    catch (Throwable e) {
      handleError(e);
    }
    return null;
  }

  @NotNull
  static DataInputStream readContentById(int contentId) {
    try {
      return ourContentAccessor.readContentDirectly(contentId, ourConnection);
    }
    catch (Throwable e) {
      handleError(e);
    }
    return null;
  }

  @Nullable
  public static DataInputStream readAttributeWithLock(int fileId, @NotNull FileAttribute att) {
    return readAndHandleErrors(() -> {
      try (DataInputStream stream = readAttribute(fileId, att)) {
        if (stream != null && att.isVersioned()) {
          try {
            int actualVersion = DataInputOutputUtil.readINT(stream);
            if (actualVersion != att.getVersion()) {
              return null;
            }
          }
          catch (IOException e) {
            return null;
          }
        }
        return stream;
      }
    });
  }

  // must be called under r or w lock
  @Nullable
  private static DataInputStream readAttribute(int fileId, @NotNull FileAttribute attribute) throws IOException {

    return ourAttributeAccessor.readAttribute(fileId, attribute, ourConnection);
  }

  static int acquireFileContent(int fileId) {
    return writeAndHandleErrors(() -> ourContentAccessor.acquireContentRecord(fileId, ourConnection));
  }

  static void releaseContent(int contentId) {
    writeAndHandleErrors(() -> ourContentAccessor.releaseContentRecord(contentId, ourConnection));
  }

  static int getContentId(int fileId) {
    return readAndHandleErrors(() -> ourConnection.getRecords().getContentRecordId(fileId));
  }

  @TestOnly
  static byte[] getContentHash(int fileId) {
    return readAndHandleErrors(() -> ourContentAccessor.getContentHash(fileId, ourConnection));
  }

  @NotNull
  static DataOutputStream writeContent(int fileId, boolean readOnly) {
    return new DataOutputStream(ourContentAccessor.new ContentOutputStream(fileId, readOnly, ourConnection)) {
      @Override
      public void close() {
        writeAndHandleErrors(() -> {
          super.close();
          if (((PersistentFSContentAccessor.ContentOutputStream)out).isModified()) {
            incModCount(fileId);
          }
        });
      }
    };
  }

  static void writeContent(int fileId, @NotNull ByteArraySequence bytes, boolean readOnly) {
    writeAndHandleErrors(() -> {
      if (ourContentAccessor.writeContent(fileId, bytes, readOnly, ourConnection)) {
        incModCount(fileId);
      }
    });
  }

  static int storeUnlinkedContent(byte[] bytes) {
    return writeAndHandleErrors(() -> ourContentAccessor.allocateContentRecordAndStore(bytes, ourConnection));
  }

  @NotNull
  public static DataOutputStream writeAttribute(final int fileId, @NotNull FileAttribute att) {
    DataOutputStream dataOutputStream = ourAttributeAccessor.writeAttribute(fileId, att, ourConnection);
    return new DataOutputStream(dataOutputStream) {
      @Override
      public void close() {
        writeAndHandleErrors(() -> super.close());
      }
    };
  }

  public static PersistentFSPaths getPersistentFSPaths() {
    return new PersistentFSPaths(getCachesDir());
  }

  static synchronized void dispose() {
    writeAndHandleErrors(() -> {
      try {
        ourConnection.doForce();
        ourConnection.closeFiles();
      }
      finally {
        ourConnection = null;
        ourContentAccessor = null;
        ourAttributeAccessor = null;
        ourTreeAccessor = null;
        ourRecordAccessor = null;
      }
    });
  }

  public static void invalidateCaches() {
    ourConnection.createBrokenMarkerFile(null);
  }

  static void checkSanity() {
    writeAndHandleErrors(() -> {
      ourRecordAccessor.checkSanity(ourConnection);
      return null;
    });
  }

  @Contract("_->fail")
  public static void handleError(Throwable e) throws RuntimeException, Error {
    if (ourConnection != null) {
      ourConnection.handleError(e);
    }
  }
}
