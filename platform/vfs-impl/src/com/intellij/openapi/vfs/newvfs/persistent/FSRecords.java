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
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.IntPredicate;

@ApiStatus.Internal
public final class FSRecords {
  static final Logger LOG = Logger.getInstance(FSRecords.class);

  public static final boolean useContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);
  static final boolean backgroundVfsFlush = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);
  static final boolean inlineAttributes = SystemProperties.getBooleanProperty("idea.inline.vfs.attributes", true);
  static final boolean bulkAttrReadSupport = SystemProperties.getBooleanProperty("idea.bulk.attr.read", false);
  static final boolean useCompressionUtil = SystemProperties.getBooleanProperty("idea.use.lightweight.compression.for.vfs", false);
  static final boolean useSmallAttrTable = SystemProperties.getBooleanProperty("idea.use.small.attr.table.for.vfs", true);
  public static final String IDE_USE_FS_ROOTS_DATA_LOADER = "idea.fs.roots.data.loader";

  private static volatile PersistentFSConnection ourConnection;
  private static volatile PersistentFSContentAccessor ourContentAccessor;
  private static volatile PersistentFSAttributeAccessor ourAttributeAccessor;
  private static volatile PersistentFSTreeAccessor ourTreeAccessor;
  private static volatile PersistentFSRecordAccessor ourRecordAccessor;
  private static volatile int ourCurrentVersion;

  private static final AtomicLong ourNamesIndexModCount = new AtomicLong();

  private static int nextMask(int value, int bits, int prevMask) {
    assert value < (1<<bits) && value >= 0 : value;
    int mask = (prevMask << bits) | value;
    if (mask < 0) throw new IllegalStateException("Too many flags, int mask overflown");
    return mask;
  }

  private static int nextMask(boolean value, int prevMask) {
    return nextMask(value ? 1 : 0, 1, prevMask);
  }

  private static int calculateVersion() {
    return nextMask(59 + (PersistentFSRecordsStorage.useLockFreeRecordsStorage ? 1 : 0),  // acceptable range is [0..255]
                    8,
                    nextMask(useContentHashes,
                    nextMask(IOUtil.useNativeByteOrderForByteBuffers(),
                    nextMask(bulkAttrReadSupport,
                    nextMask(inlineAttributes,
                    nextMask(SystemProperties.getBooleanProperty(IDE_USE_FS_ROOTS_DATA_LOADER, false),
                    nextMask(useCompressionUtil,
                    nextMask(useSmallAttrTable,
                    nextMask(PersistentHashMapValueStorage.COMPRESSION_ENABLED,
                    nextMask(FileSystemUtil.DO_NOT_RESOLVE_SYMLINKS,
                    nextMask(ZipHandlerBase.getUseCrcInsteadOfTimestampPropertyValue(), 0)))))))))));
  }

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
  static int writeAttributesToRecord(int fileId, int parentId, @NotNull FileAttributes attributes, @NotNull String name, boolean overwriteMissed) {
    int nameId = getNameId(name);
    long timestamp = attributes.lastModified;
    long length = attributes.isDirectory() ? -1L : attributes.length;
    int flags = PersistentFSImpl.fileAttributesToFlags(attributes);

    try {
      setAttributes(fileId, timestamp, length, flags, nameId, parentId, overwriteMissed);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }

    InvertedNameIndex.updateFileName(fileId, nameId, 0);
    ourNamesIndexModCount.incrementAndGet();

    return nameId;
  }

  @NotNull
  public static String diagnosticsForAlreadyCreatedFile(int fileId, int nameId, @NotNull Object existingData) {
    invalidateCaches();
    int parentId = getParent(fileId);
    String msg = "File already created: fileId=" + fileId +
                 "; nameId=" + nameId + "(" + FileNameCache.getVFileName(nameId) + ")" +
                 "; parentId=" + parentId +
                 "; \nexistingData=" + existingData;
    if (parentId > 0) {
      msg += "; parent.name=" + getName(parentId);
      msg += "; parent.children=" + list(parentId);
    }
    return msg;
  }

  private FSRecords() { }

  @ApiStatus.Internal
  public static int getVersion() {
    return ourCurrentVersion;
  }

  static void connect() {
    if (IOUtil.isSharedCachesEnabled()) {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.set(false);
    }
    try {
      ourCurrentVersion = calculateVersion();
      ourConnection = PersistentFSConnector.connect(getCachesDir(), ourCurrentVersion, useContentHashes);
      ourContentAccessor = new PersistentFSContentAccessor(useContentHashes, ourConnection);
      ourAttributeAccessor = new PersistentFSAttributeAccessor(bulkAttrReadSupport, inlineAttributes, ourConnection);
      ourTreeAccessor = new PersistentFSTreeAccessor(ourAttributeAccessor, ourConnection);
      ourRecordAccessor = new PersistentFSRecordAccessor(ourContentAccessor, ourAttributeAccessor, ourConnection);
      try {
        ourTreeAccessor.ensureLoaded();
      }
      catch (IOException e) {
        LOG.error(e);
        handleError(e);
      }
    }
    finally {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.remove();
    }
  }

  @NotNull
  public static String getCachesDir() {
    String dir = System.getProperty("caches_dir");
    return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
  }

  public static long getCreationTimestamp() {
    try {
      return ourConnection.getTimestamp();
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  // todo: Address  / capacity store in records table, size store with payload
  public static int createRecord() {
    try {
      return ourRecordAccessor.createRecord();
    }
    catch (Exception e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  public static long getNamesIndexModCount() {
    return ourNamesIndexModCount.get();
  }

  static void deleteRecordRecursively(int id) {
    writeAndHandleErrors(() -> {
      ourNamesIndexModCount.incrementAndGet();
      incModCount(id);
      markAsDeletedRecursively(id);
    });
  }

  private static void markAsDeletedRecursively(int id) throws IOException {
    for (int subRecord : listIds(id)) {
      markAsDeletedRecursively(subRecord);
    }

    int nameId = ourConnection.getRecords().getNameId(id);
    if (PersistentFS.isDirectory(getFlags(id))) {
      ourTreeAccessor.deleteDirectoryRecord(id);
    }
    ourRecordAccessor.addToFreeRecordsList(id);
    InvertedNameIndex.updateFileName(id, 0, nameId);
  }

  @TestOnly
  static int @NotNull [] listRoots() {
    try {
      return ourTreeAccessor.listRoots();
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  static void force() {
    try {
      ourConnection.doForce();
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  static boolean isDirty() {
    return ourConnection.isDirty();
  }

  @PersistentFS.Attributes
  static int getFlags(int id) {
    try {
      return ourConnection.getRecords().getFlags(id);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @ApiStatus.Internal
  public static boolean isDeleted(int id) {
    try {
      return ourRecordAccessor.isDeleted(id);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static int findRootRecord(@NotNull String rootUrl) {
    return writeAndHandleErrors(() -> ourTreeAccessor.findOrCreateRootRecord(rootUrl, () -> createRecord()));
  }

  static void loadRootData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) {
    writeAndHandleErrors(() -> ourTreeAccessor.loadRootData(id, path, fs));
  }

  static void loadDirectoryData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) {
    writeAndHandleErrors(() -> ourTreeAccessor.loadDirectoryData(id, path, fs));
  }

  static void deleteRootRecord(int fileId) {
    writeAndHandleErrors(() -> ourTreeAccessor.deleteRootRecord(fileId));
  }

  public static int @NotNull [] listIds(int fileId) {
    try {
      return ourTreeAccessor.listIds(fileId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static boolean mayHaveChildren(int fileId) {
    try {
      return ourTreeAccessor.mayHaveChildren(fileId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  // returns child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
  @NotNull
  static ListResult list(int parentId) {
    try {
      return ourTreeAccessor.doLoadChildren(parentId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static List<CharSequence> listNames(int parentId) {
    return ContainerUtil.map(list(parentId).children, c -> c.getName());
  }

  static boolean wereChildrenAccessed(int id) {
    try {
      return ourTreeAccessor.wereChildrenAccessed(id);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static <T> T read(@NotNull ThrowableComputable<T, ? extends Exception> action) {
    // otherwise DbConnection.handleError(e) (requires write lock) could fail
    if (lock.getReadHoldCount() != 0) {
      try {
        return action.compute();
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    try {
      r.lock();
      try {
        return action.compute();
      }
      finally {
        r.unlock();
      }
    }
    catch (ProcessCanceledException e) {
      // long reads like processXXX can be safely cancelled
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  static <T> T readAndHandleErrors(@NotNull ThrowableComputable<T, ? extends Exception> action) {
    try {
      return read(action);
    }
    catch (ProcessCanceledException e) {
      // long reads like processXXX can be safely cancelled
      throw e;
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

  private static void writeAndHandleErrors(@NotNull ThrowableRunnable<?> action) {
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
  static ListResult update(@NotNull VirtualFile parent, int parentId, @NotNull Function<? super ListResult, ListResult> childrenConvertor) {
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
        incModCount(parentId);
        updateSymlinksForNewChildren(parent, children, toSave);
        ourTreeAccessor.doSaveChildren(parentId, toSave);
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

  static void moveChildren(int fromParentId, int toParentId) {
    assert fromParentId > 0: fromParentId;
    assert toParentId > 0: toParentId;

    w.lock();
    try {
      final ListResult children = list(fromParentId);

      if (LOG.isDebugEnabled()) {
        LOG.debug("Move children from " + fromParentId + " to " + toParentId + "; children = " + children);
      }

      incModCount(toParentId);
      ourTreeAccessor.doSaveChildren(toParentId, children);

      incModCount(fromParentId);
      ourTreeAccessor.doSaveChildren(fromParentId, new ListResult(Collections.emptyList(), fromParentId));
    }
    catch (ProcessCanceledException e) {
      // NewVirtualFileSystem.list methods can be interrupted now
      throw e;
    }
    catch (Throwable e) {
      handleError(e);
      ExceptionUtil.rethrow(e);
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
    try (DataInputStream stream = readAttribute(id, ourSymlinkTargetAttr)) {
      if (stream != null) {
        String result = StringUtil.nullize(IOUtil.readUTF(stream));
        return result == null ? null : FileUtil.toSystemIndependentName(result);
      }
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
    return null;
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
    return ourConnection.getLocalModificationCount() + ourAttributeAccessor.getLocalModificationCount();
  }

  @TestOnly
  static int getPersistentModCount() {
    return ourConnection.getPersistentModCount();
  }

  static void incModCount(int id) throws IOException {
    ourConnection.incModCount(id);
  }

  public static int incGlobalModCount() throws IOException {
    return ourConnection.incGlobalModCount();
  }

  public static int getParent(int id) {
    try {
      int parentId = ourConnection.getRecords().getParent(id);
      if (parentId == id) {
        LOG.error("Cyclic parent child relations in the database. id = " + id);
        return 0;
      }

      return parentId;
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @Nullable
  static VirtualFileSystemEntry findFileById(int id, @NotNull VirtualDirectoryCache idToDirCache) {
    class ParentFinder implements ThrowableComputable<Void, Exception> {
      @Nullable private IntList path;
      private VirtualFileSystemEntry foundParent;

      @Override
      public Void compute() throws Exception {
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
          foundParent = idToDirCache.getCachedDir(parentId);
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
          LOG.assertTrue(childId == child.getId());
          VirtualFileSystemEntry old = idToDirCache.cacheDirIfAbsent(child);
          if (old != null) child = old;
        }
        return child;
      }
    }

    ParentFinder finder = new ParentFinder();
    readAndHandleErrors(finder);
    VirtualFileSystemEntry file = finder.findDescendantByIdPath();
    if (file != null) {
      LOG.assertTrue(file.getId() == id);
    }
    return file;
  }

  @ApiStatus.Internal
  @NotNull
  public static IntList getRemainFreeRecords() {
    return ourConnection.getFreeRecords();
  }

  @ApiStatus.Internal
  @NotNull
  public static IntList getNewFreeRecords() {
    return ourRecordAccessor.getNewFreeRecords();
  }

  static void setParent(int id, int parentId) {
    if (id == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    try {
      incGlobalModCount();
      ourConnection.getRecords().setParent(id, parentId);
    } catch (Throwable e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  public static boolean processAllNames(@NotNull Processor<? super CharSequence> processor) {
    try {
      return ourConnection.getNames().processAllDataObjects(processor);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  public static boolean processFilesWithNames(@NotNull Set<String> names, @NotNull IntPredicate processor) {
    if (names.isEmpty()) return true;
    return InvertedNameIndex.processFilesWithNames(names, processor);
  }

  public static int getNameId(@NotNull String name) {
    try {
      return ourConnection.getNames().enumerate(name);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  public static @NotNull String getName(int id) {
    return getNameSequence(id).toString();
  }

  @NotNull
  static CharSequence getNameSequence(int id) {
    int nameId = 0;
    try {
      nameId = ourConnection.getRecords().getNameId(id);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
    return nameId == 0 ? "" : FileNameCache.getVFileName(nameId);
  }

  public static CharSequence getNameByNameId(int nameId) {
    try {
      return doGetNameByNameId(nameId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  private static CharSequence doGetNameByNameId(int nameId) throws IOException {
    assert nameId >= 0 : nameId;
    return nameId == 0 ? "" : ourConnection.getNames().valueOf(nameId);
  }

  /**
   * @return nameId
   */
  static int setName(int fileId, @NotNull String name, int oldNameId) {
    return writeAndHandleErrors(() -> {
      ourNamesIndexModCount.incrementAndGet();
      incModCount(fileId);
      int nameId = getNameId(name);
      ourConnection.getRecords().setNameId(fileId, nameId);
      InvertedNameIndex.updateFileName(fileId, nameId, oldNameId);
      return nameId;
    });
  }

  static void setFlags(int id, @PersistentFS.Attributes int flags) {
    try {
      ourConnection.getRecords().setFlags(id, flags);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static long getLength(int id) {
    try {
      return ourConnection.getRecords().getLength(id);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void setLength(int id, long len) {
    try {
      ourConnection.getRecords().putLength(id, len);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void setAttributes(int id, long timestamp, long length, int flags, int nameId, int parentId, boolean overwriteMissed) throws IOException {
    ourConnection.getRecords().setAttributesAndIncModCount(id, timestamp, length, flags, nameId, parentId, overwriteMissed);
  }

  static long getTimestamp(int id) {
    try {
      return ourConnection.getRecords().getTimestamp(id);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void setTimestamp(int id, long value) {
    try {
      ourConnection.getRecords().putTimestamp(id, value);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static int getModCount(int id) {
    try {
      return ourConnection.getRecords().getModCount(id);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @Nullable
  static DataInputStream readContent(int fileId) {
    ThrowableComputable<DataInputStream, IOException> computable = readAndHandleErrors(() -> ourContentAccessor.readContent(fileId));
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
      return ourContentAccessor.readContentDirectly(contentId);
    }
    catch (Throwable e) {
      handleError(e);
    }
    return null;
  }

  @Nullable
  public static AttributeInputStream readAttributeWithLock(int fileId, @NotNull FileAttribute att) {
    try {
      try (AttributeInputStream stream = readAttribute(fileId, att)) {
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
    }
    catch (Throwable e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  // must be called under r or w lock
  @Nullable
  private static AttributeInputStream readAttribute(int fileId, @NotNull FileAttribute attribute) throws IOException {
    return ourAttributeAccessor.readAttribute(fileId, attribute);
  }

  static int acquireFileContent(int fileId) {
    return writeAndHandleErrors(() -> ourContentAccessor.acquireContentRecord(fileId));
  }

  static void releaseContent(int contentId) {
    writeAndHandleErrors(() -> ourContentAccessor.releaseContentRecord(contentId));
  }

  static int getContentId(int fileId) {
    try {
      return ourConnection.getRecords().getContentRecordId(fileId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  static byte[] getContentHash(int fileId) {
    try {
      return ourContentAccessor.getContentHash(fileId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @NotNull
  static DataOutputStream writeContent(int fileId, boolean readOnly) {
    return new DataOutputStream(ourContentAccessor.new ContentOutputStream(fileId, readOnly)) {
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
      ourContentAccessor.writeContent(fileId, bytes, readOnly);
    });
  }

  static int storeUnlinkedContent(byte[] bytes) {
    return writeAndHandleErrors(() -> ourContentAccessor.allocateContentRecordAndStore(bytes));
  }

  @NotNull
  public static AttributeOutputStream writeAttribute(final int fileId, @NotNull FileAttribute att) {
    return ourAttributeAccessor.writeAttribute(fileId, att);
  }

  static synchronized void dispose() {
    writeAndHandleErrors(() -> {
      try {
        InvertedNameIndex.clear();
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
      ourRecordAccessor.checkSanity();
      return null;
    });
  }

  @Contract("_->fail")
  public static void handleError(Throwable e) throws RuntimeException, Error {
    if (ourConnection != null) {
      ourConnection.handleError(e);
    }
  }

  @TestOnly
  public static void checkFilenameIndexConsistency() {
    InvertedNameIndex.checkConsistency();
  }
}
