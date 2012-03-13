/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import com.intellij.util.io.DupOutputStream;
import com.intellij.util.io.ReplicatorInputStream;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
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
public class PersistentFS extends ManagingFS implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.PersistentFS");

  private static final int CHILDREN_CACHED_FLAG = 0x01;
  static final int IS_DIRECTORY_FLAG = 0x02;
  private static final int IS_READ_ONLY = 0x04;
  private static final int MUST_RELOAD_CONTENT = 0x08;
  private static final int IS_SYMLINK = 0x10;
  private static final int IS_SPECIAL = 0x20;

  static final int ALL_VALID_FLAGS =
    CHILDREN_CACHED_FLAG | IS_DIRECTORY_FLAG | IS_READ_ONLY | MUST_RELOAD_CONTENT | IS_SYMLINK | IS_SPECIAL;

  private final MessageBus myEventsBus;

  @NotNull private final Map<String, VirtualFileSystemEntry> myRoots = new THashMap<String, VirtualFileSystemEntry>();
  @NotNull private final TIntObjectHashMap<VirtualFileSystemEntry> myRootsById = new TIntObjectHashMap<VirtualFileSystemEntry>();
  @Nullable private VirtualFileSystemEntry myFakeRoot;
  @NotNull private final Object INPUT_LOCK = new Object();

  public PersistentFS(MessageBus bus) {
    myEventsBus = bus;

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        performShutdown();
      }
    });
  }

  @Override
  public void disposeComponent() {
    performShutdown();
  }

  @NotNull private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(Boolean.FALSE);
  private void performShutdown() {
    if (!myShutdownPerformed.getAndSet(Boolean.TRUE)) {
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
  public void initComponent() {
    FSRecords.connect();
  }

  @Override
  public boolean areChildrenLoaded(@NotNull final VirtualFile dir) {
    return areChildrenLoaded(getFileId(dir));
  }

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
    int[] childrenIds = FSRecords.list(id);
    String[] names = listPersisted(childrenIds);
    if (areChildrenLoaded(id)) {
      return names;
    }
    Pair<String[], int[]> pair = persistAllChildren(file, id, Pair.create(names, childrenIds));
    return pair.first;
  }

  @NotNull
  public static String[] listPersisted(@NotNull VirtualFile file) {
    return listPersisted(FSRecords.list(getFileId(file)));
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
  private static Pair<String[],int[]> persistAllChildren(@NotNull VirtualFile file, int id, @NotNull Pair<String[],int[]> current) {
    String[] currentNames = current.first;
    int[] currentIds = current.second;

    final NewVirtualFileSystem delegate = getDelegate(file);
    String[] delegateNames = VfsUtil.filterNames(delegate.list(file));
    if (delegateNames.length == 0 && currentNames.length > 0) {
      return current;
    }

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
        FakeVirtualFile child = new FakeVirtualFile(file, name);
        int attributes = delegate.getBooleanAttributes(child, -1);
        int childId = createAndCopyRecord(delegate, child, id, attributes);
        childrenIds[i] = childId;
      }
    }

    FSRecords.updateList(id, childrenIds);
    int flags = FSRecords.getFlags(id);
    FSRecords.setFlags(id, flags | CHILDREN_CACHED_FLAG, true);

    return Pair.create(names, childrenIds);
  }

  @NotNull
  public static int[] listIds(@NotNull VirtualFile parent) {
    final int parentId = getFileId(parent);

    int[] ids = FSRecords.list(parentId);
    if (!areChildrenLoaded(parentId)) {
      String[] names = listPersisted(ids);
      Pair<String[], int[]> pair = persistAllChildren(parent, parentId, Pair.create(names, ids));
      return pair.second;
    }

    return ids;
  }

  @NotNull
  public static Pair<String[],int[]> listAll(@NotNull VirtualFile parent) {
    final int parentId = getFileId(parent);

    Pair<String[], int[]> pair = FSRecords.listAll(parentId);
    if (!areChildrenLoaded(parentId)) {
      return persistAllChildren(parent, parentId, pair);
    }

    return pair;
  }


  private static boolean areChildrenLoaded(final int parentId) {
    final int mask = CHILDREN_CACHED_FLAG;
    return (FSRecords.getFlags(parentId) & mask) != 0;
  }

  @Override
  @Nullable
  public DataInputStream readAttribute(@NotNull final VirtualFile file, @NotNull final FileAttribute att) {
    return FSRecords.readAttribute(getFileId(file), att.getId());
  }

  @Override
  @NotNull
  public DataOutputStream writeAttribute(@NotNull final VirtualFile file, @NotNull final FileAttribute att) {
    return FSRecords.writeAttribute(getFileId(file), att.getId(), att.isFixedSize());
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

  public int storeUnlinkedContent(byte[] bytes) {
    return FSRecords.storeUnlinkedContent(bytes);
  }

  @Override
  public int getModificationCount(@NotNull final VirtualFile file) {
    final int id = getFileId(file);
    return FSRecords.getModCount(id);
  }

  @Override
  public int getCheapFileSystemModificationCount() {
    return FSRecords.getLocalModCount();
  }

  @Override
  public int getFilesystemModificationCount() {
    return FSRecords.getModCount();
  }

  private static boolean copyRecordFromDelegateFS(final int id,
                                                  final int parentId,
                                                  @NotNull VirtualFile file,
                                                  @NotNull NewVirtualFileSystem delegate,
                                                  @FileUtil.FileBooleanAttributes int attributes) {
    String name = file.getName();

    if (!name.isEmpty() && namesEqual(delegate, name, FSRecords.getName(id))) return false; // TODO: Handle root attributes change.

    if (name.isEmpty()) {            // TODO: hack
      if (areChildrenLoaded(id)) return false;
    }

    FSRecords.setParent(id, parentId);
    FSRecords.setName(id, name);

    delegate = replaceWithNativeFS(delegate);

    FSRecords.setTimestamp(id, delegate.getTimeStamp(file));

    boolean isDir = (attributes & FileUtil.BA_DIRECTORY) != 0;
    FSRecords.setLength(id, isDir ? -1L : delegate.getLength(file));

    boolean isSpecial = (attributes & (FileUtil.BA_REGULAR | FileUtil.BA_DIRECTORY | FileUtil.BA_EXISTS)) ==
                        FileUtil.BA_EXISTS;
    FSRecords.setFlags(id, (isDir ? IS_DIRECTORY_FLAG : 0) |
                           (delegate.isWritable(file) ? 0 : IS_READ_ONLY) |
                           (delegate.isSymLink(file) ? IS_SYMLINK : 0) |
                           (isSpecial ? IS_SPECIAL : 0), true);

    return true;
  }

  @Override
  public boolean isDirectory(@NotNull final VirtualFile file) {
    final int id = getFileId(file);
    return isDirectory(id);
  }

  public static boolean isDirectory(final int id) {
    assert id > 0;
    return (FSRecords.getFlags(id) & IS_DIRECTORY_FLAG) != 0;
  }

  public static boolean isSymLink(final int id) {
    assert id > 0;
    return (FSRecords.getFlags(id) & IS_SYMLINK) != 0;
  }

  private static int getParent(final int id) {
    assert id > 0;
    return FSRecords.getParent(id);
  }

  private static boolean namesEqual(@NotNull VirtualFileSystem fs, @NotNull String n1, String n2) {
    return ((NewVirtualFileSystem)fs).isCaseSensitive() ? n1.equals(n2) : n1.equalsIgnoreCase(n2);
  }

  @Override
  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    return ((VirtualFileWithId)fileOrDirectory).getId() > 0;
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    final int id = getFileId(file);
    return FSRecords.getTimestamp(id);
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
    return (FSRecords.getFlags(getFileId(file)) & IS_SYMLINK) != 0;
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSpecialFile(@NotNull VirtualFile file) {
    return (FSRecords.getFlags(getFileId(file)) & IS_SPECIAL) != 0;
  }

  @Override
  public boolean isWritable(@NotNull final VirtualFile file) {
    return (FSRecords.getFlags(getFileId(file)) & IS_READ_ONLY) == 0;
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
    getDelegate(file).setWritable(file, writableFlag);
    processEvent(new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, isWritable(file), writableFlag, false));
  }

  public static int getId(@NotNull final VirtualFile parent, @NotNull final String childName, @NotNull NewVirtualFileSystem delegate) {
    final int parentId = getFileId(parent);

    final int[] children = FSRecords.list(parentId);
    for (final int childId : children) {
      if (namesEqual(delegate, childName, FSRecords.getName(childId))) return childId;
    }

    VirtualFile fake = new FakeVirtualFile(parent, childName);
    int attributes = delegate.getBooleanAttributes(fake, -1);
    if ((attributes & FileUtil.BA_EXISTS) != 0) {
      int child = createAndCopyRecord(delegate, fake, parentId, attributes);
      FSRecords.updateList(parentId, ArrayUtil.append(children, child));
      return child;
    }

    return 0;
  }

  @Override
  public long getLength(@NotNull final VirtualFile file) {
    final VirtualFile canonicalFile = file.getCanonicalFile();
    return canonicalFile == null ? 0 : getLengthNoFollow(canonicalFile);
  }

  @SuppressWarnings("MethodMayBeStatic")
  public long getLengthNoFollow(@NotNull final VirtualFile file) {
    final int id = getFileId(file);

    long len = FSRecords.getLength(id);
    if (len == -1) {
      len = (int)getDelegate(file).getLength(file);
      FSRecords.setLength(id, len);
    }

    return len;
  }

  @Override
  public VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent, @NotNull final String copyName)
    throws IOException {
    getDelegate(file).copyFile(requestor, file, newParent, copyName);
    processEvent(new VFileCopyEvent(requestor, file, newParent, copyName));

    final VirtualFile child = newParent.findChild(copyName);
    if (child == null) {
      throw new IOException("Cannot create child");
    }
    return child;
  }

  @Override
  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException {
    getDelegate(parent).createChildDirectory(requestor, parent, dir);
    processEvent(new VFileCreateEvent(requestor, parent, dir, true, false));

    final VirtualFile child = parent.findChild(dir);
    if (child == null) {
      throw new IOException("Cannot create child directory '" + dir + "' at " + parent.getPath());
    }
    return child;
  }

  @Override
  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException {
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
  public void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException {
    getDelegate(file).renameFile(requestor, file, newName);
    processEvent(new VFilePropertyChangeEvent(requestor, file, VirtualFile.PROP_NAME, file.getName(), newName, false));
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file) throws IOException {
    return contentsToByteArray(file, true);
  }

  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file, boolean cacheContent) throws IOException {
    final VirtualFile canonicalFile = getCanonicalFile(file);

    InputStream contentStream = null;
    boolean reloadFromDelegate;
    synchronized (INPUT_LOCK) {
      reloadFromDelegate = mustReloadContent(canonicalFile) || (contentStream = readContent(canonicalFile)) == null;
    }

    if (reloadFromDelegate) {
      final NewVirtualFileSystem delegate = getDelegate(canonicalFile);
      FSRecords.setLength(getFileId(canonicalFile), delegate.getLength(canonicalFile));
      final byte[] content = delegate.contentsToByteArray(canonicalFile);

      ApplicationEx application = (ApplicationEx)ApplicationManager.getApplication();
      // we should cache every local files content
      // because the local history feature is currently depends on this cache,
      // perforce offline mode as well
      if ((!delegate.isReadOnly() || !application.isInternal() && !application.isUnitTestMode()) &&
          content.length <= PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD) {
        synchronized (INPUT_LOCK) {
          writeContent(canonicalFile, new ByteSequence(content), delegate.isReadOnly());
          setFlag(canonicalFile, MUST_RELOAD_CONTENT, false);
        }
      }

      return content;
    }
    else {
      try {
        final int length = (int)canonicalFile.getLength();
        assert length >= 0 : canonicalFile;
        return FileUtil.loadBytes(contentStream, length);
      }
      catch (IOException e) {
        throw FSRecords.handleError(e);
      }
    }
  }

  @NotNull
  public byte[] contentsToByteArray(int contentId) throws IOException {
    return FileUtil.loadBytes(readContentById(contentId));
  }

  @Override
  @NotNull
  public InputStream getInputStream(@NotNull final VirtualFile file) throws IOException {
    final VirtualFile canonicalFile = getCanonicalFile(file);
    synchronized (INPUT_LOCK) {
      InputStream contentStream;
      if (mustReloadContent(canonicalFile) || (contentStream = readContent(canonicalFile)) == null) {
        final NewVirtualFileSystem delegate = getDelegate(canonicalFile);
        final long len = delegate.getLength(canonicalFile);
        FSRecords.setLength(getFileId(canonicalFile), len);
        final InputStream nativeStream = delegate.getInputStream(canonicalFile);

        if (len > PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD) return nativeStream;
        return createReplicator(canonicalFile, nativeStream, len, delegate.isReadOnly());
      }
      else {
        return contentStream;
      }
    }
  }

  @NotNull
  private static VirtualFile getCanonicalFile(@NotNull final VirtualFile file) throws IOException {
    final VirtualFile canonicalFile = file.getCanonicalFile();
    if (canonicalFile == null) {
      throw new IOException("Broken link: " + file);
    }
    return canonicalFile;
  }

  private InputStream createReplicator(@NotNull final VirtualFile file, final InputStream nativeStream, final long fileLength, final boolean readOnly)
    throws IOException {
    if (nativeStream instanceof BufferExposingByteArrayInputStream) {
      // optimization
      BufferExposingByteArrayInputStream  byteStream = (BufferExposingByteArrayInputStream )nativeStream;
      byte[] bytes = byteStream.getInternalBuffer();
      storeContentToStorage(fileLength, file, readOnly, bytes, bytes.length);
      return nativeStream;
    }
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
    synchronized (INPUT_LOCK) {
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
    return checkFlag(file, MUST_RELOAD_CONTENT) || FSRecords.getLength(getFileId(file)) == -1L;
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(@NotNull final VirtualFile file,
                                      final Object requestor,
                                      final long modStamp,
                                      final long timeStamp) throws IOException {
    final VirtualFile canonicalFile = getCanonicalFile(file);

    final VFileContentChangeEvent event = new VFileContentChangeEvent(requestor, canonicalFile, canonicalFile.getModificationStamp(), modStamp, false);

    final List<VFileContentChangeEvent> events = Collections.singletonList(event);

    final BulkFileListener publisher = myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES);
    publisher.before(events);

    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        super.close();

        NewVirtualFileSystem delegate = getDelegate(canonicalFile);
        final OutputStream outputStream = delegate.getOutputStream(canonicalFile, requestor, modStamp, timeStamp);

        //noinspection IOResourceOpenedButNotSafelyClosed
        final DupOutputStream sink = new DupOutputStream(new BufferedOutputStream(writeContent(canonicalFile, delegate.isReadOnly())), outputStream) {
          @Override
          public void close() throws IOException {
            try {
              super.close();
            }
            finally {
              executeTouch(canonicalFile, false, event.getModificationStamp());
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
  }

  public int acquireContent(@NotNull VirtualFile file) {
    return FSRecords.acquireFileContent(getFileId(file));
  }

  public void releaseContent(int contentId) {
    FSRecords.releaseContent(contentId);
  }

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
  private static List<? extends VFileEvent> validateEvents(@NotNull List<? extends VFileEvent> events) {
    final List<EventWrapper> deletionEvents = Lists.newArrayList();
    for (int i = 0, size = events.size(); i < size; i++) {
      final VFileEvent event = events.get(i);
      if (event instanceof VFileDeleteEvent && event.isValid()) {
        deletionEvents.add(new EventWrapper((VFileDeleteEvent)event, i));
      }
    }

    ContainerUtil.quickSort(deletionEvents, DEPTH_COMPARATOR);

    final TIntHashSet invalidIDs = new TIntHashSet(deletionEvents.size());
    final List<VirtualFile> dirsToBeDeleted = new ArrayList<VirtualFile>();
    nextEvent:
    for (EventWrapper wrapper : deletionEvents) {
      final VirtualFile candidate = wrapper.event.getFile();
      for (VirtualFile file : dirsToBeDeleted) {
        if (VfsUtilCore.isAncestor(file, candidate, false)) {
          invalidIDs.add(wrapper.id);
          continue nextEvent;
        }
      }

      if (candidate.isDirectory()) {
        dirsToBeDeleted.add(candidate);
      }
    }

    final List<VFileEvent> filtered = Lists.newArrayListWithCapacity(events.size() - invalidIDs.size());
    for (int i = 0, size = events.size(); i < size; i++) {
      final VFileEvent event = events.get(i);
      if (event.isValid() && !(event instanceof VFileDeleteEvent && invalidIDs.contains(i))) {
        filtered.add(event);
      }
    }
    return filtered;
  }

  @Override
  public void processEvents(@NotNull List<? extends VFileEvent> events) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    events = validateEvents(events);

    BulkFileListener publisher = myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES);
    publisher.before(events);
    for (VFileEvent event : events) {
      applyEvent(event);
    }
    publisher.after(events);
  }

  @NotNull public static final Object LOCK = new Object();

  @Override
  @Nullable
  public VirtualFileSystemEntry findRoot(@NotNull String basePath, @NotNull NewVirtualFileSystem fs) { // TODO: read/write locks instead of synchronized
    synchronized (LOCK) {
      String rootUrl = fs.getProtocol() + "://" + basePath;
      VirtualFileSystemEntry root = myRoots.get(rootUrl);
      if (root == null && basePath.isEmpty()) {
        root = myFakeRoot;
      }
      if (root == null) {
        try {
          final int rootId = FSRecords.findRootRecord(rootUrl);
          if (basePath.isEmpty()) {
            // fake root for windows
            root = new VirtualDirectoryImpl("", null, fs, rootId) {
              @Override
              @NotNull
              public VirtualFile[] getChildren() {
                return getRoots(getFileSystem());
              }

              @Override
              public VirtualFileSystemEntry findChild(@NotNull String name) {
                if (name.isEmpty()) return null;
                return findRoot(name, getFileSystem());
              }
            };
          }
          else {
            if (fs instanceof JarFileSystem) {
              // optimization: for jar roots do not store base path in the myName field, use local FS file's getPath()
              String parentPath = basePath.substring(0, basePath.indexOf(JarFileSystem.JAR_SEPARATOR));
              final VirtualFile parentLocalFile = LocalFileSystem.getInstance().findFileByPath(parentPath);
              if (parentLocalFile == null) return null;
              root = new JarRoot(fs, rootId, parentLocalFile);
            }
            else {
              root = new VirtualDirectoryImpl(basePath, null, fs, rootId);
            }
          }
          if (!fs.exists(root)) {
            return null;
          }

          int attributes = FileUtil.BA_DIRECTORY | FileUtil.BA_EXISTS;
          boolean newRoot = copyRecordFromDelegateFS(rootId, 0, root, fs, attributes);
          if (!newRoot) {
            if (fs.getTimeStamp(root) != FSRecords.getTimestamp(rootId)) {
              root.markDirtyRecursively();
            }
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        if (basePath.isEmpty()) {
          myFakeRoot = root;
        }
        else {
          myRoots.put(rootUrl, root);
          myRootsById.put(root.getId(), root);
        }
      }

      return root;
    }
  }

  @Override
  public void refresh(final boolean asynchronous) {
    final NewVirtualFile[] roots;
    synchronized (LOCK) {
      Collection<VirtualFileSystemEntry> values = myRoots.values();
      roots = values.toArray(new NewVirtualFile[values.size()]);
    }

    RefreshQueue.getInstance().refresh(asynchronous, true, null, roots);
  }

  @Override
  public void refresh(boolean asynchronous, Runnable postAction, @NotNull ModalityState modalityState) {
    final NewVirtualFile[] roots;
    synchronized (LOCK) {
      Collection<VirtualFileSystemEntry> values = myRoots.values();
      roots = values.toArray(new NewVirtualFile[values.size()]);
    }

    RefreshQueue.getInstance().refresh(asynchronous, true, postAction, modalityState, roots);
  }

  @Override
  @NotNull
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
  @NotNull private final StripedLockIntObjectConcurrentHashMap<NewVirtualFile> myIdToDirCache = new StripedLockIntObjectConcurrentHashMap<NewVirtualFile>();

  public void clearIdCache() {
    myIdToDirCache.clear();
  }

  @Override
  @Nullable
  public NewVirtualFile findFileById(final int id) {
    return _findFileById(id, false);
  }


  @Nullable
  public NewVirtualFile findFileByIdIfCached(final int id) {
    return _findFileById(id, true);
  }

  @Nullable
  private NewVirtualFile _findFileById(int id, final boolean cachedOnly) {
    final NewVirtualFile cached = myIdToDirCache.get(id);
    if (cached != null) {
      return cached;
    }

    NewVirtualFile result = doFindFile(id, cachedOnly);

    if (result != null && result.isDirectory()) {
      NewVirtualFile old = myIdToDirCache.putIfAbsent(id, result);
      if (old != null) result = old;
    }
    return result;
  }

  @Nullable
  private NewVirtualFile doFindFile(final int id, boolean cachedOnly) {
    final int parentId = getParent(id);
    if (parentId == 0) {
      synchronized (LOCK) {
        return myRootsById.get(id);
      }
    }
    else {
      NewVirtualFile parentFile = _findFileById(parentId, cachedOnly);
      if (parentFile == null) {
        return null;
      }
      return cachedOnly ? parentFile.findChildByIdIfCached(id) : parentFile.findChildById(id);
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getRoots() {
    synchronized (LOCK) {
      Collection<VirtualFileSystemEntry> roots = myRoots.values();
      return VfsUtil.toVirtualFileArray(roots);
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getRoots(@NotNull final NewVirtualFileSystem fs) {
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

  private void applyEvent(@NotNull VFileEvent event) {
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

  @NotNull
  @NonNls
  public String toString() {
    return "PersistentFS";
  }

  private static void executeCreateChild(@NotNull VirtualFile parent, @NotNull String name) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    VirtualFile fake = new FakeVirtualFile(parent, name);
    int attributes = delegate.getBooleanAttributes(fake, -1);
    if ((attributes & FileUtil.BA_EXISTS) != 0) {
      final int parentId = getFileId(parent);
      int childId = createAndCopyRecord(delegate, fake, parentId, attributes);
      appendIdToParentList(parentId, childId);
      assert parent instanceof VirtualDirectoryImpl : parent;
      final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
      dir.addChild(dir.createChild(name, childId));
    }
  }

  private static int createAndCopyRecord(@NotNull NewVirtualFileSystem delegateSystem, @NotNull VirtualFile delegateFile, int parentId, @FileUtil.FileBooleanAttributes int attributes) {
    int childId = FSRecords.createRecord();
    copyRecordFromDelegateFS(childId, parentId, delegateFile, delegateSystem, attributes);
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
    }
    else {
      clearIdCache();

      final int id = getFileId(file);

      final VirtualFile parent = file.getParent();
      final int parentId = parent != null ? getFileId(parent) : 0;

      FSRecords.deleteRecordRecursively(id);

      if (parentId != 0) {
        removeIdFromParentList(parentId, id, parent, file);
        VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file.getParent();
        assert directory != null;

        directory.removeChild(file);
      }
      else {
        synchronized (LOCK) {
          myRoots.remove(file.getUrl());
          myRootsById.remove(id);
          try {
            FSRecords.deleteRootRecord(id);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }

      invalidateSubtree(file);
    }
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
    ((VirtualFileSystemEntry)file).setName(newName);
    final int id = getFileId(file);
    FSRecords.setName(id, newName);
  }

  private static void executeSetWritable(@NotNull VirtualFile file, final boolean writableFlag) {
    setFlag(file, IS_READ_ONLY, !writableFlag);
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

  private static boolean checkFlag(@NotNull VirtualFile file, int mask) {
    return (FSRecords.getFlags(getFileId(file)) & mask) != 0;
  }

  private static void executeTouch(@NotNull VirtualFile file, boolean reloadContentFromDelegate, long newModificationStamp) {
    if (reloadContentFromDelegate) {
      setFlag(file, MUST_RELOAD_CONTENT, true);
    }

    final NewVirtualFileSystem delegate = getDelegate(file);
    FSRecords.setLength(getFileId(file), delegate.getLength(file));
    FSRecords.setTimestamp(getFileId(file), delegate.getTimeStamp(file));

    ((VirtualFileSystemEntry)file).setModificationStamp(newModificationStamp);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  private static void executeCopy(VirtualFile from, @NotNull VirtualFile newParent, @NotNull String copyName) {
    executeCreateChild(newParent, copyName);
  }

  private static void executeMove(@NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    final int fileId = getFileId(file);
    final int newParentId = getFileId(newParent);
    final int oldParentId = getFileId(file.getParent());

    removeIdFromParentList(oldParentId, fileId, file.getParent(), file);
    appendIdToParentList(newParentId, fileId);

    ((VirtualFileSystemEntry)file).setParent(newParent);
    FSRecords.setParent(fileId, newParentId);
  }

  public String getName(final int id) {
    assert id > 0;
    return FSRecords.getName(id);
  }

  @TestOnly
  public static void cleanPersistedContents() {
    try {
      final int[] roots = FSRecords.listRoots();
      for (int root : roots) {
        cleanPersistedContentsRecursively(root);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  private static void cleanPersistedContentsRecursively(int id) {
    if (isDirectory(id)) {
      for (int child : FSRecords.list(id)) {
        cleanPersistedContentsRecursively(child);
      }
    }
    else {
      setFlag(id, MUST_RELOAD_CONTENT, true);
    }
  }

  @NotNull
  public static NewVirtualFileSystem replaceWithNativeFS(@NotNull NewVirtualFileSystem delegate) {
    if (delegate.getProtocol().equals(LocalFileSystem.PROTOCOL) &&
        Registry.is("filesystem.useNative") &&
        SystemInfo.isWindows &&
        Win32LocalFileSystem.isAvailable()) {
      delegate = Win32LocalFileSystem.getWin32Instance();
    }
    return delegate;
  }

  private static class JarRoot extends VirtualDirectoryImpl {
    private final VirtualFile myParentLocalFile;

    public JarRoot(@NotNull NewVirtualFileSystem fs, int rootId, @NotNull VirtualFile parentLocalFile) {
      super("", null, fs, rootId);
      myParentLocalFile = parentLocalFile;
    }

    @NotNull
    @Override
    public String getName() {
      return myParentLocalFile.getName();
    }

    @Override
    protected Object rawName() {
      return myParentLocalFile.getPath() + JarFileSystem.JAR_SEPARATOR;
    }
  }
}
