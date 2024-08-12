// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationNamesInfo;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.Utf8BomOptionProvider;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.*;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSInitializationResult;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoveryInfo;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import com.intellij.util.io.ReplicatorInputStream;
import io.opentelemetry.api.metrics.Meter;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static com.intellij.configurationStore.StorageUtilKt.RELOADING_STORAGE_WRITE_REQUESTOR;
import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getLongProperty;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressWarnings("NonDefaultConstructor")
public final class PersistentFSImpl extends PersistentFS implements Disposable {
  private static final boolean simplifyFindChildInfo = Boolean.getBoolean("intellij.vfs.simplify.findChildInfo");

  private static final Logger LOG = Logger.getInstance(PersistentFSImpl.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, SECONDS.toMillis(30));

  private static final boolean LOG_NON_CACHED_ROOTS_LIST = getBooleanProperty("PersistentFSImpl.LOG_NON_CACHED_ROOTS_LIST", false);

  /**
   * Show notification about successful VFS recovery if VFS init takes longer than [nanoseconds]
   * <br/>
   * By default notification is <b>off completely</b>: there is too much controversy about it's
   * usefulness and wording, and from the other side -- so far recovery seems to work smoothly
   * enough, so user doesn't really need to even know about it.
   * TODO RC: consider removing it completely in the v243, if not re-requested
   */
  private static final long NOTIFY_OF_RECOVERY_IF_LONGER_NS = SECONDS.toNanos(
    getLongProperty("vfs.notify-user-if-recovery-longer-sec", Long.MAX_VALUE)
  );

  /**
   * Sometimes PFS got request for the files with lost (missed) roots. We try to resolve each root against persistence,
   * and it is quite expensive, so we don't want to repeat that attempt for the same root, if it is found to be missed.
   * It shouldn't be a frequently called code, so plain synchronized collection should be enough.
   */
  private final IntSet missedRootIds = IntSets.synchronize(new IntOpenHashSet());

  private final Map<String, VirtualFileSystemEntry> myRoots;

  private final VirtualDirectoryCache myIdToDirCache = new VirtualDirectoryCache();

  private final ReadWriteLock[] contentLoadingSegmentedLock = new ReadWriteLock[16];

  private final AtomicBoolean myConnected = new AtomicBoolean(false);
  private volatile FSRecordsImpl vfsPeer = null;

  private final AtomicInteger myStructureModificationCount = new AtomicInteger();
  private BulkFileListener myPublisher;
  private volatile VfsData myVfsData;

  private final Application app;

  //=========================== statistics:   ======================================================
  private final AtomicLong fileByIdCacheHits = new AtomicLong();
  private final AtomicLong fileByIdCacheMisses = new AtomicLong();

  private final AtomicLong childByName = new AtomicLong();


