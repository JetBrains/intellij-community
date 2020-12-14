// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage.RECORD_SIZE;

@ApiStatus.Internal
public final class FSRecords {
  private static final Logger LOG = Logger.getInstance(FSRecords.class);

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

  private static int nextMask(int value, int bits, int prevMask) {
    assert value < (1<<bits) && value >= 0 : value;
    int mask = (prevMask << bits) | value;
    if (mask < 0) {
      throw new IllegalStateException("Too many flags, int mask overflown");
    }
    return mask;
  }
  private static int nextMask(boolean value, int prevMask) {
    return nextMask(value ? 1 : 0, 1, prevMask);
  }
  private static int nextMask(int versionValue, int prevMask) {
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
                                     nextMask(ZipHandlerBase.USE_CRC_INSTEAD_OF_TIMESTAMP,0)))))))))));

  private static final IntList ourNewFreeRecords = new IntArrayList();
  static final FileAttribute ourChildrenAttr = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");
  private static final FileAttribute ourSymlinkTargetAttr = new FileAttribute("FsRecords.SYMLINK_TARGET");
  static final ReentrantReadWriteLock lock;
  private static final ReentrantReadWriteLock.ReadLock r;
  private static final ReentrantReadWriteLock.WriteLock w;


  static final int FREE_RECORD_FLAG = 0x400;
  static {
    assert (PersistentFS.Flags.ALL_VALID_FLAGS & FREE_RECORD_FLAG) == 0 : PersistentFS.Flags.ALL_VALID_FLAGS;
  }
  private static final int ALL_VALID_FLAGS = PersistentFS.Flags.ALL_VALID_FLAGS | FREE_RECORD_FLAG;

  static {
    lock = new ReentrantReadWriteLock();
    r = lock.readLock();
    w = lock.writeLock();
  }

  // return nameId>0
  static int writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    return writeAndHandleErrors(() -> {
      int nameId = setName(id, name);

      setTimestamp(id, attributes.lastModified);
      setLength(id, attributes.isDirectory() ? -1L : attributes.length);

      setFlags(id, PersistentFSImpl.fileAttributesToFlags(attributes), true);
      setParent(id, parentId);
      return nameId;
    });
  }

  @Contract("_->fail")
  static void requestVfsRebuild(@NotNull Throwable e) {
    handleError(e);
  }

  @NotNull
  public static String diagnosticsForAlreadyCreatedFile(int id, int nameId, @NotNull Object existingData) {
    invalidateCaches();
    int parentId = getParent(id);
    String msg = "File already created: id="+id + "; nameId="+nameId + "("+getNameByNameId(nameId)+"); parentId=" + parentId+ "; existingData=" + existingData;
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
    try {
      ourConnection.getAttributeId(ourChildrenAttr.getId()); // trigger writing / loading of vfs attribute ids in top level write action
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
    return readAndHandleErrors(FSRecords::getTimestamp);
  }

  // todo: Address  / capacity store in records table, size store with payload
  public static int createRecord() {
    return writeAndHandleErrors(() -> {
      ourConnection.markDirty();

      final int free = ourConnection.getFreeRecord();
      if (free == 0) {
        final int fileLength = length();
        LOG.assertTrue(fileLength % RECORD_SIZE == 0);
        int newRecord = fileLength / RECORD_SIZE;
        ourConnection.getRecords().cleanRecord(newRecord);
        assert fileLength + RECORD_SIZE == length();
        return newRecord;
      }
      else {
        deleteContentAndAttributes(free);
        ourConnection.getRecords().cleanRecord(free);
        return free;
      }
    });
  }

  private static int length() {
    return (int)ourConnection.getRecords().length();
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
    markAsDeleted(id);
  }

  private static void markAsDeleted(final int id) {
    writeAndHandleErrors(() -> {
      ourConnection.markDirty();
      addToFreeRecordsList(id);
    });
  }

  private static void deleteContentAndAttributes(int id) throws IOException {
    ourContentAccessor.deleteContent(id, ourConnection);
    ourAttributeAccessor.deleteAttributes(id, ourConnection);
  }

  private static void addToFreeRecordsList(int id) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourNewFreeRecords.add(id);
    }
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    setFlags(id, FREE_RECORD_FLAG, false);
  }

  private static final int ROOT_RECORD_ID = 1;

  @TestOnly
  static int @NotNull [] listRoots() {
    return readAndHandleErrors(() -> {
      if (ourStoreRootsSeparately) {
        IntList result = new IntArrayList();

        try (LineNumberReader stream = new LineNumberReader(Files.newBufferedReader(ourConnection.getPersistentFSPaths().getRootsFile()))) {
          String str;
          while ((str = stream.readLine()) != null) {
            int index = str.indexOf(' ');
            int id = Integer.parseInt(str.substring(0, index));
            result.add(id);
          }
        }
        catch (FileNotFoundException ignored) { }
        return result.toIntArray();
      }

      try (DataInputStream input = readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        if (input == null) return ArrayUtilRt.EMPTY_INT_ARRAY;
        final int count = DataInputOutputUtil.readINT(input);
        int[] result = ArrayUtil.newIntArray(count);
        int prevId = 0;
        for (int i = 0; i < count; i++) {
          DataInputOutputUtil.readINT(input); // Name
          prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId; // Id
        }
        return result;
      }
    });
  }

  @TestOnly
  static void force() {
    writeAndHandleErrors(ourConnection::doForce);
  }

  @TestOnly
  static boolean isDirty() {
    return readAndHandleErrors(ourConnection::isDirty);
  }

  static long getTimestamp() {
    return ourConnection.getTimestamp();
  }

  @PersistentFS.Attributes
  static int getFlags(int id) {
    return readAndHandleErrors(() -> ourConnection.getRecords().doGetFlags(id));
  }

  private static void saveNameIdSequenceWithDeltas(int[] names, int[] ids, DataOutputStream output) throws IOException {
    DataInputOutputUtil.writeINT(output, names.length);
    int prevId = 0;
    int prevNameId = 0;
    for (int i = 0; i < names.length; i++) {
      DataInputOutputUtil.writeINT(output, names[i] - prevNameId);
      DataInputOutputUtil.writeINT(output, ids[i] - prevId);
      prevId = ids[i];
      prevNameId = names[i];
    }
  }

  static int findRootRecord(@NotNull String rootUrl) {
    return writeAndHandleErrors(() -> {
      if (ourStoreRootsSeparately) {
        try (LineNumberReader stream = new LineNumberReader(Files.newBufferedReader(ourConnection.getPersistentFSPaths().getRootsFile()))) {
          String str;
          while((str = stream.readLine()) != null) {
            int index = str.indexOf(' ');

            if (str.substring(index + 1).equals(rootUrl)) {
              return Integer.parseInt(str.substring(0, index));
            }
          }
        }
        catch (FileNotFoundException ignored) {}

        ourConnection.markDirty();
        try (Writer stream = Files.newBufferedWriter(ourConnection.getPersistentFSPaths().getRootsFile(), StandardOpenOption.APPEND)) {
          int id = createRecord();
          stream.write(id + " " + rootUrl + "\n");
          return id;
        }
      }

      int root = ourConnection.getNames().tryEnumerate(rootUrl);

      int[] names = ArrayUtilRt.EMPTY_INT_ARRAY;
      int[] ids = ArrayUtilRt.EMPTY_INT_ARRAY;
      try (final DataInputStream input = readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        if (input != null) {
          final int count = DataInputOutputUtil.readINT(input);
          names = ArrayUtil.newIntArray(count);
          ids = ArrayUtil.newIntArray(count);
          int prevId = 0;
          int prevNameId = 0;

          for (int i = 0; i < count; i++) {
            final int name = DataInputOutputUtil.readINT(input) + prevNameId;
            final int id = DataInputOutputUtil.readINT(input) + prevId;
            if (name == root) {
              return id;
            }

            prevNameId = names[i] = name;
            prevId = ids[i] = id;
          }
        }
      }

      ourConnection.markDirty();
      root = ourConnection.getNames().enumerate(rootUrl);

      int id;
      try (DataOutputStream output = writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        id = createRecord();

        int index = Arrays.binarySearch(ids, id);
        ids = ArrayUtil.insert(ids, -index - 1, id);
        names = ArrayUtil.insert(names, -index - 1, root);

        saveNameIdSequenceWithDeltas(names, ids, output);
      }

      return id;
    });
  }

  static void deleteRootRecord(int id) {
    writeAndHandleErrors(() -> {
      ourConnection.markDirty();
      if (ourStoreRootsSeparately) {
        List<String> rootsThatLeft = new ArrayList<>();
        try (LineNumberReader stream = new LineNumberReader(Files.newBufferedReader(ourConnection.getPersistentFSPaths().getRootsFile()))) {
          String str;
          while((str = stream.readLine()) != null) {
            int index = str.indexOf(' ');
            int rootId = Integer.parseInt(str.substring(0, index));
            if (rootId != id) {
              rootsThatLeft.add(str);
            }
          }
        }
        catch (FileNotFoundException ignored) {}

        try (Writer stream = Files.newBufferedWriter(ourConnection.getPersistentFSPaths().getRootsFile())) {
          for (String line : rootsThatLeft) {
            stream.write(line);
            stream.write("\n");
          }
        }
        return;
      }

      int[] names;
      int[] ids;
      try (final DataInputStream input = readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        assert input != null;
        int count = DataInputOutputUtil.readINT(input);

        names = ArrayUtil.newIntArray(count);
        ids = ArrayUtil.newIntArray(count);
        int prevId = 0;
        int prevNameId = 0;
        for (int i = 0; i < count; i++) {
          names[i] = DataInputOutputUtil.readINT(input) + prevNameId;
          ids[i] = DataInputOutputUtil.readINT(input) + prevId;
          prevId = ids[i];
          prevNameId = names[i];
        }
      }

      final int index = ArrayUtil.find(ids, id);
      assert index >= 0;

      names = ArrayUtil.remove(names, index);
      ids = ArrayUtil.remove(ids, index);

      try (DataOutputStream output = writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        saveNameIdSequenceWithDeltas(names, ids, output);
      }
    });
  }

  static int @NotNull [] listIds(int id) {
    return readAndHandleErrors(() -> {
      try (final DataInputStream input = readAttribute(id, ourChildrenAttr)) {
        if (input == null) return ArrayUtilRt.EMPTY_INT_ARRAY;
        final int count = DataInputOutputUtil.readINT(input);
        final int[] result = ArrayUtil.newIntArray(count);
        int prevId = id;
        for (int i = 0; i < count; i++) {
          prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId;
        }
        return result;
      }
    });
  }

  static boolean mayHaveChildren(int id) {
    return readAndHandleErrors(() -> {
      try (final DataInputStream input = readAttribute(id, ourChildrenAttr)) {
        if (input == null) return true;
        final int count = DataInputOutputUtil.readINT(input);
        return count != 0;
      }
    });
  }

  // returns child infos (sorted by id) without (potentially expensive) name (or without even nameId if `loadNameId` is false)
  @NotNull
  static ListResult list(int parentId) {
    return readAndHandleErrors(() -> doLoadChildren(parentId));
  }

  @NotNull
  public static List<CharSequence> listNames(int parentId) {
    return ContainerUtil.map(list(parentId).children, c -> c.getName());
  }

  @NotNull
  private static ListResult doLoadChildren(int parentId) throws IOException {
    assert parentId > 0 : parentId;
    try (DataInputStream input = readAttribute(parentId, ourChildrenAttr)) {
      int count = input == null ? 0 : DataInputOutputUtil.readINT(input);
      List<ChildInfo> result = count == 0 ? Collections.emptyList() : new ArrayList<>(count);
      int prevId = parentId;
      for (int i = 0; i < count; i++) {
        int id = DataInputOutputUtil.readINT(input) + prevId;
        prevId = id;
        int nameId = ourConnection.getRecords().getNameId(id);
        ChildInfo child = new ChildInfoImpl(id, nameId, null, null, null);
        result.add(child);
      }
      return new ListResult(result);
    }
  }

  static boolean wereChildrenAccessed(int id) {
    checkFileIsValid(id);
    return readAndHandleErrors(() -> {
      return ourAttributeAccessor.hasAttributePage(id, ourChildrenAttr, ourConnection);
    });
  }

  static <T> T readAndHandleErrors(@NotNull ThrowableComputable<T, ?> action) {
    assert lock.getReadHoldCount() == 0; // otherwise DbConnection.handleError(e) (requires write lock) could fail
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
  static ListResult update(int parentId, @NotNull Function<? super ListResult, ? extends ListResult> childrenConvertor) {
    assert parentId > 0: parentId;
    ListResult children = list(parentId);
    ListResult result = childrenConvertor.apply(children);

    try {
      w.lock();
      ListResult toSave;
      // optimization: if the children were never changed after list(), do not check for duplicates again
      if (result.childrenWereChangedSinceLastList()) {
        children = doLoadChildren(parentId);
        toSave = childrenConvertor.apply(children);
      }
      else {
        toSave = result;
      }
      // optimization: when converter returned unchanged children (see e.g. PersistentFSImpl.findChildInfo())
      // then do not save them back again unnecessarily
      if (!toSave.equals(children)) {
        updateSymlinksForNewChildren(parentId, children, toSave);
        doSaveChildren(parentId, toSave);
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

  private static void updateSymlinksForNewChildren(int parentId, @NotNull ListResult oldChildren, @NotNull ListResult newChildren) {
    // find children which are added to the list and call updateSymlinkInfoForNewChild() on them (once)
    ContainerUtil.processSortedListsInOrder(oldChildren.children, newChildren.children, Comparator.comparingInt(ChildInfo::getId), true,
                                            (childInfo, isOldInfo) -> {
                                              if (!isOldInfo) {
                                                updateSymlinkInfoForNewChild(parentId, childInfo);
                                              }
                                            });
  }

  private static void updateSymlinkInfoForNewChild(int parentId, @NotNull ChildInfo info) {
    int attributes = info.getFileAttributeFlags();
    if (attributes != -1 && PersistentFS.isSymLink(attributes)) {
      int id = info.getId();
      String symlinkTarget = info.getSymlinkTarget();
      storeSymlinkTarget(id, symlinkTarget);
      CharSequence name = info.getName();
      LocalFileSystem fs = LocalFileSystem.getInstance();
      if (fs instanceof LocalFileSystemImpl) {
        VirtualFile parent = PersistentFS.getInstance().findFileById(parentId);
        assert parent != null : parentId + '/' + id + ": " + name + " -> " + symlinkTarget;
        String linkPath = parent.getPath() + '/' + name;
        ((LocalFileSystemImpl)fs).symlinkUpdated(id, parent, name, linkPath, symlinkTarget);
      }
    }
  }

  private static void doSaveChildren(int parentId, @NotNull ListResult toSave) throws IOException {
    ourConnection.markDirty();
    try (DataOutputStream record = writeAttribute(parentId, ourChildrenAttr)) {
      DataInputOutputUtil.writeINT(record, toSave.children.size());

      int prevId = parentId;
      for (ChildInfo childInfo : toSave.children) {
        int childId = childInfo.getId();
        if (childId <= 0) {
          throw new IllegalArgumentException("ids must be >0 but got: "+childId+"; childInfo: "+childInfo+"; list: "+toSave);
        }
        if (childId == parentId) {
          LOG.error("Cyclic parent-child relations. parentId="+parentId+"; list: "+toSave);
        }
        else {
          int delta = childId - prevId;
          if (prevId != parentId && delta <= 0) {
            throw new IllegalArgumentException("The list must be sorted by (unique) id but got parentId: " + parentId  + "; delta: " + delta+"; childInfo: "+childInfo+"; prevId: "+prevId+"; toSave: "+toSave);
          }
          DataInputOutputUtil.writeINT(record, delta);
          prevId = childId;
        }
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

  static int getModCount() {
    return readAndHandleErrors(ourConnection::getGlobalModCount);
  }

  private static void incModCount(int id) {
    int count = ourConnection.incGlobalModCount();
    ourConnection.getRecords().setModCount(id, count);
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
    class ParentFinder implements ThrowableComputable<Void, Throwable> {
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

      private VirtualFileSystemEntry findDescendantByIdPath() {
        VirtualFileSystemEntry parent = foundParent;
        if (path != null) {
          for (int i = path.size() - 1; i >= 0; i--) {
            parent = findChild(parent, path.getInt(i));
          }
        }

        return findChild(parent, id);
      }

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
    return readAndHandleErrors(() -> new IntArrayList(ourNewFreeRecords));
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

  // return nameId>0
  static int setName(int id, @NotNull String name) {
    return writeAndHandleErrors(() -> {
      incModCount(id);
      int nameId = ourConnection.getNames().enumerate(name);
      assert nameId > 0 : nameId;
      ourConnection.getRecords().setNameId(id, nameId);
      return nameId;
    });
  }

  static void setFlags(int id, @PersistentFS.Attributes int flags, final boolean markAsChange) {
    writeAndHandleErrors(() -> {
      if (markAsChange) {
        incModCount(id);
      }
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
      checkFileIsValid(fileId);
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
    checkFileIsValid(fileId);

    return ourAttributeAccessor.readAttribute(fileId, attribute, ourConnection);
  }

  private static void checkFileIsValid(int fileId) {
    assert fileId > 0 : fileId;
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
    checkFileIsValid(fileId);
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
    checkFileIsValid(fileId);
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
      }
    });
  }

  public static void invalidateCaches() {
    ourConnection.createBrokenMarkerFile(null);
  }

  static void checkSanity() {
    long t = System.currentTimeMillis();

    int recordCount=
    readAndHandleErrors(() -> {
      final int fileLength = length();
      assert fileLength % RECORD_SIZE == 0;
      return fileLength / RECORD_SIZE;
    });

    IntList usedAttributeRecordIds = new IntArrayList();
    IntList validAttributeIds = new IntArrayList();
    for (int id = 2; id < recordCount; id++) {
      int flags = getFlags(id);
      LOG.assertTrue((flags & ~ALL_VALID_FLAGS) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);
      int currentId = id;
      boolean isFreeRecord = readAndHandleErrors(() -> ourConnection.getFreeRecords().contains(currentId));
      if (BitUtil.isSet(flags, FREE_RECORD_FLAG)) {
        LOG.assertTrue(isFreeRecord, "Record, marked free, not in free list: " + id);
      }
      else {
        LOG.assertTrue(!isFreeRecord, "Record, not marked free, in free list: " + id);
        checkRecordSanity(id, recordCount, usedAttributeRecordIds, validAttributeIds);
      }
    }

    t = System.currentTimeMillis() - t;
    LOG.info("Sanity check took " + t + " ms");
  }

  private static void checkRecordSanity(int id,
                                        int recordCount,
                                        @NotNull IntList usedAttributeRecordIds,
                                        @NotNull IntList validAttributeIds) {
    int parentId = getParent(id);
    assert parentId >= 0 && parentId < recordCount;
    if (parentId > 0 && getParent(parentId) > 0) {
      int parentFlags = getFlags(parentId);
      assert !BitUtil.isSet(parentFlags, FREE_RECORD_FLAG) : parentId + ": " + Integer.toHexString(parentFlags);
      assert BitUtil.isSet(parentFlags, PersistentFS.Flags.IS_DIRECTORY) : parentId + ": " + Integer.toHexString(parentFlags);
    }

    CharSequence name = getNameSequence(id);
    LOG.assertTrue(parentId == 0 || name.length()!=0, "File with empty name found under " + getNameSequence(parentId) + ", id=" + id);

    writeAndHandleErrors(() -> {
      ourContentAccessor.checkContentsStorageSanity(id, ourConnection);
      ourAttributeAccessor.checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds, ourConnection);
    });

    long length = getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  @Contract("_->fail")
  public static void handleError(Throwable e) throws RuntimeException, Error {
    if (ourConnection != null) {
      ourConnection.handleError(e);
    }
  }
}
