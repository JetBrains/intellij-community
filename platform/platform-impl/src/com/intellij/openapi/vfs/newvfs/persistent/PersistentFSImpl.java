/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.*;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.EmptyIntHashSet;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.ReplicatorInputStream;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @author max
 */
public class PersistentFSImpl extends PersistentFS implements ApplicationComponent, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.PersistentFS");

  private final MessageBus myEventBus;

  private final Map<String, VirtualFileSystemEntry> myRoots = ContainerUtil.newConcurrentMap(10, 0.4f, JobSchedulerImpl.CORES_COUNT, FileUtil.PATH_HASHING_STRATEGY);
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myRootsById = ContainerUtil.createConcurrentIntObjectMap(10, 0.4f, JobSchedulerImpl.CORES_COUNT);

  // FS roots must be in this map too. findFileById() relies on this.
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToDirCache = ContainerUtil.createConcurrentIntObjectMap();
  private final Object myInputLock = new Object();

  private final AtomicBoolean myShutDown = new AtomicBoolean(false);
  private volatile int myStructureModificationCount;

  private IFSRecords myRecords;
  private PersistentStringEnumerator myNames;
  private FileNameCache myNamesCache;

  public static PersistentFSImpl getImpl() {
    return (PersistentFSImpl)PersistentFS.getInstance();
  }

  @TestOnly
  public IFSRecords getRecords() {
    return myRecords;
  }

  public FileNameCache getNamesCache() {
    return myNamesCache;
  }

  public PersistentFSImpl(@NotNull MessageBus bus) {
    myEventBus = bus;
    ShutDownTracker.getInstance().registerShutdownTask(this::performShutdown);
    LowMemoryWatcher.register(this::clearIdCache, this);
  }

  @Override
  public void initComponent() {
    final AtomicBoolean once = new AtomicBoolean();
    myRecords = new ShardingFSRecords(() -> {
      assert !once.get();
      once.set(true);
      return new FSRecords(new File(FSRecords.getCachesDir()));
    });
    String cachesDir = System.getProperty("caches_dir");
    cachesDir = cachesDir == null ? PathManager.getSystemPath() + "/caches/" : cachesDir;
    File namesFile = new File(cachesDir, "names" + FSRecords.VFS_FILES_EXTENSION);

    PagedFileStorage.StorageLockContext lockContext = new PagedFileStorage.StorageLockContext(false);
    try {
      myNames = new PersistentStringEnumerator(namesFile, lockContext);
      myNamesCache = new FileNameCache(myNames);
      myRecords.connect(lockContext, myNames, myNamesCache);
    }
    catch (IOException e) {
      myRecords.handleError(e);
    }
  }

  @Override
  public void disposeComponent() {
    performShutdown();
  }

  @Override
  public void dispose() {
  }

  private void performShutdown() {
    if (myShutDown.compareAndSet(false, true)) {
      LOG.info("VFS dispose started");
      myRecords.dispose();
      LOG.info("VFS dispose completed");
    }
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "app.component.PersistentFS";
  }

  @Override
  public boolean areChildrenLoaded(@NotNull final VirtualFile dir) {
    return areChildrenLoaded(getFileId(dir));
  }

  @Override
  public long getCreationTimestamp() {
    return myRecords.getCreationTimestamp();
  }

  public void requestVfsRebuild(Throwable e) {
    myRecords.handleError(e);
  }

  @NotNull
  private static NewVirtualFileSystem getDelegate(@NotNull VirtualFile file) {
    return (NewVirtualFileSystem)file.getFileSystem();
  }

  @Override
  public boolean wereChildrenAccessed(@NotNull final VirtualFile dir) {
    return myRecords.wereChildrenAccessed(getFileId(dir));
  }

  @Override
  @NotNull
  public String[] list(@NotNull final VirtualFile file) {
    System.out.println("list: file = [" + file + "]");
    int id = getFileId(file);

    FSRecords.NameId[] nameIds = myRecords.listAll(id);
    if (!areChildrenLoaded(id)) {
      nameIds  = persistAllChildren(file, id, nameIds);
    }
    return ContainerUtil.map2Array(nameIds, String.class, id1 -> id1.name.toString());
  }

  @Override
  @NotNull
  public String[] listPersisted(@NotNull VirtualFile parent) {
    System.out.println("listPersisted: parent = [" + parent + "]");
    return listPersisted(myRecords.list(getFileId(parent)));
  }

  @NotNull
  private String[] listPersisted(@NotNull int[] childrenIds) {
    String[] names = ArrayUtil.newStringArray(childrenIds.length);
    for (int i = 0; i < childrenIds.length; i++) {
      names[i] = myRecords.getName(childrenIds[i]);
    }
    return names;
  }

  @NotNull
  private FSRecords.NameId[] persistAllChildren(@NotNull final VirtualFile file, final int id, @NotNull FSRecords.NameId[] current) {
    final NewVirtualFileSystem fs = replaceWithNativeFS(getDelegate(file));

    String[] delegateNames = VfsUtil.filterNames(fs.list(file));
    if (delegateNames.length == 0 && current.length > 0) {
      return current;
    }

    Set<String> toAdd = ContainerUtil.newHashSet(delegateNames);
    for (FSRecords.NameId nameId : current) {
      toAdd.remove(nameId.name.toString());
    }

    final TIntArrayList childrenIds = new TIntArrayList(current.length + toAdd.size());
    final List<FSRecords.NameId> nameIds = ContainerUtil.newArrayListWithCapacity(current.length + toAdd.size());
    for (FSRecords.NameId nameId : current) {
      childrenIds.add(nameId.id);
      nameIds.add(nameId);
    }
    for (String newName : toAdd) {
      FakeVirtualFile child = new FakeVirtualFile(file, newName);
      FileAttributes attributes = fs.getAttributes(child);
      if (attributes != null) {
        int childId = createAndFillRecord(fs, child, id, attributes);
        childrenIds.add(childId);
        nameIds.add(new FSRecords.NameId(childId, myNamesCache.storeName(newName), newName));
      }
    }

    myRecords.updateList(id, childrenIds.toNativeArray());
    setChildrenCached(id);

    return nameIds.toArray(new FSRecords.NameId[nameIds.size()]);
  }

  private void setChildrenCached(int id) {
    int flags = myRecords.getFlags(id);
    myRecords.setFlags(id, flags | CHILDREN_CACHED_FLAG, true);
  }

  @Override
  @NotNull
  public FSRecords.NameId[] listAll(@NotNull VirtualFile parent) {
    System.out.println("listAll = [" + parent + "]");
    final int parentId = getFileId(parent);

    FSRecords.NameId[] nameIds = myRecords.listAll(parentId);
    if (!areChildrenLoaded(parentId)) {
      return persistAllChildren(parent, parentId, nameIds);
    }

    return nameIds;
  }

  private boolean areChildrenLoaded(final int parentId) {
    return BitUtil.isSet(myRecords.getFlags(parentId), CHILDREN_CACHED_FLAG); //TODO: make sure flags are set for remote files
  }

  @Override
  @Nullable
  public DataInputStream readAttribute(@NotNull final VirtualFile file, @NotNull final FileAttribute att) {
    System.out.println("readAttribute file = [" + file + "], att = [" + att + "]");
    return myRecords.readAttribute(getFileId(file), att);
  }

  @Override
  @NotNull
  public DataOutputStream writeAttribute(@NotNull final VirtualFile file, @NotNull final FileAttribute att) {
    return myRecords.writeAttribute(getFileId(file), att);
  }

  @Nullable
  public DataInputStream readContent(@NotNull VirtualFile file) {
    return myRecords.readContent(getFileId(file));
  }

  @Nullable
  private DataInputStream readContentById(int contentId) {
    return myRecords.readContentById(contentId);
  }

  @NotNull
  private DataOutputStream writeContent(@NotNull VirtualFile file, boolean readOnly) {
    return myRecords.writeContent(getFileId(file), readOnly);
  }

  private void writeContent(@NotNull VirtualFile file, ByteSequence content, boolean readOnly) {
    myRecords.writeContent(getFileId(file), content, readOnly);
  }

  @Override
  public int storeUnlinkedContent(@NotNull byte[] bytes) {
    System.out.println("storeUnlinkedContent: bytes = [" + bytes + "]");
    return myRecords.storeUnlinkedContent(bytes);
  }

  @Override
  public int getModificationCount(@NotNull final VirtualFile file) {
    System.out.println("getModificationCount: file = [" + file + "]");
    return myRecords.getModCount(getFileId(file));
  }

  @Override
  public int getModificationCount() {
    return myRecords.getLocalModCount();
  }

  @Override
  public int getStructureModificationCount() {
    return myStructureModificationCount;
  }

  public void incStructuralModificationCount() {
    myStructureModificationCount++;
  }

  @Override
  public int getFilesystemModificationCount() {
    return myRecords.getModCount();
  }

  private boolean writeAttributesToRecord(final int id,
                                          final int parentId,
                                          @NotNull VirtualFile file,
                                          @NotNull NewVirtualFileSystem fs,
                                          @NotNull FileAttributes attributes) {
    assert id > 0 : id;
    String name = file.getName();
    if (!name.isEmpty()) {
      if (namesEqual(fs, name, myRecords.getNameSequence(id))) return false; // TODO: Handle root attributes change.
    }
    else {
      if (areChildrenLoaded(id)) return false; // TODO: hack
    }

    myRecords.writeAttributesToRecord(id, parentId, attributes, name);

    return true;
  }

  @Override
  public int getFileAttributes(int id) {
    System.out.println("getFileAttributes: id = [" + id + "]");
    assert id > 0;
    //noinspection MagicConstant
    return myRecords.getFlags(id);
  }

  @Override
  public boolean isDirectory(@NotNull final VirtualFile file) {
    return isDirectory(getFileAttributes(getFileId(file)));
  }

  private static boolean namesEqual(@NotNull VirtualFileSystem fs, @NotNull CharSequence n1, CharSequence n2) {
    return Comparing.equal(n1, n2, fs.isCaseSensitive());
  }

  @Override
  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    System.out.println("exists: fileOrDirectory = [" + fileOrDirectory + "]");
    return ((VirtualFileWithId)fileOrDirectory).getId() > 0;
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    System.out.println("getTimeStamp: file = [" + file + "]");
    return myRecords.getTimestamp(getFileId(file));
  }

  @Override
  public void setTimeStamp(@NotNull final VirtualFile file, final long modStamp) throws IOException {
    final int id = getFileId(file);
    myRecords.setTimestamp(id, modStamp);
    getDelegate(file).setTimeStamp(file, modStamp);
  }

  private static int getFileId(@NotNull VirtualFile file) {
    final int id = ((VirtualFileWithId)file).getId();
    if (id <= 0) {
      throw new InvalidVirtualFileAccessException(file);
    }
    return id;
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    return isSymLink(getFileAttributes(getFileId(file)));
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    return !BitUtil.isSet(getFileAttributes(getFileId(file)), IS_READ_ONLY);
  }

  @Override
  public boolean isHidden(@NotNull VirtualFile file) {
    return BitUtil.isSet(getFileAttributes(getFileId(file)), IS_HIDDEN);
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
    getDelegate(file).setWritable(file, writableFlag);
    boolean oldWritable = isWritable(file);
    if (oldWritable != writableFlag) {
      processEvent(new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, oldWritable, writableFlag, false));
    }
  }

  @Override
  public int getId(@NotNull VirtualFile parent, @NotNull String childName, @NotNull NewVirtualFileSystem fs) {
    System.out.println("getId: parent = [" + parent + "], childName = [" + childName + "], fs = [" + fs + "]");
    int parentId = getFileId(parent);
    int[] children = myRecords.list(parentId);

    if (children.length > 0) {
      // fast path, check that some child has same nameId as given name, this avoid O(N) on retrieving names for processing non-cached children
      int nameId = myRecords.getNameId(childName);
      for (final int childId : children) {
        if (nameId == myRecords.getNameId(childId)) {
          return childId;
        }
      }
      // for case sensitive system the above check is exhaustive in consistent state of vfs
    }

    for (final int childId : children) {
      if (namesEqual(fs, childName, myRecords.getNameSequence(childId))) return childId;
    }

    final VirtualFile fake = new FakeVirtualFile(parent, childName);
    final FileAttributes attributes = fs.getAttributes(fake);
    if (attributes != null) {
      final int child = createAndFillRecord(fs, fake, parentId, attributes);
      myRecords.updateList(parentId, ArrayUtil.append(children, child));
      return child;
    }

    return 0;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    System.out.println("getLength: file = [" + file + "]");
    long len;
    if (mustReloadContent(file)) {
      len = reloadLengthFromDelegate(file, getDelegate(file));
    }
    else {
      len = getLastRecordedLength(file);
    }

    return len;
  }

  @Override
  public long getLastRecordedLength(@NotNull VirtualFile file) {
    System.out.println("getLastRecordedLength: file = [" + file + "]");
    int id = getFileId(file);
    return myRecords.getLength(id);
  }

  @NotNull
  @Override
  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    getDelegate(file).copyFile(requestor, file, parent, name);
    processEvent(new VFileCopyEvent(requestor, file, parent, name));

    final VirtualFile child = parent.findChild(name);
    if (child == null) {
      throw new IOException("Cannot create child");
    }
    return child;
  }

  @NotNull
  @Override
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
    getDelegate(parent).createChildDirectory(requestor, parent, dir);
    processEvent(new VFileCreateEvent(requestor, parent, dir, true, false));

    final VirtualFile child = parent.findChild(dir);
    if (child == null) {
      throw new IOException("Cannot create child directory '" + dir + "' at " + parent.getPath());
    }
    return child;
  }

  @NotNull
  @Override
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) throws IOException {
    getDelegate(parent).createChildFile(requestor, parent, file);
    processEvent(new VFileCreateEvent(requestor, parent, file, false, false));

    final VirtualFile child = parent.findChild(file);
    if (child == null) {
      throw new IOException("Cannot create child file '" + file + "' at " + parent.getPath());
    }
    return child;
  }

  @Override
  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException {
    final NewVirtualFileSystem delegate = getDelegate(file);
    delegate.deleteFile(requestor, file);

    if (!delegate.exists(file)) {
      processEvent(new VFileDeleteEvent(requestor, file, false));
    }
  }

  @Override
  public void renameFile(final Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    getDelegate(file).renameFile(requestor, file, newName);
    String oldName = file.getName();
    if (!newName.equals(oldName)) {
      processEvent(new VFilePropertyChangeEvent(requestor, file, VirtualFile.PROP_NAME, oldName, newName, false));
    }
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file) throws IOException {
    return contentsToByteArray(file, true);
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file, boolean cacheContent) throws IOException {
    System.out.println("contentsToByteArray: file = [" + file + "], cacheContent = [" + cacheContent + "]");
    InputStream contentStream = null;
    boolean reloadFromDelegate;
    boolean outdated;
    int fileId;
    long length = -1L;

    synchronized (myInputLock) {
      fileId = getFileId(file);
      outdated = checkFlag(fileId, MUST_RELOAD_CONTENT) || (length = myRecords.getLength(fileId)) == -1L;
      reloadFromDelegate = outdated || (contentStream = readContent(file)) == null;
    }

    if (reloadFromDelegate) {
      final NewVirtualFileSystem delegate = getDelegate(file);

      final byte[] content;
      if (outdated) {
        // in this case, file can have out-of-date length. so, update it first (it's needed for correct contentsToByteArray() work)
        // see IDEA-90813 for possible bugs
        myRecords.setLength(fileId, delegate.getLength(file));
        content = delegate.contentsToByteArray(file);
      }
      else {
        // a bit of optimization
        content = delegate.contentsToByteArray(file);
        myRecords.setLength(fileId, content.length);
      }

      Application application = ApplicationManager.getApplication();
      // we should cache every local files content
      // because the local history feature is currently depends on this cache,
      // perforce offline mode as well
      if ((!delegate.isReadOnly() ||
           // do not cache archive content unless asked
           cacheContent && !application.isInternal() && !application.isUnitTestMode()) &&
          content.length <= PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD) {
        synchronized (myInputLock) {
          writeContent(file, new ByteSequence(content), delegate.isReadOnly());
          setFlag(file, MUST_RELOAD_CONTENT, false);
        }
      }

      return content;
    }
    else {
      try {
        assert length >= 0 : file;
        return FileUtil.loadBytes(contentStream, (int)length);
      }
      catch (IOException e) {
        myRecords.handleError(fileId, e);
        return ArrayUtil.EMPTY_BYTE_ARRAY;
      }
    }
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(int contentId) throws IOException {
    final DataInputStream stream = readContentById(contentId);
    assert stream != null : contentId;
    return FileUtil.loadBytes(stream);
  }

  @Override
  @NotNull
  public InputStream getInputStream(@NotNull final VirtualFile file) throws IOException {
    System.out.println("getInputStream: file = [" + file + "]");
    synchronized (myInputLock) {
      InputStream contentStream;
      if (mustReloadContent(file) || FileUtilRt.isTooLarge(file.getLength()) || (contentStream = readContent(file)) == null) {
        NewVirtualFileSystem delegate = getDelegate(file);
        long len = reloadLengthFromDelegate(file, delegate);
        InputStream nativeStream = delegate.getInputStream(file);

        if (len > PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD) return nativeStream;
        return createReplicator(file, nativeStream, len, delegate.isReadOnly());
      }
      else {
        return contentStream;
      }
    }
  }

  private long reloadLengthFromDelegate(@NotNull VirtualFile file, @NotNull NewVirtualFileSystem delegate) {
    final long len = delegate.getLength(file);
    myRecords.setLength(getFileId(file), len);
    return len;
  }

  private InputStream createReplicator(@NotNull final VirtualFile file,
                                       final InputStream nativeStream,
                                       final long fileLength,
                                       final boolean readOnly) {
    if (nativeStream instanceof BufferExposingByteArrayInputStream) {
      // optimization
      BufferExposingByteArrayInputStream  byteStream = (BufferExposingByteArrayInputStream )nativeStream;
      byte[] bytes = byteStream.getInternalBuffer();
      storeContentToStorage(fileLength, file, readOnly, bytes, bytes.length);
      return nativeStream;
    }
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    final BufferExposingByteArrayOutputStream cache = new BufferExposingByteArrayOutputStream((int)fileLength);
    return new ReplicatorInputStream(nativeStream, cache) {
      @Override
      public void close() throws IOException {
        super.close();
        storeContentToStorage(fileLength, file, readOnly, cache.getInternalBuffer(), cache.size());
      }
    };
  }

  private void storeContentToStorage(long fileLength,
                                     @NotNull VirtualFile file,
                                     boolean readOnly, @NotNull byte[] bytes, int bytesLength) {
    synchronized (myInputLock) {
      if (bytesLength == fileLength) {
        writeContent(file, new ByteSequence(bytes, 0, bytesLength), readOnly);
        setFlag(file, MUST_RELOAD_CONTENT, false);
      }
      else {
        setFlag(file, MUST_RELOAD_CONTENT, true);
      }
    }
  }

  private boolean mustReloadContent(@NotNull VirtualFile file) {
    int fileId = getFileId(file);
    return checkFlag(fileId, MUST_RELOAD_CONTENT) || myRecords.getLength(fileId) == -1L;
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(@NotNull final VirtualFile file,
                                      final Object requestor,
                                      final long modStamp,
                                      final long timeStamp) throws IOException {
    return new ByteArrayOutputStream() {
      private boolean closed; // protection against user calling .close() twice

      @Override
      public void close() throws IOException {
        if (closed) return;
        super.close();

        ApplicationManager.getApplication().assertWriteAccessAllowed();

        VFileContentChangeEvent event = new VFileContentChangeEvent(requestor, file, file.getModificationStamp(), modStamp, false);
        List<VFileContentChangeEvent> events = Collections.singletonList(event);
        BulkFileListener publisher = myEventBus.syncPublisher(VirtualFileManager.VFS_CHANGES);
        publisher.before(events);

        NewVirtualFileSystem delegate = getDelegate(file);
        OutputStream ioFileStream = delegate.getOutputStream(file, requestor, modStamp, timeStamp);
        // FSRecords.ContentOutputStream already buffered, no need to wrap in BufferedStream
        OutputStream persistenceStream = writeContent(file, delegate.isReadOnly());

        try {
          persistenceStream.write(buf, 0, count);
        }
        finally {
          try {
            ioFileStream.write(buf, 0, count);
          }
          finally {
            closed = true;
            persistenceStream.close();
            ioFileStream.close();

            executeTouch(file, false, event.getModificationStamp());
            publisher.after(events);
          }
        }
      }
    };
  }

  @Override
  public int acquireContent(@NotNull VirtualFile file) {
    return myRecords.acquireFileContent(getFileId(file));
  }

  @Override
  public void releaseContent(int contentId) {
    myRecords.releaseContent(contentId);
  }

  @Override
  public int getCurrentContentId(@NotNull VirtualFile file) {
    return myRecords.getContentId(getFileId(file));
  }

  @Override
  public void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException {
    getDelegate(file).moveFile(requestor, file, newParent);
    processEvent(new VFileMoveEvent(requestor, file, newParent));
  }

  private void processEvent(@NotNull VFileEvent event) {
    processEvents(Collections.singletonList(event));
  }

  private static class EventWrapper {
    private final VFileDeleteEvent event;
    private final int id;

    private EventWrapper(final VFileDeleteEvent event, final int id) {
      this.event = event;
      this.id = id;
    }
  }

  @NotNull private static final Comparator<EventWrapper> DEPTH_COMPARATOR = Comparator.comparingInt(o -> o.event.getFileDepth());

  @NotNull
  private static List<VFileEvent> validateEvents(@NotNull List<VFileEvent> events) {
    final List<EventWrapper> deletionEvents = ContainerUtil.newArrayList();
    for (int i = 0, size = events.size(); i < size; i++) {
      final VFileEvent event = events.get(i);
      if (event instanceof VFileDeleteEvent && event.isValid()) {
        deletionEvents.add(new EventWrapper((VFileDeleteEvent)event, i));
      }
    }

    final TIntHashSet invalidIDs;
    if (deletionEvents.isEmpty()) {
      invalidIDs = EmptyIntHashSet.INSTANCE;
    }
    else {
      ContainerUtil.quickSort(deletionEvents, DEPTH_COMPARATOR);

      invalidIDs = new TIntHashSet(deletionEvents.size());
      final Set<VirtualFile> dirsToBeDeleted = new THashSet<>(deletionEvents.size());
      nextEvent:
      for (EventWrapper wrapper : deletionEvents) {
        final VirtualFile candidate = wrapper.event.getFile();
        VirtualFile parent = candidate;
        while (parent != null) {
          if (dirsToBeDeleted.contains(parent)) {
            invalidIDs.add(wrapper.id);
            continue nextEvent;
          }
          parent = parent.getParent();
        }

        if (candidate.isDirectory()) {
          dirsToBeDeleted.add(candidate);
        }
      }
    }

    final List<VFileEvent> filtered = new ArrayList<>(events.size() - invalidIDs.size());
    for (int i = 0, size = events.size(); i < size; i++) {
      final VFileEvent event = events.get(i);
      if (event.isValid() && !(event instanceof VFileDeleteEvent && invalidIDs.contains(i))) {
        filtered.add(event);
      }
    }
    return filtered;
  }

  @Override
  public void processEvents(@NotNull List<VFileEvent> events) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    List<VFileEvent> validated = validateEvents(events);

    BulkFileListener publisher = myEventBus.syncPublisher(VirtualFileManager.VFS_CHANGES);
    publisher.before(validated);

    THashMap<VirtualFile, List<VFileEvent>> parentToChildrenEventsChanges = null;
    for (VFileEvent event : validated) {
      VirtualFile changedParent = null;
      if (event instanceof VFileCreateEvent) {
        changedParent = ((VFileCreateEvent)event).getParent();
        ((VFileCreateEvent)event).resetCache();
      }
      else if (event instanceof VFileDeleteEvent) {
        changedParent = ((VFileDeleteEvent)event).getFile().getParent();
      }

      if (changedParent != null) {
        if (parentToChildrenEventsChanges == null) parentToChildrenEventsChanges = new THashMap<>();
        List<VFileEvent> parentChildrenChanges = parentToChildrenEventsChanges.computeIfAbsent(changedParent, k -> new SmartList<>());
        parentChildrenChanges.add(event);
      }
      else {
        applyEvent(event);
      }
    }

    if (parentToChildrenEventsChanges != null) {
      parentToChildrenEventsChanges.forEachEntry((parent, childrenEvents) -> {
        applyChildrenChangeEvents(parent, childrenEvents);
        return true;
      });
      parentToChildrenEventsChanges.clear();
    }

    publisher.after(validated);
  }

  @Override
  public DataInputStream readAttributeById(int fileId, FileAttribute attr) {
    return myRecords.readAttribute(fileId, attr);
  }

  @Override
  public DataOutputStream writeAttributeById(int fileId, FileAttribute attr) {
    return myRecords.writeAttribute(fileId, attr);
  }

  private void applyChildrenChangeEvents(@NotNull VirtualFile parent, @NotNull List<VFileEvent> events) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    TIntArrayList childrenIdsUpdated = new TIntArrayList();

    final int parentId = getFileId(parent);
    assert parentId != 0;
    TIntHashSet parentChildrenIds = new TIntHashSet(myRecords.list(parentId));
    boolean hasRemovedChildren = false;

    List<VirtualFile> childrenToBeUpdated = new SmartList<>();
    for (VFileEvent event : events) {
      if (event instanceof VFileCreateEvent) {
        String name = ((VFileCreateEvent)event).getChildName();
        final VirtualFile fake = new FakeVirtualFile(parent, name);
        final FileAttributes attributes = delegate.getAttributes(fake);

        if (attributes != null) {
          final int childId = createAndFillRecord(delegate, fake, parentId, attributes);
          assert parent instanceof VirtualDirectoryImpl : parent;
          final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
          VirtualFileSystemEntry child = dir.createChild(name, childId, dir.getFileSystem());
          childrenToBeUpdated.add(child);
          childrenIdsUpdated.add(childId);
          parentChildrenIds.add(childId);
        }
      }
      else if (event instanceof VFileDeleteEvent) {
        VirtualFile file = ((VFileDeleteEvent)event).getFile();
        if (!file.exists()) {
          LOG.error("Deleting a file, which does not exist: " + file.getPath());
          continue;
        }

        hasRemovedChildren = true;
        int id = getFileId(file);

        childrenToBeUpdated.add(file);
        childrenIdsUpdated.add(-id);
        parentChildrenIds.remove(id);
      }
    }

    myRecords.updateList(parentId, parentChildrenIds.toArray());

    if (hasRemovedChildren) clearIdCache();
    VirtualDirectoryImpl parentImpl = (VirtualDirectoryImpl)parent;

    for (int i = 0, len = childrenIdsUpdated.size(); i < len; ++i) {
      final int childId = childrenIdsUpdated.get(i);
      final VirtualFile childFile = childrenToBeUpdated.get(i);

      if (childId > 0) {
        parentImpl.addChild((VirtualFileSystemEntry)childFile);
      }
      else {
        myRecords.deleteRecordRecursively(-childId);
        parentImpl.removeChild(childFile);
        invalidateSubtree(childFile);
      }
    }
  }

  @Override
  @Nullable
  public VirtualFileSystemEntry findRoot(@NotNull String path, @NotNull NewVirtualFileSystem fs) {
    if (path.isEmpty()) {
      LOG.error("Invalid root, fs=" + fs);
      return null;
    }

    String rootUrl = normalizeRootUrl(path, fs);

    VirtualFileSystemEntry root = myRoots.get(rootUrl);
    if (root != null) return root;

    String rootName;
    String rootPath;
    if (fs instanceof ArchiveFileSystem) {
      ArchiveFileSystem afs = (ArchiveFileSystem)fs;
      VirtualFile localFile = afs.findLocalByRootPath(path);
      if (localFile == null) return null;
      rootName = localFile.getName();
      rootPath = afs.getRootPathByLocal(localFile); // make sure to not create FsRoot with ".." garbage in path
    }
    else {
      rootName = rootPath = path;
    }

    FileAttributes attributes = fs.getAttributes(new StubVirtualFile() {
      @NotNull @Override public String getPath() { return rootPath; }
      @Nullable @Override public VirtualFile getParent() { return null; }
    });
    if (attributes == null || !attributes.isDirectory()) {
      return null;
    }

    int rootId = myRecords.findRootRecord(rootUrl);

    VfsData.Segment segment = VfsData.getSegment(rootId, true);
    VfsData.DirectoryData directoryData = new VfsData.DirectoryData();
    VirtualFileSystemEntry newRoot = new FsRoot(rootId, segment, directoryData, fs, rootName, StringUtil.trimEnd(rootPath, '/'));

    boolean mark;
    synchronized (myRoots) {
      root = myRoots.get(rootUrl);
      if (root != null) return root;

      try {
        VfsData.initFile(rootId, segment, -1, directoryData);
      }
      catch (VfsData.FileAlreadyCreatedException e) {
        for (Map.Entry<String, VirtualFileSystemEntry> entry : myRoots.entrySet()) {
          final VirtualFileSystemEntry existingRoot = entry.getValue();
          if (Math.abs(existingRoot.getId()) == rootId) {
            throw new RuntimeException("Duplicate FS roots: " + rootUrl + " and " + entry.getKey() + ", id=" + rootId + ", valid=" + existingRoot.isValid(), e);
          }
        }
        throw new RuntimeException("No root duplication, roots=" + Arrays.toString(myRecords.listAll(1)), e);
      }
      incStructuralModificationCount();
      mark = writeAttributesToRecord(rootId, 0, newRoot, fs, attributes);

      myRoots.put(rootUrl, newRoot);
      myRootsById.put(rootId, newRoot);
      myIdToDirCache.put(rootId, newRoot);
    }

    if (!mark && attributes.lastModified != myRecords.getTimestamp(rootId)) {
      newRoot.markDirtyRecursively();
    }

    LOG.assertTrue(rootId == newRoot.getId(), "root=" + newRoot + " expected=" + rootId + " actual=" + newRoot.getId());

    return newRoot;
  }

  @NotNull
  private static String normalizeRootUrl(@NotNull String basePath, @NotNull NewVirtualFileSystem fs) {
    // need to protect against relative path of the form "/x/../y"
    String normalized = VfsImplUtil.normalize(fs, FileUtil.toCanonicalPath(basePath));
    String protocol = fs.getProtocol();
    StringBuilder result = new StringBuilder(protocol.length() + URLUtil.SCHEME_SEPARATOR.length() + normalized.length());
    result.append(protocol).append(URLUtil.SCHEME_SEPARATOR).append(normalized);
    return StringUtil.endsWithChar(result, '/') ? UriUtil.trimTrailingSlashes(result.toString()) : result.toString();
  }

  @Override
  public void invalidateCaches() {
    myRecords.invalidateCaches();
  }

  @Override
  public void clearIdCache() {
    // remove all except myRootsById contents
    for (Iterator<ConcurrentIntObjectMap.IntEntry<VirtualFileSystemEntry>> iterator = myIdToDirCache.entries().iterator(); iterator.hasNext(); ) {
      ConcurrentIntObjectMap.IntEntry<VirtualFileSystemEntry> entry = iterator.next();
      int id = entry.getKey();
      if (!myRootsById.containsKey(id)) {
        iterator.remove();
      }
    }
  }

  @Override
  @Nullable
  public NewVirtualFile findFileById(final int id) {
    return findFileById(id, false);
  }

  @Override
  public NewVirtualFile findFileByIdIfCached(final int id) {
    return findFileById(id, true);
  }

  @Nullable
  private VirtualFileSystemEntry findFileById(int id, boolean cachedOnly) {
    VirtualFileSystemEntry cached = myIdToDirCache.get(id);
    if (cached != null) return cached;

    TIntArrayList parents = myRecords.getParents(id, i -> myIdToDirCache.containsKey(i));

    // the last element of the parents is either a root or already cached element
    int parentId = parents.get(parents.size() - 1);
    VirtualFileSystemEntry result = myIdToDirCache.get(parentId);

    for (int i=parents.size() - 2; i>=0; i--) {
      if (!(result instanceof VirtualDirectoryImpl)) {
        return null;
      }
      parentId = parents.get(i);
      result = ((VirtualDirectoryImpl)result).findChildById(parentId, cachedOnly);
      if (result instanceof VirtualDirectoryImpl) {
        VirtualFileSystemEntry old = myIdToDirCache.putIfAbsent(parentId, result);
        if (old != null) result = old;
      }
    }

    return result;
  }

  @Override
  @NotNull
  public VirtualFile[] getRoots() {
    Collection<VirtualFileSystemEntry> roots = myRoots.values();
    return ArrayUtil.stripTrailingNulls(VfsUtilCore.toVirtualFileArray(roots));
  }

  @Override
  @NotNull
  public VirtualFile[] getRoots(@NotNull final NewVirtualFileSystem fs) {
    final List<VirtualFile> roots = new ArrayList<>();

    for (NewVirtualFile root : myRoots.values()) {
      if (root.getFileSystem() == fs) {
        roots.add(root);
      }
    }

    return VfsUtilCore.toVirtualFileArray(roots);
  }

  @Override
  @NotNull
  public VirtualFile[] getLocalRoots() {
    List<VirtualFile> roots = ContainerUtil.newSmartList();

    for (NewVirtualFile root : myRoots.values()) {
      if (root.isInLocalFileSystem() && !(root.getFileSystem() instanceof TempFileSystem)) {
        roots.add(root);
      }
    }
    return VfsUtilCore.toVirtualFileArray(roots);
  }

  private void applyEvent(@NotNull VFileEvent event) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Applying " + event);
    }
    try {
      if (event instanceof VFileCreateEvent) {
        final VFileCreateEvent createEvent = (VFileCreateEvent)event;
        executeCreateChild(createEvent.getParent(), createEvent.getChildName());
      }
      else if (event instanceof VFileDeleteEvent) {
        final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
        executeDelete(deleteEvent.getFile());
      }
      else if (event instanceof VFileContentChangeEvent) {
        final VFileContentChangeEvent contentUpdateEvent = (VFileContentChangeEvent)event;
        executeTouch(contentUpdateEvent.getFile(), contentUpdateEvent.isFromRefresh(), contentUpdateEvent.getModificationStamp());
      }
      else if (event instanceof VFileCopyEvent) {
        final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
        executeCreateChild(copyEvent.getNewParent(), copyEvent.getNewChildName());
      }
      else if (event instanceof VFileMoveEvent) {
        final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
        executeMove(moveEvent.getFile(), moveEvent.getNewParent());
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent propertyChangeEvent = (VFilePropertyChangeEvent)event;
        VirtualFile file = propertyChangeEvent.getFile();
        Object newValue = propertyChangeEvent.getNewValue();
        if (VirtualFile.PROP_NAME.equals(propertyChangeEvent.getPropertyName())) {
          executeRename(file, (String)newValue);
        }
        else if (VirtualFile.PROP_WRITABLE.equals(propertyChangeEvent.getPropertyName())) {
          executeSetWritable(file, ((Boolean)newValue).booleanValue());
          if (LOG.isDebugEnabled()) {
            LOG.debug("File " + file + " writable=" + file.isWritable() + " id=" + getFileId(file));
          }
        }
        else if (VirtualFile.PROP_HIDDEN.equals(propertyChangeEvent.getPropertyName())) {
          executeSetHidden(file, ((Boolean)newValue).booleanValue());
        }
        else if (VirtualFile.PROP_SYMLINK_TARGET.equals(propertyChangeEvent.getPropertyName())) {
          executeSetTarget(file, (String)newValue);
          markForContentReloadRecursively(getFileId(file));
        }
      }
    }
    catch (Exception e) {
      // Exception applying single event should not prevent other events from applying.
      LOG.error(e);
    }
  }

  @NotNull
  @NonNls
  public String toString() {
    return "PersistentFS";
  }

  private void executeCreateChild(@NotNull VirtualFile parent, @NotNull String name) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    final VirtualFile fake = new FakeVirtualFile(parent, name);
    final FileAttributes attributes = delegate.getAttributes(fake);
    if (attributes != null) {
      final int parentId = getFileId(parent);
      final int childId = createAndFillRecord(delegate, fake, parentId, attributes);
      appendIdToParentList(parentId, childId);
      assert parent instanceof VirtualDirectoryImpl : parent;
      final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
      VirtualFileSystemEntry child = dir.createChild(name, childId, dir.getFileSystem());
      dir.addChild(child);
    }
  }

  private int createAndFillRecord(@NotNull NewVirtualFileSystem delegateSystem,
                                  @NotNull VirtualFile delegateFile,
                                  int parentId,
                                  @NotNull FileAttributes attributes) {
    final int childId = myRecords.createChildRecord(parentId);
    writeAttributesToRecord(childId, parentId, delegateFile, delegateSystem, attributes);
    return childId;
  }

  private void appendIdToParentList(final int parentId, final int childId) {
    int[] childrenList = myRecords.list(parentId);
    childrenList = ArrayUtil.append(childrenList, childId);
    myRecords.updateList(parentId, childrenList);
  }

  private void executeDelete(@NotNull VirtualFile file) {
    if (!file.exists()) {
      LOG.error("Deleting a file, which does not exist: " + file.getPath());
      return;
    }
    clearIdCache();

    int id = getFileId(file);

    final VirtualFile parent = file.getParent();
    final int parentId = parent == null ? 0 : getFileId(parent);

    if (parentId == 0) {
      String rootUrl = normalizeRootUrl(file.getPath(), (NewVirtualFileSystem)file.getFileSystem());
      synchronized (myRoots) {
        myRoots.remove(rootUrl);
        myRootsById.remove(id);
        myIdToDirCache.remove(id);
        myRecords.deleteRootRecord(id);
      }
    }
    else {
      removeIdFromParentList(parentId, id, parent, file);
      VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file.getParent();
      assert directory != null : file;
      directory.removeChild(file);
    }

    myRecords.deleteRecordRecursively(id);

    invalidateSubtree(file);
    incStructuralModificationCount();
  }

  private static void invalidateSubtree(@NotNull VirtualFile file) {
    final VirtualFileSystemEntry impl = (VirtualFileSystemEntry)file;
    impl.invalidate();
    for (VirtualFile child : impl.getCachedChildren()) {
      invalidateSubtree(child);
    }
  }

  private void removeIdFromParentList(final int parentId, final int id, @NotNull VirtualFile parent, VirtualFile file) {
    int[] childList = myRecords.list(parentId);

    int index = ArrayUtil.indexOf(childList, id);
    if (index == -1) {
      throw new RuntimeException("Cannot find child (" + id + ")" + file
                                 + "\n\tin (" + parentId + ")" + parent
                                 + "\n\tactual children:" + Arrays.toString(childList));
    }
    childList = ArrayUtil.remove(childList, index);
    myRecords.updateList(parentId, childList);
  }

  private void executeRename(@NotNull VirtualFile file, @NotNull final String newName) {
    final int id = getFileId(file);
    myRecords.setName(id, newName);
    ((VirtualFileSystemEntry)file).setNewName(newName);
  }

  private void executeSetWritable(@NotNull VirtualFile file, boolean writableFlag) {
    setFlag(file, IS_READ_ONLY, !writableFlag);
    ((VirtualFileSystemEntry)file).updateProperty(VirtualFile.PROP_WRITABLE, writableFlag);
  }

  private void executeSetHidden(@NotNull VirtualFile file, boolean hiddenFlag) {
    setFlag(file, IS_HIDDEN, hiddenFlag);
    ((VirtualFileSystemEntry)file).updateProperty(VirtualFile.PROP_HIDDEN, hiddenFlag);
  }

  private static void executeSetTarget(@NotNull VirtualFile file, String target) {
    ((VirtualFileSystemEntry)file).setLinkTarget(target);
  }

  private void setFlag(@NotNull VirtualFile file, int mask, boolean value) {
    setFlag(getFileId(file), mask, value);
  }

  private void setFlag(final int id, final int mask, final boolean value) {
    int oldFlags = myRecords.getFlags(id);
    int flags = value ? oldFlags | mask : oldFlags & ~mask;

    if (oldFlags != flags) {
      myRecords.setFlags(id, flags, true);
    }
  }

  private boolean checkFlag(int fileId, int mask) {
    return BitUtil.isSet(myRecords.getFlags(fileId), mask);
  }

  private void executeTouch(@NotNull VirtualFile file, boolean reloadContentFromDelegate, long newModificationStamp) {
    if (reloadContentFromDelegate) {
      setFlag(file, MUST_RELOAD_CONTENT, true);
    }

    final NewVirtualFileSystem delegate = getDelegate(file);
    final FileAttributes attributes = delegate.getAttributes(file);
    myRecords.setLength(getFileId(file), attributes != null ? attributes.length : DEFAULT_LENGTH);
    myRecords.setTimestamp(getFileId(file), attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP);

    ((VirtualFileSystemEntry)file).setModificationStamp(newModificationStamp);
  }

  private void executeMove(@NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    clearIdCache();

    final int fileId = getFileId(file);
    final int newParentId = getFileId(newParent);
    final int oldParentId = getFileId(file.getParent());

    removeIdFromParentList(oldParentId, fileId, file.getParent(), file);
    myRecords.setParent(fileId, newParentId);
    appendIdToParentList(newParentId, fileId);
    ((VirtualFileSystemEntry)file).setParent(newParent);
  }

  @Override
  public String getName(int id) {
    System.out.println("getName: id = [" + id + "]");
    assert id > 0;
    return myRecords.getName(id);
  }

  @TestOnly
  public void cleanPersistedContents() {
    int[] roots = myRecords.listRoots();
    for (int root : roots) {
      markForContentReloadRecursively(root);
    }
  }

  private void markForContentReloadRecursively(int id) {
    if (isDirectory(getFileAttributes(id))) {
      for (int child : myRecords.list(id)) {
        markForContentReloadRecursively(child);
      }
    }
    else {
      setFlag(id, MUST_RELOAD_CONTENT, true);
    }
  }


  private static class FsRoot extends VirtualDirectoryImpl {
    private final String myName;
    private final String myPathBeforeSlash;

    private FsRoot(int id,
                   @NotNull VfsData.Segment segment,
                   @NotNull VfsData.DirectoryData data,
                   @NotNull NewVirtualFileSystem fs,
                   @NotNull String name,
                   @NotNull String pathBeforeSlash) {
      super(id, segment, data, null, fs);
      myName = name;
      if (!looksCanonical(pathBeforeSlash)) {
        throw new IllegalArgumentException("path must be canonical but got: '" + pathBeforeSlash + "'");
      }
      myPathBeforeSlash = pathBeforeSlash;
    }

    @NotNull
    @Override
    public CharSequence getNameSequence() {
      return myName;
    }

    @NotNull
    @Override
    protected char[] appendPathOnFileSystem(int pathLength, int[] position) {
      char[] chars = new char[pathLength + myPathBeforeSlash.length()];
      position[0] = copyString(chars, position[0], myPathBeforeSlash);
      return chars;
    }

    @Override
    public void setNewName(@NotNull String newName) {
      throw new IncorrectOperationException();
    }

    @Override
    public final void setParent(@NotNull VirtualFile newParent) {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public String getPath() {
      return myPathBeforeSlash + '/';
    }

    @NotNull
    @Override
    public String getUrl() {
      return getFileSystem().getProtocol() + "://" + getPath();
    }
  }

  private static boolean looksCanonical(@NotNull String pathBeforeSlash) {
    if (pathBeforeSlash.endsWith("/")) {
      return false;
    }
    int start = 0;
    while (true) {
      int i = pathBeforeSlash.indexOf("..", start);
      if (i == -1) break;
      if (i != 0 && pathBeforeSlash.charAt(i-1) == '/') return false; // /..
      if (i < pathBeforeSlash.length() - 2 && pathBeforeSlash.charAt(i+2) == '/') return false; // ../
      start = i+1;
    }
    return true;
  }
}