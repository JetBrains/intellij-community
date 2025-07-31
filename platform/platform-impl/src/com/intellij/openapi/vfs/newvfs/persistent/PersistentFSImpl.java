// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.*;
import com.intellij.openapi.util.io.FileAttributes.CaseSensitivity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.Utf8BomOptionProvider;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.*;
import com.intellij.openapi.vfs.newvfs.persistent.IPersistentFSRecordsStorage.RecordForRead;
import com.intellij.openapi.vfs.newvfs.persistent.IPersistentFSRecordsStorage.RecordReader;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoveryInfo;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.ReplicatorInputStream;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.intellij.configurationStore.StorageUtilKt.RELOADING_STORAGE_WRITE_REQUESTOR;
import static com.intellij.openapi.vfs.newvfs.events.VFileEvent.REFRESH_REQUESTOR;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;
import static com.intellij.util.containers.CollectionFactory.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressWarnings("NonDefaultConstructor")
@ApiStatus.Internal
public final class PersistentFSImpl extends PersistentFS implements Disposable {

  private static final Logger LOG = Logger.getInstance(PersistentFSImpl.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(30));

  private static final int READ_ACCESS_CHECK_NONE = 0;
  private static final int READ_ACCESS_CHECK_REQUIRE_RA_SOFT = 1;
  private static final int READ_ACCESS_CHECK_REQUIRE_RA_HARD = 2;
  private static final int READ_ACCESS_CHECK_REQUIRE_NO_RA = 3;
  private static final int READ_ACCESS_CHECK_KIND = getIntProperty("vfs.read-access-check-kind", READ_ACCESS_CHECK_NONE);

  private static final boolean LOG_NON_CACHED_ROOTS_LIST = getBooleanProperty("PersistentFSImpl.LOG_NON_CACHED_ROOTS_LIST", false);

  /**
   * Sometimes PFS got request for the files with lost (missed) roots -- i.e. the roots that are absent in persistence.
   * Looking up the roots in persistent storage is quite expensive, so we don't want to repeat the lookup for the
   * same root, if it was already found to be missed.
   * It shouldn't be a frequently called code, so a plain synchronized collection should be enough.
   */
  private final IntSet missedRootIds = IntSets.synchronize(new IntOpenHashSet());

  /** Map[rootUrl -> rootEntry] */
  private final Map<String, VirtualFileSystemEntry> rootsByUrl;

  private final VirtualDirectoryCache dirByIdCache = new VirtualDirectoryCache();

  private final AtomicBoolean connected = new AtomicBoolean(false);
  private volatile FSRecordsImpl vfsPeer = null;

  private final AtomicInteger structureModificationCount = new AtomicInteger();
  private BulkFileListener publisher;
  private volatile VfsData vfsData;

  private final Application app;

  //=========================== statistics:   ======================================================
  private final AtomicLong fileByIdCacheHits = new AtomicLong();
  private final AtomicLong fileByIdCacheMisses = new AtomicLong();

  private final AtomicLong childByName = new AtomicLong();
  /** How many times folder case-sensitivity was read from underlying FS */
  private final AtomicLong caseSensitivityReads = new AtomicLong();

  private final BatchCallback otelMonitoringHandle;


