/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DupOutputStream;
import com.intellij.util.io.ReplicatorInputStream;
import com.intellij.util.messages.MessageBus;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

public class PersistentFS extends ManagingFS implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.PersistentFS");

  private static final int CHILDREN_CACHED_FLAG = 0x01;
  static final int IS_DIRECTORY_FLAG = 0x02;
  private static final int IS_READ_ONLY = 0x04;
  private static final int MUST_RELOAD_CONTENT = 0x08;

  static final int ALL_VALID_FLAGS = CHILDREN_CACHED_FLAG | IS_DIRECTORY_FLAG | IS_READ_ONLY | MUST_RELOAD_CONTENT;

  public static final long FILE_LENGTH_TO_CACHE_THRESHOLD = 20 * 1024 * 1024; // 20 megabytes

  private final FSRecords myRecords;
  private final MessageBus myEventsBus;

  private final Map<String, NewVirtualFile> myRoots = new HashMap<String, NewVirtualFile>();
  private final Object INPUT_LOCK = new Object();
  /**
   * always  in range [0, PersistentFS.FILE_LENGTH_TO_CACHE_THRESHOLD]
   */
  public static final int MAX_INTELLISENSE_FILESIZE = maxIntellisenseFileSize();
  @NonNls private static final String MAX_INTELLISENSE_SIZE_PROPERTY = "idea.max.intellisense.filesize";

  public PersistentFS(MessageBus bus) {
    myEventsBus = bus;
    myRecords = new FSRecords();

    /*
    PropertiesComponent properties = PropertiesComponent.getInstance();
    if (properties != null && !properties.isTrueValue("AntivirusActivityReporter.disabled")) {
      AntivirusDetector.getInstance().enable(new Runnable() {
        public void run() {
          LOG.info("Antivirus activity detected. Please make sure IDEA system and caches directory as well as project foler are exluded from antivirus on-the-fly check list.");
        }
      });
    }
    */
    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        LOG.info("VFS dispose started");
        myRecords.dispose();
        LOG.info("VFS dispose completed");
      }
    });
  }

  public void disposeComponent() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "app.component.PersistentFS";
  }

  public void initComponent() {
    myRecords.connect();
  }

  public boolean areChildrenLoaded(final VirtualFile dir) {
    return areChildrenLoaded(getFileId(dir));
  }

  public long getCreationTimestamp() {
    return myRecords.getCreationTimestamp();
  }

  private static NewVirtualFileSystem getDelegate(VirtualFile file) {
    return (NewVirtualFileSystem)file.getFileSystem();
  }

  public boolean wereChildrenAccessed(final VirtualFile dir) {
    return myRecords.wereChildrenAccessed(getFileId(dir));
  }

  public String[] list(final VirtualFile file) {
    int id = getFileId(file);
    if (areChildrenLoaded(id)) {
      return listPersisted(file);
    }
    else {
      return persistAllChildren(file);
    }
  }

  public String[] listPersisted(final VirtualFile file) {
    return listPersisted(getFileId(file));
  }

  private String[] listPersisted(final int id) {
    final int[] childrenIds = myRecords.list(id);
    String[] names = ArrayUtil.newStringArray(childrenIds.length);
    for (int i = 0; i < childrenIds.length; i++) {
      names[i] = FSRecords.getName(childrenIds[i]);
    }
    return names;
  }

  private String[] persistAllChildren(final VirtualFile file) {
    int id = getFileId(file);
    String[] currentNames = listPersisted(file);
    int[] currentIds = myRecords.list(id);

    final NewVirtualFileSystem delegate = getDelegate(file);
    String[] delegateNames = VfsUtil.filterNames(delegate.list(file));
    if (delegateNames.length == 0 && currentNames.length > 0) return currentNames;

    final String[] names;
    if (currentNames.length == 0) {
      names = delegateNames;
    }
    else {
      Set<String> allNamesSet = new LinkedHashSet<String>((currentNames.length + delegateNames.length) * 2);
      ContainerUtil.addAll(allNamesSet, currentNames);
      ContainerUtil.addAll(allNamesSet, delegateNames);
      names = ArrayUtil.toStringArray(allNamesSet);
    }

    final int[] childrenIds = ArrayUtil.newIntArray(names.length);

    for (int i = 0; i < names.length; i++) {
      final String name = names[i];
      int idx = ArrayUtil.indexOf(currentNames, name);
      if (idx >= 0) {
        childrenIds[i] = currentIds[idx];
      }
      else {
        int childId = FSRecords.createRecord();
        copyRecordFromDelegateFS(childId, id, new FakeVirtualFile(file, name), delegate);
        childrenIds[i] = childId;
      }
    }

    myRecords.updateList(id, childrenIds);
    int flags = FSRecords.getFlags(id);
    myRecords.setFlags(id, flags | CHILDREN_CACHED_FLAG, true);

    return names;
  }

  public int[] listIds(VirtualFile parent) {
    final int parentId = getFileId(parent);

    if (!areChildrenLoaded(parentId)) {
      list(parent);
    }

    return myRecords.list(parentId);
  }


  private static boolean areChildrenLoaded(final int parentId) {
    final int mask = CHILDREN_CACHED_FLAG;
    return (FSRecords.getFlags(parentId) & mask) != 0;
  }

  @Nullable
  public DataInputStream readAttribute(final VirtualFile file, final FileAttribute att) {
    return myRecords.readAttribute(getFileId(file), att.getId());
  }

  public DataOutputStream writeAttribute(final VirtualFile file, final FileAttribute att) {
    return myRecords.writeAttribute(getFileId(file), att.getId());
  }

  @Nullable
  private DataInputStream readContent(VirtualFile file) {
    return myRecords.readContent(getFileId(file));
  }

  @Nullable
  private DataInputStream readContentById(int contentId) {
    return myRecords.readContentById(contentId);
  }

  private DataOutputStream writeContent(VirtualFile file) {
    return myRecords.writeContent(getFileId(file));
  }

  public int storeUnlinkedContent(byte[] bytes) {
    return myRecords.storeUnlinkedContent(bytes);
  }

  public int getModificationCount(final VirtualFile file) {
    final int id = getFileId(file);
    return FSRecords.getModCount(id);
  }

  @Override
  public int getCheapFileSystemModificationCount() {
    return FSRecords.getLocalModCount();
  }

  public int getFilesystemModificationCount() {
    return FSRecords.getModCount();
  }

  private void copyRecordFromDelegateFS(final int id, final int parentId, final VirtualFile file, NewVirtualFileSystem delegate) {
    if (id == parentId) {
      LOG.error("Cyclic parent-child relations for file: " + file);
      return;
    }

    String name = file.getName();

    if (name.length() > 0 && namesEqual(delegate, name, FSRecords.getName(id))) return; // TODO: Handle root attributes change.

    if (name.length() == 0) {            // TODO: hack
      if (areChildrenLoaded(id)) return;
    }

    myRecords.setParent(id, parentId);
    myRecords.setName(id, name);

    myRecords.setTimestamp(id, delegate.getTimeStamp(file));
    myRecords.setFlags(id, (delegate.isDirectory(file) ? IS_DIRECTORY_FLAG : 0) | (delegate.isWritable(file) ? 0 : IS_READ_ONLY), true);

    myRecords.setLength(id, -1L);

    // TODO!!!: More attributes?
  }

  public boolean isDirectory(final VirtualFile file) {
    final int id = getFileId(file);
    return isDirectory(id);
  }

  public boolean isDirectory(final int id) {
    assert id > 0;
    return (FSRecords.getFlags(id) & IS_DIRECTORY_FLAG) != 0;
  }

  public int getParent(final int id) {
    assert id > 0;
    return FSRecords.getParent(id);
  }

  private static boolean namesEqual(VirtualFileSystem fs, String n1, String n2) {
    return ((NewVirtualFileSystem)fs).isCaseSensitive() ? n1.equals(n2) : n1.equalsIgnoreCase(n2);
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return ((VirtualFileWithId)fileOrDirectory).getId() > 0;
  }

  public long getTimeStamp(final VirtualFile file) {
    final int id = getFileId(file);
    return FSRecords.getTimestamp(id);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    final int id = getFileId(file);

    myRecords.setTimestamp(id, modstamp);
    getDelegate(file).setTimeStamp(file, modstamp);
  }

  private static int getFileId(final VirtualFile file) {
    final int id = ((VirtualFileWithId)file).getId();
    if (id <= 0) {
      throw new InvalidVirtualFileAccessException(file);
    }
    return id;
  }

  public boolean isWritable(final VirtualFile file) {
    final int id = getFileId(file);

    return (FSRecords.getFlags(id) & IS_READ_ONLY) == 0;
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    getDelegate(file).setWritable(file, writableFlag);
    processEvent(new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, isWritable(file), writableFlag, false));
  }

  public int getId(final VirtualFile parent, final String childName) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    final int parentId = getFileId(parent);

    final int[] children = myRecords.list(parentId);
    for (final int childId : children) {
      if (namesEqual(delegate, childName, FSRecords.getName(childId))) return childId;
    }

    VirtualFile fake = new FakeVirtualFile(parent, childName);
    if (delegate.exists(fake)) {
      int child = FSRecords.createRecord();
      copyRecordFromDelegateFS(child, parentId, fake, delegate);
      myRecords.updateList(parentId, ArrayUtil.append(children, child));
      return child;
    }

    return 0;
  }

  public long getLength(final VirtualFile file) {
    final int id = getFileId(file);

    long len = FSRecords.getLength(id);
    if (len == -1) {
      len = (int)getDelegate(file).getLength(file);
      myRecords.setLength(id, len);
    }

    return len;
  }

  public VirtualFile copyFile(final Object requestor, final VirtualFile file, final VirtualFile newParent, final String copyName)
    throws IOException {
    getDelegate(file).copyFile(requestor, file, newParent, copyName);
    processEvent(new VFileCopyEvent(requestor, file, newParent, copyName));

    final VirtualFile child = newParent.findChild(copyName);
    if (child == null) {
      throw new IOException("Cannot create child");
    }
    return child;
  }

  public VirtualFile createChildDirectory(final Object requestor, final VirtualFile parent, final String dir) throws IOException {
    getDelegate(parent).createChildDirectory(requestor, parent, dir);
    processEvent(new VFileCreateEvent(requestor, parent, dir, true, false));

    final VirtualFile child = parent.findChild(dir);
    if (child == null) {
      throw new IOException("Cannot create child directory '" + dir + "' at " + parent.getPath());
    }
    return child;
  }

  public VirtualFile createChildFile(final Object requestor, final VirtualFile parent, final String file) throws IOException {
    getDelegate(parent).createChildFile(requestor, parent, file);
    processEvent(new VFileCreateEvent(requestor, parent, file, false, false));

    final VirtualFile child = parent.findChild(file);
    if (child == null) {
      throw new IOException("Cannot create child file '" + file + "' at " + parent.getPath());
    }
    return child;
  }

  public void deleteFile(final Object requestor, final VirtualFile file) throws IOException {
    final NewVirtualFileSystem delegate = getDelegate(file);
    delegate.deleteFile(requestor, file);

    if (!delegate.exists(file)) {
      processEvent(new VFileDeleteEvent(requestor, file, false));
    }
  }

  public void renameFile(final Object requestor, final VirtualFile file, final String newName) throws IOException {
    getDelegate(file).renameFile(requestor, file, newName);
    processEvent(new VFilePropertyChangeEvent(requestor, file, VirtualFile.PROP_NAME, file.getName(), newName, false));
  }

  @NotNull
  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    InputStream contentStream = null;
    boolean reloadFromDelegate;
    synchronized (INPUT_LOCK) {
      reloadFromDelegate = mustReloadContent(file) || (contentStream = readContent(file)) == null;
    }

    if (reloadFromDelegate) {
      final NewVirtualFileSystem delegate = getDelegate(file);
      final byte[] content = delegate.contentsToByteArray(file);

      if (content.length <= FILE_LENGTH_TO_CACHE_THRESHOLD) {
        synchronized (INPUT_LOCK) {
          DataOutputStream sink = writeContent(file);
          try {
            FileUtil.copy(new ByteArrayInputStream(content), sink);
          }
          finally {
            sink.close();
          }

          myRecords.setLength(getFileId(file), content.length);
          setFlag(file, MUST_RELOAD_CONTENT, false);
        }
      }

      return content;
    }
    else {
      try {
        return FileUtil.loadBytes(contentStream, (int)file.getLength());
      }
      catch (IOException e) {
        throw FSRecords.handleError(e);
      }
    }
  }

  public byte[] contentsToByteArray(int contentId) throws IOException {
    return FileUtil.loadBytes(readContentById(contentId));
  }

  @NotNull
  public InputStream getInputStream(final VirtualFile file) throws IOException {
    synchronized (INPUT_LOCK) {
      InputStream contentStream;
      if (mustReloadContent(file) || (contentStream = readContent(file)) == null) {
        final NewVirtualFileSystem delegate = getDelegate(file);
        final long len = delegate.getLength(file);
        final InputStream nativeStream = delegate.getInputStream(file);

        if (len > FILE_LENGTH_TO_CACHE_THRESHOLD) return nativeStream;

        return createReplicator(file, nativeStream, len);
      }
      else {
        return contentStream;
      }
    }
  }

  private ReplicatorInputStream createReplicator(final VirtualFile file, final InputStream nativeStream, final long len) {
    final ByteArrayOutputStream cache = new ByteArrayOutputStream((int)len);
    return new ReplicatorInputStream(nativeStream, cache) {
      public void close() throws IOException {
        super.close();

        synchronized (INPUT_LOCK) {
          if (getBytesRead() == len) {
            DataOutputStream sink = writeContent(file);
            try {
              FileUtil.copy(new ByteArrayInputStream(cache.toByteArray()), sink);
            }
            finally {
              sink.close();
            }

            myRecords.setLength(getFileId(file), len);
            setFlag(file, MUST_RELOAD_CONTENT, false);
          }
          else {
            setFlag(file, MUST_RELOAD_CONTENT, true);
          }
        }
      }
    };
  }

  private static boolean mustReloadContent(final VirtualFile file) {
    return checkFlag(file, MUST_RELOAD_CONTENT) || FSRecords.getLength(getFileId(file)) == -1L;
  }

  @NotNull
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp)
    throws IOException {
    final VFileContentChangeEvent event = new VFileContentChangeEvent(requestor, file, file.getModificationStamp(), modStamp, false);

    final List<VFileContentChangeEvent> events = Collections.singletonList(event);

    final BulkFileListener publisher = myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES);
    publisher.before(events);

    final ByteArrayOutputStream stream = new ByteArrayOutputStream() {
      public void close() throws IOException {
        super.close();

        final OutputStream delegate = getDelegate(file).getOutputStream(file, requestor, modStamp, timeStamp);

        //noinspection IOResourceOpenedButNotSafelyClosed
        final DupOutputStream sink = new DupOutputStream(new BufferedOutputStream(writeContent(file)), delegate) {
          public void close() throws IOException {
            try {
              super.close();
            }
            finally {
              executeTouch(file, false, event.getModificationStamp());
              publisher.after(events);
            }
          }
        };

        try {
          sink.write(buf, 0, count);
        }
        finally {
          sink.close();
        }
      }
    };

    final byte[] bom = file.getBOM();
    if (bom != null) {
      stream.write(bom);
    }

    return stream;
  }

  public void restoreContent(VirtualFile file, Object requestor, final long modStamp, final long timeStamp) {
    
  }

  public int acquireContent(VirtualFile file) {
    return myRecords.acquireFileContent(getFileId(file));
  }

  public void releaseContent(int contentId) {
    myRecords.releaseContent(contentId);
  }

  public int getCurrentContentId(VirtualFile file) {
    return myRecords.getContentId(getFileId(file));
  }

  public void moveFile(final Object requestor, final VirtualFile file, final VirtualFile newParent) throws IOException {
    getDelegate(file).moveFile(requestor, file, newParent);
    processEvent(new VFileMoveEvent(requestor, file, newParent));
  }

  private void processEvent(VFileEvent event) {
    processEvents(Collections.singletonList(event));
  }

  private static final Comparator<VFileDeleteEvent> DEPTH_COMPARATOR = new Comparator<VFileDeleteEvent>() {
    public int compare(final VFileDeleteEvent o1, final VFileDeleteEvent o2) {
      return o1.getFileDepth() - o2.getFileDepth();
    }
  };

  private static List<? extends VFileEvent> validateEvents(List<? extends VFileEvent> events) {
    List<VFileEvent> filtered = new ArrayList<VFileEvent>(events.size());
    List<VFileDeleteEvent> deletionList = new ArrayList<VFileDeleteEvent>();

    for (VFileEvent event : events) {
      if (event.isValid()) {
        if (event instanceof VFileDeleteEvent) {
          deletionList.add((VFileDeleteEvent)event);
        }
        else {
          filtered.add(event);
        }
      }
    }

    ContainerUtil.quickSort(deletionList, DEPTH_COMPARATOR);
    List<VirtualFile> filesToBeDeleted = new ArrayList<VirtualFile>();
    for (VFileDeleteEvent event : deletionList) {
      boolean ok = true;
      VirtualFile candidate = event.getFile();
      for (VirtualFile file : filesToBeDeleted) {
        if (VfsUtil.isAncestor(file, candidate, false)) {
          ok = false;
          break;
        }
      }

      if (ok) {
        filtered.add(event);
        if (candidate.isDirectory()) {
          filesToBeDeleted.add(candidate);
        }
      }
    }

    return filtered;
  }

  public void processEvents(List<? extends VFileEvent> events) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    events = validateEvents(events);

    myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(events);
    for (VFileEvent event : events) {
      applyEvent(event);
    }
    myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(events);
  }

  public static final Object LOCK = new Object();

  @Nullable
  public NewVirtualFile findRoot(final String basePath, final NewVirtualFileSystem fs) { // TODO: read/write locks instead of sycnrhonized
    synchronized (LOCK) {
      final String rootUrl = fs.getProtocol() + "://" + basePath;
      NewVirtualFile root = myRoots.get(rootUrl);
      if (root == null) {
        try {
          final int rootId = myRecords.findRootRecord(rootUrl);
          if (basePath.length() > 0) {
            root = new VirtualDirectoryImpl(basePath, null, fs, rootId);
          }
          else {
            // fake root for windows
            root = new VirtualDirectoryImpl(basePath, null, fs, rootId) {
              @NotNull
              public VirtualFile[] getChildren() {
                return getRoots(fs);
              }
            };
          }
          if (!fs.exists(root)) return null;

          copyRecordFromDelegateFS(rootId, 0, root, fs);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        if (basePath.length() > 0) {
          myRoots.put(rootUrl, root);
        }
      }

      return root;
    }
  }

  public void refresh(final boolean asynchronous) {
    final NewVirtualFile[] roots;
    synchronized (LOCK) {
      Collection<NewVirtualFile> values = myRoots.values();
      roots = values.toArray(new NewVirtualFile[values.size()]);
    }

    RefreshQueue.getInstance().refresh(asynchronous, true, null, roots);
  }

  public VirtualFile[] getLocalRoots() {
    List<NewVirtualFile> roots;
    synchronized (LOCK) {
      roots = new ArrayList<NewVirtualFile>(myRoots.values());

      final Iterator<NewVirtualFile> it = roots.iterator();
      while (it.hasNext()) {
        NewVirtualFile file = it.next();
        if (!file.isInLocalFileSystem()) {
          it.remove();
        }
      }
    }

    return VfsUtil.toVirtualFileArray(roots);
  }

  //guarded by dirCacheReadLock/dirCacheWriteLock
  private final TIntObjectHashMap<NewVirtualFile> myIdToDirCache = new TIntObjectHashMap<NewVirtualFile>();
  private final JBLock dirCacheReadLock;
  private final JBLock dirCacheWriteLock;

  {
    JBReentrantReadWriteLock lock = LockFactory.createReadWriteLock();
    dirCacheReadLock = lock.readLock();
    dirCacheWriteLock = lock.writeLock();
  }


  public void clearIdCache() {
    try {
      dirCacheWriteLock.lock();
      myIdToDirCache.clear();
    }
    finally {
      dirCacheWriteLock.unlock();
    }
  }

  @Nullable
  public NewVirtualFile findFileById(final int id) {
    try {
      dirCacheReadLock.lock();
      final NewVirtualFile cached = myIdToDirCache.get(id);
      if (cached != null) {
        return cached;
      }
    }
    finally {
      dirCacheReadLock.unlock();
    }

    final NewVirtualFile result = doFindFile(id);

    if (result != null && result.isDirectory()) {
      try {
        dirCacheWriteLock.lock();
        myIdToDirCache.put(id, result);
      }
      finally {
        dirCacheWriteLock.unlock();
      }
    }
    return result;
  }

  @Nullable
  private NewVirtualFile doFindFile(final int id) {
    final int parentId = getParent(id);
    if (parentId == 0) {
      synchronized (LOCK) {
        for (NewVirtualFile root : myRoots.values()) {
          if (root.getId() == id) return root;
        }
        return null;
      }
    }
    else {
      NewVirtualFile parentFile = findFileById(parentId);
      return parentFile != null ? parentFile.findChildById(id) : null;
    }
  }

  public VirtualFile[] getRoots() {
    synchronized (LOCK) {
      final Collection<NewVirtualFile> roots = myRoots.values();
      return VfsUtil.toVirtualFileArray(roots);
    }
  }

  public VirtualFile[] getRoots(final NewVirtualFileSystem fs) {
    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    synchronized (LOCK) {
      for (NewVirtualFile root : myRoots.values()) {
        if (root.getFileSystem() == fs) {
          roots.add(root);
        }
      }
    }

    return VfsUtil.toVirtualFileArray(roots);
  }

  private void applyEvent(final VFileEvent event) {
    /*System.out.println("Apply: " + event);*/

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
        executeCopy(copyEvent.getFile(), copyEvent.getNewParent(), copyEvent.getNewChildName());
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
      }
    }
    catch (Exception e) {
      // Exception applying single event should not prevent other events from applying.
      LOG.error(e);
    }
  }

  @NonNls
  public String toString() {
    return "PersistentFS";
  }

  private void executeCreateChild(final VirtualFile parent, final String name) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    VirtualFile fakeFile = new FakeVirtualFile(parent, name);
    if (delegate.exists(fakeFile)) {
      final int parentId = getFileId(parent);
      int childId = FSRecords.createRecord();
      copyRecordFromDelegateFS(childId, parentId, fakeFile, delegate);

      appendIdToParentList(parentId, childId);
      final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
      dir.addChild(dir.createChild(name, childId));
    }
  }

  private void appendIdToParentList(final int parentId, final int childId) {
    int[] childrenlist = myRecords.list(parentId);
    childrenlist = ArrayUtil.append(childrenlist, childId);
    myRecords.updateList(parentId, childrenlist);
  }

  private void executeDelete(final VirtualFile file) {
    if (!file.exists()) {
      LOG.error("Deleting a file, which does not exist: " + file.getPath());
    }
    else {
      clearIdCache();

      final int id = getFileId(file);

      final VirtualFile parent = file.getParent();
      final int parentId = parent != null ? getFileId(parent) : 0;

      myRecords.deleteRecordRecursively(id);

      if (parentId != 0) {
        removeIdFromParentList(parentId, id);
        VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file.getParent();
        assert directory != null;

        directory.removeChild(file);
      }
      else {
        synchronized (LOCK) {
          myRoots.remove(file.getUrl());
          try {
            myRecords.deleteRootRecord(id);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }

      invalidateSubtree(file);
    }
  }

  private static void invalidateSubtree(final VirtualFile file) {
    final VirtualFileSystemEntry impl = (VirtualFileSystemEntry)file;
    impl.invalidate();
    for (VirtualFile child : impl.getCachedChildren()) {
      invalidateSubtree(child);
    }
  }

  private void removeIdFromParentList(final int parentId, final int id) {
    int[] childList = myRecords.list(parentId);
    childList = ArrayUtil.remove(childList, ArrayUtil.indexOf(childList, id));
    myRecords.updateList(parentId, childList);
  }

  private void executeRename(final VirtualFile file, final String newName) {
    ((VirtualFileSystemEntry)file).setName(newName);
    final int id = getFileId(file);
    myRecords.setName(id, newName);
  }

  private void executeSetWritable(final VirtualFile file, final boolean writableFlag) {
    setFlag(file, IS_READ_ONLY, !writableFlag);
  }

  private void setFlag(VirtualFile file, int mask, boolean value) {
    setFlag(getFileId(file), mask, value);
  }

  private void setFlag(final int id, final int mask, final boolean value) {
    int oldFlags = FSRecords.getFlags(id);
    int flags = value ? oldFlags | mask : oldFlags & ~mask;

    if (oldFlags != flags) {
      myRecords.setFlags(id, flags, true);
    }
  }

  private static boolean checkFlag(VirtualFile file, int mask) {
    return (FSRecords.getFlags(getFileId(file)) & mask) != 0;
  }

  private void executeTouch(final VirtualFile file, boolean reloadContentFromDelegate, long newModificationStamp) {
    if (reloadContentFromDelegate) {
      setFlag(file, MUST_RELOAD_CONTENT, true);
    }

    final NewVirtualFileSystem delegate = getDelegate(file);
    myRecords.setLength(getFileId(file), delegate.getLength(file));
    myRecords.setTimestamp(getFileId(file), delegate.getTimeStamp(file));

    ((NewVirtualFile)file).setModificationStamp(newModificationStamp);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  private void executeCopy(final VirtualFile from, final VirtualFile newParent, final String copyName) {
    executeCreateChild(newParent, copyName);
  }

  private void executeMove(final VirtualFile what, final VirtualFile newParent) {
    final int whatId = getFileId(what);
    final int newParentId = getFileId(newParent);
    final int oldParentId = getFileId(what.getParent());

    removeIdFromParentList(oldParentId, whatId);
    appendIdToParentList(newParentId, whatId);

    ((VirtualFileSystemEntry)what).setParent(newParent);
    myRecords.setParent(whatId, newParentId);
  }

  public String getName(final int id) {
    assert id > 0;
    return FSRecords.getName(id);
  }

  public void cleanPersistedContents() {
    try {
      final int[] roots = myRecords.listRoots();
      for (int root : roots) {
        cleanPersistedContentsRecursively(root);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void cleanPersistedContentsRecursively(int id) {
    if (isDirectory(id)) {
      for (int child : myRecords.list(id)) {
        cleanPersistedContentsRecursively(child);
      }
    }
    else {
      setFlag(id, MUST_RELOAD_CONTENT, true);
    }
  }

  private static int maxIntellisenseFileSize() {
    final int maxLimitBytes = (int)FILE_LENGTH_TO_CACHE_THRESHOLD;
    final String userLimitKb = System.getProperty(MAX_INTELLISENSE_SIZE_PROPERTY);
    try {
      return userLimitKb != null ? Math.min(Integer.parseInt(userLimitKb) * 1024, maxLimitBytes) : maxLimitBytes;
    }
    catch (NumberFormatException ignored) {
      return maxLimitBytes;
    }
  }
}
