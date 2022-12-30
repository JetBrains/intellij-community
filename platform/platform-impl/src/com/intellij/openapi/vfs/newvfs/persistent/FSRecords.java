// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.SlowOperations;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.*;
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
import java.util.function.Function;
import java.util.function.IntPredicate;

import static com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID;

@ApiStatus.Internal
public final class FSRecords {
  static final Logger LOG = Logger.getInstance(FSRecords.class);

  public static final boolean useContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);
  static final boolean backgroundVfsFlush = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);
  static final boolean inlineAttributes = SystemProperties.getBooleanProperty("idea.inline.vfs.attributes", true);

  /**
   * If true, enhance each attribute record with backref to fileId owned this attribute(s).
   * @deprecated This is likely unfinished work since this backref is never used
   */
  @Deprecated
  static final boolean bulkAttrReadSupport = SystemProperties.getBooleanProperty("idea.bulk.attr.read", false);

  static final boolean useCompressionUtil = SystemProperties.getBooleanProperty("idea.use.lightweight.compression.for.vfs", false);
  /**
   * If true -> use {@link CompactRecordsTable} for managing attributes record, instead of default {@link com.intellij.util.io.storage.RecordsTable}
   */
  static final boolean useSmallAttrTable = SystemProperties.getBooleanProperty("idea.use.small.attr.table.for.vfs", true);

  public static final String IDE_USE_FS_ROOTS_DATA_LOADER = "idea.fs.roots.data.loader";

  public static final boolean USE_FAST_NAMES_IMPLEMENTATION = SystemProperties.getBooleanProperty("idea.vfs.use-fast-names-storage", false);
  public static final boolean USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION = SystemProperties.getBooleanProperty("idea.vfs.use-streamlined-attributes-storage", false);

  private static volatile PersistentFSConnection ourConnection;
  private static volatile PersistentFSContentAccessor ourContentAccessor;
  private static volatile PersistentFSAttributeAccessor ourAttributeAccessor;
  private static volatile PersistentFSTreeAccessor ourTreeAccessor;
  private static volatile PersistentFSRecordAccessor ourRecordAccessor;
  private static volatile int ourCurrentVersion;

  private static final AtomicLong ourNamesIndexModCount = new AtomicLong();

  private static int nextMask(int value, int bits, int prevMask) {
    assert value < (1 << bits) && value >= 0 : value;
    int mask = (prevMask << bits) | value;
    if (mask < 0) throw new IllegalStateException("Too many flags, int mask overflown");
    return mask;
  }

  private static int nextMask(boolean value, int prevMask) {
    return nextMask(value ? 1 : 0, 1, prevMask);
  }

  private static int calculateVersion() {
    return nextMask(59 + (PersistentFSRecordsStorage.RECORDS_STORAGE_KIND.ordinal()),  // acceptable range is [0..255]
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
                    nextMask(ZipHandlerBase.getUseCrcInsteadOfTimestampPropertyValue(),
                    nextMask(USE_FAST_NAMES_IMPLEMENTATION,
                    nextMask(USE_STREAMLINED_ATTRIBUTES_IMPLEMENTATION, 0 )))))))))))));
  }

  private static final FileAttribute ourSymlinkTargetAttr = new FileAttribute("FsRecords.SYMLINK_TARGET");
  private static final FineGrainedIdLock updateLock = new FineGrainedIdLock();

  /**
   * @return nameId > 0
   */
  static int writeAttributesToRecord(int fileId,
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
      handleError(e);
      throw new RuntimeException(e);
    }

    InvertedNameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);
    ourNamesIndexModCount.incrementAndGet();

    return nameId;
  }

  public static @NotNull String diagnosticsForAlreadyCreatedFile(int fileId, int nameId, @NotNull Object existingData) {
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
      ourAttributeAccessor = new PersistentFSAttributeAccessor(ourConnection);
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

  public static @NotNull String getCachesDir() {
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
    ourNamesIndexModCount.incrementAndGet();
    try {
      //ourConnection.incModCount(id) -> will be done anyway in .setFlags(FREE_RECORD)
      markAsDeletedRecursively(id);
      ourConnection.markDirty();
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
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
    InvertedNameIndex.updateFileName(id, NULL_NAME_ID, nameId);
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

  static @PersistentFS.Attributes int getFlags(int id) {
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
    try {
      return ourTreeAccessor.findOrCreateRootRecord(rootUrl);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void loadRootData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) {
    try {
      ourTreeAccessor.loadRootData(id, path, fs);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void loadDirectoryData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) {
    try {
      ourTreeAccessor.loadDirectoryData(id, path, fs);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void deleteRootRecord(int fileId) {
    try {
      ourTreeAccessor.deleteRootRecord(fileId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
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

  /**
   * @return child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
   */
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

  public static @NotNull List<CharSequence> listNames(int parentId) {
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

  // Perform operation on children and save the list atomically:
  // Obtain fresh children and try to apply `childrenConvertor` to the children of `parentId`.
  // If everything is still valid (i.e. no one changed the list in the meantime), commit.
  // Failing that, repeat pessimistically: retry converter inside write lock for fresh children and commit inside the same write lock
  static @NotNull ListResult update(@NotNull VirtualFile parent, int parentId, @NotNull Function<? super ListResult, ListResult> childrenConvertor) {
    SlowOperations.assertSlowOperationsAreAllowed();

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
        ourConnection.markRecordAsModified(parentId);
        ourConnection.markDirty();
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
      updateLock.unlock(parentId);
    }
  }

  static void moveChildren(int fromParentId, int toParentId) {
    assert fromParentId > 0 : fromParentId;
    assert toParentId > 0 : toParentId;

    if (fromParentId == toParentId) return;

    int minId = Math.min(fromParentId, toParentId);
    int maxId = Math.max(fromParentId, toParentId);

    updateLock.lock(minId);
    try {
      updateLock.lock(maxId);
      try {
        try {
          final ListResult children = list(fromParentId);

          if (LOG.isDebugEnabled()) {
            LOG.debug("Move children from " + fromParentId + " to " + toParentId + "; children = " + children);
          }

          ourConnection.markRecordAsModified(toParentId);
          ourTreeAccessor.doSaveChildren(toParentId, children);

          ourConnection.markRecordAsModified(fromParentId);
          ourTreeAccessor.doSaveChildren(fromParentId, new ListResult(Collections.emptyList(), fromParentId));

          ourConnection.markDirty();
        }
        catch (ProcessCanceledException e) {
          // NewVirtualFileSystem.list methods can be interrupted now
          throw e;
        }
        catch (Throwable e) {
          handleError(e);
          ExceptionUtil.rethrow(e);
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
    try {
      ourConnection.markDirty();
      try (DataOutputStream stream = writeAttribute(id, ourSymlinkTargetAttr)) {
        IOUtil.writeUTF(stream, StringUtil.notNullize(symlinkTarget));
      }
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }


  static int getLocalModCount() {
    return ourConnection.getModificationCount() + ourAttributeAccessor.getLocalModificationCount();
  }

  @TestOnly
  static int getPersistentModCount() {
    return ourConnection.getPersistentModCount();
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

  static @Nullable VirtualFileSystemEntry findFileById(int id, @NotNull VirtualDirectoryCache idToDirCache) {
    class ParentFinder implements ThrowableComputable<Void, Exception> {
      private @Nullable IntList path;
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

      private @Nullable VirtualFileSystemEntry findDescendantByIdPath() {
        VirtualFileSystemEntry parent = foundParent;
        if (path != null) {
          for (int i = path.size() - 1; i >= 0; i--) {
            parent = findChild(parent, path.getInt(i));
          }
        }

        return findChild(parent, id);
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

    ParentFinder finder = new ParentFinder();
    try {
      finder.compute();
    }
    catch (Exception e) {
      handleError(e);
      throw new RuntimeException(e);
    }
    VirtualFileSystemEntry file = finder.findDescendantByIdPath();
    if (file != null) {
      LOG.assertTrue(file.getId() == id);
    }
    return file;
  }

  /**
   * @return records (ids) freed in previous session, and not yet re-used in a current session.
   */
  @ApiStatus.Internal
  public static @NotNull IntList getRemainFreeRecords() {
    return ourConnection.getFreeRecords();
  }

  /**
   * @return records (ids) freed in current session. Returns !empty list only in unit-tests, outside of testing records freeing in
   * current session are marked by an apt flag, but not collected into free-list
   */
  @ApiStatus.Internal
  public static @NotNull IntList getNewFreeRecords() {
    return ourRecordAccessor.getNewFreeRecords();
  }

  static void setParent(int id, int parentId) {
    if (id == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    try {
      ourConnection.getRecords().setParent(id, parentId);
      ourConnection.markDirty();
    }
    catch (Throwable e) {
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

  //TODO RC: this method is used to look up files by name, but this non-strict enumerator this approach
  //         becomes 'non-strict' also: nameId returned could be the new nameId, never used before, hence
  //         in any file record, even though name was already registered for some file(s)
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

  static @NotNull CharSequence getNameSequence(int id) {
    try {
      int nameId = ourConnection.getRecords().getNameId(id);
      return nameId == 0 ? "" : FileNameCache.getVFileName(nameId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
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

  static void setName(int fileId, @NotNull String name, int oldNameId) {
    try {
      ourNamesIndexModCount.incrementAndGet();
      int nameId = getNameId(name);
      ourConnection.getRecords().setNameId(fileId, nameId);
      ourConnection.markDirty();
      InvertedNameIndex.updateFileName(fileId, nameId, oldNameId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void setFlags(int id, @PersistentFS.Attributes int flags) {
    try {
      if (ourConnection.getRecords().setFlags(id, flags)) {
        ourConnection.markDirty();
      }
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
      if (ourConnection.getRecords().putLength(id, len)) {
        ourConnection.markDirty();
      }
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void fillRecord(int id, long timestamp, long length, int flags, int nameId, int parentId, boolean overwriteMissed)
    throws IOException {
    ourConnection.getRecords().fillRecord(id, timestamp, length, flags, nameId, parentId, overwriteMissed);
    ourConnection.markDirty();
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
      if (ourConnection.getRecords().putTimestamp(id, value)) {
        ourConnection.markDirty();
      }
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

  static @Nullable DataInputStream readContent(int fileId) {
    try {
      return ourContentAccessor.readContent(fileId);
    }
    catch (OutOfMemoryError outOfMemoryError) {
      throw outOfMemoryError;
    }
    catch (Throwable e) {
      handleError(e);
    }
    return null;
  }

  static @NotNull DataInputStream readContentById(int contentId) {
    try {
      return ourContentAccessor.readContentDirectly(contentId);
    }
    catch (Throwable e) {
      handleError(e);
    }
    return null;
  }


  public static @Nullable AttributeInputStream readAttributeWithLock(int fileId, @NotNull FileAttribute attribute) {
    try {
      return readAttribute(fileId, attribute);
    }
    catch (Throwable e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  // must be called under r or w lock
  private static @Nullable AttributeInputStream readAttribute(int fileId, @NotNull FileAttribute attribute) throws IOException {
    return ourAttributeAccessor.readAttribute(fileId, attribute);
  }

  static int acquireFileContent(int fileId) {
    try {
      return ourContentAccessor.acquireContentRecord(fileId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static void releaseContent(int contentId) {
    try {
      ourContentAccessor.releaseContentRecord(contentId);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
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

  static @NotNull DataOutputStream writeContent(int fileId, boolean readOnly) {
    return new DataOutputStream(ourContentAccessor.new ContentOutputStream(fileId, readOnly)) {
      @Override
      public void close() {
        try {
          super.close();
          if (((PersistentFSContentAccessor.ContentOutputStream)out).isModified()) {
            ourConnection.markRecordAsModified(fileId);
          }
        }
        catch (IOException e) {
          handleError(e);
        }
      }
    };
  }

  static void writeContent(int fileId, @NotNull ByteArraySequence bytes, boolean readOnly) {
    try {
      ourContentAccessor.writeContent(fileId, bytes, readOnly);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  static int storeUnlinkedContent(byte[] bytes) {
    try {
      return ourContentAccessor.allocateContentRecordAndStore(bytes);
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  public static @NotNull AttributeOutputStream writeAttribute(final int fileId, @NotNull FileAttribute att) {
    return ourAttributeAccessor.writeAttribute(fileId, att);
  }

  static synchronized void dispose() {
    if (ourConnection != null) {
      PersistentFSConnector.disconnect(ourConnection);

      ourConnection = null;
      ourContentAccessor = null;
      ourAttributeAccessor = null;
      ourTreeAccessor = null;
      ourRecordAccessor = null;
    }
  }

  public static void invalidateCaches() {
    ourConnection.createBrokenMarkerFile(null);
  }

  static void checkSanity() {
    try {
      ourRecordAccessor.checkSanity();
    }
    catch (IOException e) {
      handleError(e);
      throw new RuntimeException(e);
    }
  }

  @Contract("_->fail")
  public static void handleError(Throwable e) throws RuntimeException, Error {
    if (ourConnection != null) {
      ourConnection.handleError(e);
    }
    //TODO RC: connection.handleError re-throw the exception, but in almost all
    // callsites it is called in pair with throw new RuntimeException(e). Would
    // be cleaner if handleError() do that 'throw' in a branch ourConnection==null,
    // and remove all throw new RuntimeException(e) statement from callsites
  }

  @TestOnly
  public static void checkFilenameIndexConsistency() {
    InvertedNameIndex.checkConsistency();
  }
}
