/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
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
import com.intellij.util.io.ReplicatorInputStream;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class PersistentFSImpl extends PersistentFS implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.PersistentFS");

  private final MessageBus myEventBus;

  private final Map<String, VirtualFileSystemEntry> myRoots = ContainerUtil.newConcurrentMap(10, 0.4f, JobSchedulerImpl.CORES_COUNT, FileUtil.PATH_HASHING_STRATEGY);
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myRootsById = ContainerUtil.createConcurrentIntObjectMap(10, 0.4f, JobSchedulerImpl.CORES_COUNT);

  // FS roots must be in this map too. findFileById() relies on this.
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToDirCache = ContainerUtil.createConcurrentIntObjectMap();
  private final Object myInputLock = new Object();

  private final AtomicBoolean myShutDown = new AtomicBoolean(false);
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final LowMemoryWatcher myWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      clearIdCache();
    }
  });
  private volatile int myStructureModificationCount;

  public PersistentFSImpl(@NotNull MessageBus bus) {
    myEventBus = bus;
    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        performShutdown();
      }
    });
  }

  @Override
  public void initComponent() {
    FSRecords.connect();
  }

  @Override
  public void disposeComponent() {
    performShutdown();
  }

  private void performShutdown() {
    if (myShutDown.compareAndSet(false, true)) {
      LOG.info("VFS dispose started");
      FSRecords.dispose();
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
    return FSRecords.getCreationTimestamp();
  }

  @NotNull
  private static NewVirtualFileSystem getDelegate(@NotNull VirtualFile file) {
    return (NewVirtualFileSystem)file.getFileSystem();
  }

  @Override
  public boolean wereChildrenAccessed(@NotNull final VirtualFile dir) {
    return FSRecords.wereChildrenAccessed(getFileId(dir));
  }

  @Override
  @NotNull
  public String[] list(@NotNull final VirtualFile file) {
    int id = getFileId(file);

    FSRecords.NameId[] nameIds = FSRecords.listAll(id);
    if (!areChildrenLoaded(id)) {
      nameIds  = persistAllChildren(file, id, nameIds);
    }
    return ContainerUtil.map2Array(nameIds, String.class, new Function<FSRecords.NameId, String>() {
      @Override
      public String fun(FSRecords.NameId id) {
        return id.name.toString();
      }
    });
  }

  @Override
  @NotNull
  public String[] listPersisted(@NotNull VirtualFile parent) {
    return listPersisted(FSRecords.list(getFileId(parent)));
  }

  @NotNull
  private static String[] listPersisted(@NotNull int[] childrenIds) {
    String[] names = ArrayUtil.newStringArray(childrenIds.length);
    for (int i = 0; i < childrenIds.length; i++) {
      names[i] = FSRecords.getName(childrenIds[i]);
    }
    return names;
  }

  @NotNull
  private static FSRecords.NameId[] persistAllChildren(@NotNull final VirtualFile file, final int id, @NotNull FSRecords.NameId[] current) {
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
        nameIds.add(new FSRecords.NameId(childId, FileNameCache.storeName(newName), newName));
      }
    }

    FSRecords.updateList(id, childrenIds.toNativeArray());
    setChildrenCached(id);

    return nameIds.toArray(new FSRecords.NameId[nameIds.size()]);
  }

  private static void setChildrenCached(int id) {
    int flags = FSRecords.getFlags(id);
    FSRecords.setFlags(id, flags | CHILDREN_CACHED_FLAG, true);
  }

  @Override
  @NotNull
  public FSRecords.NameId[] listAll(@NotNull VirtualFile parent) {
    final int parentId = getFileId(parent);

    FSRecords.NameId[] nameIds = FSRecords.listAll(parentId);
    if (!areChildrenLoaded(parentId)) {
      return persistAllChildren(parent, parentId, nameIds);
    }

    return nameIds;
  }

  private static boolean areChildrenLoaded(final int parentId) {
    return (FSRecords.getFlags(parentId) & CHILDREN_CACHED_FLAG) != 0;
  }

  @Override
  @Nullable
  public DataInputStream readAttribute(@NotNull final VirtualFile file, @NotNull final FileAttribute att) {
    return FSRecords.readAttributeWithLock(getFileId(file), att);
  }

  @Override
  @NotNull
  public DataOutputStream writeAttribute(@NotNull final VirtualFile file, @NotNull final FileAttribute att) {
    return FSRecords.writeAttribute(getFileId(file), att);
  }

  @Nullable
  private static DataInputStream readContent(@NotNull VirtualFile file) {
    return FSRecords.readContent(getFileId(file));
  }

  @Nullable
  private static DataInputStream readContentById(int contentId) {
    return FSRecords.readContentById(contentId);
  }

  @NotNull
  private static DataOutputStream writeContent(@NotNull VirtualFile file, boolean readOnly) {
    return FSRecords.writeContent(getFileId(file), readOnly);
  }

  private static void writeContent(@NotNull VirtualFile file, ByteSequence content, boolean readOnly) throws IOException {
    FSRecords.writeContent(getFileId(file), content, readOnly);
  }

  @Override
  public int storeUnlinkedContent(@NotNull byte[] bytes) {
    return FSRecords.storeUnlinkedContent(bytes);
  }

  @Override
  public int getModificationCount(@NotNull final VirtualFile file) {
    return FSRecords.getModCount(getFileId(file));
  }

  @Override
  public int getModificationCount() {
    return FSRecords.getLocalModCount();
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
    return FSRecords.getModCount();
  }

  private static boolean writeAttributesToRecord(final int id,
                                                 final int parentId,
                                                 @NotNull VirtualFile file,
                                                 @NotNull NewVirtualFileSystem fs,
                                                 @NotNull FileAttributes attributes) {
    String name = file.getName();
    if (!name.isEmpty()) {
      if (namesEqual(fs, name, FSRecords.getNameSequence(id))) return false; // TODO: Handle root attributes change.
    }
    else {
      if (areChildrenLoaded(id)) return false; // TODO: hack
    }

    FSRecords.writeAttributesToRecord(id, parentId, attributes, name);

    return true;
  }

  @Override
  public int getFileAttributes(int id) {
    assert id > 0;
    //noinspection MagicConstant
    return FSRecords.getFlags(id);
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
    return ((VirtualFileWithId)fileOrDirectory).getId() > 0;
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    return FSRecords.getTimestamp(getFileId(file));
  }

  @Override
  public void setTimeStamp(@NotNull final VirtualFile file, final long modStamp) throws IOException {
    final int id = getFileId(file);
    FSRecords.setTimestamp(id, modStamp);
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
    return (getFileAttributes(getFileId(file)) & IS_READ_ONLY) == 0;
  }

  @Override
  public boolean isHidden(@NotNull VirtualFile file) {
    return (getFileAttributes(getFileId(file)) & IS_HIDDEN) != 0;
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
    int parentId = getFileId(parent);
    int[] children = FSRecords.list(parentId);

    if (children.length > 0) {
      // fast path, check that some child has same nameId as given name, this avoid O(N) on retrieving names for processing non-cached children
      int nameId = FSRecords.getNameId(childName);
      for (final int childId : children) {
        if (nameId == FSRecords.getNameId(childId)) {
          return childId;
        }
      }
      // for case sensitive system the above check is exhaustive in consistent state of vfs
    }

    for (final int childId : children) {
      if (namesEqual(fs, childName, FSRecords.getNameSequence(childId))) return childId;
    }

    final VirtualFile fake = new FakeVirtualFile(parent, childName);
    final FileAttributes attributes = fs.getAttributes(fake);
    if (attributes != null) {
      final int child = createAndFillRecord(fs, fake, parentId, attributes);
      FSRecords.updateList(parentId, ArrayUtil.append(children, child));
      return child;
    }

    return 0;
  }

  @Override
  public long getLength(@NotNull final VirtualFile file) {
    long len;
    if (mustReloadContent(file)) {
      len = reloadLengthFromDelegate(file, getDelegate(file));
    }
    else {
      final int id = getFileId(file);
      len = FSRecords.getLength(id);
    }

    return len;
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
    InputStream contentStream = null;
    boolean reloadFromDelegate;
    boolean outdated;
    int fileId;
    synchronized (myInputLock) {
      fileId = getFileId(file);
      outdated = checkFlag(fileId, MUST_RELOAD_CONTENT) || FSRecords.getLength(fileId) == -1L;
      reloadFromDelegate = outdated || (contentStream = readContent(file)) == null;
    }

    if (reloadFromDelegate) {
      final NewVirtualFileSystem delegate = getDelegate(file);

      final byte[] content;
      if (outdated) {
        // in this case, file can have out-of-date length. so, update it first (it's needed for correct contentsToByteArray() work)
        // see IDEA-90813 for possible bugs
        FSRecords.setLength(fileId, delegate.getLength(file));
        content = delegate.contentsToByteArray(file);
      }
      else {
        // a bit of optimization
        content = delegate.contentsToByteArray(file);
        FSRecords.setLength(fileId, content.length);
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
        final int length = (int)file.getLength();
        assert length >= 0 : file;
        return FileUtil.loadBytes(contentStream, length);
      }
      catch (IOException e) {
        throw FSRecords.handleError(e);
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
    synchronized (myInputLock) {
      InputStream contentStream;
      if (mustReloadContent(file) || (contentStream = readContent(file)) == null) {
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

  private static long reloadLengthFromDelegate(@NotNull VirtualFile file, @NotNull NewVirtualFileSystem delegate) {
    final long len = delegate.getLength(file);
    FSRecords.setLength(getFileId(file), len);
    return len;
  }

  private InputStream createReplicator(@NotNull final VirtualFile file,
                                       final InputStream nativeStream,
                                       final long fileLength,
                                       final boolean readOnly) throws IOException {
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
                                     boolean readOnly, @NotNull byte[] bytes, int bytesLength)
    throws IOException {
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

  private static boolean mustReloadContent(@NotNull VirtualFile file) {
    int fileId = getFileId(file);
    return checkFlag(fileId, MUST_RELOAD_CONTENT) || FSRecords.getLength(fileId) == -1L;
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
    return FSRecords.acquireFileContent(getFileId(file));
  }

  @Override
  public void releaseContent(int contentId) {
    FSRecords.releaseContent(contentId);
  }

  @Override
  public int getCurrentContentId(@NotNull VirtualFile file) {
    return FSRecords.getContentId(getFileId(file));
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

  @NotNull private static final Comparator<EventWrapper> DEPTH_COMPARATOR = new Comparator<EventWrapper>() {
    @Override
    public int compare(@NotNull final EventWrapper o1, @NotNull final EventWrapper o2) {
      return o1.event.getFileDepth() - o2.event.getFileDepth();
    }
  };

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
      final Set<VirtualFile> dirsToBeDeleted = new THashSet<VirtualFile>(deletionEvents.size());
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

    final List<VFileEvent> filtered = new ArrayList<VFileEvent>(events.size() - invalidIDs.size());
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
        if (parentToChildrenEventsChanges == null) parentToChildrenEventsChanges = new THashMap<VirtualFile, List<VFileEvent>>();
        List<VFileEvent> parentChildrenChanges = parentToChildrenEventsChanges.get(changedParent);
        if (parentChildrenChanges == null) {
          parentToChildrenEventsChanges.put(changedParent, parentChildrenChanges = new SmartList<VFileEvent>());
        }
        parentChildrenChanges.add(event);
      }
      else {
        applyEvent(event);
      }
    }

    if (parentToChildrenEventsChanges != null) {
      parentToChildrenEventsChanges.forEachEntry(new TObjectObjectProcedure<VirtualFile, List<VFileEvent>>() {
        @Override
        public boolean execute(VirtualFile parent, List<VFileEvent> childrenEvents) {
          applyChildrenChangeEvents(parent, childrenEvents);
          return true;
        }
      });
      parentToChildrenEventsChanges.clear();
    }

    publisher.after(validated);
  }

  private void applyChildrenChangeEvents(@NotNull VirtualFile parent, @NotNull List<VFileEvent> events) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    TIntArrayList childrenIdsUpdated = new TIntArrayList();
    List<VirtualFile> childrenToBeUpdated = new SmartList<VirtualFile>();

    final int parentId = getFileId(parent);
    assert parentId != 0;
    TIntHashSet parentChildrenIds = new TIntHashSet(FSRecords.list(parentId));
    boolean hasRemovedChildren = false;

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

    FSRecords.updateList(parentId, parentChildrenIds.toArray());

    if (hasRemovedChildren) clearIdCache();
    VirtualDirectoryImpl parentImpl = (VirtualDirectoryImpl)parent;

    for (int i = 0, len = childrenIdsUpdated.size(); i < len; ++i) {
      final int childId = childrenIdsUpdated.get(i);
      final VirtualFile childFile = childrenToBeUpdated.get(i);

      if (childId > 0) {
        parentImpl.addChild((VirtualFileSystemEntry)childFile);
      }
      else {
        FSRecords.deleteRecordRecursively(-childId);
        parentImpl.removeChild(childFile);
        invalidateSubtree(childFile);
      }
    }
  }

  @Override
  @Nullable
  public VirtualFileSystemEntry findRoot(@NotNull String basePath, @NotNull NewVirtualFileSystem fs) {
    if (basePath.isEmpty()) {
      LOG.error("Invalid root, fs=" + fs);
      return null;
    }

    String rootUrl = normalizeRootUrl(basePath, fs);

    VirtualFileSystemEntry root = myRoots.get(rootUrl);
    if (root != null) return root;

    final VirtualFileSystemEntry newRoot;
    int rootId = FSRecords.findRootRecord(rootUrl);

    VfsData.Segment segment = VfsData.getSegment(rootId, true);
    VfsData.DirectoryData directoryData = new VfsData.DirectoryData();
    if (fs instanceof JarFileSystem) {
      String parentPath = basePath.substring(0, basePath.indexOf(JarFileSystem.JAR_SEPARATOR));
      VirtualFile parentFile = LocalFileSystem.getInstance().findFileByPath(parentPath);
      if (parentFile == null) return null;
      FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(parentFile.getName());
      if (type != FileTypes.ARCHIVE) return null;
      newRoot = new FsRoot(rootId, segment, directoryData, fs, parentFile.getName(), parentFile.getPath() + "!");
    }
    else {
      newRoot = new FsRoot(rootId, segment, directoryData, fs, basePath, StringUtil.trimEnd(basePath, "/"));
    }

    FileAttributes attributes = fs.getAttributes(new StubVirtualFile() {
      @NotNull
      @Override
      public String getPath() {
        return newRoot.getPath();
      }

      @Nullable
      @Override
      public VirtualFile getParent() {
        return null;
      }
    });
    if (attributes == null || !attributes.isDirectory()) {
      return null;
    }

    boolean mark;
    synchronized (myRoots) {
      root = myRoots.get(rootUrl);
      if (root != null) return root;

      try {
        VfsData.initFile(rootId, segment, -1, directoryData);
      }
      catch (AssertionError e) {
        for (Map.Entry<String, VirtualFileSystemEntry> entry : myRoots.entrySet()) {
          final VirtualFileSystemEntry existingRoot = entry.getValue();
          if (Math.abs(existingRoot.getId()) == rootId) {
            throw new RuntimeException("Duplicate FS roots: " + rootUrl + " and " + entry.getKey() + ", id=" + rootId + ", valid=" + existingRoot.isValid(), e);
          }
        }
        throw new RuntimeException("No root duplication", e);
      }
      incStructuralModificationCount();
      mark = writeAttributesToRecord(rootId, 0, newRoot, fs, attributes);

      myRoots.put(rootUrl, newRoot);
      myRootsById.put(rootId, newRoot);
      myIdToDirCache.put(rootId, newRoot);
    }

    if (!mark && attributes.lastModified != FSRecords.getTimestamp(rootId)) {
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

    TIntArrayList parents = FSRecords.getParents(id, myIdToDirCache);
    // the last element of the parents is either a root or already cached element
    int parentId = parents.get(parents.size() - 1);
    VirtualFileSystemEntry result = myIdToDirCache.get(parentId);

    for (int i=parents.size() - 2; i>=0; i--) {
      if (result == null) {
        break;
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
    final List<VirtualFile> roots = new ArrayList<VirtualFile>();

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

  private VirtualFileSystemEntry applyEvent(@NotNull VFileEvent event) {
    try {
      if (event instanceof VFileCreateEvent) {
        final VFileCreateEvent createEvent = (VFileCreateEvent)event;
        return executeCreateChild(createEvent.getParent(), createEvent.getChildName());
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
        return executeCreateChild(copyEvent.getNewParent(), copyEvent.getNewChildName());
      }
      else if (event instanceof VFileMoveEvent) {
        final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
        executeMove(moveEvent.getFile(), moveEvent.getNewParent());
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent propertyChangeEvent = (VFilePropertyChangeEvent)event;
        if (VirtualFile.PROP_NAME.equals(propertyChangeEvent.getPropertyName())) {
          executeRename(propertyChangeEvent.getFile(), (String)propertyChangeEvent.getNewValue());
        }
        else if (VirtualFile.PROP_WRITABLE.equals(propertyChangeEvent.getPropertyName())) {
          executeSetWritable(propertyChangeEvent.getFile(), ((Boolean)propertyChangeEvent.getNewValue()).booleanValue());
        }
        else if (VirtualFile.PROP_HIDDEN.equals(propertyChangeEvent.getPropertyName())) {
          executeSetHidden(propertyChangeEvent.getFile(), ((Boolean)propertyChangeEvent.getNewValue()).booleanValue());
        }
        else if (VirtualFile.PROP_SYMLINK_TARGET.equals(propertyChangeEvent.getPropertyName())) {
          executeSetTarget(propertyChangeEvent.getFile(), (String)propertyChangeEvent.getNewValue());
        }
      }
    }
    catch (Exception e) {
      // Exception applying single event should not prevent other events from applying.
      LOG.error(e);
    }
    return null;
  }

  @NotNull
  @NonNls
  public String toString() {
    return "PersistentFS";
  }

  private static VirtualFileSystemEntry executeCreateChild(@NotNull VirtualFile parent, @NotNull String name) {
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
      return child;
    }
    return null;
  }

  private static int createAndFillRecord(@NotNull NewVirtualFileSystem delegateSystem,
                                         @NotNull VirtualFile delegateFile,
                                         int parentId,
                                         @NotNull FileAttributes attributes) {
    final int childId = FSRecords.createRecord();
    writeAttributesToRecord(childId, parentId, delegateFile, delegateSystem, attributes);
    return childId;
  }

  private static void appendIdToParentList(final int parentId, final int childId) {
    int[] childrenList = FSRecords.list(parentId);
    childrenList = ArrayUtil.append(childrenList, childId);
    FSRecords.updateList(parentId, childrenList);
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
        FSRecords.deleteRootRecord(id);
      }
    }
    else {
      removeIdFromParentList(parentId, id, parent, file);
      VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file.getParent();
      assert directory != null : file;
      directory.removeChild(file);
    }

    FSRecords.deleteRecordRecursively(id);

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

  private static void removeIdFromParentList(final int parentId, final int id, @NotNull VirtualFile parent, VirtualFile file) {
    int[] childList = FSRecords.list(parentId);

    int index = ArrayUtil.indexOf(childList, id);
    if (index == -1) {
      throw new RuntimeException("Cannot find child (" + id + ")" + file
                                 + "\n\tin (" + parentId + ")" + parent
                                 + "\n\tactual children:" + Arrays.toString(childList));
    }
    childList = ArrayUtil.remove(childList, index);
    FSRecords.updateList(parentId, childList);
  }

  private static void executeRename(@NotNull VirtualFile file, @NotNull final String newName) {
    final int id = getFileId(file);
    FSRecords.setName(id, newName);
    ((VirtualFileSystemEntry)file).setNewName(newName);
  }

  private static void executeSetWritable(@NotNull VirtualFile file, boolean writableFlag) {
    setFlag(file, IS_READ_ONLY, !writableFlag);
    ((VirtualFileSystemEntry)file).updateProperty(VirtualFile.PROP_WRITABLE, writableFlag);
  }

  private static void executeSetHidden(@NotNull VirtualFile file, boolean hiddenFlag) {
    setFlag(file, IS_HIDDEN, hiddenFlag);
    ((VirtualFileSystemEntry)file).updateProperty(VirtualFile.PROP_HIDDEN, hiddenFlag);
  }

  private static void executeSetTarget(@NotNull VirtualFile file, String target) {
    ((VirtualFileSystemEntry)file).setLinkTarget(target);
  }

  private static void setFlag(@NotNull VirtualFile file, int mask, boolean value) {
    setFlag(getFileId(file), mask, value);
  }

  private static void setFlag(final int id, final int mask, final boolean value) {
    int oldFlags = FSRecords.getFlags(id);
    int flags = value ? oldFlags | mask : oldFlags & ~mask;

    if (oldFlags != flags) {
      FSRecords.setFlags(id, flags, true);
    }
  }

  private static boolean checkFlag(int fileId, int mask) {
    return (FSRecords.getFlags(fileId) & mask) != 0;
  }

  private static void executeTouch(@NotNull VirtualFile file, boolean reloadContentFromDelegate, long newModificationStamp) {
    if (reloadContentFromDelegate) {
      setFlag(file, MUST_RELOAD_CONTENT, true);
    }

    final NewVirtualFileSystem delegate = getDelegate(file);
    final FileAttributes attributes = delegate.getAttributes(file);
    FSRecords.setLength(getFileId(file), attributes != null ? attributes.length : DEFAULT_LENGTH);
    FSRecords.setTimestamp(getFileId(file), attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP);

    ((VirtualFileSystemEntry)file).setModificationStamp(newModificationStamp);
  }

  private void executeMove(@NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    clearIdCache();

    final int fileId = getFileId(file);
    final int newParentId = getFileId(newParent);
    final int oldParentId = getFileId(file.getParent());

    removeIdFromParentList(oldParentId, fileId, file.getParent(), file);
    FSRecords.setParent(fileId, newParentId);
    appendIdToParentList(newParentId, fileId);
    ((VirtualFileSystemEntry)file).setParent(newParent);
  }

  @Override
  public String getName(int id) {
    assert id > 0;
    return FSRecords.getName(id);
  }

  @TestOnly
  public void cleanPersistedContents() {
    final int[] roots = FSRecords.listRoots();
    for (int root : roots) {
      cleanPersistedContentsRecursively(root);
    }
  }

  @TestOnly
  private void cleanPersistedContentsRecursively(int id) {
    if (isDirectory(getFileAttributes(id))) {
      for (int child : FSRecords.list(id)) {
        cleanPersistedContentsRecursively(child);
      }
    }
    else {
      setFlag(id, MUST_RELOAD_CONTENT, true);
    }
  }


  private static class FsRoot extends VirtualDirectoryImpl {
    private final String myName;
    private final String myPathBeforeSlash;

    private FsRoot(int id, VfsData.Segment segment, VfsData.DirectoryData data, NewVirtualFileSystem fs, String name, String pathBeforeSlash) {
      super(id, segment, data, null, fs);
      myName = name;
      myPathBeforeSlash = pathBeforeSlash;
    }

    @NotNull
    @Override
    public CharSequence getNameSequence() {
      return myName;
    }

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
      return myPathBeforeSlash + "/";
    }

    @NotNull
    @Override
    public String getUrl() {
      return getFileSystem().getProtocol() + "://" + getPath();
    }
  }

}
