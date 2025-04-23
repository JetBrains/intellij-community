// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public final class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements Disposable, BulkFileListener {
  private static final Logger LOG = Logger.getInstance(VirtualFilePointerManagerImpl.class);
  private static final boolean IS_UNDER_UNIT_TEST = ApplicationManager.getApplication().isUnitTestMode();
  private static final boolean IS_INTERNAL = ApplicationManager.getApplication().isInternal();
  private static final Key<Boolean> DISABLE_VFS_CONSISTENCY_CHECK_IN_TEST = Key.create("DISABLE_VFS_CONSISTENCY_CHECK_IN_TEST");

  static boolean shouldCheckConsistency() {
    return IS_UNDER_UNIT_TEST && !ApplicationManagerEx.isInStressTest()
           && !TestModeFlags.is(DISABLE_VFS_CONSISTENCY_CHECK_IN_TEST);
  }

  @TestOnly
  public static void disableConsistencyChecksInTestsTemporarily(@NotNull Disposable testDisposable) {
    TestModeFlags.set(DISABLE_VFS_CONSISTENCY_CHECK_IN_TEST, true, testDisposable);
  }

  /*
   virtual file pointers are stored in a trie structure rooted either here in myLocalRoot or in myTempRoot.
   vfp for a local file "file://c:/temp/x.txt" is stored in myLocalRoot->FilePartNode(c:)->FilePartNode(temp)->FilePartNode(x.txt)
   vfp for jar://c:/temp/x.jar!/META-INF" is stored in myLocalRoot->FilePartNode(c:)->FilePartNode(temp)->FilePartNode(x.jar)->FilePartNode(!/)->FilePartNode(META-INF)
   When the corresponding virtual file doesn't exist on disk, UrlPartNode is used instead of FilePartNode and replaced with the latter as soon as the file is created
  */
  private final FilePartNodeRoot myLocalRoot = FilePartNodeRoot.createFakeRoot(LocalFileSystem.getInstance()); // guarded by this
  private final FilePartNodeRoot myTempRoot = FilePartNodeRoot.createFakeRoot(TempFileSystem.getInstance()); // guarded by this
  // compare by identity because VirtualFilePointerContainer.equals() is too smart
  private final Set<VirtualFilePointerContainerImpl> myContainers = new ReferenceOpenHashSet<>();  // guarded by myContainers
  private final @NotNull VirtualFilePointerListener myPublisher;

  private int myPointerSetModCount;
  private volatile CollectedEvents myCollectedEvents;

  public VirtualFilePointerManagerImpl() {
    myPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFilePointerListener.TOPIC);
  }

  static final class MyAsyncFileListener implements AsyncFileListener {
    @Override
    public ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
      return ((VirtualFilePointerManagerImpl)getInstance()).prepareChange(events);
    }
  }

  static final class MyPersistentFsConnectionListener implements PersistentFsConnectionListener {

    @Override
    public void connectionOpen() {
      final var service = ApplicationManager.getApplication().getServiceIfCreated(VirtualFilePointerManager.class);
      if (service != null) {
        ((VirtualFilePointerManagerImpl)service).resolveUrlBasedPointers();
      }
    }

    @Override
    public void beforeConnectionClosed() {
      Application app = ApplicationManager.getApplication();
      VirtualFilePointerManager service = app == null ? null : app.getServiceIfCreated(VirtualFilePointerManager.class);
      if (service != null) {
        ((VirtualFilePointerManagerImpl)service).switchToUrlBasedPointers();
      }
    }
  }

  @Override
  public void dispose() {
    assertAllPointersDisposed();
  }

  private static final class EventDescriptor {
    private final @NotNull VirtualFilePointerListener myListener;
    private final VirtualFilePointer @NotNull [] myPointers;

    private EventDescriptor(@NotNull VirtualFilePointerListener listener, VirtualFilePointer @NotNull [] pointers) {
      myListener = listener;
      myPointers = pointers;
      if (pointers.length == 0) {
        throw new IllegalArgumentException();
      }
    }

    private void fireBefore() {
      myListener.beforeValidityChanged(myPointers);
    }


    private void fireAfter() {
      myListener.validityChanged(myPointers);
    }

    @Override
    public String toString() {
      return myListener + " -> " + Arrays.toString(myPointers);
    }
  }

  @TestOnly
  synchronized @NotNull Collection<? extends VirtualFilePointer> getPointersUnder(@NotNull VirtualFileSystemEntry parent, @NotNull String childName) {
    assert !StringUtil.isEmptyOrSpaces(childName);
    @NotNull MultiMap<VirtualFilePointerListener, VirtualFilePointerImpl> nodes = MultiMap.create();
    addRelevantPointers(null, parent, toNameId(childName), nodes, new ArrayList<>(), true, parent.getFileSystem(), new VFileDeleteEvent(this, parent));
    return nodes.values();
  }

  private void addRelevantPointers(@Nullable VirtualFile file,
                                   @NotNull VirtualFileSystemEntry parent,
                                   int childNameId,
                                   @NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers,
                                   @NotNull List<? super NodeToUpdate> toUpdateNodes,
                                   boolean addSubdirectoryPointers,
                                   @NotNull NewVirtualFileSystem fs,
                                   @NotNull VFileEvent event) {
    addRelevantPointers(parent, file, childNameId, toFirePointers, toUpdateNodes, addSubdirectoryPointers, fs, true, event);
  }

  private void addRelevantPointers(@NotNull VirtualFileSystemEntry parent,
                                   @Nullable VirtualFile file,
                                   int childNameId,
                                   @NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers,
                                   @NotNull List<? super NodeToUpdate> toUpdateNodes,
                                   boolean addSubdirectoryPointers,
                                   @NotNull NewVirtualFileSystem fs,
                                   boolean addRecursiveDirectoryPointers,
                                   @NotNull VFileEvent event) {
    getRoot(fs).addRelevantPointersFrom(parent, file, childNameId, toFirePointers, toUpdateNodes, addSubdirectoryPointers, fs,
                                        addRecursiveDirectoryPointers, event);
  }

  private @NotNull FilePartNodeRoot getRoot(@NotNull NewVirtualFileSystem fs) {
    // have to have at least two roots unfortunately: for the local and temp file systems because their paths can overlap
    return fs instanceof TempFileSystem ? myTempRoot : myLocalRoot;
  }

  @Override
  public @NotNull VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return create(null, url, parent, listener, false);
  }

  @Override
  public @NotNull VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return create(file, null, parent, listener, false);
  }

  private @NotNull VirtualFilePointer create(@Nullable("null means the pointer will be created from the (not null) url") VirtualFile file,
                                             @Nullable("null means url has to be computed from the (not-null) file path") String url,
                                             @NotNull Disposable parentDisposable,
                                             @Nullable VirtualFilePointerListener listener, boolean recursive) {
    VirtualFileSystem fileSystem;
    String protocol;
    String path;
    if (file == null) {
      //noinspection ConstantConditions (guaranteed by callers)
      int protocolEnd = url.indexOf(URLUtil.SCHEME_SEPARATOR);
      if (protocolEnd == -1) {
        protocol = null;
        fileSystem = null;
        path = url;
      }
      else {
        protocol = url.substring(0, protocolEnd);
        fileSystem = VirtualFileManager.getInstance().getFileSystem(protocol);
        path = url.substring(protocolEnd + URLUtil.SCHEME_SEPARATOR.length());
      }
      if (fileSystem == null) {
        if (IS_UNDER_UNIT_TEST || IS_INTERNAL) {
          throw new IllegalArgumentException("Unknown filesystem: '" + protocol + "' in url: '" + url+"'");
        }
        // will always be null
        return new LightFilePointer(url);
      }
    }
    else {
      fileSystem = file.getFileSystem();
      protocol = fileSystem.getProtocol();
      path = null;
      url = null;
    }

    if (fileSystem instanceof TempFileSystem && listener == null) {
      // Since VFS events work correctly in temp FS as well, ideally, this branch shouldn't exist and normal VFPointer should be used in all tests.
      // But we have so many tests that create pointers, not dispose and leak them,
      // so for now we create normal pointers only when there are listeners.
      // maybe, later we'll fix all those tests
      VirtualFile found = file == null ? VirtualFileManager.getInstance().findFileByUrl(url) : file;
      return found == null ? new LightFilePointer(url) : new LightFilePointer(found);
    }

    if (!(fileSystem instanceof VirtualFilePointerCapableFileSystem) || file != null && !(file instanceof VirtualFileSystemEntry)) {
      // we are unable to track alien file systems for now
      VirtualFile found = file == null ? VirtualFileManager.getInstance().findFileByUrl(url) : file;
      // if file is null, this pointer will never be alive
      if (url == null) {
        url = file.getUrl();
      }
      return getOrCreateIdentity(url, found, recursive, parentDisposable, listener);
    }

    if (file == null) {
      String cleanPath = cleanupPath(path);
      // if newly created path is the same as the one extracted from url then the url did not change, we can reuse it
      if (!Strings.areSameInstance(cleanPath, path)) {
        url = VirtualFileManager.constructUrl(protocol, cleanPath);
        path = cleanPath;
      }
      if (url.contains("..")) {
        // the url of the form "/x/../y" should resolve to "/y" (or something else in the case of symlinks)
        file = VirtualFileManager.getInstance().findFileByUrl(url);
        if (file != null) {
          url = file.getUrl();
          path = file.getPath();
        }
        else {
          // when someone is crazy enough to create VFP for not-yet existing file from the path with "..", ignore all symlinks
          path = FileUtil.toCanonicalPath(path);
          url = VirtualFileManager.constructUrl(protocol, path);
        }
      }
      if (file == null && StringUtil.isEmptyOrSpaces(path)) {
        // somebody tries to create pointer to root which is pointless but damages our fake root node.
        return getOrCreateIdentity(url, VirtualFileManager.getInstance().findFileByUrl(url), recursive, parentDisposable, listener);
      }
    }
    // else url has come from VirtualFile.getPath() and is good enough
    return getOrCreate((VirtualFileSystemEntry)file, path, url, recursive, parentDisposable, listener, (NewVirtualFileSystem)fileSystem);
  }

  private final Map<String, IdentityVirtualFilePointer> myUrlToIdentity = CollectionFactory.createSmallMemoryFootprintMap(); // guarded by this

  private synchronized @NotNull IdentityVirtualFilePointer getOrCreateIdentity(@NotNull String url,
                                                                               @Nullable VirtualFile found,
                                                                               boolean recursive,
                                                                               @NotNull Disposable parentDisposable,
                                                                               @Nullable VirtualFilePointerListener listener) {
    IdentityVirtualFilePointer pointer = myUrlToIdentity.get(url);
    if (pointer == null) {
      pointer = new IdentityVirtualFilePointer(found, url, myUrlToIdentity, this, listener);
      myUrlToIdentity.put(url, pointer);
      DelegatingDisposable.registerDisposable(parentDisposable, pointer);
    }
    pointer.incrementUsageCount(1);
    pointer.recursive = recursive;
    return pointer;
  }

  // convert \ -> /
  // convert // -> / (except // at the beginning of a UNC path)
  // convert /. ->
  // trim trailing /
  private static @NotNull String cleanupPath(@NotNull String path) {
    path = FileUtilRt.toSystemIndependentName(path);
    path = trimTrailingSeparators(path);
    for (int i = 0; i < path.length(); ) {
      int slash = path.indexOf('/', i);
      if (slash == -1 || slash == path.length()-1) {
        break;
      }
      char next = path.charAt(slash + 1);

      if (next == '/' && i != 0 ||
          next == '/' && !SystemInfo.isWindows ||// additional condition for Windows UNC
          next == '/' && slash == 2 && OSAgnosticPathUtil.startsWithWindowsDrive(path) || // Z://foo -> Z:/foo
          next == '.' && (slash == path.length()-2 || path.charAt(slash+2) == '/')) {
        return cleanupTail(path, slash);
      }
      i = slash + 1;
    }
    return path;
  }

  // removes // and //. when we know for sure they are there, starting from 'slashIndex'
  private static @NotNull String cleanupTail(@NotNull String path, int slashIndex) {
    StringBuilder s = new StringBuilder(path.length());
    s.append(path, 0, slashIndex);
    for (int i = slashIndex; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '/') {
        char nextC = i == path.length()-1 ? 0 : path.charAt(i + 1);
        if (nextC == '.') {
          if (i == path.length() - 2) {
            // ends with "/.", ignore
            break;
          }
          char nextNextC = path.charAt(i + 2);
          if (nextNextC == '/') {
            i++;
            // "/./" in the middle, ignore "/."
            continue;
          }
          // "/.xxx", append
        }
        else if (nextC == '/') {
          // ignore duplicate /
          continue;
        }
      }
      s.append(c);
    }
    return s.toString();
  }

  private static @NotNull String trimTrailingSeparators(@NotNull String path) {
    path = StringUtil.trimEnd(path, JarFileSystem.JAR_SEPARATOR);
    path = UriUtil.trimTrailingSlashes(path);
    return path;
  }

  private synchronized @NotNull VirtualFilePointerImpl getOrCreate(VirtualFileSystemEntry file,
                                                                   String path,
                                                                   String url,
                                                                   boolean recursive,
                                                                   @NotNull Disposable parentDisposable,
                                                                   @Nullable VirtualFilePointerListener listener,
                                                                   @NotNull NewVirtualFileSystem fs) {
    VirtualFileSystem fsFromFile = file == null ? VirtualFileManager.getInstance().getFileSystem(VirtualFileManager.extractProtocol(url)) : file.getFileSystem();
    assert fs == fsFromFile : "fs=" + fs + "; file.fs=" + fsFromFile+"; url='"+url+"'; file="+file;

    FilePartNodeRoot root = getRoot(fs);
    NodeToUpdate toUpdate;
    if (file == null) {
      String normPath = path;
      if (fs instanceof ArchiveFileSystem) {
        // check that "!/" separator is placed correctly in the url:
        // "xx!/yyy" has jar separator; but "xx/!/yyy" doesn't: directory separator only
        int index = -1;
        do {
          index = path.indexOf(JarFileSystem.JAR_SEPARATOR, index + 1);
        }
        while (index > 0 && path.charAt(index-1) == '/');
        if (index == -1 && !isArchiveInTheWindowsDiskRoot(path)) {
          // treat url "jar://xx/x.jar" as "jar://xx/x.jar!/"
          normPath = path + JarFileSystem.JAR_SEPARATOR;
        }
      }
      toUpdate = root.findOrCreateByPath(normPath, fs);
    }
    else {
      toUpdate = root.findOrCreateByFile(file);
    }
    FilePartNode node = toUpdate.node;
    if (fs != node.fs) {
      if (url != null && (IS_UNDER_UNIT_TEST || IS_INTERNAL)) {
        throw new IllegalArgumentException("Invalid url: '" + url + "'. " +
                                           "Its protocol '" + VirtualFileManager.extractProtocol(url) + "' is from " + fsFromFile +
                                           " but the path part points to " + node.fs);
      }
      LOG.error("fs=" + fs + "; node.myFS=" + node.fs + "; url=" + url + "; file=" + file + "; node=" + node);
    }

    VirtualFilePointerImpl pointer = node.getPointer(listener);
    if (pointer == null) {
      pointer = new VirtualFilePointerImpl(listener);
      node.addLeaf(pointer);
    }
    pointer.incrementUsageCount(1);
    if (!pointer.recursive) {
      pointer.recursive = recursive;
    }

    root.checkConsistency();
    DelegatingDisposable.registerDisposable(parentDisposable, pointer);
    myPointerSetModCount++;
    return pointer;
  }

  private static boolean isArchiveInTheWindowsDiskRoot(@NotNull String path) {
    // special case: "C:!/foo" means the archive is in the disk root - we shouldn't treat it as relative path starting with "!"
    // other special case: "C:/!/foo" means the archive is in the disk root - we shouldn't treat it as under the (local) directory "!" in the disk root
    return OSAgnosticPathUtil.startsWithWindowsDrive(path)
           && path.length() >= 4
           && (path.charAt(2) == '!'
               && path.charAt(3) == '/'
               ||
               path.length() >= 5
               && path.charAt(2) == '/'
               && path.charAt(3) == '!'
               && path.charAt(4) == '/'
           );
  }

  @Override
  public @NotNull VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer,
                                               @NotNull Disposable parent,
                                               @Nullable VirtualFilePointerListener listener) {
    VirtualFile file = pointer.getFile();
    return file == null ? create(pointer.getUrl(), parent, listener) : create(file, parent, listener);
  }

  synchronized void resolveUrlBasedPointers() {
    resolveUrlBasedPointers(myLocalRoot);
    resolveUrlBasedPointers(myTempRoot);
  }

  private static void resolveUrlBasedPointers(@NotNull FilePartNodeRoot root) {
    for (FilePartNode child : root.children) {
      if (child.isUrlBased()) {
        var resolvedChild = VirtualFileManager.getInstance().findFileByUrl(FilePartNode.urlOf(child.fileOrUrl));
        if (resolvedChild != null) {
          child = child.replaceWithFPPN(resolvedChild, root);
        }
      }

      resolveUrlBasedPointers(child, root, root);
    }
  }

  private static void resolveUrlBasedPointers(@NotNull FilePartNode node, @NotNull FilePartNode parent, @NotNull FilePartNodeRoot root) {
    node.update(parent, root, "VFPMI invalidated VFP during FS connection", null);

    for (FilePartNode child : node.children) {
      resolveUrlBasedPointers(child, node, root);
    }
  }

  synchronized void switchToUrlBasedPointers() {
    myLocalRoot.replaceChildrenWithUPN();
    myTempRoot.replaceChildrenWithUPN();
  }

  public synchronized void assertUrlBasedPointers() {
    assertUrlBasedPointers(myLocalRoot);
    assertUrlBasedPointers(myTempRoot);
  }

  private static void assertUrlBasedPointers(@NotNull FilePartNode node) {
    if (node.isUrlBased()) {
      for (FilePartNode child : node.children) {
        assertUrlBasedPointers(child);
      }
    }
    else {
      throw new IllegalStateException("Node for " + node.fileOrUrl + " is not a url-based");
    }
  }

  private synchronized void assertAllPointersDisposed() {
    List<VirtualFilePointer> leaked = new ArrayList<>(dumpAllPointers());
    leaked.sort(Comparator.comparing(VirtualFilePointer::getUrl));
    for (VirtualFilePointer pointer : leaked) {
      try {
        ((VirtualFilePointerImpl)pointer).throwDisposalError("Not disposed pointer: " + pointer);
      }
      finally {
        ((VirtualFilePointerImpl)pointer).dispose();
      }
    }

    synchronized (myContainers) {
      if (!myContainers.isEmpty()) {
        VirtualFilePointerContainerImpl container = myContainers.iterator().next();
        container.throwDisposalError("Not disposed container");
      }
    }
  }

  @Override
  public @NotNull VirtualFilePointerContainer createContainer(@NotNull Disposable parent) {
    return createContainer(parent, null);
  }

  @Override
  public synchronized @NotNull VirtualFilePointerContainer createContainer(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return registerContainer(parent, new VirtualFilePointerContainerImpl(this, parent, listener));
  }

  private @NotNull VirtualFilePointerContainer registerContainer(@NotNull Disposable parent, @NotNull VirtualFilePointerContainerImpl container) {
    synchronized (myContainers) {
      myContainers.add(container);
    }
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        Disposer.dispose(container);
        boolean removed;
        synchronized (myContainers) {
          removed = myContainers.remove(container);
        }
        if (!IS_UNDER_UNIT_TEST) {
          assert removed;
        }
      }

      @Override
      public String toString() {
        return "Disposing container " + container;
      }
    });
    return container;
  }

  private record CollectedEvents(@NotNull MultiMap<VirtualFilePointerListener, VirtualFilePointerImpl> toFirePointers,
                                 @NotNull List<NodeToUpdate> toUpdateNodes,
                                 @NotNull List<EventDescriptor> eventList,
                                 long startModCount,
                                 long prepareElapsedMs) {
  }

  static final class NodeToUpdate {
    private final FilePartNode parent;
    final FilePartNode node;
    VFileEvent myEvent;

    NodeToUpdate(@NotNull FilePartNode parent, @NotNull FilePartNode node) {
      this.parent = parent;
      this.node = node;
    }
  }

  private @NotNull CollectedEvents collectEvents(@NotNull List<? extends VFileEvent> events) {
    if (!hasAnyPointers()) {
      // e.g., in some VFS stress tests
      return new CollectedEvents(new MultiMap<>(), List.of(), List.of(), 0, 0);
    }
    long start = System.currentTimeMillis();
    MultiMap<VirtualFilePointerListener, VirtualFilePointerImpl> toFirePointers = MultiMap.create();
    List<NodeToUpdate> toUpdateNodes = new ArrayList<>();

    long startModCount;
    List<EventDescriptor> eventList;
    List<VirtualFilePointer> allPointersToFire;

    //noinspection SynchronizeOnThis
    synchronized (this) {
      startModCount = myPointerSetModCount;
      for (VFileEvent event : events) {
        ProgressManager.checkCanceled();
        VirtualFileSystem vfs = event.getFileSystem();
        if (!(vfs instanceof VirtualFilePointerCapableFileSystem) || !(vfs instanceof NewVirtualFileSystem fs)) continue;
        if (event instanceof VFileDeleteEvent deleteEvent) {
          VirtualFileSystemEntry file = (VirtualFileSystemEntry)deleteEvent.getFile();
          VirtualFileSystemEntry parent = (VirtualFileSystemEntry)FilePartNode.getParentThroughJar(file, file.getFileSystem());
          if (parent != null) {
            addRelevantPointers(file, parent, FilePartNode.getNameId(file), toFirePointers, toUpdateNodes, true, fs, event);
          }
        }
        else if (event instanceof VFileCreateEvent createEvent) {
          boolean fireSubdirectoryPointers;
          if (createEvent.isDirectory()) {
            // when a new empty directory "/a/b" is created, there's no need to fire any deeper pointers like "/a/b/c/d.txt" - they're not created yet
            // OTOH when refresh found a new directory "/a/b" which is non-empty, we must fire deeper pointers because they may exist already
            fireSubdirectoryPointers = !createEvent.isEmptyDirectory();
          }
          else {
            String createdFileName = createEvent.getChildName();
            // if the .jar file created, there may be many files hiding inside
            FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(FileUtilRt.getExtension(createdFileName));
            fireSubdirectoryPointers = fileType instanceof ArchiveFileType;
          }
          addRelevantPointers(null, (VirtualFileSystemEntry)createEvent.getParent(), createEvent.getChildNameId(), toFirePointers,
                              toUpdateNodes, fireSubdirectoryPointers, fs, event);
        }
        else if (event instanceof VFileCopyEvent copyEvent) {
          addRelevantPointers(null, (VirtualFileSystemEntry)copyEvent.getNewParent(), toNameId(copyEvent.getNewChildName()), toFirePointers,
                              toUpdateNodes, true, fs, event);
        }
        else if (event instanceof VFileMoveEvent moveEvent) {
          VirtualFileSystemEntry eventFile = (VirtualFileSystemEntry)moveEvent.getFile();
          int newNameId = FilePartNode.getNameId(eventFile);
          // files deleted from eventFile and created in moveEvent.getNewParent()
          addRelevantPointers(null, (VirtualFileSystemEntry)moveEvent.getNewParent(), newNameId, toFirePointers, toUpdateNodes, true, fs, event);

          VirtualFileSystemEntry parent = (VirtualFileSystemEntry)FilePartNode.getParentThroughJar(eventFile, eventFile.getFileSystem());
          if (parent != null) {
            addRelevantPointers(eventFile, parent, newNameId, toFirePointers, toUpdateNodes, true, fs, event);
          }
        }
        else if (event instanceof VFilePropertyChangeEvent change) {
          if (VirtualFile.PROP_NAME.equals(change.getPropertyName()) && !Comparing.equal(change.getOldValue(), change.getNewValue())) {
            VirtualFileSystemEntry eventFile = (VirtualFileSystemEntry)change.getFile();
            VirtualFileSystemEntry parent = (VirtualFileSystemEntry)FilePartNode.getParentThroughJar(eventFile, eventFile.getFileSystem());
            // e.g., for LightVirtualFiles
            if (parent != null) {
              int newNameId = toNameId(change.getNewValue().toString());
              addRelevantPointers(eventFile, parent, newNameId, toFirePointers, toUpdateNodes, true, fs, event);

              // old pointers remain valid after rename, no need to fire
              addRelevantPointers(parent, eventFile, FilePartNode.getNameId(eventFile), toFirePointers, toUpdateNodes, true, fs, false, event);
            }
          }
        }
      }

      eventList = new ArrayList<>();
      allPointersToFire = new ArrayList<>();
      groupPointersToFire(toFirePointers, eventList, allPointersToFire);
    }
    if (!allPointersToFire.isEmpty()) {
      VirtualFilePointer[] allPointers = allPointersToFire.toArray(VirtualFilePointer.EMPTY_ARRAY);
      eventList.add(new EventDescriptor(myPublisher, allPointers));
    }
    long prepareElapsedMs = System.currentTimeMillis() - start;

    return new CollectedEvents(toFirePointers, toUpdateNodes, eventList, startModCount, prepareElapsedMs);
  }

  private boolean hasAnyPointers() {
    return myLocalRoot.children.length != 0 || myTempRoot.children.length != 0;
  }

  // converts multi-map with pointers-to-fire into convenient
  // - (listener->pointers created with this listener) map for firing individual listeners and
  // - allPointersToFire list to fire in bulk via VirtualFilePointerListener.TOPIC
  private static void groupPointersToFire(@NotNull MultiMap<VirtualFilePointerListener, VirtualFilePointerImpl> toFirePointers,
                                          @NotNull List<? super EventDescriptor> eventList,
                                          @NotNull List<? super VirtualFilePointer> allPointersToFire) {
    for (Map.Entry<VirtualFilePointerListener, Collection<VirtualFilePointerImpl>> entry : toFirePointers.entrySet()) {
      VirtualFilePointerListener listener = entry.getKey();
      if (listener == null) continue;
      Collection<VirtualFilePointerImpl> values = entry.getValue();
      VirtualFilePointerImpl[] array = values.toArray(new VirtualFilePointerImpl[0]);
      if (array.length != 0) {
        eventList.add(new EventDescriptor(listener, array));
        ContainerUtil.addAll(allPointersToFire, array);
      }
    }
  }

  @NotNull
  ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
    myCollectedEvents = collectEvents(events);
    return new ChangeApplier() {
      @Override
      public void beforeVfsChange() {
        CollectedEvents collected;
        //noinspection SynchronizeOnThis
        synchronized (VirtualFilePointerManagerImpl.this) {
          collected = myCollectedEvents;
          if (collected.startModCount == myPointerSetModCount) {
            incModificationCount();
          }
          else {
            myCollectedEvents = collected = collectEvents(events);
          }
        }

        for (EventDescriptor descriptor : collected.eventList) {
          descriptor.fireBefore();
        }

        assertConsistency();
      }
    };
  }

  private static int toNameId(@NotNull String name) {
    return FSRecords.getInstance().getNameId(name);
  }

  synchronized void assertConsistency() {
    if (IS_UNDER_UNIT_TEST && !ApplicationManagerEx.isInStressTest()) {
      myLocalRoot.checkConsistency();
      myTempRoot.checkConsistency();
    }
  }

  @Override
  public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
    if (myCollectedEvents == null) {
      myCollectedEvents = collectEvents(events);
    }
  }

  @Override
  public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
    after(events.size());
  }

  private void after(int eventsSize) {
    CollectedEvents collectedEvents = myCollectedEvents;
    if (collectedEvents == null) {
      // some crazy invalid event nesting happened, like in DbSrcFileSystem.getOutputStream
      // where refresh is called right inside stream.close(), leading to before(), before(), after(), after() events
      return;
    }
    myCollectedEvents = null;
    long start = System.currentTimeMillis();
    ApplicationManager.getApplication().assertWriteIntentLockAcquired(); // guarantees no attempts to get read action lock under "this" lock
    incModificationCount();

    //noinspection SynchronizeOnThis
    synchronized (this) {
      for (NodeToUpdate toUpdate : collectedEvents.toUpdateNodes) {
        FilePartNode parent = toUpdate.parent;
        FilePartNode node = toUpdate.node;

        node.update(parent, getRoot(node.fs), "VFPMI invalidated VFP during update", toUpdate.myEvent);
      }
    }

    for (EventDescriptor event : collectedEvents.eventList) {
      event.fireAfter();
    }

    assertConsistency();
    long afterElapsedMs = System.currentTimeMillis() - start;
    if (afterElapsedMs > 1000 || collectedEvents.prepareElapsedMs > 1000) {
      LOG.warn("VirtualFilePointerManagerImpl.prepareChange(" + eventsSize + " events): " + collectedEvents.prepareElapsedMs + "ms."
               + "; total pointers: " + numberOfPointers()
               + "; afterElapsedMs: " + afterElapsedMs + "ms.; eventList.size(): " + collectedEvents.eventList.size()
               + "; toFirePointers.size(): " + collectedEvents.toFirePointers.size() + "; toUpdateNodes.size(): " + collectedEvents.toUpdateNodes.size()
               + "; eventList: " + ContainerUtil.getFirstItems(collectedEvents.eventList, 100));
    }
  }

  synchronized boolean decrementUsageCount(@NotNull VirtualFilePointerImpl pointer) {
    boolean shouldKill = pointer.incrementUsageCount(-1) == 0;
    if (!shouldKill) {
      return false;
    }
    getRoot(pointer.getNode().fs).removePointer(pointer);
    pointer.myNode = null;
    assertConsistency();
    myPointerSetModCount++;
    return true;
  }

  @Override
  public long getModificationCount() {
    // Depends on PersistentFS.getStructureModificationCount() - because com.intellij.openapi.vfs.impl.FilePartNode.update does.
    // Depends on its own modification counter - because we need to change both before and after VFS changes
    return super.getModificationCount() + PersistentFS.getInstance().getStructureModificationCount();
  }

  private static final class DelegatingDisposable implements Disposable {
    private static final ConcurrentMap<Disposable, DelegatingDisposable> ourInstances = ConcurrentCollectionFactory.createConcurrentIdentityMap();
    private final Reference2IntOpenHashMap<VirtualFilePointerImpl> myCounts = new Reference2IntOpenHashMap<>(); // guarded by this
    private final Disposable myParent;

    private DelegatingDisposable(@NotNull Disposable parent, @NotNull VirtualFilePointerImpl firstPointer) {
      myParent = parent;
      //noinspection SynchronizeOnThis
      synchronized (this) {
        myCounts.put(firstPointer, 1);
      }
    }

    private static void registerDisposable(@NotNull Disposable parentDisposable, @NotNull VirtualFilePointerImpl pointer) {
      DelegatingDisposable result = ourInstances.get(parentDisposable);
      if (result == null) {
        DelegatingDisposable newDisposable = new DelegatingDisposable(parentDisposable, pointer);
        result = ConcurrencyUtil.cacheOrGet(ourInstances, parentDisposable, newDisposable);
        if (result == newDisposable) {
          Disposer.register(parentDisposable, result);
          return;
        }
      }
      result.increment(pointer);
    }

    synchronized void increment(@NotNull VirtualFilePointerImpl pointer) {
      myCounts.addTo(pointer, 1);
    }

    @Override
    public void dispose() {
      ourInstances.remove(myParent);
      //noinspection SynchronizeOnThis
      synchronized (this) {
        for (Iterator<Reference2IntMap.Entry<VirtualFilePointerImpl>> iterator = myCounts.reference2IntEntrySet().fastIterator(); iterator.hasNext(); ) {
          Reference2IntMap.Entry<VirtualFilePointerImpl> entry = iterator.next();
          VirtualFilePointerImpl pointer = entry.getKey();
          int disposeCount = entry.getIntValue();
          boolean isDisposed = !(pointer instanceof IdentityVirtualFilePointer) && pointer.getNode() == null;
          if (isDisposed) {
            pointer.throwDisposalError("Already disposed:\n" + pointer.getStackTrace());
          }
          int after = pointer.incrementUsageCount(-(disposeCount - 1));
          LOG.assertTrue(after > 0, after);
          pointer.dispose();
        }
        myCounts.clear();
      }
    }
  }

  @Override
  public @NotNull VirtualFilePointer createDirectoryPointer(@NotNull String url,
                                                            boolean recursively,
                                                            @NotNull Disposable parent,
                                                            @NotNull VirtualFilePointerListener listener) {
    return create(null, url, parent, listener, true);
  }

  @TestOnly
  synchronized int numberOfPointers() {
    return dumpAllPointers().size();
  }

  @TestOnly
  synchronized int numberOfListeners() {
    return ContainerUtil.count(dumpAllPointers(), pointer -> ((VirtualFilePointerImpl)pointer).myListener != null);
  }

  @TestOnly
  synchronized int numberOfCachedUrlToIdentity() {
    return myUrlToIdentity.size();
  }

  // some tests need to operate on the deterministic number of pointers, so we clear all of them out of the way during the test execution
  @TestOnly
  void shelveAllPointersIn(@NotNull Runnable runnable) {
    FilePartNode[] oldChildren;
    //noinspection SynchronizeOnThis
    FilePartNodeRoot localRoot = myLocalRoot;
    synchronized (this) {
      oldChildren = localRoot.children;
      localRoot.children = FilePartNode.EMPTY_ARRAY;
    }
    try {
      runnable.run();
    }
    finally {
      //noinspection SynchronizeOnThis
      synchronized (this) {
        localRoot.children = oldChildren;
      }
    }
  }

  synchronized @NotNull Collection<VirtualFilePointer> dumpAllPointers() {
    Collection<VirtualFilePointer> result = new ArrayList<>();
    dumpPointersRecursivelyTo(myLocalRoot, result);
    dumpPointersRecursivelyTo(myTempRoot, result);
    return result;
  }

  private static void dumpPointersRecursivelyTo(@NotNull FilePartNode node, @NotNull Collection<? super VirtualFilePointer> result) {
    node.addAllPointersTo(result);
    for (FilePartNode child : node.children) {
      dumpPointersRecursivelyTo(child, result);
    }
  }
}