  public PersistentFSImpl(@NotNull Application app) {
    for (int i = 0; i < contentLoadingSegmentedLock.length; i++) {
      contentLoadingSegmentedLock[i] = new ReentrantReadWriteLock();
    }

    this.app = app;
    myRoots = SystemInfoRt.isFileSystemCaseSensitive
              ? new ConcurrentHashMap<>(10, 0.4f, JobSchedulerImpl.getCPUCoresCount())
              : ConcurrentCollectionFactory.createConcurrentMap(10, 0.4f, JobSchedulerImpl.getCPUCoresCount(),
                                                                HashingStrategy.caseInsensitive());

    AsyncEventSupport.startListening();

    app.getMessageBus().simpleConnect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // `myIdToDirCache` could retain alien file systems
        clearIdCache();
        // remove alien file system references from myRoots
        for (Iterator<Map.Entry<String, VirtualFileSystemEntry>> iterator = myRoots.entrySet().iterator(); iterator.hasNext(); ) {
          Map.Entry<String, VirtualFileSystemEntry> entry = iterator.next();
          VirtualFileSystemEntry root = entry.getValue();
          if (VirtualFileManager.getInstance().getFileSystem(root.getFileSystem().getProtocol()) == null) {
            // the file system must have been unregistered
            iterator.remove();//TODO RC: but entry in a .idToDirCache.myIdToRootCache -- still remains?
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

    setupOTelMonitoring(TelemetryManager.getInstance().getMeter(PlatformScopesKt.VFS));
  }

  @ApiStatus.Internal
  synchronized public void connect() {
    LOG.assertTrue(!myConnected.get());// vfsPeer could be !=null after disconnect
    myIdToDirCache.clear();
    myVfsData = new VfsData(app, this);
    doConnect();
    PersistentFsConnectionListener.EP_NAME.getExtensionList().forEach(PersistentFsConnectionListener::connectionOpen);
  }

  @ApiStatus.Internal
  synchronized public void disconnect() {
    if (myConnected.compareAndSet(true, false)) {
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
        myRoots.clear();
        myIdToDirCache.clear();
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
    if (myConnected.compareAndSet(false, true)) {
      Activity activity = StartUpMeasurer.startActivity("connect FSRecords");
      FSRecordsImpl _vfsPeer = FSRecords.connect();
      vfsPeer = _vfsPeer;
      activity.end();

      VFSRecoveryInfo recoveryInfo = _vfsPeer.connection().recoveryInfo();
      List<VFSInitException> recoveredErrors = recoveryInfo.recoveredErrors;
      if (!recoveredErrors.isEmpty()) {

        //if there was recovery, and it took long enough for user to notice:
        VFSInitializationResult initializationResult = _vfsPeer.initializationResult();
        if (app != null && !app.isHeadlessEnvironment()
            && initializationResult.totalInitializationDurationNs > NOTIFY_OF_RECOVERY_IF_LONGER_NS) {
          showNotificationAboutLongRecovery();
        }

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

  private static void showNotificationAboutLongRecovery() {
    NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("VFS");
    if (notificationGroup != null) {
      ApplicationNamesInfo names = ApplicationNamesInfo.getInstance();
      Notification notification = notificationGroup.createNotification(
          IdeBundle.message("notification.vfs.vfs-recovered.notification.title", names.getFullProductName()),
          IdeBundle.message("notification.vfs.vfs-recovered.notification.text", names.getFullProductName()),
          INFORMATION
        )
        .setDisplayId("VFS.recovery.happened")
        .setImportant(false);
      AnAction reportProblemAction = ActionManager.getInstance().getAction("ReportProblem");
      if (reportProblemAction != null) {
        notification = notification.addAction(reportProblemAction);
      }
      notification.notify(/*project: */ null);
    }
  }

  @ApiStatus.Internal
  public boolean isConnected() {
    return myConnected.get();
  }

  private @NotNull BulkFileListener getPublisher() {
    BulkFileListener publisher = myPublisher;
    if (publisher == null) {
      // the field cannot be initialized in constructor, to ensure that lazy listeners won't be created too early
      publisher = app.getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
      myPublisher = publisher;
    }
    return publisher;
  }

  @Override
  public void dispose() {
    disconnect();
  }

  @Override
  public boolean areChildrenLoaded(@NotNull VirtualFile dir) {
    return areChildrenLoaded(getFileId(dir));
  }

  @Override
  public long getCreationTimestamp() {
    return vfsPeer.getCreationTimestamp();
  }

  public @NotNull VirtualFileSystemEntry getOrCacheDir(@NotNull VirtualDirectoryImpl newDir) {
    return myIdToDirCache.getOrCacheDir(newDir);
  }

  public VirtualFileSystemEntry getCachedDir(int id) {
    return myIdToDirCache.getCachedDir(id);
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
    return vfsPeer.wereChildrenAccessed(getFileId(dir));
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    List<? extends ChildInfo> children = listAll(file);
    return ContainerUtil.map2Array(children, String.class, id -> id.getName().toString());
  }

  @Override
  public String @NotNull [] listPersisted(@NotNull VirtualFile parent) {
    int[] childrenIds = vfsPeer.listIds(getFileId(parent));
    String[] names = ArrayUtil.newStringArray(childrenIds.length);
    for (int i = 0; i < childrenIds.length; i++) {
      names[i] = vfsPeer.getName(childrenIds[i]);
    }
    return names;
  }

  // return actual children
  private @NotNull List<? extends ChildInfo> persistAllChildren(VirtualFile dir, int dirId) {
    NewVirtualFileSystem fs = getFileSystem(dir);

    //FIXME RC: this method is not thread-safe: it creates new file records from children names outside
    //          of lock, hence it is possible >1 child to be created for the same name, and only
    //          last children list remains written, while children created during invocations that
    //          lost the race are remains orphan
    try {
      String[] fsNames = VfsUtil.filterNames(
        fs instanceof LocalFileSystemImpl ? ((LocalFileSystemImpl)fs).listWithCaching(dir) : fs.list(dir)
      );

      Map<String, ChildInfo> justCreated = new HashMap<>();
      ListResult saved = vfsPeer.update(dir, dirId, current -> {
        List<? extends ChildInfo> currentChildren = current.children;
        if (fsNames.length == 0 && !currentChildren.isEmpty()) {
          return current;
        }
        // preserve current children which match fsNames (to have stable id)
        // (on case-insensitive systems, replace those from current with case-changes ones from fsNames preserving the id)
        // add those from fsNames which are absent from current
        boolean caseSensitive = dir.isCaseSensitive();
        Set<String> toAddNames = CollectionFactory.createFilePathSet(fsNames, caseSensitive);
        for (ChildInfo currentChild : currentChildren) {
          toAddNames.remove(currentChild.getName().toString());
        }

        List<ChildInfo> toAddChildren = new ArrayList<>(toAddNames.size());
        if (fs instanceof BatchingFileSystem) {
          Map<String, FileAttributes> map = ((BatchingFileSystem)fs).listWithAttributes(dir, toAddNames);
          for (Map.Entry<String, FileAttributes> entry : map.entrySet()) {
            String newName = entry.getKey();
            FileAttributes attrs = entry.getValue();
            String target = attrs.isSymLink() ? fs.resolveSymLink(new FakeVirtualFile(dir, newName)) : null;
            Pair<@NotNull FileAttributes, String> childData = getChildData(fs, dir, newName, attrs, target);
            ChildInfo newChild = justCreated.computeIfAbsent(newName, name -> makeChildRecord(dir, dirId, name, childData, fs, null));
            toAddChildren.add(newChild);
          }
        }
        else {
          for (String newName : toAddNames) {
            Pair<@NotNull FileAttributes, String> childData = getChildData(fs, dir, newName, null, null);
            if (childData != null) {
              ChildInfo newChild = justCreated.computeIfAbsent(newName, name -> makeChildRecord(dir, dirId, name, childData, fs, null));
              toAddChildren.add(newChild);
            }
          }
        }

        // some clients (e.g. RefreshWorker) expect subsequent list() calls to return equal arrays
        toAddChildren.sort(ChildInfo.BY_ID);
        return current.merge(vfsPeer, toAddChildren, caseSensitive);
      });

      setChildrenCached(dirId);

      return saved.children;
    }
    finally {
      if (fs instanceof LocalFileSystemImpl) {
        ((LocalFileSystemImpl)fs).clearListCache();
      }
    }
  }

  private void setChildrenCached(int id) {
    int flags = vfsPeer.getFlags(id);
    vfsPeer.setFlags(id, flags | Flags.CHILDREN_CACHED);
  }

  @Override
  @ApiStatus.Internal
  public @NotNull List<? extends ChildInfo> listAll(@NotNull VirtualFile file) {
    int id = getFileId(file);
    return areChildrenLoaded(id)
           ? vfsPeer.list(id).children
           : persistAllChildren(file, id);
  }

  private boolean areChildrenLoaded(int parentId) {
    return BitUtil.isSet(vfsPeer.getFlags(parentId), Flags.CHILDREN_CACHED);
  }

  @Override
  public @Nullable AttributeInputStream readAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att) {
    return vfsPeer.readAttributeWithLock(getFileId(file), att);
  }

  @Override
  public @NotNull AttributeOutputStream writeAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att) {
    return vfsPeer.writeAttribute(getFileId(file), att);
  }

  private @Nullable InputStream readContent(@NotNull VirtualFile file) {
    return vfsPeer.readContent(getFileId(file));
  }

  private @NotNull InputStream readContentById(int contentId) {
    return vfsPeer.readContentById(contentId);
  }

  private @NotNull OutputStream writeContent(@NotNull VirtualFile file, boolean contentOfFixedSize) {
    return vfsPeer.writeContent(getFileId(file), contentOfFixedSize);
  }

  @Override
  public int storeUnlinkedContent(byte @NotNull [] bytes) {
    return vfsPeer.writeContentRecord(new ByteArraySequence(bytes));
  }

  @SuppressWarnings("removal")
  @Override
  public int getModificationCount(@NotNull VirtualFile file) {
    return vfsPeer.getModCount(getFileId(file));
  }

  @Override
  public int getStructureModificationCount() {
    return myStructureModificationCount.get();
  }

  public void incStructuralModificationCount() {
    myStructureModificationCount.incrementAndGet();
  }

  @TestOnly
  @Override
  public int getFilesystemModificationCount() {
    return vfsPeer.getPersistentModCount();
  }

  /** @return `nameId` > 0 */
  private int writeRecordFields(int fileId,
                                int parentId,
                                @NotNull CharSequence name,
                                @NotNull FileAttributes attributes) {
    assert fileId > 0 : fileId;
    assert parentId > 0 : parentId; // 0 means it's root => should use .writeRootFields() instead
    return vfsPeer.updateRecordFields(fileId, parentId, attributes, name.toString(), /* cleanAttributeRef: */ true);
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
      if (areChildrenLoaded(rootId)) {
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
    return isDirectory(getFileAttributes(getFileId(file)));
  }

  @Override
  public boolean exists(@NotNull VirtualFile fileOrDirectory) {
    return fileOrDirectory.exists();
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    return vfsPeer.getTimestamp(getFileId(file));
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long modStamp) throws IOException {
    int id = getFileId(file);
    vfsPeer.setTimestamp(id, modStamp);
    getFileSystem(file).setTimeStamp(file, modStamp);
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
    return vfsPeer.readSymlinkTarget(getFileId(file));
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    return !BitUtil.isSet(getFileAttributes(getFileId(file)), Flags.IS_READ_ONLY);
  }

  @Override
  public boolean isHidden(@NotNull VirtualFile file) {
    return BitUtil.isSet(getFileAttributes(getFileId(file)), Flags.IS_HIDDEN);
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    getFileSystem(file).setWritable(file, writableFlag);
    boolean oldWritable = isWritable(file);
    if (oldWritable != writableFlag) {
      processEvent(new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, oldWritable, writableFlag));
    }
  }

  @Override
  @ApiStatus.Internal
  public ChildInfo findChildInfo(@NotNull VirtualFile parent, @NotNull String childName, @NotNull NewVirtualFileSystem fs) {
    int parentId = getFileId(parent);
    Ref<ChildInfo> foundChildRef = new Ref<>();

    Function<ListResult, ListResult> convertor = children -> {
      ChildInfo child = findExistingChildInfo(parent, childName, children.children);
      if (child != null) {
        foundChildRef.set(child);
        return children;
      }

      if (simplifyFindChildInfo) {
        return children;
      }

      //MAYBE RC: why do we access FS on lookup? maybe it is better to look only VFS (i.e. snapshot), and issue
      //          refresh request if children is not loaded -- and rely on automatic refresh to update VFS if
      //          actual FS children are changed?
      //          This way here we'll have read-only scan without concurrent modification problems
      //          I.e. the whole code below is (seems to be) just a 'small local refresh' -- executed during
      //          children lookup, under the VFS lock.
      //          I really want to remove it entirely, and just rely on automatic/explicit refresh
      Pair<@NotNull FileAttributes, String> childData = getChildData(fs, parent, childName, null, null); // todo: use BatchingFileSystem
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
          child = findExistingChildInfo(parent, canonicalName, children.children);
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

    vfsPeer.update(parent, parentId, convertor);
    return foundChildRef.get();
  }

  private ChildInfo findExistingChildInfo(@NotNull VirtualFile parent,
                                          @NotNull String childName,
                                          @NotNull List<? extends ChildInfo> children) {
    if (children.isEmpty()) {
      return null;
    }
    // fast path, check that some child has the same `nameId` as a given name - to avoid an overhead on retrieving names of non-cached children
    FSRecordsImpl vfs = vfsPeer;
    int nameId = vfs.getNameId(childName);
    for (ChildInfo info : children) {
      if (nameId == info.getNameId()) {
        return info;
      }
    }
    if (!parent.isCaseSensitive()) {
      for (ChildInfo info : children) {
        if (Comparing.equal(childName, vfs.getNameByNameId(info.getNameId()), false)) {
          return info;
        }
      }
    }
    return null;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    long length = getLengthIfUpToDate(file);
    return length == -1 ? reloadLengthFromFS(file, getFileSystem(file)) : length;
  }

  @Override
  public long getLastRecordedLength(@NotNull VirtualFile file) {
    int id = getFileId(file);
    return vfsPeer.getLength(id);
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile parent, @NotNull String name)
    throws IOException {
    getFileSystem(file).copyFile(requestor, file, parent, name);
    processEvent(new VFileCopyEvent(requestor, file, parent, name));

    VirtualFile child = parent.findChild(name);
    if (child == null) {
      throw new IOException("Cannot create child");
    }
    return child;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
    getFileSystem(parent).createChildDirectory(requestor, parent, dir);

    processEvent(new VFileCreateEvent(requestor, parent, dir, true, null, null, ChildInfo.EMPTY_ARRAY));
    VFileEvent caseSensitivityEvent = VirtualDirectoryImpl.generateCaseSensitivityChangedEventForUnknownCase(parent, dir);
    if (caseSensitivityEvent != null) {
      processEvent(caseSensitivityEvent);
    }

    VirtualFile child = parent.findChild(dir);
    if (child == null) {
      throw new IOException("Cannot create child directory '" + dir + "' at " + parent.getPath());
    }
    return child;
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException {
    getFileSystem(parent).createChildFile(requestor, parent, name);
    processEvent(new VFileCreateEvent(requestor, parent, name, false, null, null, null));
    VFileEvent caseSensitivityEvent = VirtualDirectoryImpl.generateCaseSensitivityChangedEventForUnknownCase(parent, name);
    if (caseSensitivityEvent != null) {
      processEvent(caseSensitivityEvent);
    }

    VirtualFile child = parent.findChild(name);
    if (child == null) {
      throw new IOException("Cannot create child file '" + name + "' at " + parent.getPath());
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
    NewVirtualFileSystem fs = getFileSystem(file);
    fs.deleteFile(requestor, file);
    if (!fs.exists(file)) {
      processEvent(new VFileDeleteEvent(requestor, file));
    }
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
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

    int fileId = getFileId(file);

    long length;
    int contentRecordId;

    //MAYBE RC: we could reduce contention on this lock by utilising FileIdLock inside the vfsPeer.
    ReadWriteLock contentLoadingLock = contentLoadingSegmentedLock[fileId % contentLoadingSegmentedLock.length];
    contentLoadingLock.readLock().lock();
    try {
      int flags = vfsPeer.getFlags(fileId);
      boolean mustReloadLength = BitUtil.isSet(flags, Flags.MUST_RELOAD_LENGTH);
      boolean mustReloadContent = BitUtil.isSet(flags, Flags.MUST_RELOAD_CONTENT);
      length = mustReloadLength ? -1 : vfsPeer.getLength(fileId);
      boolean contentOutdated = (length == -1) || mustReloadContent;
      if (contentOutdated) {
        contentRecordId = -1;
      }
      else {
        // As soon as we got a contentId -- there is no need for locking anymore,
        // since VFSContentStorage is a thread-safe append-only storage
        contentRecordId = vfsPeer.getContentRecordId(fileId);
      }
    }
    finally {
      contentLoadingLock.readLock().unlock();
    }

    if (contentRecordId <= 0) {
      NewVirtualFileSystem fs = getFileSystem(file);

      byte[] content = fs.contentsToByteArray(file);

      if (mayCacheContent && shouldCache(content.length)) {
        updateContentForFile(fileId, new ByteArraySequence(content));
      }
      else {
        contentLoadingLock.writeLock().lock();
        try {
          setLength(fileId, content.length);
        }
        finally {
          contentLoadingLock.writeLock().unlock();
        }
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

  //@GuardedBy(myInputLock.writeLock)
  private void updateContentId(int fileId,
                               int newContentRecordId,
                               int newContentLength) throws IOException {
    PersistentFSConnection connection = vfsPeer.connection();
    PersistentFSRecordsStorage records = connection.getRecords();
    //TODO RC: ideally, this method shouldn't be protected by external myInputLock, but .updateRecord() should
    //         involve VFS internal locking to protect it from concurrent writes
    records.updateRecord(fileId, record -> {
      int flags = record.getFlags();
      int newFlags = flags & ~(Flags.MUST_RELOAD_LENGTH | Flags.MUST_RELOAD_CONTENT);
      if (newFlags != flags) {
        record.setFlags(newFlags);
      }
      record.setContentRecordId(newContentRecordId);
      record.setLength(newContentLength);
      return true;
    });
    connection.markDirty();
  }

  @Override
  public byte @NotNull [] contentsToByteArray(int contentId) throws IOException {
    //noinspection resource
    return readContentById(contentId).readAllBytes();
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    InputStream contentStream;
    boolean useReplicator = false;
    long len = 0L;

    int fileId = ((VirtualFileWithId)file).getId();
    ReadWriteLock contentLoadingLock = contentLoadingSegmentedLock[fileId % contentLoadingSegmentedLock.length];
    contentLoadingLock.readLock().lock();
    try {
      long storedLength = getLengthIfUpToDate(file);
      boolean mustReloadLength = storedLength == -1;

      if (mustReloadLength
          || mustReloadContent(file)
          || FileUtilRt.isTooLarge(file.getLength())
          || (contentStream = readContent(file)) == null) {
        NewVirtualFileSystem fs = getFileSystem(file);
        len = mustReloadLength ? reloadLengthFromFS(file, fs) : storedLength;
        contentStream = fs.getInputStream(file);

        if (shouldCache(len)) {
          useReplicator = true;
        }
      }
    }
    finally {
      contentLoadingLock.readLock().unlock();
    }

    if (useReplicator) {
      contentStream = createReplicatorAndStoreContent(file, contentStream, len);
    }

    return contentStream;
  }

  private static boolean shouldCache(long len) {
    if (len > PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD) {
      return false;
    }
    return true;
  }

  private boolean mustReloadContent(@NotNull VirtualFile file) {
    return BitUtil.isSet(vfsPeer.getFlags(getFileId(file)), Flags.MUST_RELOAD_CONTENT);
  }

  private long reloadLengthFromFS(@NotNull VirtualFile file, @NotNull FileSystemInterface fs) {
    final long len = fs.getLength(file);
    int fileId = getFileId(file);
    setLength(fileId, len);
    return len;
  }

  private void setLength(int fileId, long len) {
    vfsPeer.setLength(fileId, len);
    setFlag(fileId, Flags.MUST_RELOAD_LENGTH, false);
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
                                     int byteLength) throws IOException {
    int fileId = getFileId(file);

    if (byteLength == fileLength) {
      ByteArraySequence newContent = new ByteArraySequence(bytes, 0, byteLength);
      updateContentForFile(fileId, newContent);
    }
    else {
      doCleanPersistedContent(fileId);
    }
  }

  private void updateContentForFile(int fileId,
                                    @NotNull ByteArraySequence newContent) throws IOException {
    //VFS content storage is append-only, hence storing could be done outside the lock:
    int newContentId = vfsPeer.writeContentRecord(newContent);

    ReadWriteLock contentLoadingLock = contentLoadingSegmentedLock[fileId % contentLoadingSegmentedLock.length];
    contentLoadingLock.writeLock().lock();
    try {
      updateContentId(fileId, newContentId, newContent.length());
    }
    finally {
      contentLoadingLock.writeLock().unlock();
    }
  }

  /** Method is obsolete, migrate to {@link #contentHashIfStored(VirtualFile)} instance method */
  @TestOnly
  @ApiStatus.Obsolete
  public static byte @Nullable [] getContentHashIfStored(@NotNull VirtualFile file) {
    return FSRecords.getInstance().getContentHash(getFileId(file));
  }

  @TestOnly
  public byte @Nullable [] contentHashIfStored(@NotNull VirtualFile file) {
    return FSRecords.getInstance().getContentHash(getFileId(file));
  }

  // returns last recorded length or -1 if it must reload from FS
  private long getLengthIfUpToDate(@NotNull VirtualFile file) {
    int fileId = getFileId(file);
    return BitUtil.isSet(vfsPeer.getFlags(fileId), Flags.MUST_RELOAD_LENGTH) ? -1 : vfsPeer.getLength(fileId);
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) {
    return new ByteArrayOutputStream() {
      private boolean closed; // protection against user calling .close() twice

      @Override
      public void close() throws IOException {
        if (closed) return;
        super.close();

        app.assertWriteAccessAllowed();

        long oldLength = getLastRecordedLength(file);
        VFileContentChangeEvent event = new VFileContentChangeEvent(
          requestor, file, file.getModificationStamp(), modStamp, file.getTimeStamp(), -1, oldLength, count);
        List<VFileEvent> events = List.of(event);
        fireBeforeEvents(getPublisher(), events);
        NewVirtualFileSystem fs = getFileSystem(file);
        // `FSRecords.ContentOutputStream` already buffered, no need to wrap in `BufferedStream`
        try (OutputStream persistenceStream = writeContent(file, /*contentOfFixedSize: */ fs.isReadOnly())) {
          persistenceStream.write(buf, 0, count);
        }
        finally {
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
      }
    };
  }

  @Override
  public int acquireContent(@NotNull VirtualFile file) {
    return vfsPeer.acquireFileContent(getFileId(file));
  }

  @Override
  public void releaseContent(int contentId) {
    vfsPeer.releaseContent(contentId);
  }

  @Override
  public int getCurrentContentId(@NotNull VirtualFile file) {
    return vfsPeer.getContentRecordId(getFileId(file));
  }

  public boolean isOwnData(@NotNull VfsData data) {
    return data == myVfsData;
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    getFileSystem(file).moveFile(requestor, file, newParent);
    processEvent(new VFileMoveEvent(requestor, file, newParent));
  }

  private void processEvent(@NotNull VFileEvent event) {
    app.assertWriteAccessAllowed();
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

  private static void runSuppressing(@NotNull Runnable r1, @NotNull Runnable r2, @NotNull Runnable r3) {
    Throwable t = null;
    try {
      r1.run();
    }
    catch (Throwable e) {
      t = e;
    }
    try {
      r2.run();
    }
    catch (Throwable e) {
      if (t == null) {
        t = e;
      }
      else {
        t.addSuppressed(e);
      }
    }
    try {
      r3.run();
    }
    catch (Throwable e) {
      if (t == null) {
        t = e;
      }
      else {
        t.addSuppressed(e);
      }
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
  private static int groupByPath(@NotNull List<? extends CompoundVFileEvent> events,
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
  private int groupAndValidate(@NotNull List<? extends CompoundVFileEvent> events,
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
  private void groupDeletions(@NotNull List<? extends CompoundVFileEvent> events,
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
  private void groupOthers(@NotNull List<? extends CompoundVFileEvent> events,
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

  private static final int INNER_ARRAYS_THRESHOLD = 1024; // max initial size, to avoid OOM on million-events processing

  @ApiStatus.Internal
  public void processEventsImpl(@NotNull List<? extends CompoundVFileEvent> events, boolean excludeAsyncListeners) {
    app.assertWriteAccessAllowed();

    int startIndex = 0;
    int cappedInitialSize = Math.min(events.size(), INNER_ARRAYS_THRESHOLD);
    List<Runnable> applyActions = new ArrayList<>(cappedInitialSize);
    // even in the unlikely case when case-insensitive maps falsely detect conflicts of case-sensitive paths,
    // the worst outcome will be one extra event batch, which is acceptable
    MostlySingularMultiMap<String, VFileEvent> files = new MostlySingularMultiMap<>(CollectionFactory.createFilePathMap(cappedInitialSize));
    Set<String> middleDirs = CollectionFactory.createFilePathSet(cappedInitialSize);

    List<VFileEvent> validated = new ArrayList<>(cappedInitialSize);
    BulkFileListener publisher = getPublisher();
    Map<VirtualDirectoryImpl, Object> toCreate = new LinkedHashMap<>();
    Set<VFileEvent> toIgnore = new ReferenceOpenHashSet<>(); // VFileEvent overrides equals(), hence identity-based
    Set<VirtualFile> toDelete = CollectionFactory.createSmallMemoryFootprintSet();
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

  private static void fireBeforeEvents(@NotNull BulkFileListener publisher, @NotNull List<? extends VFileEvent> toSend) {
    runSuppressing(
      () -> publisher.before(toSend),
      () -> ((BulkFileListener)VirtualFilePointerManager.getInstance()).before(toSend),
      () -> {
      }
    );
  }

  private static void fireAfterEvents(@NotNull BulkFileListener publisher, @NotNull List<? extends VFileEvent> toSend) {
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

      int parentId = getFileId(parent);
      List<CharSequence> childrenNamesDeleted = new ArrayList<>(deleteEvents.size());
      IntSet childrenIdsDeleted = new IntOpenHashSet(deleteEvents.size());
      List<ChildInfo> deleted = new ArrayList<>(deleteEvents.size());
      for (VFileDeleteEvent event : deleteEvents) {
        VirtualFile file = event.getFile();
        int id = getFileId(file);
        childrenNamesDeleted.add(file.getNameSequence());
        childrenIdsDeleted.add(id);
        vfsPeer.deleteRecordRecursively(id);
        invalidateSubtree(file, "Bulk file deletions", event);
        deleted.add(new ChildInfoImpl(id, ChildInfoImpl.UNKNOWN_ID_YET, null, null, null));
      }
      deleted.sort(ChildInfo.BY_ID);
      vfsPeer.update(parent, parentId, oldChildren -> oldChildren.subtract(deleted));
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
                                            @NotNull Collection<? extends VFileCreateEvent> createEvents) {
    int parentId = getFileId(parent);
    NewVirtualFile vf = findFileById(parentId);
    if (!(vf instanceof VirtualDirectoryImpl)) return;
    parent =
      (VirtualDirectoryImpl)vf;  // retain in `myIdToDirCache` at least for the duration of this block, so that subsequent `findFileById` won't crash
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
    vfsPeer.update(parent, parentId, oldChildren -> oldChildren.merge(vfsPeer, childrenAdded, caseSensitive));
    parent.createAndAddChildren(childrenAdded, false, (__, ___) -> {
    });

    saveScannedChildrenRecursively(createEvents, fs, parent.isCaseSensitive());
  }

  private void saveScannedChildrenRecursively(@NotNull Collection<? extends VFileCreateEvent> createEvents,
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
          vfsPeer.update(directory, directoryId, oldChildren -> oldChildren.merge(vfsPeer, added, isCaseSensitive));
          setChildrenCached(directoryId);
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
    if (!myConnected.get()) {
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
    VirtualFileSystemEntry root = myRoots.get(rootUrl);
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
      //FIXME RC: put special sentinel value into myRoots & myIdToDirCache so next attempt to resolve the root
      //          doesn't need to repeat the whole lookup process (which is slow)
      return null;
    }
    // assume roots have the FS default case sensitivity
    attributes = attributes.withCaseSensitivity(
      fs.isCaseSensitive() ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE);
    // avoid creating gazillions of roots which are not actual roots
    String parentPath;
    if (fs instanceof LocalFileSystem && !(parentPath = PathUtil.getParentPath(rootPath)).isEmpty()) {
      FileAttributes parentAttributes = loadAttributes(fs, parentPath);
      if (parentAttributes != null) {
        throw new IllegalArgumentException(
          "Must pass FS root path, but got: '" + path + "' (url: " + rootUrl + "), " +
          "which has a parent '" + parentPath + "'. " +
          "Use NewVirtualFileSystem.extractRootPath() for obtaining root path");
      }
    }

    int rootId = vfsPeer.findOrCreateRootRecord(rootUrl);
    vfsPeer.loadRootData(rootId, path, fs);


    int rootNameId = vfsPeer.getNameId(rootName);
    boolean markModified;
    VirtualFileSystemEntry newRoot;
    synchronized (myRoots) {
      root = myRoots.get(rootUrl);
      if (root != null) return root;

      try {
        String pathBeforeSlash = UriUtil.trimTrailingSlashes(rootPath);
        newRoot = new FsRoot(rootId, myVfsData, fs, pathBeforeSlash, attributes, path, this);
      }
      catch (VfsData.FileAlreadyCreatedException e) {
        for (Map.Entry<String, VirtualFileSystemEntry> entry : myRoots.entrySet()) {
          VirtualFileSystemEntry existingRoot = entry.getValue();
          if (existingRoot.getId() == rootId) {
            String message =
              "Duplicate FS roots: " + rootUrl + " / " + entry.getKey() + " id=" + rootId + " valid=" + existingRoot.isValid();
            throw new RuntimeException(message, e);
          }
        }
        throw new RuntimeException(
          "No root duplication, rootName='" + rootName + "'; rootNameId=" + rootNameId + "; rootId=" + rootId + ";" +
          " path='" + path + "'; fs=" + fs + "; rootUrl='" + rootUrl + "'", e);
      }
      incStructuralModificationCount();
      markModified = writeRootFields(rootId, rootName, fs.isCaseSensitive(), attributes) != -1;

      myRoots.put(rootUrl, newRoot);
      myIdToDirCache.cacheDir(newRoot);
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
    myIdToDirCache.dropNonRootCachedDirs();
  }

  @Override
  public @Nullable NewVirtualFile findFileById(int fileId) {
    if (fileId == FSRecords.NULL_FILE_ID) {
      fileByIdCacheHits.incrementAndGet();  //a bit of a stretch, but...
      return null;
    }
    VirtualFileSystemEntry cached = myIdToDirCache.getCachedDir(fileId);
    if (cached != null) {
      fileByIdCacheHits.incrementAndGet();
      return cached;
    }

    fileByIdCacheMisses.incrementAndGet();
    ParentFinder finder = new ParentFinder();
    return finder.find(fileId);
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
    try {
      vfsPeer.treeAccessor().forEachRoot((rootFileId, rootUrlId) -> {
        if (rootId == rootFileId) {
          missedRootUrlRef.set(getNameByNameId(rootUrlId));
        }
      });
    }
    catch (IOException e) {
      throw vfsPeer.handleError(e);
    }

    if (missedRootUrlRef.isNull()) {
      missedRootIds.add(rootId);
      THROTTLED_LOG.warn("Can't find root[#" + rootId + "] in persistence");
      return;
    }

    ensureRootCached(getRootPath(missedRootUrlRef.get(), vfsPeer.getName(rootId)), missedRootUrlRef.get());
  }

  /**
   * rootName == rootPath in case of roots that are not archives
   * but for archives e.g. jars rootName will be just name (see {@link PersistentFSImpl#findRoot})
   * so we need to extract path from url (IDEA-341011)
   * Path should not end with '!' because then '!' won't be stripped and file won't be found (see {@link ArchiveFileSystem#findLocalByRootPath})
   */
  @NotNull
  private static String getRootPath(@NotNull String rootUrl, @NotNull String rootName) {
    NewVirtualFileSystem fs = detectFileSystem(rootUrl, rootName);
    if (fs instanceof ArchiveFileSystem) {
      String path = VirtualFileManager.extractPath(rootUrl);
      return StringUtil.trimEnd(path, "!");
    }
    return rootName;
  }

  @VisibleForTesting
  NewVirtualFile ensureRootCached(@NotNull String missedRootPath, @NotNull String missedRootUrl) {
    NewVirtualFileSystem fs = detectFileSystem(missedRootUrl, missedRootPath);
    if (fs == null) {
      return null;
    }

    try {
      NewVirtualFile cachedRoot = findRoot(missedRootPath, fs);
      LOG.trace(
        "\tforce caching " + missedRootUrl + " (protocol: " + fs.getProtocol() + ", path: " + missedRootPath + ") -> " + cachedRoot);
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
  static @Nullable NewVirtualFileSystem detectFileSystem(@NotNull String rootUrl,
                                                         @NotNull String rootPath) {

    if (rootUrl.endsWith(":")) {
      if (OSAgnosticPathUtil.startsWithWindowsDrive(rootUrl) && rootUrl.length() == 2) {
        //Workaround for IDEA-331415: it shouldn't happen: rootUrl must be an url (even though sometimes not
        // fully correct URL), not a win-path -- but it sometimes happens, even though shouldn't:
        LOG.warn("detectFileSystem[root url='" + rootUrl + "', path='" + rootPath + "']: root URL is not an URL, but Win drive path");
        return LocalFileSystem.getInstance();
        //TODO RC: I hope this was just a fluck -- i.e. some VFS instances somehow got 'infected' by these wrong
        // root urls, but they wash off with time -- and the need for this branch disappears. Lets replace the
        // workaround with an AssertionError in v24.1, and see.
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
      LOG.warn("\tcan't force caching " + rootUrl + " (protocol: " + protocol + ", path: " + rootPath + ") " +
               "-> protocol:'" + protocol + "' is not registered (yet?)");
    }
    else {
      LOG.warn("\tcan't force caching " + rootUrl + " (protocol: " + protocol + ", path: " + rootPath + ") " +
               "-> " + fs + " is not NewVirtualFileSystem");
    }
    return null;
  }


  /**
   * We climb up from fileId, collecting parentIds (=path), until find a parent which is already cached
   * (in idToDirCache). From that (grand)parent we climb down (findDescendantByIdPath) back to fileId,
   * resolving every child on the way via idToDirCache:
   */
  final class ParentFinder {

    /**
     * List of parentIds towards the root (or first cached directory).
     * null, if the first parent is already cached
     */
    private @Nullable IntList parentIds;
    private VirtualFileSystemEntry foundParent;

    ParentFinder() {

    }

    private void ascendUntilCachedParent(int fileId) {
      int currentId = fileId;

      while (true) {

        int parentId = vfsPeer.getParent(currentId);

        if (parentId != FSRecords.NULL_FILE_ID) {
          foundParent = myIdToDirCache.getCachedDir(parentId);
          if (foundParent != null) {
            return;
          }
        }
        else {
          //RC: currentId is root, but not cached -- it is OK, root _could_ be not (yet) cached, since
          //    idToDirCache caches a root only during PersistentFSImpl.findRoot() call -- it could be
          //    that not all the roots known to FSRecords were requested at a given moment.
          //    => we need to force idToDirCache to cache the root it misses:
          cacheMissedRootFromPersistence(currentId);

          foundParent = myIdToDirCache.getCachedDir(currentId);
          if (foundParent != null) {
            //currentId is in the list, but shouldn't be, if it is == foundParent
            // => remove it
            if (parentIds != null && !parentIds.isEmpty()) {
              parentIds.removeInt(parentIds.size() - 1);
            }
            else {
              parentIds = null;
            }
            return;
          }


          //MAYBE RC: despite all the efforts the root entry wasn't found/loaded -- it means VFS is corrupted,
          // and we should throw assertion (VFS rebuild?).
          // But (it seems) the method .findFileById() is used in an assumption it just returns null if 'incorrect'
          // fileId is passed in? -- so I keep that legacy behaviour (just log warning with diagnostic) until I'll
          // be sure all 'legal' cases are covered:
          logVeryDetailedErrorMessageAboutParentNotFound(currentId, fileId);
          return;
        }


        if (parentIds != null && (parentIds.size() % 128 == 0 && parentIds.contains(parentId))) {
          //circularity check is expensive: do it only once-in-a-while, as path became deep enough
          //  to start to suspect something may be wrong.
          throw new AssertionError("Cyclic parent-child relations: fileId: " + fileId + ", parentId: " + parentId + ", path: " + parentIds);
        }

        if (parentIds == null) {
          parentIds = new IntArrayList(IntArrayList.DEFAULT_INITIAL_CAPACITY);
        }
        parentIds.add(parentId);

        currentId = parentId;
      }
    }

    private @Nullable VirtualFileSystemEntry findDescendantByIdPath(int fileId) {
      VirtualFileSystemEntry parent = foundParent;
      if (parentIds != null) {
        for (int i = parentIds.size() - 1; i >= 0; i--) {
          parent = findChild(parent, parentIds.getInt(i));
        }
      }

      return findChild(parent, fileId);
    }

    private @Nullable VirtualFileSystemEntry findChild(VirtualFileSystemEntry parent,
                                                       int childId) {
      if (!(parent instanceof VirtualDirectoryImpl)) {
        return null;
      }
      VirtualFileSystemEntry child = ((VirtualDirectoryImpl)parent).doFindChildById(childId);
      if (child instanceof VirtualDirectoryImpl) {
        if (child.getId() != childId) {
          LOG.error("doFindChildById(" + childId + "): " + child + " doesn't have expected id!");
        }
        VirtualFileSystemEntry old = myIdToDirCache.cacheDirIfAbsent(child);
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
          for (VirtualFileSystemEntry cachedRoot : myIdToDirCache.getCachedRootDirs()) {
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
              if (myIdToDirCache.getCachedDir(rootFileId) == null) {
                String rootName = vfsPeer.getName(rootFileId);
                nonCachedRootsPerLine.append("\t" + rootFileId + ": [name:'" + rootName + "'][url:'" + rootUrl + "']\n");
              }
            });
          }

          StringBuilder relativePath = new StringBuilder();
          if (parentIds != null) {
            for (int i = parentIds.size() - 1; i >= 0; i--) {
              int fileId = parentIds.getInt(i);
              String fileName = vfsPeer.getName(fileId);
              relativePath.append('/').append(fileName);
            }
          }


          return
            "file[" + startingFileId + ", flags: " + startingFileFlags + "]: " +
            "top parent (id: " + currentId + ", name: '" + preRootFileName + "', flags: " + preRootIdFlags + " parent: 0), " +
            "is still not in the idToDirCache. " +
            "path: " + parentIds + " [" + relativePath + "], " +
            "cachedRoots.size(=" + cachedRootsIds.size() + "), roots.size(=" + rootIds.size() + "), " +
            "pfs.roots.contains(" + currentId + ")=" + rootIds.contains(currentId) + ", " +
            "fs.roots.contains(" + currentId + ")=" + fsRootsHasCurrentId + ", " +
            "non-cached roots: " + nonCachedRoots.length + ", cached non-roots: " + cachedNonRoots.length + ", " +
            "FS roots not PFS roots: " + fsRootsNonPFSRoots.length + ": \n" + nonCachedRootsPerLine;
        }
      );
    }

    public NewVirtualFile find(int fileId) {
      assert fileId != FSRecords.NULL_FILE_ID : "fileId=NULL_ID(0) must not be passed into find()";
      try {
        ascendUntilCachedParent(fileId);
      }
      catch (Exception e) {
        throw vfsPeer.handleError(e);
      }
      return findDescendantByIdPath(fileId);
    }
  }

  @Override
  public VirtualFile @NotNull [] getRoots() {
    return VfsUtilCore.toVirtualFileArray(myRoots.values());
  }

  @Override
  public VirtualFile @NotNull [] getRoots(@NotNull NewVirtualFileSystem fs) {
    List<VirtualFile> roots = new ArrayList<>();

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
    SlowOperations.allowSlowOperations(() -> doApplyEvent(event));
  }

  private void doApplyEvent(@NotNull VFileEvent event) {
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
              LOG.debug("File " + file + " writable=" + file.isWritable() + " id=" + getFileId(file));
            }
          }
          case VirtualFile.PROP_HIDDEN -> executeSetHidden(file, ((Boolean)newValue).booleanValue());
          case VirtualFile.PROP_SYMLINK_TARGET -> executeSetTarget(file, (String)newValue);
          case VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY -> executeChangeCaseSensitivity(file, (FileAttributes.CaseSensitivity)newValue);
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

  @ApiStatus.Internal
  public void executeChangeCaseSensitivity(@NotNull VirtualFile file, @NotNull FileAttributes.CaseSensitivity newCaseSensitivity) {
    VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file;
    setFlag(directory, Flags.CHILDREN_CASE_SENSITIVE, newCaseSensitivity == FileAttributes.CaseSensitivity.SENSITIVE);
    setFlag(directory, Flags.CHILDREN_CASE_SENSITIVITY_CACHED, true);
    directory.setCaseSensitivityFlag(newCaseSensitivity);
  }

  @Override
  public String toString() {
    return "PersistentFS[connected: " + isConnected() + ", ownData: " + myVfsData + "]";
  }

  private void executeCreateChild(@NotNull VirtualFile parent,
                                  @NotNull String name,
                                  @Nullable FileAttributes attributes,
                                  @Nullable String symlinkTarget,
                                  boolean isEmptyDirectory) {
    NewVirtualFileSystem fs = getFileSystem(parent);
    int parentId = getFileId(parent);
    Pair<@NotNull FileAttributes, String> childData = getChildData(fs, parent, name, attributes, symlinkTarget);
    if (childData == null) {
      return;
    }
    ChildInfo childInfo = makeChildRecord(parent, parentId, name, childData, fs, null);
    vfsPeer.update(parent, parentId, children -> {
      // check that names are not duplicated
      ChildInfo duplicate = findExistingChildInfo(parent, name, children.children);
      if (duplicate != null) return children;

      return children.insert(childInfo);
    });
    int childId = childInfo.getId();
    assert parent instanceof VirtualDirectoryImpl : parent;
    VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
    int nameId = vfsPeer.getNameId(name);
    VirtualFileSystemEntry child = dir.createChild(childId, nameId, fileAttributesToFlags(childData.first), isEmptyDirectory
    );
    if (isEmptyDirectory) {
      // When creating an empty directory, we need to make sure every file created inside will fire a "file-created" event,
      // in order to `VirtualFilePointerManager` get those events to update its pointers properly
      // (because currently it ignores empty directory creation events for performance reasons).
      setChildrenCached(childId);
    }
    dir.addChild(child);
    incStructuralModificationCount();
  }

  private @NotNull ChildInfo makeChildRecord(@NotNull VirtualFile parentFile,
                                             int parentId,
                                             @NotNull CharSequence name,
                                             @NotNull Pair<@NotNull FileAttributes, String> childData,
                                             @NotNull NewVirtualFileSystem fs,
                                             @NotNull ChildInfo @Nullable [] children) {
    FileAttributes attributes = childData.first;

    int childId = vfsPeer.createRecord();
    int nameId = writeRecordFields(childId, parentId, name, attributes);

    if (attributes.isDirectory()) {
      FSRecords.loadDirectoryData(childId, parentFile, name, fs);
    }
    return new ChildInfoImpl(childId, nameId, attributes, children, childData.second);
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

  // return File attributes, symlink target
  // null when file not found
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
      symlinkTarget = attributes != null && attributes.isSymLink() ? fs.resolveSymLink(virtualFile) : null;
    }
    return attributes == null ? null : new Pair<>(attributes, symlinkTarget);
  }

  private void executeDelete(@NotNull VFileDeleteEvent event) {
    VirtualFile file = event.getFile();
    if (!file.exists()) {
      LOG.error("Deleting a file which does not exist: " + ((VirtualFileWithId)file).getId() + " " + file.getPath());
      return;
    }
    clearIdCache();

    int id = getFileId(file);

    VirtualFile parent = file.getParent();
    int parentId = parent == null ? 0 : getFileId(parent);

    if (parentId == 0) {
      String rootUrl = UriUtil.trimTrailingSlashes(file.getUrl());
      synchronized (myRoots) {
        myRoots.remove(rootUrl);
        myIdToDirCache.remove(id);
        vfsPeer.deleteRootRecord(id);
      }
    }
    else {
      removeIdFromChildren(parent, parentId, id);
      VirtualDirectoryImpl directory = (VirtualDirectoryImpl)file.getParent();
      assert directory != null : file;
      directory.removeChild(file);
    }

    vfsPeer.deleteRecordRecursively(id);

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

  private void removeIdFromChildren(@NotNull VirtualFile parent, int parentId, int childId) {
    ChildInfo toRemove = new ChildInfoImpl(childId, ChildInfoImpl.UNKNOWN_ID_YET, null, null, null);
    vfsPeer.update(parent, parentId, list -> list.remove(toRemove));
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
    ((PersistentFSImpl)PersistentFS.getInstance()).offlineByDefault(file, offlineByDefaultFlag);
  }

  @ApiStatus.Experimental
  public void offlineByDefault(@NotNull VirtualFile file, boolean offlineByDefaultFlag) {
    setFlag(file, Flags.OFFLINE_BY_DEFAULT, offlineByDefaultFlag);
    if (offlineByDefaultFlag) {
      ((VirtualFileSystemEntry)file).setOffline(true);
    }
  }

  private void executeSetTarget(@NotNull VirtualFile file, @Nullable String target) {
    int id = getFileId(file);
    vfsPeer.storeSymlinkTarget(id, target);
    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof LocalFileSystemImpl) {
      ((LocalFileSystemImpl)fs).symlinkUpdated(id, file.getParent(), file.getNameSequence(), file.getPath(), target);
    }
  }

  private void setFlag(@NotNull VirtualFile file, @Attributes int mask, boolean value) {
    setFlag(getFileId(file), mask, value);
  }

  private void setFlag(int id, @Attributes int mask, boolean value) {
    int oldFlags = vfsPeer.getFlags(id);
    int flags = value ? oldFlags | mask
                      : oldFlags & ~mask;

    if (oldFlags != flags) {
      //noinspection MagicConstant
      vfsPeer.setFlags(id, flags);
    }
  }

  private void executeTouch(@NotNull VirtualFile file,
                            boolean reloadContentFromFS,
                            long newModificationStamp,
                            long newLength,
                            long newTimestamp) {
    if (reloadContentFromFS) {
      setFlag(file, Flags.MUST_RELOAD_CONTENT, true);
    }

    int fileId = getFileId(file);
    setLength(fileId, newLength);
    vfsPeer.setTimestamp(fileId, newTimestamp);

    ((VirtualFileSystemEntry)file).setModificationStamp(newModificationStamp);
  }

  private void executeMove(@NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    clearIdCache();

    int fileId = getFileId(file);
    int newParentId = getFileId(newParent);
    VirtualFile oldParent = file.getParent();
    int oldParentId = getFileId(oldParent);

    VirtualFileSystemEntry virtualFileSystemEntry = (VirtualFileSystemEntry)file;

    removeIdFromChildren(oldParent, oldParentId, fileId);
    vfsPeer.setParent(fileId, newParentId);
    ChildInfo newChild = new ChildInfoImpl(fileId, virtualFileSystemEntry.getNameId(), null, null, null);
    vfsPeer.update(newParent, newParentId, children -> {
      // check that names are not duplicated
      ChildInfo duplicate = findExistingChildInfo(file, file.getName(), children.children);
      if (duplicate != null) return children;
      return children.insert(newChild);
    });
    virtualFileSystemEntry.setParent(newParent);
  }

  @Override
  public @NotNull String getName(int fileId) {
    assert fileId > 0;
    return vfsPeer.getName(fileId);
  }

  @TestOnly
  public void cleanPersistedContent(int id) {
    doCleanPersistedContent(id);
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
    ReadWriteLock rwLock = contentLoadingSegmentedLock[id % contentLoadingSegmentedLock.length];
    rwLock.writeLock().lock();
    try {
      setFlag(id, Flags.MUST_RELOAD_CONTENT | Flags.MUST_RELOAD_LENGTH, true);
    }
    finally {
      rwLock.writeLock().unlock();
    }
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
  @NotNull
  Iterable<? extends VirtualFileSystemEntry> getDirCache() {
    return myIdToDirCache.getCachedDirs();
  }

  static @Attributes int fileAttributesToFlags(@NotNull FileAttributes attributes) {
    FileAttributes.CaseSensitivity sensitivity = attributes.areChildrenCaseSensitive();
    boolean isCaseSensitive = sensitivity == FileAttributes.CaseSensitivity.SENSITIVE;
    return fileAttributesToFlags(attributes.isDirectory(), attributes.isWritable(), attributes.isSymLink(), attributes.isSpecial(),
                                 attributes.isHidden(), sensitivity != FileAttributes.CaseSensitivity.UNKNOWN, isCaseSensitive);
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

  private void setupOTelMonitoring(@NotNull Meter meter) {
    var fileByIdCacheHitsCounter = meter.counterBuilder("VFS.fileByIdCache.hits").buildObserver();
    var fileByIdCacheMissesCounter = meter.counterBuilder("VFS.fileByIdCache.misses").buildObserver();
    var fileChildByNameCounter = meter.counterBuilder("VFS.fileChildByName").buildObserver();
    var invertedFileNameIndexRequestsCount = meter.counterBuilder("VFS.invertedFileNameIndex.requests").buildObserver();
    meter.batchCallback(() -> {
      fileByIdCacheHitsCounter.record(fileByIdCacheHits.get());
      fileByIdCacheMissesCounter.record(fileByIdCacheMisses.get());
      fileChildByNameCounter.record(childByName.get());
      FSRecordsImpl vfs = vfsPeer;
      if (vfs != null) {
        invertedFileNameIndexRequestsCount.record(vfs.invertedNameIndexRequestsServed());
      }
    }, fileByIdCacheHitsCounter, fileByIdCacheMissesCounter, fileChildByNameCounter, invertedFileNameIndexRequestsCount);
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
