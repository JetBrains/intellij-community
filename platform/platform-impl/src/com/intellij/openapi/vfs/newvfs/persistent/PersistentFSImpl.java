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
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.UriUtil;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
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
public class PersistentFSImpl extends PersistentFS implements ApplicationComponent, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.PersistentFS");

  private final Map<String, VirtualFileSystemEntry> myRoots =
    ConcurrentCollectionFactory.createMap(10, 0.4f, JobSchedulerImpl.CORES_COUNT, FileUtil.PATH_HASHING_STRATEGY);
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myRootsById = ContainerUtil.createConcurrentIntObjectMap(10, 0.4f, JobSchedulerImpl.CORES_COUNT);

  // FS roots must be in this map too. findFileById() relies on this.
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToDirCache = ContainerUtil.createConcurrentIntObjectMap();
  private final Object myInputLock = new Object();

  private final AtomicBoolean myShutDown = new AtomicBoolean(false);
  private volatile int myStructureModificationCount;
  private final BulkFileListener myPublisher;

  public PersistentFSImpl(@NotNull MessageBus bus) {
    ShutDownTracker.getInstance().registerShutdownTask(this::performShutdown);
    LowMemoryWatcher.register(this::clearIdCache, this);
    myPublisher = bus.syncPublisher(VirtualFileManager.VFS_CHANGES);
  }

  @Override
  public void initComponent() {
    FSRecords.connect();
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
    return ContainerUtil.map2Array(nameIds, String.class, id1 -> id1.name.toString());
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
    return BitUtil.isSet(FSRecords.getFlags(parentId), CHILDREN_CACHED_FLAG);
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

  private static void writeContent(@NotNull VirtualFile file, ByteSequence content, boolean readOnly) {
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
    assert id > 0 : id;
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
  public long getLength(@NotNull VirtualFile file) {
    return mustReloadContent(file) ?
           reloadLengthFromDelegate(file, getDelegate(file)) :
           getLastRecordedLength(file);
  }

  @Override
  public long getLastRecordedLength(@NotNull VirtualFile file) {
    int id = getFileId(file);
    return FSRecords.getLength(id);
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
    if (child.getCharset().equals(CharsetToolkit.UTF8_CHARSET)) {
      Project project = ProjectLocator.getInstance().guessProjectForFile(child);
      EncodingManager encodingManager = project == null ? EncodingManager.getInstance() : EncodingProjectManager.getInstance(project);
      if (encodingManager.shouldAddBOMForNewUtf8File()) {
        child.setBOM(CharsetToolkit.UTF8_BOM);
      }
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
    long length = -1L;

    synchronized (myInputLock) {
      fileId = getFileId(file);
      outdated = checkFlag(fileId, MUST_RELOAD_CONTENT) || (length = FSRecords.getLength(fileId)) == -1L;
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
    try {
      assert length >= 0 : file;
      return FileUtil.loadBytes(contentStream, (int)length);
    }
    catch (IOException e) {
      FSRecords.handleError(e);
      return ArrayUtil.EMPTY_BYTE_ARRAY;
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

  private static long reloadLengthFromDelegate(@NotNull VirtualFile file, @NotNull NewVirtualFileSystem delegate) {
    final long len = delegate.getLength(file);
    FSRecords.setLength(getFileId(file), len);
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
        myPublisher.before(events);

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
            myPublisher.after(events);
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
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    // optimisation: skip all groupings
    if (event.isValid()) {
      List<VFileEvent> events = Collections.singletonList(event);
      myPublisher.before(events);

      applyEvent(event);

      myPublisher.after(events);
    }
  }

  // Tries to find a group of non-conflicting events in range [startIndex..inEvents.size()).
  // Two events are conflicting if the originating file of one event is an ancestor (non-strict) of the file from the other.
  // E.g. "change(a/b/c/x.txt)" and "delete(a/b/c)" are conflicting because "a/b/c/x.txt" is under the "a/b/c" directory from the other event.
  //
  // returns index after the last grouped event.
  private static int groupByPath(@NotNull List<VFileEvent> inEvents, int startIndex, Set<String> files, Set<String> middleDirs) {
    // store all paths from all events (including all parents)
    // check the each new event's path against this set and if it's there, this event is conflicting

    int i;
    for (i = startIndex; i < inEvents.size(); i++) {
      VFileEvent event = inEvents.get(i);
      String path = event.getPath();
      if (checkIfConflictingEvent(path, files, middleDirs)) {
        break;
      }
      // some synthetic events really are composite events, e.g. VFileMoveEvent = VFileDeleteEvent+VFileCreateEvent, so both paths should be checked for conflicts
      String path2 = null;
      if (event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).getPropertyName().equals(VirtualFile.PROP_NAME)) {
        VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
        VirtualFile parent = pce.getFile().getParent();
        String newName = (String)pce.getNewValue();
        path2 = parent == null ? newName : parent.getPath()+"/"+newName;
      }
      else if (event instanceof VFileCopyEvent) {
        path2 = ((VFileCopyEvent)event).getFile().getPath();
      }
      else if (event instanceof VFileMoveEvent) {
        VFileMoveEvent vme = (VFileMoveEvent)event;
        String newName = vme.getFile().getName();
        path2 = vme.getNewParent().getPath() + "/" + newName;
      }
      if (path2 != null && !path2.equals(path) && checkIfConflictingEvent(path2, files, middleDirs)) {
        break;
      }
    }

    return i;
  }

  private static boolean checkIfConflictingEvent(String path, Set<String> files, Set<String> middleDirs) {
    if (!files.add(path) || middleDirs.contains(path)) {
      // conflicting event found for (non-strict) descendant, stop
      return true;
    }
    int li = path.length();
    while (true) {
      int liPrev = path.lastIndexOf('/', li-1);
      if (liPrev == -1) break;
      String parentDir = path.substring(0, liPrev);
      if (files.contains(parentDir)) {
        // conflicting event found for ancestor, stop
        return true;
      }
      if (!middleDirs.add(parentDir)) break;  // all parents up already stored, stop
      li = liPrev;
    }

    return false;
  }

  // finds a group of non-conflicting events, validate them.
  // "outApplyEvents" will contain handlers for applying the grouped events
  // "outValidatedEvents" will contain events for which VFileEvent.isValid() is true
  // return index after the last processed event
  private int groupAndValidate(@NotNull List<VFileEvent> events,
                               int startIndex,
                               @NotNull List<Runnable> outApplyEvents,
                               @NotNull List<VFileEvent> outValidatedEvents,
                               @NotNull Set<String> files, @NotNull Set<String> middleDirs) {
    int endIndex = groupByPath(events, startIndex, files, middleDirs);
    // since all events in the group are mutually non-conflicting, we can re-arrange creations/deletions together
    groupCreations(events, startIndex, endIndex, outValidatedEvents, outApplyEvents);
    groupDeletions(events, startIndex, endIndex, outValidatedEvents, outApplyEvents);
    groupOthers(events, startIndex, endIndex, outValidatedEvents, outApplyEvents);

    return endIndex;
  }

  // find all VFileCreateEvent events in [start..end)
  // group them by parent directory, validate in bulk for each directory, and return "applyCreations()" runnable
  private void groupCreations(@NotNull List<VFileEvent> events,
                              int start,
                              int end,
                              @NotNull List<VFileEvent> outValidated,
                              @NotNull List<Runnable> outApplyEvents) {
    MultiMap<VirtualDirectoryImpl, VFileCreateEvent> grouped = new MultiMap<VirtualDirectoryImpl, VFileCreateEvent>(){
      @NotNull
      @Override
      protected Map<VirtualDirectoryImpl, Collection<VFileCreateEvent>> createMap() {
        return new THashMap<>(end-start);
      }
    };

    for (int i = start; i < end; i++) {
      VFileEvent e = events.get(i);
      if (!(e instanceof VFileCreateEvent)) continue;
      VFileCreateEvent event = (VFileCreateEvent)e;
      VirtualDirectoryImpl parent = (VirtualDirectoryImpl)event.getParent();
      grouped.putValue(parent, event);
    }

    // since the VCreateEvent.isValid() is extremely expensive, combine all creation events for the directory together
    // and use VirtualDirectoryImpl.validateChildrenToCreate() optimised for bulk validation
    boolean hasValidEvents = false;
    for (Map.Entry<VirtualDirectoryImpl, Collection<VFileCreateEvent>> entry : grouped.entrySet()) {
      VirtualDirectoryImpl directory = entry.getKey();
      List<VFileCreateEvent> createEvents = (List<VFileCreateEvent>)entry.getValue();
      directory.validateChildrenToCreate(createEvents);
      hasValidEvents |= !createEvents.isEmpty();
      outValidated.addAll(createEvents);
    }

    if (hasValidEvents) {
      outApplyEvents.add(()->{
        applyCreations(grouped);
        incStructuralModificationCount();
      });
    }
  }

  // find all VFileDeleteEvent events in [start..end)
  // group them by parent directory (can be null), filter out files which parent dir is to be deleted too, and return "applyDeletions()" runnable
  private void groupDeletions(@NotNull List<VFileEvent> events,
                              int start,
                              int end,
                              @NotNull List<VFileEvent> outValidated,
                              @NotNull List<Runnable> outApplyEvents) {
    MultiMap<VirtualDirectoryImpl, VFileDeleteEvent> grouped = new MultiMap<VirtualDirectoryImpl, VFileDeleteEvent>(){
      @NotNull
      @Override
      protected Map<VirtualDirectoryImpl, Collection<VFileDeleteEvent>> createMap() {
        return new HashMap<>(end-start); // can be null keys
      }
    };
    boolean hasValidEvents = false;
    for (int i = start; i < end; i++) {
      VFileEvent event = events.get(i);
      if (!(event instanceof VFileDeleteEvent) || !event.isValid()) continue;
      VFileDeleteEvent de = (VFileDeleteEvent)event;
      @Nullable VirtualDirectoryImpl parent = (VirtualDirectoryImpl)de.getFile().getParent();
      grouped.putValue(parent, de);
      outValidated.add(event);
      hasValidEvents = true;
    }

    if (hasValidEvents) {
      outApplyEvents.add(() -> {
        clearIdCache();
        applyDeletions(grouped);
        incStructuralModificationCount();
      });
    }
  }

  // find events other than VFileCreateEvent or VFileDeleteEvent in [start..end)
  // validate and return "applyEvent()" runnable for each event because it's assumed there won't be too many of them
  private void groupOthers(@NotNull List<VFileEvent> events,
                           int start,
                           int end,
                           @NotNull List<VFileEvent> outValidated,
                           @NotNull List<Runnable> outApplyEvents) {
    for (int i = start; i < end; i++) {
      VFileEvent event = events.get(i);
      if (event instanceof VFileCreateEvent || event instanceof VFileDeleteEvent || !event.isValid()) continue;
      outValidated.add(event);
      outApplyEvents.add(() -> applyEvent(event));
    }
  }

  @Override
  public void processEvents(@NotNull List<VFileEvent> events) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    int startIndex = 0;
    List<Runnable> applyEvents = new ArrayList<>(events.size());
    List<VFileEvent> validated = new ArrayList<>(events.size());
    Set<String> files = new THashSet<>(events.size());
    Set<String> middleDirs = new THashSet<>(events.size());
    while (startIndex != events.size()) {
      applyEvents.clear();
      validated.clear();
      files.clear();
      middleDirs.clear();
      startIndex = groupAndValidate(events, startIndex, applyEvents, validated, files, middleDirs);

      if (!validated.isEmpty()) {
        List<VFileEvent> toSend = Collections.unmodifiableList(validated);
        myPublisher.before(toSend);

        applyEvents.forEach(Runnable::run);

        myPublisher.after(toSend);
      }
    }
  }

  // remove children from specified directories using VirtualDirectoryImpl.removeChildren() optimised for bulk removals
  private void applyDeletions(@NotNull MultiMap<VirtualDirectoryImpl, VFileDeleteEvent> deletions) {
    for (Map.Entry<VirtualDirectoryImpl, Collection<VFileDeleteEvent>> entry : deletions.entrySet()) {
      VirtualDirectoryImpl parent = entry.getKey();
      Collection<VFileDeleteEvent> deleteEvents = entry.getValue();
      // no valid containing directory, apply events the old way - one by one
      if (parent == null || !parent.isValid()) {
        deleteEvents.forEach(this::applyEvent);
        return;
      }

      int parentId = getFileId(parent);
      int[] oldIds = FSRecords.list(parentId);
      TIntHashSet parentChildrenIds = new TIntHashSet(Math.max(deleteEvents.size(), oldIds.length));
      parentChildrenIds.addAll(oldIds);

      List<CharSequence> childrenNamesDeleted = new ArrayList<>(deleteEvents.size());
      TIntHashSet childrenIdsDeleted = new TIntHashSet(deleteEvents.size());

      for (VFileDeleteEvent event : deleteEvents) {
        VirtualFile file = event.getFile();
        int id = getFileId(file);
        childrenNamesDeleted.add(file.getNameSequence());
        childrenIdsDeleted.add(id);
        FSRecords.deleteRecordRecursively(id);
        invalidateSubtree(file);
        parentChildrenIds.remove(id);
      }
      parent.removeChildren(childrenIdsDeleted, childrenNamesDeleted);
      FSRecords.updateList(parentId, parentChildrenIds.toArray());
    }
  }

  // add children to specified directories using VirtualDirectoryImpl.createAndAddChildren() optimised for bulk additions
  private void applyCreations(@NotNull MultiMap<VirtualDirectoryImpl, VFileCreateEvent> creations) {
    for (Map.Entry<VirtualDirectoryImpl, Collection<VFileCreateEvent>> entry : creations.entrySet()) {
      VirtualDirectoryImpl parent = entry.getKey();
      Collection<VFileCreateEvent> createEvents = entry.getValue();
      int parentId = getFileId(parent);
      int[] oldIds = FSRecords.list(parentId);
      TIntHashSet parentChildrenIds = new TIntHashSet(Math.max(createEvents.size(), oldIds.length));
      parentChildrenIds.addAll(oldIds);

      List<FSRecords.NameId> childrenAdded = new ArrayList<>(createEvents.size());
      final NewVirtualFileSystem delegate = replaceWithNativeFS(getDelegate(parent));
      delegate.list(parent); // cache children getAttributes
      for (VFileCreateEvent createEvent : createEvents) {
        createEvent.resetCache();
        String name = createEvent.getChildName();
        final VirtualFile fake = new FakeVirtualFile(parent, name);
        final FileAttributes attributes = delegate.getAttributes(fake);

        if (attributes != null) {
          final int childId = createAndFillRecord(delegate, fake, parentId, attributes);
          childrenAdded.add(new FSRecords.NameId(childId, -1, name));
          parentChildrenIds.add(childId);
        }
      }
      parent.createAndAddChildren(childrenAdded);
      if (ApplicationManager.getApplication().isUnitTestMode() && !ApplicationInfoImpl.isInStressTest()) {
        long count = Arrays.stream(parentChildrenIds.toArray()).mapToObj(this::findFileById).map(VirtualFile::getName).distinct().count();
        assert count == parentChildrenIds.size();
      }
      FSRecords.updateList(parentId, parentChildrenIds.toArray());
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

    int rootId = FSRecords.findRootRecord(rootUrl);

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
        throw new RuntimeException("No root duplication, roots=" + Arrays.toString(FSRecords.listAll(1)), e);
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
      incStructuralModificationCount();
    }
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
      LOG.error("Deleting a file which does not exist: " +((VirtualFileWithId)file).getId()+ " "+file.getPath());
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
    return BitUtil.isSet(FSRecords.getFlags(fileId), mask);
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
    int[] roots = FSRecords.listRoots();
    for (int root : roots) {
      markForContentReloadRecursively(root);
    }
  }

  private void markForContentReloadRecursively(int id) {
    if (isDirectory(getFileAttributes(id))) {
      for (int child : FSRecords.list(id)) {
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