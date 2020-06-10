// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.Utf8BomOptionProvider;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import com.intellij.util.io.ReplicatorInputStream;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.intellij.openapi.util.Pair.pair;

public final class PersistentFSImpl extends PersistentFS implements Disposable {
  private static final Logger LOG = Logger.getInstance(PersistentFS.class);

  private final Map<String, VirtualFileSystemEntry> myRoots =
    ConcurrentCollectionFactory.createMap(10, 0.4f, JobSchedulerImpl.getCPUCoresCount(), FileUtil.PATH_HASHING_STRATEGY);

  // FS roots must be in this map too. findFileById() relies on this.
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToDirCache = ContainerUtil.createConcurrentIntObjectSoftValueMap();
  private final Object myInputLock = new Object();

  private final AtomicBoolean myShutDown = new AtomicBoolean(false);
  private final AtomicInteger myStructureModificationCount = new AtomicInteger();
  private BulkFileListener myPublisher;
  private final VfsData myVfsData = new VfsData();

  public PersistentFSImpl() {
    ShutDownTracker.getInstance().registerShutdownTask(this::performShutdown);
    LowMemoryWatcher.register(this::clearIdCache, this);

    AsyncEventSupport.startListening();

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener(){
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // myIdToDirCache could retain alien file systems
        clearIdCache();
        // remove alien file system references from myRoots
        for (Iterator<Map.Entry<String, VirtualFileSystemEntry>> iterator = myRoots.entrySet().iterator(); iterator.hasNext(); ) {
          Map.Entry<String, VirtualFileSystemEntry> entry = iterator.next();
          VirtualFileSystemEntry root = entry.getValue();
          if (VirtualFileManager.getInstance().getFileSystem(root.getFileSystem().getProtocol()) == null) {
            // the file system must have been unregistered
            iterator.remove();
          }
        }
      }
    });
    Activity activity = StartUpMeasurer.startActivity("connect FSRecords");
    FSRecords.connect();
    activity.end();
  }

  @NotNull
  private BulkFileListener getPublisher() {
    BulkFileListener publisher = myPublisher;
    if (publisher == null) {
      // cannot be in constructor, to ensure that lazy listeners will be not created too early
      publisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
      myPublisher = publisher;
    }
    return publisher;
  }

  @Override
  public void dispose() {
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
  public boolean areChildrenLoaded(@NotNull VirtualFile dir) {
    return areChildrenLoaded(getFileId(dir));
  }

  @Override
  public long getCreationTimestamp() {
    return FSRecords.getCreationTimestamp();
  }

  @NotNull
  public VirtualFileSystemEntry getOrCacheDir(int id, @NotNull VirtualDirectoryImpl newDir) {
    VirtualFileSystemEntry dir = myIdToDirCache.get(id);
    if (dir != null) return dir;
    return myIdToDirCache.cacheOrGet(id, newDir);
  }
  public VirtualFileSystemEntry getCachedDir(int id) {
    return myIdToDirCache.get(id);
  }

  @NotNull
  private static NewVirtualFileSystem getDelegate(@NotNull VirtualFile file) {
    return (NewVirtualFileSystem)file.getFileSystem();
  }

  @Override
  public boolean wereChildrenAccessed(@NotNull VirtualFile dir) {
    return FSRecords.wereChildrenAccessed(getFileId(dir));
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    List<? extends ChildInfo> children = listAll(file);
    return ContainerUtil.map2Array(children, String.class, id -> id.getName().toString());
  }

  @Override
  public String @NotNull [] listPersisted(@NotNull VirtualFile parent) {
    int[] childrenIds = FSRecords.listIds(getFileId(parent));
    String[] names = ArrayUtil.newStringArray(childrenIds.length);
    for (int i = 0; i < childrenIds.length; i++) {
      names[i] = FSRecords.getName(childrenIds[i]);
    }
    return names;
  }

  @NotNull
  // return actual children
  private static List<? extends ChildInfo> persistAllChildren(@NotNull VirtualFile file, int id) {
    final NewVirtualFileSystem fs = replaceWithNativeFS(getDelegate(file));
    Map<String, ChildInfo> justCreated = new HashMap<>();
    String[] delegateNames = VfsUtil.filterNames(fs.list(file));
    ListResult saved = FSRecords.update(id, current -> {
      List<? extends ChildInfo> currentChildren = current.children;
      if (delegateNames.length == 0 && !currentChildren.isEmpty()) {
        return current;
      }
      // preserve current children which match delegateNames (to have stable id)
      // (on case-insensitive system replace those from current with case-changes ones from delegateNames preserving the id)
      // add those from delegateNames which are absent from current
      Set<String> toAddNames = new THashSet<>(Arrays.asList(delegateNames), FilePathHashingStrategy.create(fs.isCaseSensitive()));
      for (ChildInfo currentChild : currentChildren) {
        toAddNames.remove(currentChild.getName().toString());
      }
      List<ChildInfo> toAddChildren = new ArrayList<>(toAddNames.size());
      for (String newName : toAddNames) {
        Pair<FileAttributes, String> childData = getChildData(fs, file, newName, null, null);
        if (childData != null) {
          ChildInfo newChild = justCreated.computeIfAbsent(newName, name->makeChildRecord(id, name, childData, fs, null));
          toAddChildren.add(newChild);
        }
      }

      // some clients (e.g. RefreshWorker) expect subsequent list() calls to return equal arrays
      toAddChildren.sort(ChildInfo.BY_ID);
      TObjectHashingStrategy<CharSequence> hashingStrategy = FilePathHashingStrategy.createForCharSequence(fs.isCaseSensitive());
      return current.merge(toAddChildren, hashingStrategy);
    });

    setChildrenCached(id);

    return saved.children;
  }

  private static void setChildrenCached(int id) {
    int flags = FSRecords.getFlags(id);
    FSRecords.setFlags(id, flags | CHILDREN_CACHED_FLAG, true);
  }

  @Override
  @ApiStatus.Internal
  public @NotNull List<? extends ChildInfo> listAll(@NotNull VirtualFile file) {
    int id = getFileId(file);

    if (areChildrenLoaded(id)) {
      return FSRecords.list(id).children;
    }
    return persistAllChildren(file, id);
  }

  private static boolean areChildrenLoaded(int parentId) {
    return BitUtil.isSet(FSRecords.getFlags(parentId), CHILDREN_CACHED_FLAG);
  }

  @Override
  @Nullable
  public DataInputStream readAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att) {
    return FSRecords.readAttributeWithLock(getFileId(file), att);
  }

  @Override
  @NotNull
  public DataOutputStream writeAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att) {
    return FSRecords.writeAttribute(getFileId(file), att);
  }

  @Nullable
  private static DataInputStream readContent(@NotNull VirtualFile file) {
    return FSRecords.readContent(getFileId(file));
  }

  @NotNull
  private static DataInputStream readContentById(int contentId) {
    return FSRecords.readContentById(contentId);
  }

  @NotNull
  private static DataOutputStream writeContent(@NotNull VirtualFile file, boolean readOnly) {
    return FSRecords.writeContent(getFileId(file), readOnly);
  }

  private static void writeContent(@NotNull VirtualFile file, ByteArraySequence content, boolean readOnly) {
    FSRecords.writeContent(getFileId(file), content, readOnly);
  }

  @Override
  public int storeUnlinkedContent(byte @NotNull [] bytes) {
    return FSRecords.storeUnlinkedContent(bytes);
  }

  @Override
  public int getModificationCount(@NotNull VirtualFile file) {
    return FSRecords.getModCount(getFileId(file));
  }

  @Override
  public int getModificationCount() {
    return FSRecords.getLocalModCount();
  }

  @Override
  public int getStructureModificationCount() {
    return myStructureModificationCount.get();
  }

  public void incStructuralModificationCount() {
    myStructureModificationCount.incrementAndGet();
  }

  @Override
  public int getFilesystemModificationCount() {
    return FSRecords.getModCount();
  }

  // return nameId>0 if write successful, -1 if not
  private static int writeAttributesToRecord(int id,
                                             int parentId,
                                             @NotNull CharSequence name,
                                             @NotNull NewVirtualFileSystem fs,
                                             @NotNull FileAttributes attributes,
                                             @Nullable String symlinkTarget) {
    assert id > 0 : id;
    assert parentId >= 0 : parentId; // 0 means there's no parent
    if (name.length() != 0) {
      if (namesEqual(fs, name, FSRecords.getNameSequence(id))) return -1; // TODO: Handle root attributes change.
    }
    else {
      if (areChildrenLoaded(id)) return -1; // TODO: hack
    }

    int nameId = FSRecords.writeAttributesToRecord(id, parentId, attributes, name.toString());

    if (attributes.isSymLink()) {
      FSRecords.storeSymlinkTarget(id, symlinkTarget);
      if (fs instanceof Win32LocalFileSystem) {
        fs = LocalFileSystem.getInstance();
      }
      if (fs instanceof LocalFileSystemImpl) {
        VirtualFile parent = getInstance().findFileById(parentId);
        assert parent != null : parentId + '/' + id + ": " + name + " -> " + symlinkTarget;
        String linkPath = parent.getPath() + '/' + name;
        ((LocalFileSystemImpl)fs).symlinkUpdated(id, parent, linkPath, symlinkTarget);
      }
    }

    return nameId;
  }

  @Override
  public int getFileAttributes(int id) {
    assert id > 0;
    //noinspection MagicConstant
    return FSRecords.getFlags(id);
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    return isDirectory(getFileAttributes(getFileId(file)));
  }

  private static boolean namesEqual(@NotNull VirtualFileSystem fs, @NotNull CharSequence n1, @NotNull CharSequence n2) {
    return Comparing.equal(n1, n2, fs.isCaseSensitive());
  }

  @Override
  public boolean exists(@NotNull VirtualFile fileOrDirectory) {
    return fileOrDirectory.exists();
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    return FSRecords.getTimestamp(getFileId(file));
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long modStamp) throws IOException {
    final int id = getFileId(file);
    FSRecords.setTimestamp(id, modStamp);
    getDelegate(file).setTimeStamp(file, modStamp);
  }

  private static int getFileId(@NotNull VirtualFile file) {
    return ((VirtualFileWithId)file).getId();
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    return isSymLink(getFileAttributes(getFileId(file)));
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    return FSRecords.readSymlinkTarget(getFileId(file));
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
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    getDelegate(file).setWritable(file, writableFlag);
    boolean oldWritable = isWritable(file);
    if (oldWritable != writableFlag) {
      processEvent(new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, oldWritable, writableFlag, false));
    }
  }

  @Override
  @ApiStatus.Internal
  public ChildInfo findChildInfo(@NotNull VirtualFile parent, @NotNull String childName, @NotNull NewVirtualFileSystem fs) {
    int parentId = getFileId(parent);
    Ref<ChildInfo> result = new Ref<>();

    Function<ListResult, ListResult> convertor = children -> {
      ChildInfo child = findExistingChildInfo(childName, children.children, fs);
      if (child != null) {
        result.set(child);
        return children;
      }
      Pair<FileAttributes, String> childData = getChildData(fs, parent, childName, null, null);
      if (childData == null) {
        return children;
      }
      String canonicalName;
      if (fs.isCaseSensitive()) {
        canonicalName = childName;
      }
      else {
        canonicalName = fs.getCanonicallyCasedName(new FakeVirtualFile(parent, childName));
        if (StringUtil.isEmptyOrSpaces(canonicalName)) return children;

        if (!childName.equals(canonicalName)) {
          child = findExistingChildInfo(canonicalName, children.children, fs);
          result.set(child);
        }
      }
      if (child == null) {
        if (result.isNull()) {
          child = makeChildRecord(parentId, canonicalName, childData, fs, null);
          result.set(child);
        }
        else {
          // might have stored on previous attempt
          child = result.get();
        }
        return children.insert(child);
      }
      return children;
    };
    FSRecords.update(parentId, convertor);
    return result.get();
  }

  private static ChildInfo findExistingChildInfo(@NotNull String childName,
                                                 @NotNull List<? extends ChildInfo> children,
                                                 @NotNull NewVirtualFileSystem fs) {
    if (!children.isEmpty()) {
      // fast path, check that some child has same nameId as given name to avoid overhead on retrieving names for non-cached children
      int nameId = FSRecords.getNameId(childName);
      for (ChildInfo info : children) {
        if (nameId == info.getNameId()) {
          return info;
        }
      }
      // for case sensitive system the above check is exhaustive in consistent state of vfs
    }
    for (ChildInfo info : children) {
      if (namesEqual(fs, childName, FSRecords.getNameByNameId(info.getNameId()))) {
        return info;
      }
    }
    return null;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    long length = getLengthIfUpToDate(file);
    return length == -1 ? reloadLengthFromDelegate(file, getDelegate(file)) : length;
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
    processEvent(new VFileCreateEvent(requestor, parent, dir, true, null, null, false, ChildInfo.EMPTY_ARRAY));

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
    processEvent(new VFileCreateEvent(requestor, parent, file, false, null, null, false, null));

    final VirtualFile child = parent.findChild(file);
    if (child == null) {
      throw new IOException("Cannot create child file '" + file + "' at " + parent.getPath());
    }
    if (child.getCharset().equals(StandardCharsets.UTF_8) &&
        !(child.getFileType() instanceof InternalFileType) &&
        isUtf8BomRequired(child)) {
      child.setBOM(CharsetToolkit.UTF8_BOM);
    }
    return child;
  }

  private static boolean isUtf8BomRequired(@NotNull VirtualFile file) {
    for (Utf8BomOptionProvider encodingProvider : Utf8BomOptionProvider.EP_NAME.getExtensionList()) {
      if (encodingProvider.shouldAddBOMForNewUtf8File(file)) {
        return true;
      }
    }
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    EncodingManager encodingManager = project == null ? EncodingManager.getInstance() : EncodingProjectManager.getInstance(project);
    return encodingManager.shouldAddBOMForNewUtf8File();
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
    final NewVirtualFileSystem delegate = getDelegate(file);
    delegate.deleteFile(requestor, file);

    if (!delegate.exists(file)) {
      processEvent(new VFileDeleteEvent(requestor, file, false));
    }
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    getDelegate(file).renameFile(requestor, file, newName);
    String oldName = file.getName();
    if (!newName.equals(oldName)) {
      processEvent(new VFilePropertyChangeEvent(requestor, file, VirtualFile.PROP_NAME, oldName, newName, false));
    }
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    return contentsToByteArray(file, true);
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file, boolean cacheContent) throws IOException {
    InputStream contentStream = null;
    boolean reloadFromDelegate;
    boolean outdated;
    int fileId;
    long length;

    synchronized (myInputLock) {
      fileId = getFileId(file);
      length = getLengthIfUpToDate(file);
      outdated = length == -1;
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
          writeContent(file, new ByteArraySequence(content), delegate.isReadOnly());
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
    }
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  @Override
  public byte @NotNull [] contentsToByteArray(int contentId) throws IOException {
    final DataInputStream stream = readContentById(contentId);
    return FileUtil.loadBytes(stream);
  }

  @Override
  @NotNull
  public InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    synchronized (myInputLock) {
      InputStream contentStream;
      if (getLengthIfUpToDate(file) == -1 || FileUtilRt.isTooLarge(file.getLength()) || (contentStream = readContent(file)) == null) {
        NewVirtualFileSystem delegate = getDelegate(file);
        long len = reloadLengthFromDelegate(file, delegate);
        InputStream nativeStream = delegate.getInputStream(file);

        if (len > PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD) return nativeStream;
        return createReplicator(file, nativeStream, len, delegate.isReadOnly());
      }
      return contentStream;
    }
  }

  private static long reloadLengthFromDelegate(@NotNull VirtualFile file, @NotNull FileSystemInterface delegate) {
    final long len = delegate.getLength(file);
    FSRecords.setLength(getFileId(file), len);
    return len;
  }

  @NotNull
  private InputStream createReplicator(@NotNull VirtualFile file,
                                       @NotNull InputStream nativeStream,
                                       long fileLength,
                                       boolean readOnly) {
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
                                     boolean readOnly, byte @NotNull [] bytes, int bytesLength) {
    synchronized (myInputLock) {
      if (bytesLength == fileLength) {
        writeContent(file, new ByteArraySequence(bytes, 0, bytesLength), readOnly);
        setFlag(file, MUST_RELOAD_CONTENT, false);
      }
      else {
        setFlag(file, MUST_RELOAD_CONTENT, true);
      }
    }
  }

  public static byte @Nullable [] getContentHashIfStored(@NotNull VirtualFile file) {
    return FSRecords.getContentHash(getFileId(file));
  }

  // returns last recorded length or -1 if must reload from delegate
  private static long getLengthIfUpToDate(@NotNull VirtualFile file) {
    int fileId = getFileId(file);
    return BitUtil.isSet(FSRecords.getFlags(fileId), MUST_RELOAD_CONTENT) ? -1 : FSRecords.getLength(fileId);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(@NotNull VirtualFile file,
                                      Object requestor,
                                      long modStamp,
                                      long timeStamp) {
    return new ByteArrayOutputStream() {
      private boolean closed; // protection against user calling .close() twice

      @Override
      public void close() throws IOException {
        if (closed) return;
        super.close();

        ApplicationManager.getApplication().assertWriteAccessAllowed();

        VFileContentChangeEvent event = new VFileContentChangeEvent(requestor, file, file.getModificationStamp(), modStamp, false);
        List<VFileContentChangeEvent> events = Collections.singletonList(event);
        fireBeforeEvents(getPublisher(), events);

        NewVirtualFileSystem delegate = getDelegate(file);
        // FSRecords.ContentOutputStream already buffered, no need to wrap in BufferedStream
        try (OutputStream persistenceStream = writeContent(file, delegate.isReadOnly())) {
          persistenceStream.write(buf, 0, count);
        }
        finally {
          try (OutputStream ioFileStream = delegate.getOutputStream(file, requestor, modStamp, timeStamp)) {
            ioFileStream.write(buf, 0, count);
          }
          finally {
            closed = true;

            final FileAttributes attributes = delegate.getAttributes(file);
            executeTouch(file, false, event.getModificationStamp(),
                         attributes != null ? attributes.length : DEFAULT_LENGTH,
                         // due to fs rounding timestamp of written file can be significantly different from current time
                         attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP);
            fireAfterEvents(getPublisher(), events);
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
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    getDelegate(file).moveFile(requestor, file, newParent);
    processEvent(new VFileMoveEvent(requestor, file, newParent));
  }

  private void processEvent(@NotNull VFileEvent event) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!event.isValid()) {
      return;
    }
    List<VFileEvent> outValidatedEvents = new ArrayList<>();
    outValidatedEvents.add(event);
    List<Runnable> outApplyActions = new ArrayList<>();
    List<VFileDeleteEvent> jarDeleteEvents = VfsImplUtil.getJarInvalidationEvents(event, outApplyActions);
    BulkFileListener publisher = getPublisher();
    if (jarDeleteEvents.isEmpty() && outApplyActions.isEmpty()) {
      // optimisation: skip all groupings
      fireBeforeEvents(publisher, outValidatedEvents);

      applyEvent(event);

      fireAfterEvents(publisher, outValidatedEvents);
    }
    else {
      outApplyActions.add(() -> applyEvent(event));
      // there are a number of additional jar events generated
      for (VFileDeleteEvent jarDeleteEvent : jarDeleteEvents) {
        outApplyActions.add(() -> applyEvent(jarDeleteEvent));
        outValidatedEvents.add(jarDeleteEvent);
      }

      applyMultipleEvents(publisher, outApplyActions, outValidatedEvents);
    }
  }

  // Tries to find a group of non-conflicting events in range [startIndex..inEvents.size()).
  // Two events are conflicting if the originating file of one event is an ancestor (non-strict) of the file from the other.
  // E.g. "change(a/b/c/x.txt)" and "delete(a/b/c)" are conflicting because "a/b/c/x.txt" is under the "a/b/c" directory from the other event.
  //
  // returns index after the last grouped event.
  private static int groupByPath(@NotNull List<? extends VFileEvent> events,
                                 int startIndex,
                                 @NotNull MostlySingularMultiMap<String, VFileEvent> filesInvolved,
                                 @NotNull Set<? super String> middleDirsInvolved,
                                 @NotNull Set<? super String> deletedPaths,
                                 @NotNull Set<? super String> createdPaths,
                                 @NotNull Set<? super VFileEvent> eventsToRemove) {
    // store all paths from all events (including all parents)
    // check the each new event's path against this set and if it's there, this event is conflicting

    int i;
    for (i = startIndex; i < events.size(); i++) {
      VFileEvent event = events.get(i);
      String path = event.getPath();
      if (event instanceof VFileDeleteEvent && removeNestedDelete(path, deletedPaths)) {
        eventsToRemove.add(event);
        continue;
      }
      if (event instanceof VFileCreateEvent && !createdPaths.add(path)) {
        eventsToRemove.add(event);
        continue;
      }

      if (checkIfConflictingPaths(event, path, filesInvolved, middleDirsInvolved)) {
        break;
      }
      // some synthetic events really are composite events, e.g. VFileMoveEvent = VFileDeleteEvent+VFileCreateEvent,
      // so both paths should be checked for conflicts
      String path2 = getAlternativePath(event);
      if (path2 != null && !FileUtil.PATH_HASHING_STRATEGY.equals(path2, path) && checkIfConflictingPaths(event, path2, filesInvolved, middleDirsInvolved)) {
        break;
      }
    }

    return i;
  }

  @Nullable
  private static String getAlternativePath(@NotNull VFileEvent event) {
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
    return path2;
  }

  private static boolean removeNestedDelete(@NotNull String path, @NotNull Set<? super String> deletedPaths) {
    if (!deletedPaths.add(path)) {
      return true;
    }
    int li = path.length();
    while (true) {
      int liPrev = path.lastIndexOf('/', li - 1);
      if (liPrev == -1) break;
      path = path.substring(0, liPrev);
      li = liPrev;
      if (deletedPaths.contains(path)) {
        return true;
      }
    }

    return false;
  }

  private static boolean checkIfConflictingPaths(@NotNull VFileEvent event,
                                                 @NotNull String path,
                                                 @NotNull MostlySingularMultiMap<String, VFileEvent> files,
                                                 @NotNull Set<? super String> middleDirs) {
    boolean canReconcileEvents = true;
    for (VFileEvent t : files.get(path)) {
      if (!(isContentChangeLikeHarmlessEvent(event) && isContentChangeLikeHarmlessEvent(t))) {
        canReconcileEvents = false;
        break;
      }
    }
    if (!canReconcileEvents) {
      // conflicting event found for (non-strict) descendant, stop
      return true;
    }
    if (middleDirs.contains(path)) {
      // conflicting event found for (non-strict) descendant, stop
      return true;
    }
    files.add(path, event);
    int li = path.length();
    while (true) {
      int liPrev = path.lastIndexOf('/', li-1);
      if (liPrev == -1) break;
      String parentDir = path.substring(0, liPrev);
      if (files.containsKey(parentDir)) {
        // conflicting event found for ancestor, stop
        return true;
      }
      if (!middleDirs.add(parentDir)) break;  // all parents up already stored, stop
      li = liPrev;
    }

    return false;
  }

  private static boolean isContentChangeLikeHarmlessEvent(@NotNull VFileEvent event1) {
    return event1 instanceof VFileContentChangeEvent ||
           event1 instanceof VFilePropertyChangeEvent && (((VFilePropertyChangeEvent)event1).getPropertyName().equals(VirtualFile.PROP_WRITABLE)
                                                          || ((VFilePropertyChangeEvent)event1).getPropertyName().equals(VirtualFile.PROP_ENCODING));
  }

  // finds a group of non-conflicting events, validate them.
  // "outApplyActions" will contain handlers for applying the grouped events
  // "outValidatedEvents" will contain events for which VFileEvent.isValid() is true
  // return index after the last processed event
  private int groupAndValidate(@NotNull List<? extends VFileEvent> events,
                               int startIndex,
                               @NotNull List<? super Runnable> outApplyActions,
                               @NotNull List<? super VFileEvent> outValidatedEvents,
                               @NotNull MostlySingularMultiMap<String, VFileEvent> filesInvolved,
                               @NotNull Set<? super String> middleDirsInvolved) {
    Set<VFileEvent> toIgnore = new ReferenceOpenHashSet<>(); // VFileEvents override equals()
    int endIndex = groupByPath(events, startIndex, filesInvolved, middleDirsInvolved, CollectionFactory.createFilePathSet(),
                               CollectionFactory.createFilePathSet(), toIgnore);
    assert endIndex > startIndex : events.get(startIndex) +"; files: "+filesInvolved+"; middleDirs: "+middleDirsInvolved;
    // since all events in the group events[startIndex..endIndex) are mutually non-conflicting, we can re-arrange creations/deletions together
    groupCreations(events, startIndex, endIndex, outValidatedEvents, outApplyActions, toIgnore);
    groupDeletions(events, startIndex, endIndex, outValidatedEvents, outApplyActions, toIgnore);
    groupOthers(events, startIndex, endIndex, outValidatedEvents, outApplyActions);

    for (int i = startIndex; i < endIndex; i++) {
      VFileEvent event = events.get(i);
      List<VFileDeleteEvent> jarDeleteEvents = VfsImplUtil.getJarInvalidationEvents(event, outApplyActions);
      for (VFileDeleteEvent jarDeleteEvent : jarDeleteEvents) {
        outApplyActions.add((Runnable)() -> applyEvent(jarDeleteEvent));
        outValidatedEvents.add(jarDeleteEvent);
      }
    }

    return endIndex;
  }

  // find all VFileCreateEvent events in [start..end)
  // group them by parent directory, validate in bulk for each directory, and return "applyCreations()" runnable
  private void groupCreations(@NotNull List<? extends VFileEvent> events,
                              int start,
                              int end,
                              @NotNull List<? super VFileEvent> outValidated,
                              @NotNull List<? super Runnable> outApplyActions,
                              @NotNull Set<? extends VFileEvent> toIgnore) {
    MultiMap<VirtualDirectoryImpl, VFileCreateEvent> grouped = null;

    for (int i = start; i < end; i++) {
      VFileEvent e = events.get(i);
      if (!(e instanceof VFileCreateEvent) || toIgnore.contains(e)) continue;
      VFileCreateEvent event = (VFileCreateEvent)e;
      VirtualDirectoryImpl parent = (VirtualDirectoryImpl)event.getParent();
      if (grouped == null) {
        grouped = new MultiMap<>(end - start);
      }
      grouped.putValue(parent, event);
    }
    if (grouped != null) {
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
        MultiMap<VirtualDirectoryImpl, VFileCreateEvent> finalGrouped = grouped;
        outApplyActions.add((Runnable)() -> {
          applyCreations(finalGrouped);
          incStructuralModificationCount();
        });
      }
    }
  }

  // find all VFileDeleteEvent events in [start..end)
  // group them by parent directory (can be null), filter out files which parent dir is to be deleted too, and return "applyDeletions()" runnable
  private void groupDeletions(@NotNull List<? extends VFileEvent> events,
                              int start,
                              int end,
                              @NotNull List<? super VFileEvent> outValidated,
                              @NotNull List<? super Runnable> outApplyActions,
                              @NotNull Set<? extends VFileEvent> toIgnore) {
    MultiMap<VirtualDirectoryImpl, VFileDeleteEvent> grouped = null;
    boolean hasValidEvents = false;
    for (int i = start; i < end; i++) {
      VFileEvent event = events.get(i);
      if (!(event instanceof VFileDeleteEvent) || toIgnore.contains(event) || !event.isValid()) continue;
      VFileDeleteEvent de = (VFileDeleteEvent)event;
      @Nullable VirtualDirectoryImpl parent = (VirtualDirectoryImpl)de.getFile().getParent();
      if (grouped == null) {
        grouped = new MultiMap<>(end - start);
      }
      grouped.putValue(parent, de);
      outValidated.add(event);
      hasValidEvents = true;
    }

    if (hasValidEvents) {
      MultiMap<VirtualDirectoryImpl, VFileDeleteEvent> finalGrouped = grouped;
      outApplyActions.add((Runnable)() -> {
        clearIdCache();
        applyDeletions(finalGrouped);
        incStructuralModificationCount();
      });
    }
  }

  // find events other than VFileCreateEvent or VFileDeleteEvent in [start..end)
  // validate and return "applyEvent()" runnable for each event because it's assumed there won't be too many of them
  private void groupOthers(@NotNull List<? extends VFileEvent> events,
                           int start,
                           int end,
                           @NotNull List<? super VFileEvent> outValidated,
                           @NotNull List<? super Runnable> outApplyActions) {
    for (int i = start; i < end; i++) {
      VFileEvent event = events.get(i);
      if (event instanceof VFileCreateEvent || event instanceof VFileDeleteEvent || !event.isValid()) continue;
      outValidated.add(event);
      outApplyActions.add((Runnable)() -> applyEvent(event));
    }
  }

  private static final int INNER_ARRAYS_THRESHOLD = 1024; // max initial size, to avoid OOM on million-events processing
  @Override
  public void processEvents(@NotNull List<? extends VFileEvent> events) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    int startIndex = 0;
    int cappedInitialSize = Math.min(events.size(), INNER_ARRAYS_THRESHOLD);
    List<Runnable> applyActions = new ArrayList<>(cappedInitialSize);
    MostlySingularMultiMap<String, VFileEvent> files = new MostlySingularMultiMap<>(CollectionFactory.createFilePathMap(cappedInitialSize));
    Set<String> middleDirs = CollectionFactory.createFilePathSet(cappedInitialSize);
    List<VFileEvent> validated = new ArrayList<>(cappedInitialSize);
    BulkFileListener publisher = getPublisher();
    while (startIndex != events.size()) {
      PingProgress.interactWithEdtProgress();

      applyActions.clear();
      files.clear();
      middleDirs.clear();
      validated.clear();
      startIndex = groupAndValidate(events, startIndex, applyActions, validated, files, middleDirs);

      if (!validated.isEmpty()) {
        applyMultipleEvents(publisher, applyActions, validated);
      }
    }
  }

  private static void applyMultipleEvents(@NotNull BulkFileListener publisher,
                                          @NotNull List<? extends Runnable> applyActions,
                                          @NotNull List<? extends VFileEvent> applyEvents) {
    PingProgress.interactWithEdtProgress();
    // do defensive copy to cope with ill-written listeners that save passed list for later processing
    List<VFileEvent> toSend = ContainerUtil.immutableList(applyEvents.toArray(new VFileEvent[0]));
    fireBeforeEvents(publisher, toSend);

    PingProgress.interactWithEdtProgress();
    applyActions.forEach(Runnable::run);

    PingProgress.interactWithEdtProgress();
    fireAfterEvents(publisher, toSend);
  }

  private static void fireBeforeEvents(@NotNull BulkFileListener publisher, @NotNull List<? extends VFileEvent> toSend) {
    publisher.before(toSend);
    ((BulkFileListener)VirtualFilePointerManager.getInstance()).before(toSend);
  }

  private static void fireAfterEvents(@NotNull BulkFileListener publisher, @NotNull List<? extends VFileEvent> toSend) {
    ((BulkFileListener)VirtualFilePointerManager.getInstance()).after(toSend);
    publisher.after(toSend);
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
      List<CharSequence> childrenNamesDeleted = new ArrayList<>(deleteEvents.size());
      IntSet childrenIdsDeleted = new IntOpenHashSet(deleteEvents.size());
      List<ChildInfo> deleted = new ArrayList<>(deleteEvents.size());
      for (VFileDeleteEvent event : deleteEvents) {
        VirtualFile file = event.getFile();
        int id = getFileId(file);
        childrenNamesDeleted.add(file.getNameSequence());
        childrenIdsDeleted.add(id);
        FSRecords.deleteRecordRecursively(id);
        invalidateSubtree(file);
        deleted.add(new ChildInfoImpl(id, ChildInfoImpl.UNKNOWN_ID_YET, null, null, null));
      }
      deleted.sort(ChildInfo.BY_ID);
      FSRecords.update(parentId, oldChildren -> oldChildren.subtract(deleted));
      parent.removeChildren(childrenIdsDeleted, childrenNamesDeleted);
    }
  }

  // add children to specified directories using VirtualDirectoryImpl.createAndAddChildren() optimised for bulk additions
  private void applyCreations(@NotNull MultiMap<VirtualDirectoryImpl, VFileCreateEvent> creations) {
    for (Map.Entry<VirtualDirectoryImpl, Collection<VFileCreateEvent>> entry : creations.entrySet()) {
      VirtualDirectoryImpl parent = entry.getKey();
      Collection<VFileCreateEvent> createEvents = entry.getValue();
      applyCreateEventsInDirectory(parent, createEvents);
    }
  }

  private void applyCreateEventsInDirectory(@NotNull VirtualDirectoryImpl parent,
                                            @NotNull Collection<? extends VFileCreateEvent> createEvents) {
    int parentId = getFileId(parent);
    NewVirtualFile vf = findFileById(parentId);
    if (!(vf instanceof VirtualDirectoryImpl)) return;
    parent = (VirtualDirectoryImpl)vf;  // retain in myIdToDirCache at least for the duration of this block in order to subsequent findFileById() won't crash
    NewVirtualFileSystem delegate = replaceWithNativeFS(getDelegate(parent));

    List<ChildInfo> childrenAdded = new ArrayList<>(createEvents.size());
    for (VFileCreateEvent createEvent : createEvents) {
      createEvent.resetCache();
      String name = createEvent.getChildName();
      Pair<FileAttributes, String> childData = getChildData(delegate, createEvent.getParent(), name, createEvent.getAttributes(), createEvent.getSymlinkTarget());
      if (childData != null) {
        ChildInfo child = makeChildRecord(parentId, name, childData, delegate, createEvent.getChildren());
        childrenAdded.add(child);
      }
    }
    childrenAdded.sort(ChildInfo.BY_ID);
    TObjectHashingStrategy<CharSequence> hashingStrategy = FilePathHashingStrategy.createForCharSequence(delegate.isCaseSensitive());
    FSRecords.update(parentId, oldChildren -> oldChildren.merge(childrenAdded, hashingStrategy));
    parent.createAndAddChildren(childrenAdded, false, (__,___)->{});

    saveScannedChildrenRecursively(createEvents, delegate, hashingStrategy);
  }

  private static void saveScannedChildrenRecursively(@NotNull Collection<? extends VFileCreateEvent> createEvents,
                                                     @NotNull NewVirtualFileSystem delegate,
                                                     @NotNull TObjectHashingStrategy<? super CharSequence> hashingStrategy) {
    for (VFileCreateEvent createEvent : createEvents) {
      ChildInfo[] children = createEvent.getChildren();
      if (children == null || !createEvent.isDirectory()) continue;
      // todo avoid expensive findFile
      VirtualFile createdDir = createEvent.getFile();
      if (createdDir instanceof VirtualDirectoryImpl) {
        Queue<Pair<VirtualDirectoryImpl, ChildInfo[]>> queue = new ArrayDeque<>();
        queue.add(pair((VirtualDirectoryImpl)createdDir, children));
        while (!queue.isEmpty()) {
          Pair<VirtualDirectoryImpl, ChildInfo[]> queued = queue.remove();
          VirtualDirectoryImpl directory = queued.first;
          List<ChildInfo> scannedChildren = Arrays.asList(queued.second);
          int directoryId = directory.getId();
          List<ChildInfo> added = new ArrayList<>(scannedChildren.size());
          for (ChildInfo childInfo : scannedChildren) {
            CharSequence childName = childInfo.getName();
            Pair<FileAttributes, String> childData = getChildData(delegate, directory, childName.toString(), childInfo.getFileAttributes(), childInfo.getSymLinkTarget());
            if (childData != null) {
              ChildInfo newChild = makeChildRecord(directoryId, childName, childData, delegate, childInfo.getChildren());
              added.add(newChild);
            }
          }

          added.sort(ChildInfo.BY_ID);
          FSRecords.update(directoryId, oldChildren -> oldChildren.merge(added, hashingStrategy));
          setChildrenCached(directoryId);
          // set "all children loaded" because the first "fileCreated" listener (looking at you, local history)
          // will call getChildren() anyway, beyond a shadow of a doubt
          directory.createAndAddChildren(added, true, (childCreated, childInfo) -> {
            // enqueue recursive children
            if (childCreated instanceof VirtualDirectoryImpl && childInfo.getChildren() != null) {
              queue.add(pair((VirtualDirectoryImpl)childCreated, childInfo.getChildren()));
            }
          });
        }
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

    String rootUrl = UriUtil.trimTrailingSlashes(VirtualFileManager.constructUrl(fs.getProtocol(), path));
    VirtualFileSystemEntry root = myRoots.get(rootUrl);
    if (root != null) return root;

    CharSequence rootName;
    String rootPath;
    if (fs instanceof ArchiveFileSystem) {
      ArchiveFileSystem afs = (ArchiveFileSystem)fs;
      VirtualFile localFile = afs.findLocalByRootPath(path);
      if (localFile == null) return null;
      rootName = localFile.getNameSequence();
      rootPath = afs.getRootPathByLocal(localFile);
      rootUrl = UriUtil.trimTrailingSlashes(VirtualFileManager.constructUrl(fs.getProtocol(), rootPath));
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

    // avoid creating zillion of roots which are not actual roots
    String parentPath = fs instanceof LocalFileSystem ? PathUtil.getParentPath(rootPath) : "";
    if (!parentPath.isEmpty()) {
      FileAttributes parentAttributes = fs.getAttributes(new StubVirtualFile() {
        @NotNull @Override public String getPath() { return parentPath; }
        @Nullable @Override public VirtualFile getParent() { return null; }
      });
      if (parentAttributes != null) {
        throw new IllegalArgumentException("Must pass FS root path, but got: '" + path + "', which has a parent '" + parentPath + "'." +
                                           " Use NewVirtualFileSystem.extractRootPath() for obtaining root path");
      }
    }

    int rootId = FSRecords.findRootRecord(rootUrl);

    int rootNameId = FileNameCache.storeName(rootName.toString());
    boolean mark;
    VirtualFileSystemEntry newRoot;
    synchronized (myRoots) {
      root = myRoots.get(rootUrl);
      if (root != null) return root;

      try {
        newRoot = new FsRoot(rootId, rootNameId, myVfsData, fs, StringUtil.trimTrailing(rootPath, '/'));
      }
      catch (VfsData.FileAlreadyCreatedException e) {
        for (Map.Entry<String, VirtualFileSystemEntry> entry : myRoots.entrySet()) {
          final VirtualFileSystemEntry existingRoot = entry.getValue();
          if (existingRoot.getId() == rootId) {
            String message = "Duplicate FS roots: " + rootUrl + " / " + entry.getKey() + " id=" + rootId + " valid=" + existingRoot.isValid();
            throw new RuntimeException(message, e);
          }
        }
        throw new RuntimeException("No root duplication, rootName='" + rootName + "'; rootNameId=" + rootNameId + "; rootId=" + rootId + ";" +
                                   " path='" + path + "'; fs=" + fs + "; rootUrl='" + rootUrl + "'", e);
      }
      incStructuralModificationCount();
      mark = writeAttributesToRecord(rootId, 0, rootName, fs, attributes, null) != -1;

      myRoots.put(rootUrl, newRoot);
      myIdToDirCache.put(rootId, newRoot);
    }

    if (!mark && attributes.lastModified != FSRecords.getTimestamp(rootId)) {
      newRoot.markDirtyRecursively();
    }

    LOG.assertTrue(rootId == newRoot.getId(), "root=" + newRoot + " expected=" + rootId + " actual=" + newRoot.getId());

    return newRoot;
  }

  @Override
  public void clearIdCache() {
    // remove all except roots
    myIdToDirCache.entrySet().removeIf(e -> e.getValue().getParent() != null);
  }

  @Override
  @Nullable
  public NewVirtualFile findFileById(int id) {
    VirtualFileSystemEntry cached = myIdToDirCache.get(id);
    return cached != null ? cached : FSRecords.findFileById(id, myIdToDirCache);
  }

  @Override
  public NewVirtualFile findFileByIdIfCached(int id) {
    return myVfsData.hasLoadedFile(id) ? findFileById(id) : null;
  }

  @Override
  public VirtualFile @NotNull [] getRoots() {
    Collection<VirtualFileSystemEntry> roots = myRoots.values();
    return VfsUtilCore.toVirtualFileArray(roots); // ConcurrentHashMap.keySet().toArray(new T[0]) guaranteed to return array with no nulls
  }

  @Override
  public VirtualFile @NotNull [] getRoots(@NotNull NewVirtualFileSystem fs) {
    final List<VirtualFile> roots = new ArrayList<>();

    for (NewVirtualFile root : myRoots.values()) {
      if (root.getFileSystem() == fs) {
        roots.add(root);
      }
    }

    return VfsUtilCore.toVirtualFileArray(roots);
  }

  @Override
  public VirtualFile @NotNull [] getLocalRoots() {
    List<VirtualFile> roots = new SmartList<>();

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
        VFileCreateEvent ce = (VFileCreateEvent)event;
        executeCreateChild(ce.getParent(), ce.getChildName(), ce.getAttributes(), ce.getSymlinkTarget(), ce.isEmptyDirectory());
      }
      else if (event instanceof VFileDeleteEvent) {
        final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
        executeDelete(deleteEvent.getFile());
      }
      else if (event instanceof VFileContentChangeEvent) {
        final VFileContentChangeEvent contentUpdateEvent = (VFileContentChangeEvent)event;
        VirtualFile file = contentUpdateEvent.getFile();
        long length = contentUpdateEvent.getNewLength();
        long timestamp = contentUpdateEvent.getNewTimestamp();

        if (!contentUpdateEvent.isLengthAndTimestampDiffProvided()) {
          final NewVirtualFileSystem delegate = getDelegate(file);
          final FileAttributes attributes = delegate.getAttributes(file);
          length =  attributes != null ? attributes.length : DEFAULT_LENGTH;
          timestamp = attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP;
        }

        executeTouch(file, contentUpdateEvent.isFromRefresh(), contentUpdateEvent.getModificationStamp(), length, timestamp);
      }
      else if (event instanceof VFileCopyEvent) {
        VFileCopyEvent ce = (VFileCopyEvent)event;
        executeCreateChild(ce.getNewParent(), ce.getNewChildName(), null, null, ce.getFile().getChildren().length == 0);
      }
      else if (event instanceof VFileMoveEvent) {
        final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
        executeMove(moveEvent.getFile(), moveEvent.getNewParent());
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent propertyChangeEvent = (VFilePropertyChangeEvent)event;
        VirtualFile file = propertyChangeEvent.getFile();
        Object newValue = propertyChangeEvent.getNewValue();
        switch (propertyChangeEvent.getPropertyName()) {
          case VirtualFile.PROP_NAME:
            executeRename(file, (String)newValue);
            break;
          case VirtualFile.PROP_WRITABLE:
            executeSetWritable(file, ((Boolean)newValue).booleanValue());
            if (LOG.isDebugEnabled()) {
              LOG.debug("File " + file + " writable=" + file.isWritable() + " id=" + getFileId(file));
            }
            break;
          case VirtualFile.PROP_HIDDEN:
            executeSetHidden(file, ((Boolean)newValue).booleanValue());
            break;
          case VirtualFile.PROP_SYMLINK_TARGET:
            executeSetTarget(file, (String)newValue);
            markForContentReloadRecursively(getFileId(file));
            break;
        }
      }
    }
    catch (Exception e) {
      // Exception applying single event should not prevent other events from applying.
      LOG.error(e);
    }
  }

  @Override
  public String toString() {
    return "PersistentFS";
  }

  private void executeCreateChild(@NotNull VirtualFile parent,
                                  @NotNull String name,
                                  @Nullable FileAttributes attributes,
                                  @Nullable String symlinkTarget,
                                  boolean isEmptyDirectory) {
    NewVirtualFileSystem delegate = getDelegate(parent);
    int parentId = getFileId(parent);
    Pair<FileAttributes, String> childData = getChildData(delegate, parent, name, attributes, symlinkTarget);
    if (childData != null) {
      ChildInfo childInfo = makeChildRecord(parentId, name, childData, delegate, null);
      FSRecords.update(parentId, children -> {
        // check that names are not duplicated
        ChildInfo duplicate = findExistingChildInfo(name, children.children, delegate);
        if (duplicate != null) return children;

        return children.insert(childInfo);
      });
      int childId = childInfo.getId();
      assert parent instanceof VirtualDirectoryImpl : parent;
      VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
      VirtualFileSystemEntry child = dir.createChild(name, childId, dir.getFileSystem(), childData.first, isEmptyDirectory);
      if (isEmptyDirectory) {
        // when creating empty directory we need to make sure every file created inside will fire "file created" event
        // in order to virtual file pointer manager get those events to update its pointers properly
        // (because currently VirtualFilePointerManager ignores empty directory creation events for performance reasons)
        setChildrenCached(childId);
      }
      dir.addChild(child);
      incStructuralModificationCount();
    }
  }

  @NotNull
  private static ChildInfo makeChildRecord(int parentId,
                                           @NotNull CharSequence name,
                                           @NotNull Pair<FileAttributes, String> childData,
                                           @NotNull NewVirtualFileSystem fs,
                                           ChildInfo @Nullable [] children) {
    int childId = FSRecords.createRecord();
    int nameId = writeAttributesToRecord(childId, parentId, name, fs, childData.first, childData.second);
    assert childId > 0 : childId;
    return new ChildInfoImpl(childId, nameId, childData.first, children, childData.second);
  }

  // return File attributes, symlink target
  @Nullable // null when file not found
  private static Pair<FileAttributes, String> getChildData(@NotNull NewVirtualFileSystem fs,
                                                           @NotNull VirtualFile parent,
                                                           @NotNull String name,
                                                           @Nullable FileAttributes attributes,
                                                           @Nullable String symlinkTarget) {
    if (attributes == null) {
      FakeVirtualFile virtualFile = new FakeVirtualFile(parent, name);
      attributes = fs.getAttributes(virtualFile);
      symlinkTarget = attributes != null && attributes.isSymLink() ? fs.resolveSymLink(virtualFile) : null;
    }
    return attributes == null ? null : pair(attributes, symlinkTarget);
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
      String rootUrl = UriUtil.trimTrailingSlashes(file.getUrl());
      synchronized (myRoots) {
        myRoots.remove(rootUrl);
        myIdToDirCache.remove(id);
        FSRecords.deleteRootRecord(id);
      }
    }
    else {
      removeIdFromChildren(parentId, id);
      VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file.getParent();
      assert directory != null : file;
      directory.removeChild(file);
    }

    FSRecords.deleteRecordRecursively(id);

    invalidateSubtree(file);
    incStructuralModificationCount();
  }

  private static void invalidateSubtree(@NotNull VirtualFile file) {
    VirtualFileSystemEntry impl = (VirtualFileSystemEntry)file;
    if (file.is(VFileProperty.SYMLINK)) {
      VirtualFileSystem fs = file.getFileSystem();
      if (fs instanceof LocalFileSystemImpl) {
        ((LocalFileSystemImpl)fs).symlinkRemoved(impl.getId());
      }
    }
    impl.invalidate();
    for (VirtualFile child : impl.getCachedChildren()) {
      invalidateSubtree(child);
    }
  }

  private static void removeIdFromChildren(int parentId, int childId) {
    ChildInfo toRemove = new ChildInfoImpl(childId, ChildInfoImpl.UNKNOWN_ID_YET, null, null, null);
    FSRecords.update(parentId, list -> list.remove(toRemove));
  }

  private static void executeRename(@NotNull VirtualFile file, @NotNull String newName) {
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

  private static void executeSetTarget(@NotNull VirtualFile file, @Nullable String target) {
    int id = getFileId(file);
    FSRecords.storeSymlinkTarget(id, target);
    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof LocalFileSystemImpl) {
      ((LocalFileSystemImpl)fs).symlinkUpdated(id, file.getParent(), file.getPath(), target);
    }
  }

  private static void setFlag(@NotNull VirtualFile file, int mask, boolean value) {
    setFlag(getFileId(file), mask, value);
  }

  private static void setFlag(int id, int mask, boolean value) {
    int oldFlags = FSRecords.getFlags(id);
    int flags = value ? oldFlags | mask : oldFlags & ~mask;

    if (oldFlags != flags) {
      FSRecords.setFlags(id, flags, true);
    }
  }

  private static void executeTouch(@NotNull VirtualFile file,
                                   boolean reloadContentFromDelegate,
                                   long newModificationStamp,
                                   long newLength,
                                   long newTimestamp) {
    if (reloadContentFromDelegate) {
      setFlag(file, MUST_RELOAD_CONTENT, true);
    }

    int fileId = getFileId(file);
    FSRecords.setLength(fileId, newLength);
    FSRecords.setTimestamp(fileId, newTimestamp);

    ((VirtualFileSystemEntry)file).setModificationStamp(newModificationStamp);
  }

  private void executeMove(@NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    clearIdCache();

    final int fileId = getFileId(file);
    final int newParentId = getFileId(newParent);
    final int oldParentId = getFileId(file.getParent());

    VirtualFileSystemEntry virtualFileSystemEntry = (VirtualFileSystemEntry)file;
    NewVirtualFileSystem fileSystem = virtualFileSystemEntry.getFileSystem();

    removeIdFromChildren(oldParentId, fileId);
    FSRecords.setParent(fileId, newParentId);
    ChildInfo newChild = new ChildInfoImpl(fileId, virtualFileSystemEntry.getNameId(), null, null, null);
    FSRecords.update(newParentId, children -> {
      // check that names are not duplicated
      ChildInfo duplicate = findExistingChildInfo(file.getName(), children.children, fileSystem);
      if (duplicate != null) return children;
      return children.insert(newChild);
    });
    virtualFileSystemEntry.setParent(newParent);
  }

  @Override
  public String getName(int id) {
    assert id > 0;
    return FSRecords.getName(id);
  }

  @TestOnly
  public static void cleanPersistedContent(int id) {
    doCleanPersistedContent(id);
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
      for (int child : FSRecords.listIds(id)) {
        markForContentReloadRecursively(child);
      }
    }
    else {
      doCleanPersistedContent(id);
    }
  }

  private static void doCleanPersistedContent(int id) {
    setFlag(id, MUST_RELOAD_CONTENT, true);
  }

  @Override
  public boolean mayHaveChildren(int id) {
    return FSRecords.mayHaveChildren(id);
  }

  @TestOnly
  ConcurrentIntObjectMap<VirtualFileSystemEntry> getIdToDirCache() {
    return myIdToDirCache;
  }
}