  public PersistentFSImpl(@NotNull Application app) {
    this.app = app;
    rootsByUrl = SystemInfoRt.isFileSystemCaseSensitive
                 ? new ConcurrentHashMap<>(10, 0.4f, JobSchedulerImpl.getCPUCoresCount())
                 : ConcurrentCollectionFactory.createConcurrentMap(10, 0.4f, JobSchedulerImpl.getCPUCoresCount(),
                                                                   HashingStrategy.caseInsensitive());

    AsyncEventSupport.startListening();

    app.getMessageBus().simpleConnect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // idToDirCache could retain alien file systems
        clearIdCache();

        //Remove unregistered file system references: plugin provides no explicit information about FileSystem(s) it is
        // registered, so we scan all the FS roots, and check are they still registered in VirtualFileManager

        var rootsByUrlCopy = new HashMap<>(rootsByUrl);//to prevent concurrent mods while removing roots in loop
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        String requestor = "unloading [" + pluginDescriptor.getPluginId() + "] plugin";
        for (Map.Entry<String, VirtualFileSystemEntry> entry : rootsByUrlCopy.entrySet()) {
          VirtualFileSystemEntry root = entry.getValue();
          String protocol = root.getFileSystem().getProtocol();
          if (virtualFileManager.getFileSystem(protocol) == null) {// the file system likely have been unregistered

            //We don't use root.delete() since we don't want to delete any actual files (FileSystem could even be read-only),
            //we want to delete only the VFS structures behind root's subtree:
            executeDelete(new VFileDeleteEvent(requestor, root));
          }
        }
      }
    });

    connect();

    LowMemoryWatcher.register(this::clearIdCache, this);

    //PersistentFSImpl is an application service, and generally disposed as such, via .dispose(), but to
    // be on the safe side -- we added a shutdown task also.
    //
    //'Eager' vs 'regular' shutdown task priority: there are 2 priorities of shutdown tasks: regular and eager
    // (_Cache_ShutdownTask) -- and eager is executed before others. If an application is shutting down by
    // external signal (i.e. OS demands termination because of reboot), it is worth disposing VFS early, and
    // not waiting for all other services disposed first -- because OS could be impatient, and just kill the
    // app if the termination request not satisfied in 100-200-500ms, and we don't want to leave VFS in inconsistent
    // state because of that. It's absolutely important to shutdown VFS after Indexes eagerly otherwise data might be lost.
    // Services might throw `AlreadyDisposedException`-s after and we have to suppress those exceptions or wrap with PCE-s.
    ShutDownTracker.getInstance().registerCacheShutdownTask(this::disconnect);

    otelMonitoringHandle = setupOTelMonitoring(TelemetryManager.getInstance().getMeter(PlatformScopesKt.VFS));

    LOG.info("VFS.MAX_FILE_LENGTH_TO_CACHE: " + PersistentFSConstants.MAX_FILE_LENGTH_TO_CACHE);
  }

  @ApiStatus.Internal
  public synchronized void connect() {
    LOG.assertTrue(!connected.get());// vfsPeer could be !=null after disconnect
    dirByIdCache.clear();
    vfsData = new VfsData(app, this);
    doConnect();
    PersistentFsConnectionListener.EP_NAME.getExtensionList().forEach(PersistentFsConnectionListener::connectionOpen);
  }

  @ApiStatus.Internal
  public synchronized void disconnect() {
    if (connected.compareAndSet(true, false)) {
      for (PersistentFsConnectionListener listener : PersistentFsConnectionListener.EP_NAME.getExtensionList()) {
        listener.beforeConnectionClosed();
      }

      LOG.info("VFS dispose started");
      long startedAtNs = System.nanoTime();
      try {
        //Give LFSystem a chance to clear caches/stop file watchers:
        //TODO RC: would be much better use PersistentFsConnectionListener or alike instead of direct calling
        //         (PFSImpl shouldn't even explicitly know that LocalFileSystem needs cleaning)
        ((LocalFileSystemImpl)LocalFileSystem.getInstance()).onDisconnecting();
        // TODO make sure we don't have files in memory
        rootsByUrl.clear();
        dirByIdCache.clear();
        missedRootIds.clear();
      }
      finally {
        FSRecordsImpl vfsPeer = this.vfsPeer;
        if (vfsPeer != null && !vfsPeer.isClosed()) {
          vfsPeer.close();
          //better not set this.vfsPeer=null, but leave it as-is: on access instead of just NPE we'll get
          // more understandable AlreadyDisposedException with additional diagnostic info
        }
      }
      LOG.info("VFS dispose completed in " + NANOSECONDS.toMillis(System.nanoTime() - startedAtNs) + " ms.");
    }
  }

  private void doConnect() {
    if (connected.compareAndSet(false, true)) {
      Activity activity = StartUpMeasurer.startActivity("connect FSRecords");
      FSRecordsImpl _vfsPeer = FSRecords.connect();
      vfsPeer = _vfsPeer;
      activity.end();

      VFSRecoveryInfo recoveryInfo = _vfsPeer.connection().recoveryInfo();
      List<VFSInitException> recoveredErrors = recoveryInfo.recoveredErrors;
      if (!recoveredErrors.isEmpty()) {
        //refresh the folders there something was 'recovered':
        refreshSuspiciousDirectories(recoveryInfo.directoriesIdsToRefresh());
      }
    }
  }

  private void refreshSuspiciousDirectories(@NotNull IntList directoryIdsToRefresh) {
    if (!directoryIdsToRefresh.isEmpty()) {
      try {
        List<NewVirtualFile> directoriesToRefresh = directoryIdsToRefresh.intStream()
          .mapToObj(dirId -> {
            try {
              return findFileById(dirId);
            }
            catch (Throwable t) {
              LOG.info("Directory to refresh [#" + dirId + "] can't be resolved", t);
              return null;
            }
          })
          .filter(Objects::nonNull)
          .toList();
        RefreshQueue.getInstance().refresh(false, false, null, directoriesToRefresh);
      }
      catch (Throwable t) {
        LOG.warn("Can't refresh recovered directories: " + directoryIdsToRefresh, t);
      }
    }
  }

  @ApiStatus.Internal
  public boolean isConnected() {
    return connected.get();
  }

  private @NotNull BulkFileListener getPublisher() {
    BulkFileListener publisher = this.publisher;
    if (publisher == null) {
      // the field cannot be initialized in constructor, to ensure that lazy listeners won't be created too early
      publisher = app.getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
      this.publisher = publisher;
    }
    return publisher;
  }

  @Override
  public void dispose() {
    //noinspection IncorrectCancellationExceptionHandling
    try {
      disconnect();
    }
    catch (ProcessCanceledException e) {
      // Application may be closed before `LocalFileSystem` gets initialized()
      //noinspection IncorrectCancellationExceptionHandling
      LOG.warn("Detected cancellation during dispose of PersistentFS. Application was likely closed before VFS got completely initialized",
               e);
    }
    otelMonitoringHandle.close();
  }

  @Override
  public boolean areChildrenLoaded(@NotNull VirtualFile dir) {
    return areChildrenCached(fileId(dir));
  }

  @Override
  public long getCreationTimestamp() {
    return vfsPeer.getCreationTimestamp();
  }

  public @NotNull VirtualFileSystemEntry getOrCacheDir(@NotNull VirtualDirectoryImpl newDir) {
    return dirByIdCache.getOrCacheDir(newDir);
  }

  public VirtualDirectoryImpl getCachedDir(int id) {
    return dirByIdCache.getCachedDir(id);
  }

  @ApiStatus.Internal
  public String getNameByNameId(int nameId) {
    return vfsPeer.getNameByNameId(nameId);
  }

  private static @NotNull NewVirtualFileSystem getFileSystem(@NotNull VirtualFile file) {
    return (NewVirtualFileSystem)file.getFileSystem();
  }

  @Override
  public boolean wereChildrenAccessed(@NotNull VirtualFile dir) {
    checkReadAccess();
    return vfsPeer.wereChildrenAccessed(fileId(dir));
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    checkReadAccess();

    List<? extends ChildInfo> children = listAll(file);
    return ContainerUtil.map2Array(children, String.class, id -> id.getName().toString());
  }

  @Override
  public String @NotNull [] listPersisted(@NotNull VirtualFile parent) {
    checkReadAccess();

    int[] childrenIds = vfsPeer.listIds(fileId(parent));
    String[] names = ArrayUtil.newStringArray(childrenIds.length);
    for (int i = 0; i < childrenIds.length; i++) {
      names[i] = vfsPeer.getName(childrenIds[i]);
    }
    return names;
  }


  @Override
  @ApiStatus.Internal
  public @Unmodifiable @NotNull List<? extends ChildInfo> listAll(@NotNull VirtualFile dir) {
    checkReadAccess();

    int dirId = fileId(dir);
    return areChildrenCached(dirId) ?
           vfsPeer.list(dirId).children :
           persistAllChildren(dir, dirId);
  }

  // return actual children
  private @NotNull List<? extends ChildInfo> persistAllChildren(@NotNull VirtualFile dir, int dirId) {
    NewVirtualFileSystem fs = getFileSystem(dir);
    boolean caseSensitive = dir.isCaseSensitive();
    //MAYBE RC: .list()/.listWithAttributes() use DiskQueryRelay offloading under the hood -- which seems useless
    //          here, because it seems there is no cancellability deep in VFS anyway, and DiskQueryRelay offloading
    //          only occurs an overhead
    String[] childrenNames;
    Map<String, FileAttributes> childrenWithAttributes;
    if (fs instanceof BatchingFileSystem batchingFileSystem) {
      childrenWithAttributes = batchingFileSystem.listWithAttributes(dir);
      childrenNames = VfsUtil.filterNames(ArrayUtil.toStringArray(childrenWithAttributes.keySet()));
    }
    else {
      childrenWithAttributes = null;
      childrenNames = VfsUtil.filterNames(fs.list(dir));
    }

    //MAYBE RC: the .update() below could be re-implemented as read-modify-write, to reduce lock duration.
    //          1. request current directory content in first read-only .update()
    //          2. calculate the difference, request information about new children from FSes (IO) -- outside the lock
    //          3. second .update() modifies directory content speculatively, checking !current.childrenWereChangedSinceLast(vfs)
    //          4. retry if check in (3) fails
    //          ...but with wider adoption of BatchingFileSystem it become less beneficial, since for BatchingFS most IO
    //          (and DiskQueryRelay waiting) is done before the locked region anyway?

    //TODO RC: there are few places in this class .update() is used to update a hierarchy, but there is no consistency
    //         in how those updates are organised: in some cases real FS queries and makeChildRecord() calls are made
    //         _inside_ the .update(), i.e. inside the .update()'s lock -- as it is done here. But in other cases FS
    //         requests and corresponding makeChildRecord() calls are done outside the .update() and it's lock. This
    //         is quite misleading, it makes unclear that is the consistency model of that hierarchy update.
    //         (See also an overall VFS thread-safety rant in FSRecordsImpl)


    ListResult saved = vfsPeer.update(
      dir,
      dirId,
      current -> {
        List<? extends ChildInfo> currentChildren = current.children;
        if (childrenNames.length == 0 && !currentChildren.isEmpty()) {
          return current;
        }
        // preserve current children which match childrenNames (to have stable id)
        // (on case-insensitive systems, replace those from current with case-changes ones from childrenNames preserving the id)
        // add those from childrenNames which are absent from current
        Set<String> childrenNamesToAdd = createFilePathSet(childrenNames, caseSensitive);
        for (ChildInfo currentChild : currentChildren) {
          childrenNamesToAdd.remove(currentChild.getName().toString());
        }
        if (childrenNamesToAdd.isEmpty()) {
          return current;
        }

        List<ChildInfo> childrenToAdd = childrenWithAttributes != null ? //i.e. (fs is BatchingFileSystem)
                                        createNewChildrenRecords(dir, childrenNamesToAdd, childrenWithAttributes, fs) :
                                        createNewChildrenRecords(dir, childrenNamesToAdd, fs);

        // some clients (e.g. RefreshWorker) expect subsequent list() calls to return equal arrays, so we must
        // provide a stable order:
        childrenToAdd.sort(ChildInfo.BY_ID);
        return current.merge(vfsPeer, childrenToAdd, caseSensitive);
      },
      /*setAllChildrenCached: */ true
    );

    return saved.children;
  }

  private @NotNull List<ChildInfo> createNewChildrenRecords(@NotNull VirtualFile dir,
                                                            @NotNull Set<String> childrenNamesToAdd,
                                                            @NotNull NewVirtualFileSystem fs) {
    List<ChildInfo> childrenToAdd = new ArrayList<>(childrenNamesToAdd.size());
    Map<String, ChildInfo> justCreated = createFilePathMap(childrenNamesToAdd.size(), dir.isCaseSensitive());
    int dirId = fileId(dir);
    for (String newChildName : childrenNamesToAdd) {
      Pair<@NotNull FileAttributes, String> childData = getChildData(fs, dir, newChildName, null, null);
      if (childData != null) {
        //TODO RC: we use a map here to prevent duplicates -- but we still add those duplicates to childrenToAdd
        //         -- what's the point?
        ChildInfo newChild = justCreated.computeIfAbsent(
          newChildName,
          _newChildName -> makeChildRecord(dir, dirId, _newChildName, childData, fs, null)
        );
        childrenToAdd.add(newChild);
      }
    }
    return childrenToAdd;
  }

  private @NotNull List<ChildInfo> createNewChildrenRecords(@NotNull VirtualFile dir,
                                                            @NotNull Set<String> childrenNamesToAdd,
                                                            @NotNull Map<String, FileAttributes> childrenWithAttributes,
                                                            @NotNull NewVirtualFileSystem fs) {
    List<ChildInfo> childrenToAdd = new ArrayList<>(childrenNamesToAdd.size());
    Map<String, ChildInfo> justCreated = createFilePathMap(childrenNamesToAdd.size(), dir.isCaseSensitive());
    int dirId = fileId(dir);
    for (String newChildName : childrenNamesToAdd) {
      FileAttributes childAttrs = childrenWithAttributes.get(newChildName);
      String symLinkTarget = childAttrs.isSymLink() ?
                             fs.resolveSymLink(new FakeVirtualFile(dir, newChildName)) :
                             null;

      //inlined getChildData(fs, dir, newChildName, childAttrs, symLinkTarget):
      Pair<FileAttributes, String> childData = new Pair<>(childAttrs, symLinkTarget);

      //TODO RC: we use a map here to prevent duplicates -- but we still add those duplicates to childrenToAdd
      //         -- what's the point?
      //MAYBE RC: duplicates may indicate wrongly-detected dir.caseSensitivity -- so we should consider re-detect it?
      ChildInfo newChild = justCreated.computeIfAbsent(
        newChildName,
        _newChildName -> makeChildRecord(dir, dirId, _newChildName, childData, fs, null)
      );
      childrenToAdd.add(newChild);
    }

    return childrenToAdd;
  }

  private boolean areChildrenCached(int dirId) {
    return BitUtil.isSet(vfsPeer.getFlags(dirId), Flags.CHILDREN_CACHED);
  }

  @Override
  public @Nullable AttributeInputStream readAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att) {
    checkReadAccess();

    return vfsPeer.readAttribute(fileId(file), att);
  }

  @Override
  public @NotNull AttributeOutputStream writeAttribute(@NotNull VirtualFile file,
                                                       @NotNull FileAttribute attribute) {
    //TODO RC: ThreadingAssertions.assertWriteAccess();
    return vfsPeer.writeAttribute(fileId(file), attribute);
  }

  private @NotNull InputStream readContentById(int contentId) {
    return vfsPeer.readContentById(contentId);
  }

  private @NotNull OutputStream writeContent(@NotNull VirtualFile file,
                                             boolean contentOfFixedSize) {
    ThreadingAssertions.assertWriteAccess();
    return vfsPeer.writeContent(fileId(file), contentOfFixedSize);
  }

  @Override
  public int storeUnlinkedContent(byte @NotNull [] bytes) throws ContentTooBigException {
    //TODO RC: ThreadingAssertions.assertWriteAccess() ?
    return vfsPeer.writeContentRecord(new ByteArraySequence(bytes));
  }

  @SuppressWarnings("removal")
  @Override
  public int getModificationCount(@NotNull VirtualFile file) {
    return vfsPeer.getModCount(fileId(file));
  }

  @Override
  public int getStructureModificationCount() {
    return structureModificationCount.get();
  }

  public void incStructuralModificationCount() {
    structureModificationCount.incrementAndGet();
  }

  @TestOnly
  @Override
  public int getFilesystemModificationCount() {
    return vfsPeer.getPersistentModCount();
  }

  /** @return `nameId` > 0 if write was actually done, -1 if write was bypassed */
  private int writeRootFields(int rootId,
                              @NotNull String name,
                              boolean caseSensitive,
                              @NotNull FileAttributes attributes) {
    assert rootId > 0 : rootId;
    //RC: why we reject the changes in those 2 cases -- what is special with loaded children or
    //    same-name?
    //    A guess: we call the method from findRoot() -- always, even if the root already exists in
    //    persistence -- but we don't want to really overwrite root fields every time -- so we skip
    //    the write here by comparing names?
    if (!name.isEmpty()) {
      if (Comparing.equal(name, vfsPeer.getName(rootId), caseSensitive)) {
        // TODO RC: what if name doesn't change -- but root _attributes_ do? Handle this case also
        return -1;
      }
    }
    else {
      if (areChildrenCached(rootId)) {
        return -1; // TODO: hack
      }
    }

    return vfsPeer.updateRecordFields(rootId, FSRecords.NULL_FILE_ID, attributes, name, /* cleanAttributeRef: */ false);
  }


  @Override
  public @Attributes int getFileAttributes(int id) {
    assert id > 0;
    return vfsPeer.getFlags(id);
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    return isDirectory(getFileAttributes(fileId(file)));
  }

  @Override
  public boolean exists(@NotNull VirtualFile fileOrDirectory) {
    return fileOrDirectory.exists();
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    return vfsPeer.getTimestamp(fileId(file));
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long modStamp) throws IOException {
    int id = fileId(file);
    vfsPeer.setTimestamp(id, modStamp);
    getFileSystem(file).setTimeStamp(file, modStamp);
  }

  private static int fileId(@NotNull VirtualFile file) {
    return ((VirtualFileWithId)file).getId();
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    return isSymLink(getFileAttributes(fileId(file)));
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    return vfsPeer.readSymlinkTarget(fileId(file));
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    return !BitUtil.isSet(getFileAttributes(fileId(file)), Flags.IS_READ_ONLY);
  }

  @Override
  public boolean isHidden(@NotNull VirtualFile file) {
    return BitUtil.isSet(getFileAttributes(fileId(file)), Flags.IS_HIDDEN);
  }

  @Override
  public void setWritable(@NotNull VirtualFile file,
                          boolean writableFlag) throws IOException {
    ThreadingAssertions.assertWriteAccess();
    getFileSystem(file).setWritable(file, writableFlag);
    boolean oldWritable = isWritable(file);
    if (oldWritable != writableFlag) {
      processEvent(new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, oldWritable, writableFlag));
    }
  }

  @Override
  @ApiStatus.Internal
  public ChildInfo findChildInfo(@NotNull VirtualFile parent,
                                 @NotNull String childName,
                                 @NotNull NewVirtualFileSystem fs) {
    checkReadAccess();

    int parentId = fileId(parent);
    Ref<ChildInfo> foundChildRef = new Ref<>();

    Function<ListResult, ListResult> convertor = children -> {
      ChildInfo child = findExistingChildInfo(children.children, childName, parent.isCaseSensitive());
      if (child != null) {
        foundChildRef.set(child);
        return children;
      }

      //MAYBE RC: why do we access FS on lookup? maybe it is better to look only VFS (i.e. snapshot), and issue
      //          refresh request if children is not loaded -- and rely on automatic refresh to update VFS if
      //          actual FS children are changed?
      //          This way here we'll have read-only scan without concurrent modification problems
      //          I.e. the whole code below is (seems to be) just a 'small local refresh' -- executed during
      //          children lookup, under the VFS lock.
      //          I really want to remove it entirely, and just rely on automatic/explicit refresh, but seems like
      //          there is a lot to do to implement this: i.e. an attempt to skip this 'local refresh' fails tests
      Pair<@NotNull FileAttributes, String> childData = getChildData(fs, parent, childName, null, null);
      if (childData == null) {
        return children;
      }
      //[childData != null]
      // => file DOES exist
      // => We haven't found it yet because either look up the wrong name
      //    or it is the new file, and VFS hasn't received the update yet

      String canonicalName;
      if (parent.isCaseSensitive()) {
        canonicalName = childName;
      }
      else {
        canonicalName = fs.getCanonicallyCasedName(new FakeVirtualFile(parent, childName));
        if (Strings.isEmptyOrSpaces(canonicalName)) {
          return children;
        }

        if (!childName.equals(canonicalName)) {
          child = findExistingChildInfo(children.children, canonicalName, /*caseSensitive: */ false);
        }
      }

      if (child == null) {
        child = makeChildRecord(parent, parentId, canonicalName, childData, fs, null);
        foundChildRef.set(child);
        return children.insert(child);
      }
      else {
        foundChildRef.set(child);
        return children;
      }
    };

    vfsPeer.update(parent, parentId, convertor, /*setAllChildrenCached: */ false);
    return foundChildRef.get();
  }

  /**
   * @param caseSensitive is containing folder case-sensitive?
   * @return child from children list, with a given childName, with case sensitivity given by parent
   */
  private ChildInfo findExistingChildInfo(@NotNull List<? extends ChildInfo> children,
                                          @NotNull String childName,
                                          boolean caseSensitive) {
    if (children.isEmpty()) {
      return null;
    }

    // fast path: lookup child by nameId, which is equivalent to case-sensitive name comparison:
    FSRecordsImpl vfs = vfsPeer;
    int nameId = vfs.getNameId(childName);
    for (ChildInfo info : children) {
      if (nameId == info.getNameId()) {
        return info;
      }
    }

    //if parent is !case-sensitive -- repeat lookup, now by actual name, with case-insensitive comparison:
    if (!caseSensitive) {
      for (ChildInfo info : children) {
        if (Comparing.equal(childName, vfs.getNameByNameId(info.getNameId()),  /* caseSensitive: */ false)) {
          return info;
        }
      }
    }
    return null;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    int fileId = fileId(file);

    //speculate: read under read-lock assuming length is already cached:
    long length = vfsPeer.readRecordFields(fileId, record -> {
      int flags = record.getFlags();
      boolean mustReloadLength = BitUtil.isSet(flags, Flags.MUST_RELOAD_LENGTH);
      long cachedLength = record.getLength();
      if (!mustReloadLength && cachedLength >= 0) {
        return cachedLength;
      }
      return -1L;
    });
    if (length >= 0) {
      return length;
    }

    //speculation failed: re-read, and update cache:
    NewVirtualFileSystem fileSystem = getFileSystem(file);
    //1) do IO outside lock 2) some FileSystems (e.g. ArchiveFileSystem) .getLength() impl can call other VirtualFile.getLength(),
    // which creates a possibility for deadlock, if lock segments happen to be the same. The downside is that we call
    // getLength() even if the length is already set by racing thread -- but that should be a rare case, so ignore it
    long actualLength = fileSystem.getLength(file);

    long[] lengthRef = new long[1];
    vfsPeer.updateRecordFields(fileId, record -> {
      int flags = record.getFlags();
      boolean mustReloadLength = BitUtil.isSet(flags, Flags.MUST_RELOAD_LENGTH);
      long cachedLength = record.getLength();
      if (!mustReloadLength && cachedLength >= 0) {
        lengthRef[0] = cachedLength;
        return false;
      }

      record.setLength(actualLength);
      record.removeFlags(Flags.MUST_RELOAD_LENGTH);

      lengthRef[0] = actualLength;
      return true;
    });
    return lengthRef[0];
  }

  @Override
  public long getLastRecordedLength(@NotNull VirtualFile file) {
    int id = fileId(file);
    return vfsPeer.getLength(id);
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor,
                                       @NotNull VirtualFile file,
                                       @NotNull VirtualFile parent,
                                       @NotNull String name) throws IOException {
    ThreadingAssertions.assertWriteAccess();

    getFileSystem(file).copyFile(requestor, file, parent, name);
    processEvent(new VFileCopyEvent(requestor, file, parent, name));

    VirtualFile child = parent.findChild(name);
    if (child == null) {
      throw new IOException("Cannot create child");
    }
    return child;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor,
                                                   @NotNull VirtualFile parent,
                                                   @NotNull String childDirectoryName) throws IOException {
    ThreadingAssertions.assertWriteAccess();

    getFileSystem(parent).createChildDirectory(requestor, parent, childDirectoryName);

    processEvent(new VFileCreateEvent(requestor, parent, childDirectoryName, true, null, null, ChildInfo.EMPTY_ARRAY));
    VFileEvent caseSensitivityEvent = determineCaseSensitivityAndPrepareUpdate(parent, childDirectoryName);
    if (caseSensitivityEvent != null) {
      processEvent(caseSensitivityEvent);
    }

    VirtualFile child = parent.findChild(childDirectoryName);
    if (child == null) {
      throw new IOException("Cannot create child directory '" + childDirectoryName + "' at " + parent.getPath());
    }
    return child;
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor,
                                              @NotNull VirtualFile parent,
                                              @NotNull String childName) throws IOException {
    ThreadingAssertions.assertWriteAccess();

    getFileSystem(parent).createChildFile(requestor, parent, childName);

    processEvent(new VFileCreateEvent(requestor, parent, childName, false, null, null, null));
    VFileEvent caseSensitivityEvent = determineCaseSensitivityAndPrepareUpdate(parent, childName);
    if (caseSensitivityEvent != null) {
      processEvent(caseSensitivityEvent);
    }

    VirtualFile child = parent.findChild(childName);
    if (child == null) {
      throw new IOException("Cannot create child file '" + childName + "' at " + parent.getPath());
    }
    if (child.getCharset().equals(StandardCharsets.UTF_8) &&
        !(child.getFileType() instanceof InternalFileType) &&
        isUtf8BomRequired(child)) {
      child.setBOM(CharsetToolkit.UTF8_BOM);
    }
    return child;
  }

  private static boolean isUtf8BomRequired(@NotNull VirtualFile file) {
    for (Utf8BomOptionProvider encodingProvider : Utf8BomOptionProvider.EP_NAME.getIterable()) {
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
    ThreadingAssertions.assertWriteAccess();

    NewVirtualFileSystem fs = getFileSystem(file);
    fs.deleteFile(requestor, file);
    if (!fs.exists(file)) {
      processEvent(new VFileDeleteEvent(requestor, file));
    }
  }

  @Override
  public void renameFile(Object requestor,
                         @NotNull VirtualFile file,
                         @NotNull String newName) throws IOException {
    ThreadingAssertions.assertWriteAccess();

    getFileSystem(file).renameFile(requestor, file, newName);
    String oldName = file.getName();
    if (!newName.equals(oldName)) {
      processEvent(new VFilePropertyChangeEvent(requestor, file, VirtualFile.PROP_NAME, oldName, newName));
    }
  }

  /** {@inheritDoc} */
  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    // We _should_ cache every local file's content, because the local history feature and Perforce offline mode depend on the cache
    // But caching of readOnly (which 99% means 'archived') file content is useless
    boolean cacheContent = !getFileSystem(file).isReadOnly();
    return contentsToByteArray(file, cacheContent);
  }

  /** {@inheritDoc} */
  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file, boolean mayCacheContent) throws IOException {
    checkReadAccess();

    int fileId = fileId(file);

    LengthAndContentIdReader reader = vfsPeer.readRecordFields(fileId, new LengthAndContentIdReader());
    long length = reader.length;
    int contentRecordId = reader.contentRecordId;


    if (contentRecordId <= 0) {
      NewVirtualFileSystem fs = getFileSystem(file);

      byte[] content = fs.contentsToByteArray(file);

      if (mayCacheContent && shouldCacheFileContentInVFS(content.length)) {
        updateContentForFile(fileId, new ByteArraySequence(content));
      }
      else {
        //just actualise the length:
        vfsPeer.updateRecordFields(fileId, record -> {
          record.setLength(content.length);
          int oldFlags = record.getFlags();
          int flags = oldFlags & ~Flags.MUST_RELOAD_LENGTH;

          if (oldFlags != flags) {
            record.setFlags(flags);
          }
          return true;
        });
      }

      return content;
    }

    //VFS content storage is append-only, hence doesn't need lock for reading:
    try (InputStream contentStream = vfsPeer.readContentById(contentRecordId)) {
      assert length >= 0 : file;
      return contentStream.readNBytes((int)length);
    }
    catch (IOException e) {
      throw vfsPeer.handleError(e);
    }
  }

  private void updateContentId(int fileId,
                               int newContentRecordId,
                               int newContentLength) {
    vfsPeer.updateRecordFields(fileId, record -> {
      //MAYBE RC: should we keep MUST_RELOAD_CONTENT if newContentRecordId == 0?
      record.removeFlags(Flags.MUST_RELOAD_LENGTH | Flags.MUST_RELOAD_CONTENT);
      record.setContentRecordId(newContentRecordId);
      record.setLength(newContentLength);
      return true;
    });
  }

  @Override
  public byte @NotNull [] contentsToByteArray(int contentId) throws IOException {
    //noinspection resource
    return readContentById(contentId).readAllBytes();
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    checkReadAccess();

    int fileId = fileId(file);
    NewVirtualFileSystem fs = getFileSystem(file);

    final class Result {
      private long actualFileLength;
      private boolean mustReloadContent;
      private int contentRecordId;
    }

    Result result = new Result();

    vfsPeer.updateRecordFields(fileId, record -> {
      long vfsStoredLength = record.getLength();
      int contentRecordId = record.getContentRecordId();
      int flags = record.getFlags();
      boolean mustReloadLength = BitUtil.isSet(flags, Flags.MUST_RELOAD_LENGTH);
      boolean mustReloadContent = BitUtil.isSet(flags, Flags.MUST_RELOAD_CONTENT);
      boolean lengthIsInvalid = mustReloadLength || (vfsStoredLength == -1);

      result.mustReloadContent = mustReloadContent;
      result.contentRecordId = contentRecordId;
      result.actualFileLength = vfsStoredLength;

      if (lengthIsInvalid) {
        //TODO RC: this branch is the only reason for update() instead of read() -- maybe it is better to upgrade the lock
        //         only when we fall here, instead of acquire write lock from the start? (StampedLock allows upgrades)
        //TODO RC: it is not a good idea to request length from actual FS (IO) being under write lock -- but that else could
        //         we do here? we need exclusive lock to prevent other threads from updating
        result.actualFileLength = fs.getLength(file);
        record.setLength(result.actualFileLength);
        record.removeFlags(Flags.MUST_RELOAD_LENGTH);
        return true;
      }

      return false;
    });

    InputStream contentStream;
    if (result.contentRecordId <= 0 || result.mustReloadContent) {
      InputStream fileStream = fs.getInputStream(file);
      if (shouldCacheFileContentInVFS(result.actualFileLength)) {
        contentStream = createReplicatorAndStoreContent(file, fileStream, result.actualFileLength);
      }
      else {
        contentStream = fileStream;
      }
    }
    else {
      contentStream = vfsPeer.readContentById(result.contentRecordId);
    }

    return contentStream;
  }

  private static boolean shouldCacheFileContentInVFS(long fileLength) {
    return fileLength <= PersistentFSConstants.MAX_FILE_LENGTH_TO_CACHE;
  }

  private @NotNull InputStream createReplicatorAndStoreContent(@NotNull VirtualFile file,
                                                               @NotNull InputStream nativeStream,
                                                               long fileLength) throws IOException {
    if (nativeStream instanceof BufferExposingByteArrayInputStream byteStream) {
      // optimization
      byte[] bytes = byteStream.getInternalBuffer();
      storeContentToStorage(fileLength, file, bytes, bytes.length);
      return nativeStream;
    }
    BufferExposingByteArrayOutputStream cache = new BufferExposingByteArrayOutputStream((int)fileLength);
    return new ReplicatorInputStream(nativeStream, cache) {
      boolean isClosed;

      @Override
      public void close() throws IOException {
        if (!isClosed) {
          try {
            boolean isEndOfFileReached;
            try {
              isEndOfFileReached = available() < 0 || read() == -1;
            }
            catch (IOException ignored) {
              isEndOfFileReached = false;
            }
            super.close();
            if (isEndOfFileReached) {
              storeContentToStorage(fileLength, file, cache.getInternalBuffer(), cache.size());
            }
          }
          finally {
            isClosed = true;
          }
        }
      }
    };
  }

  private void storeContentToStorage(long fileLength,
                                     @NotNull VirtualFile file,
                                     byte @NotNull [] bytes,
                                     int byteLength) throws IOException, ContentTooBigException {
    int fileId = fileId(file);

    if (byteLength == fileLength) {
      ByteArraySequence newContent = new ByteArraySequence(bytes, 0, byteLength);
      updateContentForFile(fileId, newContent);
    }
    else {
      doCleanPersistedContent(fileId);
    }
  }

  private void updateContentForFile(int fileId,
                                    @NotNull ByteArraySequence newContent) throws IOException, ContentTooBigException {
    //VFS content storage is append-only, hence storing could be done outside the lock:
    int newContentId;
    try {
      newContentId = vfsPeer.writeContentRecord(newContent);
    }
    catch (ContentTooBigException e) {
      LOG.warn("file[" + fileId + "]: content[" + newContent.length() + "b uncompressed] is too big -- don't store it in VFS", e);
      newContentId = 0;
    }

    updateContentId(fileId, newContentId, newContent.length());
  }

  /** Method is obsolete, migrate to {@link #contentHashIfStored(VirtualFile)} instance method */
  @TestOnly
  @ApiStatus.Obsolete
  public static byte @Nullable [] getContentHashIfStored(@NotNull VirtualFile file) {
    return FSRecords.getInstance().getContentHash(fileId(file));
  }

  @TestOnly
  public byte @Nullable [] contentHashIfStored(@NotNull VirtualFile file) {
    return FSRecords.getInstance().getContentHash(fileId(file));
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) {
    return new ByteArrayOutputStream() {
      private boolean closed; // protection against user calling .close() twice

      @Override
      public void close() throws IOException {
        if (closed) return;
        super.close();

        ThreadingAssertions.assertWriteAccess();

        long oldLength = getLastRecordedLength(file);
        VFileContentChangeEvent event = new VFileContentChangeEvent(
          requestor, file, file.getModificationStamp(), modStamp, file.getTimeStamp(), -1, oldLength, count
        );
        List<VFileEvent> events = List.of(event);
        fireBeforeEvents(getPublisher(), events);

        NewVirtualFileSystem fs = getFileSystem(file);
        try {
          if (shouldCacheFileContentInVFS(count)) {
            // `FSRecords.ContentOutputStream` is already buffered => no need to wrap in `BufferedStream`
            try (OutputStream persistenceStream = writeContent(file, /*contentOfFixedSize: */ fs.isReadOnly())) {
              persistenceStream.write(buf, 0, count);
            }
          }
          else {
            cleanPersistedContent(fileId(file));//so next turn content will be loaded from FS again
          }
        }
        finally {
          writeToDisk(fs, event, events);
        }
      }

      private void writeToDisk(@NotNull NewVirtualFileSystem fs,
                               @NotNull VFileContentChangeEvent event,
                               @NotNull List<VFileEvent> events) throws IOException {
        try (OutputStream ioFileStream = fs.getOutputStream(file, requestor, modStamp, timeStamp)) {
          ioFileStream.write(buf, 0, count);
        }
        finally {
          closed = true;
          FileAttributes attributes = fs.getAttributes(file);
          // due to FS rounding, the timestamp of the file can significantly differ from the current time
          long newTimestamp = attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP;
          long newLength = attributes != null ? attributes.length : DEFAULT_LENGTH;
          executeTouch(file, false, event.getModificationStamp(), newLength, newTimestamp);
          fireAfterEvents(getPublisher(), events);
        }
      }
    };
  }

  @Override
  public int acquireContent(@NotNull VirtualFile file) {
    return vfsPeer.acquireFileContent(fileId(file));
  }

  @Override
  public void releaseContent(int contentId) {
    vfsPeer.releaseContent(contentId);
  }

  @Override
  public int getCurrentContentId(@NotNull VirtualFile file) {
    return vfsPeer.getContentRecordId(fileId(file));
  }

  @ApiStatus.Internal
  public boolean isOwnData(@NotNull VfsData data) {
    return data == vfsData;
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    getFileSystem(file).moveFile(requestor, file, newParent);
    processEvent(new VFileMoveEvent(requestor, file, newParent));
  }

  private void processEvent(@NotNull VFileEvent event) {
    ThreadingAssertions.assertWriteAccess();

    if (!event.isValid()) return;

    List<VFileEvent> outValidatedEvents = new ArrayList<>();
    outValidatedEvents.add(event);
    List<Runnable> outApplyActions = new ArrayList<>();
    List<VFileEvent> jarDeleteEvents = VfsImplUtil.getJarInvalidationEvents(event, outApplyActions);
    BulkFileListener publisher = getPublisher();
    if (jarDeleteEvents.isEmpty() && outApplyActions.isEmpty()) {
      // optimisation: skip all groupings
      runSuppressing(
        () -> fireBeforeEvents(publisher, outValidatedEvents),
        () -> applyEvent(event),
        () -> fireAfterEvents(publisher, outValidatedEvents)
      );
    }
    else {
      outApplyActions.add(() -> applyEvent(event));
      // there are a number of additional jar events generated
      for (VFileEvent jarDeleteEvent : jarDeleteEvents) {
        outApplyActions.add(() -> applyEvent(jarDeleteEvent));
        outValidatedEvents.add(jarDeleteEvent);
      }
      applyMultipleEvents(publisher, outApplyActions, outValidatedEvents, false);
    }
  }

  private static void runSuppressing(Runnable r1, Runnable r2, Runnable r3) {
    Throwable t = null;
    try {
      r1.run();
    }
    catch (Throwable e) {
      t = Suppressions.addSuppressed(t, e);
    }
    try {
      r2.run();
    }
    catch (Throwable e) {
      t = Suppressions.addSuppressed(t, e);
    }
    try {
      r3.run();
    }
    catch (Throwable e) {
      t = Suppressions.addSuppressed(t, e);
    }
    if (t != null) {
      ExceptionUtilRt.rethrowUnchecked(t);
    }
  }

  // Tries to find a group of non-conflicting events in range [`startIndex`..`inEvents.size()`).
  // Two events are conflicting if the originating file of one event is an ancestor (non-strict) of the file from the other.
  // E.g., "change(a/b/c/x.txt)" and "delete(a/b/c)" are conflicting because "a/b/c/x.txt" is under the "a/b/c" directory from the other event.
  //
  // returns index after the last grouped event.
  private static int groupByPath(@NotNull List<CompoundVFileEvent> events,
                                 int startIndex,
                                 @NotNull MostlySingularMultiMap<String, VFileEvent> filesInvolved,
                                 @NotNull Set<? super String> middleDirsInvolved,
                                 @NotNull Set<? super VirtualFile> deleted,
                                 @NotNull Map<VirtualDirectoryImpl, Object> toCreate,
                                 // dir -> VFileCreateEvent|Collection<VFileCreateEvent> in this dir
                                 @NotNull Set<? super VFileEvent> eventsToRemove) {
    // storing all paths from all events (including all parents),
    // checking each new event's path against this set, and if it's there, this event is conflicting
    int i;
    for (i = startIndex; i < events.size(); i++) {
      VFileEvent event = events.get(i).getFileEvent();
      String path = event.getPath();
      if (event instanceof VFileDeleteEvent && removeNestedDelete(((VFileDeleteEvent)event).getFile(), deleted)) {
        eventsToRemove.add(event);
        continue;
      }
      if (event instanceof VFileCreateEvent createEvent) {
        VirtualDirectoryImpl parent = (VirtualDirectoryImpl)createEvent.getParent();
        Object createEvents = toCreate.get(parent);
        if (createEvents == null) {
          toCreate.put(parent, createEvent);
        }
        else {
          if (createEvents instanceof VFileCreateEvent prevEvent) {
            Set<VFileCreateEvent> children;
            children = parent.isCaseSensitive() ? new LinkedHashSet<>() : new ObjectLinkedOpenCustomHashSet<>(CASE_INSENSITIVE_STRATEGY);
            children.add(prevEvent);
            toCreate.put(parent, children);
            createEvents = children;
          }
          //noinspection unchecked
          Collection<VFileCreateEvent> children = (Collection<VFileCreateEvent>)createEvents;
          if (!children.add(createEvent)) {
            eventsToRemove.add(createEvent);
            continue;
          }
        }
      }

      if (eventConflictsWithPrevious(event, path, filesInvolved, middleDirsInvolved)) {
        break;
      }
      // Some events are composite (e.g. `VFileMoveEvent` = `VFileDeleteEvent` + `VFileCreateEvent`),
      // so both paths should be checked for conflicts.
      String path2 = getAlternativePath(event);
      if (path2 != null &&
          !(SystemInfoRt.isFileSystemCaseSensitive ? path2.equals(path) : path2.equalsIgnoreCase(path)) &&
          eventConflictsWithPrevious(event, path2, filesInvolved, middleDirsInvolved)) {
        break;
      }
    }

    return i;
  }

  private static String getAlternativePath(@NotNull VFileEvent event) {
    if (event instanceof VFilePropertyChangeEvent pce
        && pce.getPropertyName().equals(VirtualFile.PROP_NAME)) {
      VirtualFile parent = pce.getFile().getParent();
      String newName = (String)pce.getNewValue();
      return parent == null ? newName : parent.getPath() + "/" + newName;
    }
    if (event instanceof VFileCopyEvent) {
      return ((VFileCopyEvent)event).getFile().getPath();
    }
    if (event instanceof VFileMoveEvent vme) {
      String newName = vme.getFile().getName();
      return vme.getNewParent().getPath() + "/" + newName;
    }
    return null;
  }

  // return true if the file or the ancestor of the file is going to be deleted
  private static boolean removeNestedDelete(@NotNull VirtualFile file, @NotNull Set<? super VirtualFile> deleted) {
    if (!deleted.add(file)) {
      return true;
    }
    while (true) {
      file = file.getParent();
      if (file == null) break;
      if (deleted.contains(file)) {
        return true;
      }
    }
    return false;
  }

  private static boolean eventConflictsWithPrevious(@NotNull VFileEvent event,
                                                    @NotNull String path,
                                                    @NotNull MostlySingularMultiMap<String, VFileEvent> files,
                                                    @NotNull Set<? super String> middleDirs) {
    boolean canReconcileEvents = true;
    for (VFileEvent prev : files.get(path)) {
      if (!(isContentChangeLikeHarmlessEvent(event) && isContentChangeLikeHarmlessEvent(prev))) {
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
      int liPrev = path.lastIndexOf('/', li - 1);
      if (liPrev == -1) break;
      String parentDir = path.substring(0, liPrev);
      if (files.containsKey(parentDir)) {
        // conflicting event found for the ancestor, stop
        return true;
      }
      if (!middleDirs.add(parentDir)) break;  // all parents are already stored; stop
      li = liPrev;
    }

    return false;
  }

  private static boolean isContentChangeLikeHarmlessEvent(@NotNull VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) return true;
    if (event instanceof VFilePropertyChangeEvent) {
      String p = ((VFilePropertyChangeEvent)event).getPropertyName();
      return p.equals(VirtualFile.PROP_WRITABLE) ||
             p.equals(VirtualFile.PROP_ENCODING) ||
             p.equals(VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY);
    }
    return false;
  }

  // Finds a group of non-conflicting events and validates them.
  // The `outApplyActions` will contain handlers for applying grouped events.
  // The `outValidatedEvents` will contain events for which `VFileEvent#isValid` is true.
  //
  // return index after the last processed event
  private int groupAndValidate(@NotNull List<CompoundVFileEvent> events,
                               int startIndex,
                               @NotNull List<? super Runnable> outApplyActions,
                               @NotNull List<? super VFileEvent> outValidatedEvents,
                               @NotNull MostlySingularMultiMap<String, VFileEvent> filesInvolved,
                               @NotNull Set<? super String> middleDirsInvolved,
                               @NotNull Map<VirtualDirectoryImpl, Object> toCreate,
                               @NotNull Set<VFileEvent> toIgnore,
                               @NotNull Set<? super VirtualFile> toDelete,
                               boolean excludeAsyncListeners) {
    int endIndex = groupByPath(events, startIndex, filesInvolved, middleDirsInvolved, toDelete, toCreate, toIgnore);
    assert endIndex > startIndex : events.get(startIndex) + "; files: " + filesInvolved + "; middleDirs: " + middleDirsInvolved;
    // since all events in the group events[`startIndex`..`endIndex`) are mutually non-conflicting, we can re-arrange creations/deletions together
    groupCreations(outValidatedEvents, outApplyActions, toCreate);
    groupDeletions(events, startIndex, endIndex, outValidatedEvents, outApplyActions, toIgnore);
    groupOthers(events, startIndex, endIndex, outValidatedEvents, outApplyActions);

    for (int i = startIndex; i < endIndex; i++) {
      CompoundVFileEvent event = events.get(i);

      outApplyActions.addAll(event.getApplyActions());

      if (excludeAsyncListeners && !event.areInducedEventsCalculated()) {
        LOG.error("Nested file events must be processed by async file listeners! Event: " + event);
      }

      for (VFileEvent jarDeleteEvent : event.getInducedEvents()) {
        outApplyActions.add((Runnable)() -> applyEvent(jarDeleteEvent));
        outValidatedEvents.add(jarDeleteEvent);
      }
    }
    return endIndex;
  }

  // Finds all `VFileCreateEvent` instances in [`start`..`end`), groups them by parent directory, validates in bulk for each directory,
  // and returns `applyCreations()` runnable
  private void groupCreations(@NotNull List<? super VFileEvent> outValidated,
                              @NotNull List<? super Runnable> outApplyActions,
                              @NotNull Map<VirtualDirectoryImpl, Object> created) {
    if (!created.isEmpty()) {
      // since the VCreateEvent.isValid() is extremely expensive, combine all creation events for the directory
      // and use VirtualDirectoryImpl.validateChildrenToCreate() optimised for bulk validation
      boolean hasValidEvents = false;
      for (Map.Entry<VirtualDirectoryImpl, Object> entry : created.entrySet()) {
        VirtualDirectoryImpl directory = entry.getKey();
        Object value = entry.getValue();
        //noinspection unchecked
        Set<VFileCreateEvent> createEvents =
          value instanceof VFileCreateEvent ? new HashSet<>(List.of((VFileCreateEvent)value)) : (Set<VFileCreateEvent>)value;
        directory.validateChildrenToCreate(createEvents);
        hasValidEvents |= !createEvents.isEmpty();
        outValidated.addAll(createEvents);
        entry.setValue(createEvents);
      }

      if (hasValidEvents) {
        //noinspection unchecked,rawtypes
        Map<VirtualDirectoryImpl, Set<VFileCreateEvent>> finalGrouped = (Map)created;
        outApplyActions.add((Runnable)() -> {
          applyCreations(finalGrouped);
          incStructuralModificationCount();
        });
      }
    }
  }

  // Finds all `VFileDeleteEvent` instances in [`start`..`end`), groups them by parent directory (can be null),
  // filters out files which parent dir is to be deleted too, and returns `applyDeletions()` runnable.
  private void groupDeletions(@NotNull List<CompoundVFileEvent> events,
                              int start,
                              int end,
                              @NotNull List<? super VFileEvent> outValidated,
                              @NotNull List<? super Runnable> outApplyActions,
                              @NotNull Set<? extends VFileEvent> toIgnore) {
    MultiMap<VirtualDirectoryImpl, VFileDeleteEvent> grouped = null;
    boolean hasValidEvents = false;
    for (int i = start; i < end; i++) {
      VFileEvent event = events.get(i).getFileEvent();
      if (!(event instanceof VFileDeleteEvent de) || toIgnore.contains(event) || !event.isValid()) continue;
      VirtualDirectoryImpl parent = (VirtualDirectoryImpl)de.getFile().getParent();
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

  // Finds events other than `VFileCreateEvent` or `VFileDeleteEvent` in [`start`..`end`), validates,
  // and returns `applyEvent()` runnable for each event because it's assumed there won't be too many of them.
  private void groupOthers(@NotNull List<CompoundVFileEvent> events,
                           int start,
                           int end,
                           @NotNull List<? super VFileEvent> outValidated,
                           @NotNull List<? super Runnable> outApplyActions) {
    for (int i = start; i < end; i++) {
      VFileEvent event = events.get(i).getFileEvent();
      if (event instanceof VFileCreateEvent || event instanceof VFileDeleteEvent || !event.isValid()) continue;
      outValidated.add(event);
      outApplyActions.add((Runnable)() -> applyEvent(event));
    }
  }

  /** Limit initial size, to avoid OOM on million-events processing */
  private static final int INNER_ARRAYS_THRESHOLD = 4096;

  @ApiStatus.Internal
  public void processEventsImpl(@NotNull List<CompoundVFileEvent> events, boolean excludeAsyncListeners) {
    ThreadingAssertions.assertWriteAccess();

    int startIndex = 0;
    int cappedInitialSize = Math.min(events.size(), INNER_ARRAYS_THRESHOLD);
    List<Runnable> applyActions = new ArrayList<>(cappedInitialSize);
    // even in the unlikely case when case-insensitive maps falsely detect conflicts of case-sensitive paths,
    // the worst outcome will be one extra event batch, which is acceptable
    MostlySingularMultiMap<String, VFileEvent> files = new MostlySingularMultiMap<>(createFilePathMap(cappedInitialSize));
    Set<String> middleDirs = createFilePathSet(cappedInitialSize);

    List<VFileEvent> validated = new ArrayList<>(cappedInitialSize);
    BulkFileListener publisher = getPublisher();
    Map<VirtualDirectoryImpl, Object> toCreate = new LinkedHashMap<>();
    Set<VFileEvent> toIgnore = new ReferenceOpenHashSet<>(); // VFileEvent overrides equals(), hence identity-based
    Set<VirtualFile> toDelete = createSmallMemoryFootprintSet();
    while (startIndex != events.size()) {
      PingProgress.interactWithEdtProgress();

      applyActions.clear();
      files.clear();
      middleDirs.clear();
      validated.clear();
      toCreate.clear();
      toIgnore.clear();
      toDelete.clear();
      startIndex = groupAndValidate(events, startIndex, applyActions, validated, files, middleDirs, toCreate, toIgnore, toDelete,
                                    excludeAsyncListeners);

      if (!validated.isEmpty()) {
        applyMultipleEvents(publisher, applyActions, validated, excludeAsyncListeners);
      }
    }
  }

  private static void applyMultipleEvents(@NotNull BulkFileListener publisher,
                                          @NotNull List<? extends @NotNull Runnable> applyActions,
                                          @NotNull List<? extends @NotNull VFileEvent> applyEvents,
                                          boolean excludeAsyncListeners) {
    PingProgress.interactWithEdtProgress();
    // defensive copying to cope with ill-written listeners that save the passed list for later processing
    List<VFileEvent> toSend = List.of(applyEvents.toArray(new VFileEvent[0]));
    Throwable x = null;

    try {
      if (excludeAsyncListeners) AsyncEventSupport.markAsynchronouslyProcessedEvents(toSend);

      try {
        fireBeforeEvents(publisher, toSend);
      }
      catch (Throwable t) {
        x = t;
      }

      PingProgress.interactWithEdtProgress();
      for (Runnable runnable : applyActions) {
        try {
          runnable.run();
        }
        catch (Throwable t) {
          if (x != null) t.addSuppressed(x);
          x = t;
        }
      }

      PingProgress.interactWithEdtProgress();
      try {
        fireAfterEvents(publisher, toSend);
      }
      catch (Throwable t) {
        if (x != null) t.addSuppressed(x);
        x = t;
      }
    }
    finally {
      if (excludeAsyncListeners) AsyncEventSupport.unmarkAsynchronouslyProcessedEvents(toSend);
      if (x != null) ExceptionUtil.rethrow(x);
    }
  }

  private static void fireBeforeEvents(BulkFileListener publisher, List<? extends VFileEvent> toSend) {
    runSuppressing(
      () -> publisher.before(toSend),
      () -> ((BulkFileListener)VirtualFilePointerManager.getInstance()).before(toSend),
      () -> {
      }
    );
  }

  private static void fireAfterEvents(BulkFileListener publisher, List<? extends VFileEvent> toSend) {
    runSuppressing(
      () -> CachedFileType.clearCache(),
      () -> ((BulkFileListener)VirtualFilePointerManager.getInstance()).after(toSend),
      () -> publisher.after(toSend)
    );
  }

  // remove children from specified directories using VirtualDirectoryImpl.removeChildren() optimised for bulk removals
  private void applyDeletions(@NotNull MultiMap<VirtualDirectoryImpl, VFileDeleteEvent> deletions) {
    for (Map.Entry<VirtualDirectoryImpl, Collection<VFileDeleteEvent>> entry : deletions.entrySet()) {
      VirtualDirectoryImpl parent = entry.getKey();
      Collection<VFileDeleteEvent> deleteEvents = entry.getValue();
      // no valid containing directory; applying events the old way - one by one
      if (parent == null || !parent.isValid()) {
        deleteEvents.forEach(this::applyEvent);
        return;
      }

      int parentId = fileId(parent);
      List<CharSequence> childrenNamesDeleted = new ArrayList<>(deleteEvents.size());
      IntSet childrenIdsDeleted = new IntOpenHashSet(deleteEvents.size());
      List<ChildInfo> deleted = new ArrayList<>(deleteEvents.size());
      for (VFileDeleteEvent event : deleteEvents) {
        VirtualFile file = event.getFile();
        int id = fileId(file);
        childrenNamesDeleted.add(file.getNameSequence());
        childrenIdsDeleted.add(id);
        vfsPeer.deleteRecordRecursively(id);
        invalidateSubtree(file, "Bulk file deletions", event);
        deleted.add(new ChildInfoImpl(id, ChildInfoImpl.UNKNOWN_ID_YET, null, null, null));
      }
      deleted.sort(ChildInfo.BY_ID);
      vfsPeer.update(parent, parentId, oldChildren -> oldChildren.subtract(deleted), /*setAllChildrenCached: */ false);
      parent.removeChildren(childrenIdsDeleted, childrenNamesDeleted);
    }
  }

  // add children to specified directories using VirtualDirectoryImpl.createAndAddChildren() optimised for bulk additions
  private void applyCreations(@NotNull Map<VirtualDirectoryImpl, Set<VFileCreateEvent>> creations) {
    for (Map.Entry<VirtualDirectoryImpl, Set<VFileCreateEvent>> entry : creations.entrySet()) {
      VirtualDirectoryImpl parent = entry.getKey();
      Collection<VFileCreateEvent> createEvents = entry.getValue();
      applyCreateEventsInDirectory(parent, createEvents);
    }
  }

  private void applyCreateEventsInDirectory(@NotNull VirtualDirectoryImpl parent,
                                            @NotNull Collection<VFileCreateEvent> createEvents) {
    int parentId = fileId(parent);
    NewVirtualFile vf = findFileById(parentId);
    if (!(vf instanceof VirtualDirectoryImpl)) {
      return;
    }
    parent =
      (VirtualDirectoryImpl)vf;  // retain in `idToDirCache` at least for the duration of this block, so that subsequent `findFileById` won't crash
    NewVirtualFileSystem fs = getFileSystem(parent);

    List<ChildInfo> childrenAdded = new ArrayList<>(createEvents.size());
    for (VFileCreateEvent createEvent : createEvents) {
      createEvent.resetCache();
      String name = createEvent.getChildName();
      Pair<@NotNull FileAttributes, String> childData =
        getChildData(fs, createEvent.getParent(), name, createEvent.getAttributes(), createEvent.getSymlinkTarget());
      if (childData != null) {
        ChildInfo child = makeChildRecord(parent, parentId, name, childData, fs, createEvent.getChildren());
        childrenAdded.add(child);
      }
    }
    childrenAdded.sort(ChildInfo.BY_ID);
    boolean caseSensitive = parent.isCaseSensitive();
    //@formatter:off
    vfsPeer.update(parent, parentId, oldChildren -> oldChildren.merge(vfsPeer, childrenAdded, caseSensitive), /*setAllChildrenCached: */ false);
    parent.createAndAddChildren(childrenAdded, false, (__, ___) -> { });
    //@formatter:on

    saveScannedChildrenRecursively(createEvents, fs, parent.isCaseSensitive());
  }

  private void saveScannedChildrenRecursively(@NotNull Collection<VFileCreateEvent> createEvents,
                                              @NotNull NewVirtualFileSystem fs,
                                              boolean isCaseSensitive) {
    for (VFileCreateEvent createEvent : createEvents) {
      ChildInfo[] children = createEvent.getChildren();
      if (children == null || !createEvent.isDirectory()) continue;
      // todo avoid expensive findFile
      VirtualFile createdDir = createEvent.getFile();
      if (createdDir instanceof VirtualDirectoryImpl) {
        Queue<Pair<VirtualDirectoryImpl, ChildInfo[]>> queue = new ArrayDeque<>();
        queue.add(new Pair<>((VirtualDirectoryImpl)createdDir, children));
        while (!queue.isEmpty()) {
          Pair<VirtualDirectoryImpl, ChildInfo[]> queued = queue.remove();
          VirtualDirectoryImpl directory = queued.first;
          List<ChildInfo> scannedChildren = Arrays.asList(queued.second);
          int directoryId = directory.getId();
          List<ChildInfo> added = new ArrayList<>(scannedChildren.size());
          for (ChildInfo childInfo : scannedChildren) {
            CharSequence childName = childInfo.getName();
            Pair<@NotNull FileAttributes, String> childData =
              getChildData(fs, directory, childName.toString(), childInfo.getFileAttributes(), childInfo.getSymlinkTarget());
            if (childData != null) {
              added.add(makeChildRecord(directory, directoryId, childName, childData, fs, childInfo.getChildren()));
            }
          }

          added.sort(ChildInfo.BY_ID);
          vfsPeer.update(directory, directoryId,
                         oldChildren -> oldChildren.merge(vfsPeer, added, isCaseSensitive), /*setAllChildrenCached: */ true);
          // set "all children loaded" because the first "fileCreated" listener (looking at you, local history)
          // will call getChildren() anyway, beyond a shadow of a doubt
          directory.createAndAddChildren(added, true, (childCreated, childInfo) -> {
            // enqueue recursive children
            if (childCreated instanceof VirtualDirectoryImpl && childInfo.getChildren() != null) {
              queue.add(new Pair<>((VirtualDirectoryImpl)childCreated, childInfo.getChildren()));
            }
          });
        }
      }
    }
  }

  @Override
  public @Nullable VirtualFileSystemEntry findRoot(@NotNull String path, @NotNull NewVirtualFileSystem fs) {
    if (!connected.get()) {
      LOG.info("VFS disconnected. Can't provide root for " + path + " in " + fs);
      return null;
    }
    if (path.isEmpty()) {
      LOG.error("Invalid root, fs=" + fs);
      return null;
    }

    //RC: Why do we strip the trailing '/'? This makes rootUrl not a correct URL anymore, e.g.:
    //   'file:///' -> 'file:'
    //   'jar:///rt.jar!/' -> 'jar:///rt.jar!'
    String rootUrl = UriUtil.trimTrailingSlashes(VirtualFileManager.constructUrl(fs.getProtocol(), path));
    VirtualFileSystemEntry root = rootsByUrl.get(rootUrl);
    if (root != null) return root;

    String rootName;
    String rootPath;
    FileAttributes attributes;
    if (fs instanceof ArchiveFileSystem afs) {
      //RC: I suspect that in most cases this will be an identity transformation:
      //   path(/x/y/z.jar!/) -> localFile(/x/y/z.jar) -> rootPath(/x/y/z.jar!/)
      //   So this branch is basically assigns rootName to jar-file name (instead of root path)
      //   and attributes to _archive_ attributes, instead of file attributes
      VirtualFile localFile = afs.findLocalByRootPath(path);
      if (localFile == null) return null;
      rootName = localFile.getName();
      rootPath = afs.getRootPathByLocal(localFile);// '/x/y/z.jar' -> '/x/y/z.jar!/'
      rootUrl = UriUtil.trimTrailingSlashes(VirtualFileManager.constructUrl(fs.getProtocol(), rootPath));
      attributes = afs.getArchiveRootAttributes(new StubVirtualFile(fs) {
        @Override
        public @NotNull String getPath() { return rootPath; }

        @Override
        public @Nullable VirtualFile getParent() { return null; }
      });
    }
    else {
      rootName = rootPath = path;
      attributes = loadAttributes(fs, rootPath);
    }

    if (attributes == null || !attributes.isDirectory()) {
      return null;
    }
    // assume roots have the FS default case sensitivity
    attributes = attributes.withCaseSensitivity(
      CaseSensitivity.fromBoolean(fs.isCaseSensitive())
    );
    // avoid creating gazillions of roots which are not actual roots
    String parentPath;
    if (fs instanceof LocalFileSystem && !(parentPath = PathUtil.getParentPath(rootPath)).isEmpty()) {
      FileAttributes parentAttributes = loadAttributes(fs, parentPath);
      if (parentAttributes != null) {
        throw new IllegalArgumentException(
          "Must pass FS root path, but got: '" + path + "' (url: '" + rootUrl + "'), " +
          "which has a parent '" + parentPath + "'. " +
          "Use NewVirtualFileSystem.extractRootPath() for obtaining root path");
      }
    }

    int rootId = vfsPeer.findOrCreateRootRecord(rootUrl);
    vfsPeer.loadRootData(rootId, path, fs);


    int rootNameId = vfsPeer.getNameId(rootName);
    boolean markModified;
    FsRoot newRoot;
    synchronized (rootsByUrl) {
      root = rootsByUrl.get(rootUrl);
      if (root != null) return root;

      try {
        String pathBeforeSlash = UriUtil.trimTrailingSlashes(rootPath);
        newRoot = new FsRoot(rootId, vfsData, fs, pathBeforeSlash, attributes, path, this);
      }
      catch (VfsData.FileAlreadyCreatedException e) {
        for (Map.Entry<String, VirtualFileSystemEntry> entry : rootsByUrl.entrySet()) {
          VirtualFileSystemEntry existingRoot = entry.getValue();
          if (existingRoot.getId() == rootId) {
            throw new RuntimeException(
              "Tried to create FS root => conflicted with already existing root: " +
              "(path='" + path + "', fs=" + fs + ", rootUrl='" + rootUrl + "'), conflicted with existing " +
              "(rootUrl='" + entry.getKey() + "', rootId=" + rootId + ", valid=" + existingRoot.isValid() + ")", e);
          }
        }
        VirtualFileSystemEntry cachedDir = dirByIdCache.getCachedDir(rootId);
        VirtualFileSystemEntry cachedRoot = dirByIdCache.getCachedRoot(rootId);
        throw new RuntimeException(
          "Tried to create FS root => conflicted with already existing file: " +
          "(path='" + path + "', fs=" + fs + ", rootUrl='" + rootUrl + "') -> " +
          "(rootName='" + rootName + "', rootNameId=" + rootNameId + ", rootId=" + rootId + "), " +
          "cachedDir: " + cachedDir + ", cachedRoot: " + cachedRoot, e);
      }
      incStructuralModificationCount();
      markModified = writeRootFields(rootId, rootName, fs.isCaseSensitive(), attributes) != -1;

      rootsByUrl.put(rootUrl, newRoot);
      dirByIdCache.cacheDir(newRoot);
      //To be on a safe side: remove rootId from missed, to prevent any possibility of covering an actually existing root
      missedRootIds.remove(rootId);
    }

    if (!markModified && attributes.lastModified != vfsPeer.getTimestamp(rootId)) {
      newRoot.markDirtyRecursively();
    }

    LOG.assertTrue(rootId == newRoot.getId(), "root=" + newRoot + " expected=" + rootId + " actual=" + newRoot.getId());

    return newRoot;
  }

  private static @Nullable FileAttributes loadAttributes(@NotNull NewVirtualFileSystem fs, @NotNull String path) {
    return fs.getAttributes(new StubVirtualFile(fs) {
      @Override
      public @NotNull String getPath() { return path; }

      @Override
      public @Nullable VirtualFile getParent() { return null; }
    });
  }

  @Override
  public void clearIdCache() {
    // remove all except roots
    dirByIdCache.dropNonRootCachedDirs();
  }

  @Override
  public @Nullable NewVirtualFile findFileById(int fileId) {
    if (fileId == FSRecords.NULL_FILE_ID) {
      fileByIdCacheHits.incrementAndGet();  //a bit of a stretch, but...
      return null;
    }
    VirtualFileSystemEntry cached = dirByIdCache.getCachedDir(fileId);
    if (cached != null) {
      fileByIdCacheHits.incrementAndGet();
      if (cached.isValid()) {
        return cached;
      }
      else {
        return null;// most likely deleted
      }
    }

    fileByIdCacheMisses.incrementAndGet();
    NewVirtualFile file = new FileByIdResolver().resolve(fileId);
    if (file != null && file.isValid()) {
      return file;
    }
    else {
      return null;
    }
  }

  /**
   * Usually we cache the roots during {@link #findRoot(String, NewVirtualFileSystem)} call, so at a given
   * moment some roots _could_ be not (yet) cached. So we need to force idToDirCache to cache the root it
   * misses
   */
  private void cacheMissedRootFromPersistence(int rootId) {
    if (missedRootIds.contains(rootId)) {
      THROTTLED_LOG.warn("Can't find root[#" + rootId + "] in persistence");
      //don't repeat long lookup if already know it won't find anything
      return;
    }
    Ref<String> missedRootUrlRef = new Ref<>();
    vfsPeer.forEachRoot((rootFileId, rootUrlId) -> {
      if (rootId == rootFileId) {
        missedRootUrlRef.set(getNameByNameId(rootUrlId));
        return false; //stop iteration
      }
      return true;
    });

    if (missedRootUrlRef.isNull()) {
      missedRootIds.add(rootId);
      THROTTLED_LOG.warn("Can't find root[#" + rootId + "] in persistence");
      return;
    }

    //TODO RC: tuple (rootUrl, rootPath/name, rootFS) is better to be wrapped as 'record Root(url,path,fs)',
    //         with all the normalization methods encapsulated. It will be much better than all the components
    //         dancing/messing around individually
    String missedRootUrl = missedRootUrlRef.get();
    String missedRootName = vfsPeer.getName(rootId);
    String missedRootPath = getRootPath(missedRootUrl, missedRootName);
    NewVirtualFile root = ensureRootCached(missedRootPath, missedRootUrl);
    if (root != null) {
      if (root.getId() != rootId) {
        //Diogen reports like 49432216: rootId is _found_ among existing roots, but somehow ensureRootCached(rootPath, rootUrl)
        // leads to insertion of a _new_ root.
        // I suspect this is a bug, and this check is to provide more diagnostics for it:
        throw new IllegalStateException(
          "root[#" + rootId + "]{rootName: '" + missedRootName + "', rootPath: '" + missedRootPath + "'} cached to something else: " +
          "cached [#" + root.getId() + "]" +
          "{rootName: '" + root.getName() + "', rootPath: '" + root.getPath() + "', rootUrl: '" + root.getUrl() + "'}"
        );
      }
    }
  }

  /**
   * rootPath == rootName in case of roots that are not archives
   * But for archives e.g. jars rootName will be just file name (see {@link PersistentFSImpl#findRoot(String, NewVirtualFileSystem)})
   * so we need to extract a path from url (IDEA-341011)
   * (Path should not end with '!' because then '!' won't be stripped and the apt file won't be found, see
   * {@link ArchiveFileSystem#findLocalByRootPath})
   */
  private static @NotNull String getRootPath(@NotNull String rootUrl, @NotNull String rootName) {
    NewVirtualFileSystem fs = detectFileSystem(rootUrl);
    if (fs instanceof ArchiveFileSystem) {
      String path = VirtualFileManager.extractPath(rootUrl);
      return StringUtil.trimEnd(path, "!");
    }
    return rootName;
  }

  private NewVirtualFile ensureRootCached(@NotNull String missedRootPath,
                                          @NotNull String missedRootUrl) {
    NewVirtualFileSystem fs = detectFileSystem(missedRootUrl);
    if (fs == null) {
      return null;
    }

    try {
      NewVirtualFile cachedRoot = findRoot(missedRootPath, fs);
      if (LOG.isTraceEnabled()) {
        LOG.trace("\tforce caching " + missedRootUrl + " (protocol: " + fs.getProtocol() + ", path: " + missedRootPath + ")" +
                  " -> " + cachedRoot);
      }
      return cachedRoot;
    }
    catch (Throwable t) {
      if (t instanceof ControlFlowException) {
        throw t;
      }
      StringBuilder sb = new StringBuilder();
      vfsPeer.forEachRoot((rootUrl, rootFileId) -> {
        sb.append("[#").append(rootFileId).append("]: '").append(rootUrl).append("'\n");
      });
      LOG.warn("Can't cache root[url: " + missedRootUrl + "][path: " + missedRootPath + "]. \nAll roots: " + sb, t);
      return null;
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @Nullable NewVirtualFileSystem detectFileSystem(@NotNull String rootUrl) {
    if (rootUrl.endsWith(":")) {
      if (OSAgnosticPathUtil.startsWithWindowsDrive(rootUrl) && rootUrl.length() == 2) {
        //It shouldn't happen: rootUrl must be an url (even though sometimes not fully correct URL), not a win-path
        // -- but it did happen (IDEA-331415) even though shouldn't.
        // I hope this was just a temporary fluck: i.e. some VFS instances somehow got 'infected' by these wrong root
        // urls, but they washed off with time -- so the exception here is justified
        throw new IllegalArgumentException("detectFileSystem(rootUrl='" + rootUrl + "'): root URL is not an URL, but Win drive path");
      }

      //We truncated all trailing '/' in the .findRoot(), before putting rootUrl into FSRecords.findOrCreateRoot()
      // -- now we need to append them back, otherwise .extractProtocol() won't recognize the protocol
      // E.g. 'file:' -> 'file:///'
      rootUrl += "///";
    }

    String protocol = VirtualFileManager.extractProtocol(rootUrl);
    VirtualFileSystem fs = VirtualFileManager.getInstance().getFileSystem(protocol);
    if (fs instanceof NewVirtualFileSystem) {
      return (NewVirtualFileSystem)fs;
    }

    if (fs == null) {
      LOG.warn("\tdetectFileSystem(" + rootUrl + ") -> protocol [" + protocol + "] is not registered (yet?)");
    }
    else {
      LOG.warn("\tdetectFileSystem(" + rootUrl + ") -> protocol [" + protocol + "] -> " + fs + " is not NewVirtualFileSystem");
    }
    return null;
  }

  /**
   * Encapsulates resolution fileId -> {@link VirtualFile} (={@link VirtualFileSystemEntry}).
   * <p/>
   * Namely:
   * <ol>
   * <li>{@link #lookupCachedAncestorOrSelf(int)}: climbs up from fileId, collecting {@link #ancestorsIds} (=path), until finds an ancestor
   *     which is already cached in {@link #dirByIdCache}.</li>
   * <li>{@link #resolveDescending(VirtualFileSystemEntry, IntList, int)}: from that cached ancestor climbs down back to fileId,
   *     resolving {@link #ancestorsIds} along the way via {@link #findChild(VirtualFileSystemEntry, int)}</li>
   * </ol>
   */
  final class FileByIdResolver {

    /**
     * List of ancestors' ids towards the root (or a first cached directory), or null(=empty) if the first parent is already cached.
     * <pre>{cachedAncestor} / { ancestorsIds[N] / ... / ancestorsIds[0] } / fileId</pre>
     */
    private @Nullable IntList ancestorsIds;

    public NewVirtualFile resolve(int fileId) {
      assert fileId != FSRecords.NULL_FILE_ID : "fileId=NULL_ID(0) must not be passed into find()";
      VirtualDirectoryImpl cachedAncestorOrSelf;
      try {
        cachedAncestorOrSelf = lookupCachedAncestorOrSelf(fileId);
        if (cachedAncestorOrSelf == null) {
          //fileId is deleted or orphan (=one of its ancestors is deleted, or it's root is missed)
          return null;
        }
        else if (cachedAncestorOrSelf.getId() == fileId) {
          return cachedAncestorOrSelf;
        }
      }
      catch (Exception e) {
        throw vfsPeer.handleError(e);
      }
      // {cachedAncestor} / { ancestorsIds[N] / ... / ancestorsIds[0] } / fileId
      return resolveDescending(cachedAncestorOrSelf, ancestorsIds, fileId);
    }

    /**
     * Climbs up hierarchy, from fileId, until _cached_ ancestor is found, and return this cached ancestor.
     * If file with fileId itself is cached -- it is returned (which is why ...OrSelf)
     * Collects all the non-cached ancestors along the way into {@link #ancestorsIds}
     */
    private @Nullable VirtualDirectoryImpl lookupCachedAncestorOrSelf(int fileId) {
      int currentId = fileId;
      while (true) {
        int parentId = vfsPeer.getParent(currentId);

        if (parentId != FSRecords.NULL_FILE_ID) {
          VirtualDirectoryImpl cachedParent = dirByIdCache.getCachedDir(parentId);
          if (cachedParent != null) {
            if (cachedParent.isValid()) {
              return cachedParent;
            }
            else {
              return null;
            }
          }
        }
        else {
          //RC: currentId is root, but not cached -- it is OK, root _could_ be not (yet) cached, since
          //    dirByIdCache caches a root only during PersistentFSImpl.findRoot() call -- it could be
          //    that not all the roots known to FSRecords were requested at a given moment.
          //    => we need to force dirByIdCache to cache the root it misses:
          cacheMissedRootFromPersistence(currentId);

          VirtualDirectoryImpl cachedParent = dirByIdCache.getCachedDir(currentId);
          if (cachedParent != null) {
            //currentId is in the list, but shouldn't be, if it is == foundParent
            // => remove it
            if (ancestorsIds != null && !ancestorsIds.isEmpty()) {
              ancestorsIds.removeInt(ancestorsIds.size() - 1);
            }
            else {
              ancestorsIds = null;
            }
            return cachedParent;
          }


          //MAYBE RC: despite all the efforts the root entry wasn't found/loaded -- it means VFS is corrupted,
          // and we should throw assertion (VFS rebuild?).
          // But (it seems) the method .findFileById() is used in an assumption it just returns null if 'incorrect'
          // fileId is passed in? -- so I keep that legacy behaviour (just log warning with diagnostic) until I'll
          // be sure all 'legal' cases are covered:
          logVeryDetailedErrorMessageAboutParentNotFound(currentId, fileId);
          return null; // =null
        }


        if (ancestorsIds != null && (ancestorsIds.size() % 128 == 0 && ancestorsIds.contains(parentId))) {
          //circularity check is expensive: do it only once-in-a-while, as path became deep enough
          //  to start suspecting something may be wrong.
          throw new AssertionError(
            "Cyclic parent-child relations: fileId: " + fileId + ", current parentId: " + parentId + ", path: " + ancestorsIds
          );
        }

        if (ancestorsIds == null) {
          ancestorsIds = new IntArrayList(IntArrayList.DEFAULT_INITIAL_CAPACITY);
        }
        ancestorsIds.add(parentId);

        currentId = parentId;
      }
    }

    /**
     * Starting from cachedRoot, descends along {@link #ancestorsIds}, resolves (=instantiates and caches)
     * {@link VirtualFileSystemEntry} along the way, at the end resolves fileId, and returns it:
     *
     * <pre>{cachedAncestor} -> { ancestorsIds[N] -> ... -> ancestorsIds[0] } -> fileId</pre>
     */
    private @Nullable VirtualFileSystemEntry resolveDescending(@NotNull VirtualDirectoryImpl cachedRoot,
                                                               @Nullable IntList ancestorsIds,
                                                               int fileId) {
      VirtualDirectoryImpl currentDir = cachedRoot;
      if (ancestorsIds != null) {
        for (int i = ancestorsIds.size() - 1; i >= 0; i--) {
          currentDir = (VirtualDirectoryImpl)findChild(currentDir, ancestorsIds.getInt(i));
          if (currentDir == null) {
            return null;//most likely deleted
          }
        }
      }

      return findChild(currentDir, fileId);
    }

    private @Nullable VirtualFileSystemEntry findChild(@NotNull VirtualDirectoryImpl parent,
                                                       int childId) {
      VirtualFileSystemEntry child = parent.findChildById(childId);
      if (child instanceof VirtualDirectoryImpl childDir) {
        if (child.getId() != childId) {
          LOG.error("findChildById(" + childId + "): " + child + " doesn't have expected id!");
        }
        VirtualDirectoryImpl old = dirByIdCache.cacheDirIfAbsent(childDir);
        if (old != null) child = old;
      }
      return child;
    }

    private void logVeryDetailedErrorMessageAboutParentNotFound(int currentId, int startingFileId) {
      String preRootFileName = vfsPeer.getName(currentId);
      int preRootIdFlags = vfsPeer.getFlags(currentId);
      int startingFileFlags = vfsPeer.getFlags(startingFileId);

      THROTTLED_LOG.warn(
        () -> {
          //Check roots and cachedRoots are consistent
          IntOpenHashSet cachedRootsIds = new IntOpenHashSet();
          for (VirtualFileSystemEntry cachedRoot : dirByIdCache.getCachedRootDirs()) {
            cachedRootsIds.add(cachedRoot.getId());
          }
          IntOpenHashSet rootIds = new IntOpenHashSet();
          for (VirtualFile root : PersistentFSImpl.this.getRoots()) {
            rootIds.add(((VirtualFileWithId)root).getId());
          }
          int[] nonCachedRoots = rootIds.intStream()
            .filter(rootId -> !cachedRootsIds.contains(rootId))
            .toArray();
          int[] cachedNonRoots = cachedRootsIds.intStream()
            .filter(rootId -> !rootIds.contains(rootId))
            .toArray();
          int[] fsRootsNonPFSRoots = Arrays.stream(vfsPeer.listRoots())
            .filter(rootId -> !rootIds.contains(rootId))
            .toArray();
          boolean fsRootsHasCurrentId = Arrays.stream(vfsPeer.listRoots())
            .anyMatch(rootId -> rootId == currentId);

          StringBuilder nonCachedRootsPerLine = new StringBuilder();
          if (LOG_NON_CACHED_ROOTS_LIST) {
            vfsPeer.forEachRoot((rootUrl, rootFileId) -> {
              if (dirByIdCache.getCachedDir(rootFileId) == null) {
                String rootName = vfsPeer.getName(rootFileId);
                nonCachedRootsPerLine.append("\t").append(rootFileId)
                  .append(": [name:'").append(rootName)
                  .append("'][url:'").append(rootUrl)
                  .append("']\n");
              }
            });
          }

          StringBuilder relativePath = new StringBuilder();
          if (ancestorsIds != null) {
            for (int i = ancestorsIds.size() - 1; i >= 0; i--) {
              int fileId = ancestorsIds.getInt(i);
              String fileName = vfsPeer.getName(fileId);
              relativePath.append('/').append(fileName);
            }
          }


          return
            "file[" + startingFileId + ", flags: " + startingFileFlags + "]: " +
            "top parent (id: " + currentId + ", name: '" + preRootFileName + "', flags: " + preRootIdFlags + " parent: 0), " +
            "is still not in the idToDirCache. " +
            "path: " + ancestorsIds + " [" + relativePath + "], " +
            "cachedRoots.size(=" + cachedRootsIds.size() + "), roots.size(=" + rootIds.size() + "), " +
            "pfs.roots.contains(" + currentId + ")=" + rootIds.contains(currentId) + ", " +
            "fs.roots.contains(" + currentId + ")=" + fsRootsHasCurrentId + ", " +
            "non-cached roots: " + nonCachedRoots.length + ", cached non-roots: " + cachedNonRoots.length + ", " +
            "FS roots not PFS roots: " + fsRootsNonPFSRoots.length + ": \n" + nonCachedRootsPerLine;
        }
      );
    }
  }

  @Override
  public VirtualFile @NotNull [] getRoots() {
    return VfsUtilCore.toVirtualFileArray(rootsByUrl.values());
  }

  @Override
  public VirtualFile @NotNull [] getRoots(@NotNull NewVirtualFileSystem fs) {
    List<VirtualFile> roots = new ArrayList<>();

    for (NewVirtualFile root : rootsByUrl.values()) {
      if (root.getFileSystem() == fs) {
        roots.add(root);
      }
    }

    return VfsUtilCore.toVirtualFileArray(roots);
  }

  @Override
  public VirtualFile @NotNull [] getLocalRoots() {
    List<VirtualFile> roots = new SmartList<>();

    for (NewVirtualFile root : rootsByUrl.values()) {
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
      if (event instanceof VFileCreateEvent ce) {
        executeCreateChild(ce.getParent(), ce.getChildName(), ce.getAttributes(), ce.getSymlinkTarget(), ce.isEmptyDirectory());
      }
      else if (event instanceof VFileDeleteEvent deleteEvent) {
        executeDelete(deleteEvent);
      }
      else if (event instanceof VFileContentChangeEvent contentUpdateEvent) {
        VirtualFile file = contentUpdateEvent.getFile();
        long length = contentUpdateEvent.getNewLength();
        long timestamp = contentUpdateEvent.getNewTimestamp();

        if (!contentUpdateEvent.isLengthAndTimestampDiffProvided()) {
          NewVirtualFileSystem fs = getFileSystem(file);
          FileAttributes attributes = fs.getAttributes(file);
          length = attributes != null ? attributes.length : DEFAULT_LENGTH;
          timestamp = attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP;
        }

        var reloadContent = contentUpdateEvent.isFromRefresh() || contentUpdateEvent.getRequestor() == RELOADING_STORAGE_WRITE_REQUESTOR;
        executeTouch(file, reloadContent, contentUpdateEvent.getModificationStamp(), length, timestamp);
      }
      else if (event instanceof VFileCopyEvent ce) {
        executeCreateChild(ce.getNewParent(), ce.getNewChildName(), null, null, ce.getFile().getChildren().length == 0);
      }
      else if (event instanceof VFileMoveEvent moveEvent) {
        executeMove(moveEvent.getFile(), moveEvent.getNewParent());
      }
      else if (event instanceof VFilePropertyChangeEvent propertyChangeEvent) {
        VirtualFile file = propertyChangeEvent.getFile();
        Object newValue = propertyChangeEvent.getNewValue();
        switch (propertyChangeEvent.getPropertyName()) {
          case VirtualFile.PROP_NAME -> executeRename(file, (String)newValue);
          case VirtualFile.PROP_WRITABLE -> {
            executeSetWritable(file, ((Boolean)newValue).booleanValue());
            if (LOG.isDebugEnabled()) {
              LOG.debug("File " + file + " writable=" + file.isWritable() + " id=" + fileId(file));
            }
          }
          case VirtualFile.PROP_HIDDEN -> executeSetHidden(file, ((Boolean)newValue).booleanValue());
          case VirtualFile.PROP_SYMLINK_TARGET -> executeSetTarget(file, (String)newValue);
          case VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY ->
            executeChangeCaseSensitivity((VirtualDirectoryImpl)file, ((CaseSensitivity)newValue).toBooleanOrFail());
        }
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  /**
   * Update case-sensitivity of the directory, both in persistent VFS structure, and in in-memory cache.
   * Change doesn't produce a modification event
   */
  @ApiStatus.Internal
  public void executeChangeCaseSensitivity(@NotNull VirtualDirectoryImpl directory,
                                           boolean newIsCaseSensitive) {
    int fileId = fileId(directory);
    vfsPeer.updateRecordFields(fileId, record -> {
      boolean sensitivityChanged = newIsCaseSensitive
                                   ? record.addFlags(Flags.CHILDREN_CASE_SENSITIVE)
                                   : record.removeFlags(Flags.CHILDREN_CASE_SENSITIVE);
      return record.addFlags(Flags.CHILDREN_CASE_SENSITIVITY_CACHED)
             || sensitivityChanged;
    });
    directory.setCaseSensitivityFlag(newIsCaseSensitive);
  }


  /**
   * If the {@code parent} case-sensitivity flag is still not known, try to determine it (via {@link LocalFileSystemBase#fetchCaseSensitivity(VirtualFile, String)}),
   * and if the actual case-sensitivity value determined happens to be different from current -- prepares and returns the
   * appropriate {@link VirtualFile#PROP_CHILDREN_CASE_SENSITIVITY} event for the change.
   * <p>
   * Otherwise, return null.
   */
  @ApiStatus.Internal
  public @Nullable VFilePropertyChangeEvent determineCaseSensitivityAndPrepareUpdate(@NotNull VirtualFile parent,
                                                                                     @NotNull String childName) {
    VirtualDirectoryImpl vDirectory = (VirtualDirectoryImpl)parent;
    if (vDirectory.getChildrenCaseSensitivity().isKnown()) {
      //do not update case-sensitivity once determined: assume folder case-sensitivity is constant through the run
      // time of an app -- which is, strictly speaking, incorrect, but we don't want to process those cases so far
      return null;
    }

    CaseSensitivity actualDirCaseSensitivity = determineCaseSensitivity(vDirectory, childName);
    if (actualDirCaseSensitivity.isUnknown()) {
      return null;
    }

    return prepareCaseSensitivityUpdateIfNeeded(vDirectory, actualDirCaseSensitivity.toBooleanOrFail());
  }

  /** @return actual case-sensitivity for 'parent' directory, or {@link CaseSensitivity#UNKNOWN}, if it can't be determined */
  private @NotNull CaseSensitivity determineCaseSensitivity(@NotNull VirtualFile parent,
                                                            @NotNull String childName) {
    VirtualFileSystem fileSystem = parent.getFileSystem();
    if (!(fileSystem instanceof LocalFileSystemBase)) {//MAYBE RC: introduce CaseSensitivityProvidingFileSystem?
      //For non-local FS we have case-sensitivity defined during RefreshWorker?
      return CaseSensitivity.UNKNOWN;
    }

    CaseSensitivity actualDirCaseSensitivity = ((LocalFileSystemBase)fileSystem).fetchCaseSensitivity(parent, childName);
    //MAYBE RC: also measure and record execution _time_?
    caseSensitivityReads.incrementAndGet();
    return actualDirCaseSensitivity;
  }

  /**
   * Applies case-sensitivity value for the directory, if needed -- either synchronously, or produces a case-sensitivity-changing
   * event, to apply the change later on:
   * If actualIsCaseSensitive is the same, as default file-system case-sensitivity -- updates the value synchronously, and does
   * not produce cs-changing event, since publicly available dir properties do not change.
   * If actualIsCaseSensitive != default file-system case-sensitivity -- updates nothing, but returns a case-sensitivity-changing
   * event to be applied later
   */
  @ApiStatus.Internal
  public VFilePropertyChangeEvent prepareCaseSensitivityUpdateIfNeeded(@NotNull VirtualDirectoryImpl dir,
                                                                       boolean actualIsCaseSensitive) {
    CaseSensitivity currentCaseSensitivity = dir.getChildrenCaseSensitivity();
    boolean currentIsCaseSensitive = dir.isCaseSensitive();
    if (currentIsCaseSensitive == actualIsCaseSensitive) {
      //If [actualIsCaseSensitive == dir.isCaseSensitive()] => externally-visible dir.isCaseSensitive() does NOT change
      if (currentCaseSensitivity.isUnknown()) {
        // But underneath case-sensitivity may be changed from 'UNKNOWN(=FS.default)' to 'known(=actualIsCaseSensitive)':
        // So, we still need to update the values in appropriate fields to avoid repeating case-sensitivity lookup later on:
        executeChangeCaseSensitivity(dir, actualIsCaseSensitive);
      }
      // ... but because externally-visible dir.isCaseSensitive() does NOT change => we don't need to issue the
      // PROP_CHILDREN_CASE_SENSITIVITY event about the change -- this helps us avoid issuing A LOT of useless events:
      return null;
    }

    //dir case-sensitivity is actually changed, and to non-default value: return appropriate event to be applied later:
    return new VFilePropertyChangeEvent(
      REFRESH_REQUESTOR,
      dir,
      VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY,
      currentCaseSensitivity, /* => */ CaseSensitivity.fromBoolean(actualIsCaseSensitive)
    );
  }

  @Override
  public String toString() {
    return "PersistentFS[connected: " + isConnected() + ", ownData: " + vfsData + "]";
  }

  private void executeCreateChild(@NotNull VirtualFile parent,
                                  @NotNull String name,
                                  @Nullable FileAttributes attributes,
                                  @Nullable String symlinkTarget,
                                  boolean isEmptyDirectory) {
    NewVirtualFileSystem fs = getFileSystem(parent);
    int parentId = fileId(parent);
    Pair<@NotNull FileAttributes, String> childData = getChildData(fs, parent, name, attributes, symlinkTarget);
    if (childData == null) {
      return;
    }

    class ChildInserter implements Function<ListResult, ListResult> {
      ChildInfo insertedChildInfo = null;

      @Override
      public @NotNull ListResult apply(@NotNull ListResult children) {
        // check that names are not duplicated:
        ChildInfo duplicate = findExistingChildInfo(children.children, name, parent.isCaseSensitive());
        if (duplicate != null) return children;

        insertedChildInfo = makeChildRecord(parent, parentId, name, childData, fs, null);
        return children.insert(insertedChildInfo);
      }
    }
    ChildInserter inserter = new ChildInserter();

    // When creating an empty directory, we need to make sure every file created inside will fire a "file-created"
    // event, for `VirtualFilePointerManager` to get those events to update its pointers properly (because currently
    // it ignores empty directory creation events for performance reasons):
    vfsPeer.update(parent, parentId, inserter, /*setAllChildrenCached: */ isEmptyDirectory);
    if (inserter.insertedChildInfo == null) {
      return; //nothing has been inserted: child{name} is already exist
    }

    int childId = inserter.insertedChildInfo.getId();
    int nameId = inserter.insertedChildInfo.getNameId();//vfsPeer.getNameId(name);
    assert parent instanceof VirtualDirectoryImpl : parent;
    VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
    VirtualFileSystemEntry child = dir.createChildIfNotExist(childId, nameId, fileAttributesToFlags(childData.first), isEmptyDirectory);
    dir.addChild(child);
    incStructuralModificationCount();
  }

  private @NotNull ChildInfo makeChildRecord(@NotNull VirtualFile parentFile,
                                             int parentId,
                                             @NotNull CharSequence name,
                                             @NotNull Pair<@NotNull FileAttributes, String> childData,
                                             @NotNull NewVirtualFileSystem fs,
                                             @NotNull ChildInfo @Nullable [] children) {
    assert parentId > 0 : parentId; // 0 means it's root => should use .writeRootFields() instead

    FileAttributes attributes = childData.first;
    String symLinkTarget = childData.second;

    //MAYBE RC: .updateRecordFields(id=0, ...) also creates a new record, so .createRecord() could be dropped?
    int newChildId = vfsPeer.createRecord();
    int nameId = vfsPeer.updateRecordFields(newChildId, parentId, attributes, name.toString(), /* cleanAttributeRef: */ true);

    if (attributes.isDirectory()) {
      vfsPeer.loadDirectoryData(newChildId, parentFile, name, fs);
    }

    return new ChildInfoImpl(newChildId, nameId, attributes, children, symLinkTarget);
  }

  /** @deprecated use instance {@link PersistentFSImpl#moveChildren(int, int)} instead */
  @Deprecated(forRemoval = true)
  public static void moveChildrenRecords(int fromParentId, int toParentId) {
    ((PersistentFSImpl)getInstance()).moveChildren(fromParentId, toParentId);
  }

  public void moveChildren(int fromParentId, int toParentId) {
    if (fromParentId == -1) return;
    if (fromParentId == FSRecords.NULL_FILE_ID) {
      throw new AssertionError("Move(" + fromParentId + " -> " + toParentId + "): can't move root to become non-root");
    }

    vfsPeer.moveChildren(fromParentId, toParentId);
  }

  /** @return [File attributes, symlink target] tuple, or null when the file not found */
  private static @Nullable Pair<@NotNull FileAttributes, String> getChildData(@NotNull NewVirtualFileSystem fs,
                                                                              @NotNull VirtualFile parent,
                                                                              @NotNull String name,
                                                                              @Nullable FileAttributes attributes,
                                                                              @Nullable String symlinkTarget) {
    if (attributes == null) {
      if (".".equals(name) || "..".equals(name)) {
        //these names have special meaning, so FS will report that such children exist, but they must not be added to VFS
        return null;
      }
      FakeVirtualFile virtualFile = new FakeVirtualFile(parent, name);
      attributes = fs.getAttributes(virtualFile);
      symlinkTarget = (attributes != null && attributes.isSymLink()) ?
                      fs.resolveSymLink(virtualFile) :
                      null;
    }
    return attributes == null ? null : new Pair<>(attributes, symlinkTarget);
  }

  private void executeDelete(@NotNull VFileDeleteEvent event) {
    VirtualFile file = event.getFile();
    if (!file.exists()) {
      LOG.error("Deleting a file which does not exist in VFS: #" + ((VirtualFileWithId)file).getId() + ", path: " + file.getPath());
      return;
    }

    int fileIdToDelete = fileId(file);

    VirtualFile parent = file.getParent();
    int parentId = parent == null ? 0 : fileId(parent);

    clearIdCache(); //TODO RC: why drop _all_ the cache just for a single delete? maybe we could be more fine-grained?
    if (parentId == 0) {
      String rootUrl = UriUtil.trimTrailingSlashes(file.getUrl());
      synchronized (rootsByUrl) {
        rootsByUrl.remove(rootUrl);
        dirByIdCache.drop(fileIdToDelete);
        //TODO RC: deleting root entry from roots catalog, and deleting the root record and it's subtree (deleteRecordRecursively)
        //         are not atomic!
        vfsPeer.deleteRootRecord(fileIdToDelete);
      }
    }
    else {
      vfsPeer.update(parent, parentId, list -> list.remove(fileIdToDelete), /*setAllChildrenCached: */ false);

      VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file.getParent();
      assert directory != null : file;
      directory.removeChild((VirtualFileSystemEntry)file);
    }

    vfsPeer.deleteRecordRecursively(fileIdToDelete);

    invalidateSubtree(file, "File deleted", event);
    incStructuralModificationCount();
  }

  private static void invalidateSubtree(@NotNull VirtualFile file, @NotNull Object source, @NotNull Object reason) {
    VirtualFileSystemEntry root = (VirtualFileSystemEntry)file;
    if (root.isDirectory()) {
      Queue<VirtualFile> queue = new ArrayDeque<>(root.getCachedChildren());
      while (!queue.isEmpty()) {
        VirtualFileSystemEntry child = (VirtualFileSystemEntry)queue.remove();
        queue.addAll(child.getCachedChildren());
        doInvalidate(child, source, reason);
      }
    }
    doInvalidate(root, source, reason);
  }

  private static void doInvalidate(@NotNull VirtualFileSystemEntry file, @NotNull Object source, @NotNull Object reason) {
    if (file.is(VFileProperty.SYMLINK)) {
      VirtualFileSystem fs = file.getFileSystem();
      if (fs instanceof LocalFileSystemImpl) {
        ((LocalFileSystemImpl)fs).symlinkRemoved(file.getId());
      }
    }
    file.invalidate(source, reason);
  }

  private void executeRename(@NotNull VirtualFile file, @NotNull String newName) {
    ((VirtualFileSystemEntry)file).setNewName(newName);
  }

  private void executeSetWritable(@NotNull VirtualFile file, boolean writableFlag) {
    setFlag(file, Flags.IS_READ_ONLY, !writableFlag);
    ((VirtualFileSystemEntry)file).setWritableFlag(writableFlag);
  }

  private void executeSetHidden(@NotNull VirtualFile file, boolean hiddenFlag) {
    setFlag(file, Flags.IS_HIDDEN, hiddenFlag);
    ((VirtualFileSystemEntry)file).setHiddenFlag(hiddenFlag);
  }

  /** Use instance {@link #offlineByDefault(VirtualFile, boolean)} method instead of static */
  @ApiStatus.Experimental
  @ApiStatus.Obsolete
  public static void setOfflineByDefault(@NotNull VirtualFile file, boolean offlineByDefaultFlag) {
    ((PersistentFSImpl)getInstance()).offlineByDefault(file, offlineByDefaultFlag);
  }

  @ApiStatus.Experimental
  public void offlineByDefault(@NotNull VirtualFile file, boolean offlineByDefaultFlag) {
    setFlag(file, Flags.OFFLINE_BY_DEFAULT, offlineByDefaultFlag);
    if (offlineByDefaultFlag) {
      ((VirtualFileSystemEntry)file).setOffline(true);
    }
  }

  private void executeSetTarget(@NotNull VirtualFile file, @Nullable String target) {
    int id = fileId(file);
    vfsPeer.storeSymlinkTarget(id, target);
    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof LocalFileSystemImpl) {
      ((LocalFileSystemImpl)fs).symlinkUpdated(id, file.getParent(), file.getNameSequence(), file.getPath(), target);
    }
  }

  private void setFlag(@NotNull VirtualFile file, @Attributes int flagsMask, boolean value) {
    int fileId = fileId(file);
    vfsPeer.updateRecordFields(fileId, record -> {
      if (value) {
        return record.addFlags(flagsMask);
      }
      else {
        return record.removeFlags(flagsMask);
      }
    });
  }

  private void executeTouch(@NotNull VirtualFile file,
                            boolean reloadContentFromFS,
                            long newModificationStamp,
                            long newLength,
                            long newTimestamp) {
    int fileId = fileId(file);
    vfsPeer.updateRecordFields(fileId, record -> {
      if (reloadContentFromFS) {
        record.addFlags(Flags.MUST_RELOAD_CONTENT);
      }

      record.setLength(newLength);
      record.removeFlags(Flags.MUST_RELOAD_LENGTH);

      record.setTimestamp(newTimestamp);
      return true;
    });

    ((VirtualFileSystemEntry)file).setModificationStamp(newModificationStamp);
  }

  private void executeMove(@NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    clearIdCache();

    int childToMoveId = fileId(file);
    int newParentId = fileId(newParent);
    VirtualFile oldParent = file.getParent();
    int oldParentId = fileId(oldParent);

    vfsPeer.moveChildren(newParent::isCaseSensitive, oldParentId, newParentId, childToMoveId);

    ((VirtualFileSystemEntry)file).setParent(newParent);
  }

  @Override
  public @NotNull String getName(int fileId) {
    assert fileId > 0;
    return vfsPeer.getName(fileId);
  }

  @TestOnly
  public void cleanPersistedContent(int fileId) {
    doCleanPersistedContent(fileId);
  }

  @TestOnly
  public void cleanPersistedContents() {
    IntArrayList ids = new IntArrayList(vfsPeer.listRoots());
    while (!ids.isEmpty()) {
      int id = ids.popInt();
      if (isDirectory(getFileAttributes(id))) {
        int[] children = vfsPeer.listIds(id);
        ids.addElements(ids.size(), children, 0, children.length);
      }
      else {
        doCleanPersistedContent(id);
      }
    }
  }

  private void doCleanPersistedContent(int id) {
    vfsPeer.updateRecordFields(id, record -> {
      //current .contentId, if any, should be ignored when MUST_RELOAD_CONTENT flag is set
      return record.addFlags(Flags.MUST_RELOAD_CONTENT | Flags.MUST_RELOAD_LENGTH);
    });
  }

  @Override
  public boolean mayHaveChildren(int id) {
    try {
      return vfsPeer.mayHaveChildren(id);
    }
    catch (IllegalArgumentException e) {
      //here we +/- sure the id _should_ exist => give VFS a kick to rebuild
      vfsPeer.handleError(e);
      throw e;
    }
  }

  @ApiStatus.Internal
  public FSRecordsImpl peer() {
    return vfsPeer;
  }


  @ApiStatus.Internal
  public void incrementFindChildByNameCount() {
    childByName.incrementAndGet();
  }

  @TestOnly
  @ApiStatus.Internal
  public @NotNull Iterable<? extends VirtualFileSystemEntry> getDirCache() {
    return dirByIdCache.getCachedDirs();
  }

  static @Attributes int fileAttributesToFlags(@NotNull FileAttributes attributes) {
    CaseSensitivity sensitivity = attributes.areChildrenCaseSensitive();
    return fileAttributesToFlags(
      attributes.isDirectory(), attributes.isWritable(), attributes.isSymLink(), attributes.isSpecial(), attributes.isHidden(),
      sensitivity.isKnown(), sensitivity.isSensitive()
    );
  }

  public static @Attributes int fileAttributesToFlags(boolean isDirectory,
                                                      boolean isWritable,
                                                      boolean isSymLink,
                                                      boolean isSpecial,
                                                      boolean isHidden,
                                                      boolean isChildrenCaseSensitivityCached,
                                                      boolean areChildrenCaseSensitive) {
    return (isDirectory ? Flags.IS_DIRECTORY : 0) |
           (isWritable ? 0 : Flags.IS_READ_ONLY) |
           (isSymLink ? Flags.IS_SYMLINK : 0) |
           (isSpecial ? Flags.IS_SPECIAL : 0) |
           (isHidden ? Flags.IS_HIDDEN : 0) |
           (isChildrenCaseSensitivityCached ? Flags.CHILDREN_CASE_SENSITIVITY_CACHED : 0) |
           (areChildrenCaseSensitive ? Flags.CHILDREN_CASE_SENSITIVE : 0);
  }

  private static void checkReadAccess() {
    switch (READ_ACCESS_CHECK_KIND) {
      case READ_ACCESS_CHECK_REQUIRE_RA_SOFT:
        ThreadingAssertions.softAssertReadAccess();
        break;
      case READ_ACCESS_CHECK_REQUIRE_RA_HARD:
        ThreadingAssertions.assertReadAccess();
        break;
      case READ_ACCESS_CHECK_REQUIRE_NO_RA:
        ThreadingAssertions.assertNoReadAccess();
        break;

      case READ_ACCESS_CHECK_NONE:
      default:
        //no check
    }
  }

  private BatchCallback setupOTelMonitoring(@NotNull Meter meter) {
    var fileByIdCacheHitsCounter = meter.counterBuilder("VFS.fileByIdCache.hits").buildObserver();
    var fileByIdCacheMissesCounter = meter.counterBuilder("VFS.fileByIdCache.misses").buildObserver();
    var fileChildByNameCounter = meter.counterBuilder("VFS.fileChildByName").buildObserver();
    var caseSensitivityReadsCounter = meter.counterBuilder("VFS.folderCaseSensitivityReads").buildObserver();
    var invertedFileNameIndexRequestsCount = meter.counterBuilder("VFS.invertedFileNameIndex.requests").buildObserver();
    return meter.batchCallback(
      () -> {
        fileByIdCacheHitsCounter.record(fileByIdCacheHits.get());
        fileByIdCacheMissesCounter.record(fileByIdCacheMisses.get());
        fileChildByNameCounter.record(childByName.get());
        caseSensitivityReadsCounter.record(caseSensitivityReads.get());
        FSRecordsImpl vfs = vfsPeer;
        if (vfs != null) {
          invertedFileNameIndexRequestsCount.record(vfs.invertedNameIndexRequestsServed());
        }
      },
      fileByIdCacheHitsCounter, fileByIdCacheMissesCounter, fileChildByNameCounter,
      caseSensitivityReadsCounter,
      invertedFileNameIndexRequestsCount
    );
  }

  private static class LengthAndContentIdReader implements RecordReader<LengthAndContentIdReader> {
    private long length;
    private int contentRecordId;

    @Override
    public LengthAndContentIdReader readRecord(@NotNull RecordForRead record) throws IOException {
      int flags = record.getFlags();
      boolean mustReloadLength = BitUtil.isSet(flags, Flags.MUST_RELOAD_LENGTH);
      boolean mustReloadContent = BitUtil.isSet(flags, Flags.MUST_RELOAD_CONTENT);
      length = mustReloadLength ? -1 : record.getLength();
      boolean contentOutdated = (length == -1) || mustReloadContent;
      if (contentOutdated) {
        contentRecordId = -1;
      }
      else {
        // As soon as we got a contentId -- there is no need for locking anymore,
        // since VFSContentStorage is a thread-safe append-only storage
        contentRecordId = record.getContentRecordId();
      }
      return this;
    }
  }

  private static final Hash.Strategy<VFileCreateEvent> CASE_INSENSITIVE_STRATEGY = new Hash.Strategy<>() {
    @Override
    public int hashCode(@Nullable VFileCreateEvent object) {
      return object == null ? 0 : Strings.stringHashCodeInsensitive(object.getChildName());
    }

    @Override
    public boolean equals(VFileCreateEvent o1, VFileCreateEvent o2) {
      if (o1 == o2) {
        return true;
      }
      return o2 != null && o1.getChildName().equalsIgnoreCase(o2.getChildName());
    }
  };
}
