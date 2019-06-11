// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements Disposable, BulkFileListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl");
  private static final Comparator<String> URL_COMPARATOR = SystemInfo.isFileSystemCaseSensitive ? String::compareTo : String::compareToIgnoreCase;
  static final boolean IS_UNDER_UNIT_TEST = ApplicationManager.getApplication().isUnitTestMode();

  private static final VirtualFilePointerListener NULL_LISTENER = new VirtualFilePointerListener() {};
  private final Map<VirtualFilePointerListener, FilePointerPartNode> myPointers = ContainerUtil.newIdentityTroveMap(); // guarded by this
  // compare by identity because VirtualFilePointerContainer has too smart equals
  private final Set<VirtualFilePointerContainerImpl> myContainers = ContainerUtil.newIdentityTroveSet();  // guarded by myContainers
  @NotNull private final VirtualFileManager myVirtualFileManager;
  @NotNull private final MessageBus myBus;
  @NotNull private final FileTypeManager myFileTypeManager;

  VirtualFilePointerManagerImpl() {
    myVirtualFileManager = VirtualFileManager.getInstance();
    myBus = ApplicationManager.getApplication().getMessageBus();
    myFileTypeManager = FileTypeManager.getInstance();
    myBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  @Override
  public void dispose() {
    assertAllPointersDisposed();
  }

  private static class EventDescriptor {
    @NotNull private final VirtualFilePointerListener myListener;
    @NotNull private final VirtualFilePointer[] myPointers;

    private EventDescriptor(@NotNull VirtualFilePointerListener listener, @NotNull VirtualFilePointer[] pointers) {
      myListener = listener;
      myPointers = pointers;
      if (pointers.length == 0) throw new IllegalArgumentException();
    }

    private void fireBefore() {
      myListener.beforeValidityChanged(myPointers);
    }

    private void fireAfter() {
      myListener.validityChanged(myPointers);
    }
  }

  @NotNull
  private static VirtualFilePointer[] toPointers(@NotNull List<? extends FilePointerPartNode> nodes) {
    if (nodes.isEmpty()) return VirtualFilePointer.EMPTY_ARRAY;
    List<VirtualFilePointer> list = new ArrayList<>(nodes.size());
    for (FilePointerPartNode node : nodes) {
      node.addAllPointersTo(list);
    }
    return list.toArray(VirtualFilePointer.EMPTY_ARRAY);
  }

  @TestOnly
  @NotNull
  synchronized VirtualFilePointer[] getPointersUnder(@NotNull VirtualFile parent, @NotNull String childName) {
    List<FilePointerPartNode> nodes = new ArrayList<>();
    addRelevantPointers(parent, true, childName, nodes, true);
    return toPointers(nodes);
  }

  private void addRelevantPointers(VirtualFile parent,
                                   boolean separator,
                                   @NotNull CharSequence childName,
                                   @NotNull List<? super FilePointerPartNode> out, boolean addSubdirectoryPointers) {
    for (FilePointerPartNode root : myPointers.values()) {
      root.addRelevantPointersFrom(parent, separator, childName, out, addSubdirectoryPointers);
    }
  }

  @Override
  @NotNull
  public VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return create(null, url, parent, listener, false);
  }

  @Override
  @NotNull
  public VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return create(file, null, parent, listener, false);
  }

  @NotNull
  private VirtualFilePointer create(@Nullable("null means the pointer will be created from the (not null) url") VirtualFile file,
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
        fileSystem = myVirtualFileManager.getFileSystem(protocol);
        path = url.substring(protocolEnd + URLUtil.SCHEME_SEPARATOR.length());
      }
    }
    else {
      fileSystem = file.getFileSystem();
      protocol = fileSystem.getProtocol();
      path = file.getPath();
      url = VirtualFileManager.constructUrl(protocol, path);
    }

    if (fileSystem instanceof TempFileSystem && listener == null) {
      // Since VFS events work correctly in temp FS as well, ideally, this branch shouldn't exist and normal VFPointer should be used in all tests
      // but we have so many tests that create pointers, not dispose and leak them,
      // so for now we create normal pointers only when there are listeners.
      // maybe, later we'll fix all those tests
      VirtualFile found = file == null ? VirtualFileManager.getInstance().findFileByUrl(url) : file;
      return found == null ? new LightFilePointer(url) : new LightFilePointer(found);
    }

    if (!(fileSystem instanceof VirtualFilePointerCapableFileSystem)) {
      // we are unable to track alien file systems for now
      VirtualFile found = fileSystem == null ? null : file != null ? file : VirtualFileManager.getInstance().findFileByUrl(url);
      // if file is null, this pointer will never be alive
      return getOrCreateIdentity(url, found, recursive, parentDisposable, listener);
    }

    if (file == null) {
      String cleanPath = cleanupPath(path);
      // if newly created path is the same as the one extracted from url then the url did not change, we can reuse it
      //noinspection StringEquality
      if (cleanPath != path) {
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
      }
    }
    // else url has come from VirtualFile.getPath() and is good enough
    return getOrCreate(path, file, url, recursive, parentDisposable, listener);
  }

  private final Map<String, IdentityVirtualFilePointer> myUrlToIdentity = new THashMap<>(); // guarded by this

  @NotNull
  private synchronized IdentityVirtualFilePointer getOrCreateIdentity(@NotNull String url,
                                                                      @Nullable VirtualFile found,
                                                                      boolean recursive,
                                                                      @NotNull Disposable parentDisposable,
                                                                      @Nullable VirtualFilePointerListener listener) {
    IdentityVirtualFilePointer pointer = myUrlToIdentity.get(url);
    if (pointer == null) {
      pointer = new IdentityVirtualFilePointer(found, url, listener) {
        @Override
        public void dispose() {
          synchronized (VirtualFilePointerManagerImpl.this) {
            super.dispose();
            myUrlToIdentity.remove(url);
          }
        }
      };
      myUrlToIdentity.put(url, pointer);

      DelegatingDisposable.registerDisposable(parentDisposable, pointer);
    }
    pointer.incrementUsageCount(1);
    pointer.recursive = recursive;
    return pointer;
  }

  // convert \ -> /
  // convert // -> /
  // convert /. ->
  // trim trailing / (except when it's !/)
  @NotNull
  private static String cleanupPath(@NotNull String path) {
    path = FileUtilRt.toSystemIndependentName(path);
    path = trimTrailingSeparators(path);
    for (int i = 0; i < path.length(); ) {
      int slash = path.indexOf('/', i);
      if (slash == -1 || slash == path.length()-1) {
        break;
      }
      char next = path.charAt(slash + 1);

      if (next == '/' || next == '.' && (slash == path.length()-2 || path.charAt(slash+2) == '/')) {
        return cleanupTail(path, slash);
      }
      i = slash + 1;
    }
    return path;
  }

  // removes // and //. when we know for sure they are there, starting from 'slashIndex'
  @NotNull
  private static String cleanupTail(@NotNull String path, int slashIndex) {
    StringBuilder s = new StringBuilder(path.length());
    s.append(path, 0, slashIndex);
    for (int i = slashIndex; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '/') {
        char nextc = i == path.length()-1 ? 0 : path.charAt(i + 1);
        if (nextc == '.') {
          if (i == path.length() - 2) {
            // ends with "/.", ignore
            break;
          }
          char nextNextc = path.charAt(i + 2);
          if (nextNextc == '/') {
            i+=2;
            // "/./" in the middle, ignore "/."
            continue;
          }
          // "/.xxx", append
        }
        else if (nextc == '/') {
          // ignore duplicate /
          continue;
        }
      }
      s.append(c);
    }
    return s.toString();
  }

  @NotNull
  private static String trimTrailingSeparators(@NotNull String path) {
    if (StringUtil.endsWithChar(path, '/') && !path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = StringUtil.trimTrailing(path, '/');
    }
    return path;
  }

  @NotNull
  private synchronized VirtualFilePointerImpl getOrCreate(@NotNull String path,
                                                          @Nullable VirtualFile file,
                                                          @NotNull String url,
                                                          boolean recursive,
                                                          @NotNull Disposable parentDisposable,
                                                          @Nullable VirtualFilePointerListener listener) {
    VirtualFilePointerListener nl = ObjectUtils.notNull(listener, NULL_LISTENER);
    FilePointerPartNode root = myPointers.get(nl);
    FilePointerPartNode node;
    Pair<VirtualFile, String> fileAndUrl = Pair.create(file, url);
    if (root == null) {
      root = new FilePointerPartNode(path, null, fileAndUrl, 1);
      myPointers.put(nl, root);
      node = root;
    }
    else {
      node = root.findPointerOrCreate(path, 0, fileAndUrl, 1);
    }

    VirtualFilePointerImpl pointer = node.getAnyPointer();
    if (pointer == null) {
      pointer = new VirtualFilePointerImpl(listener);
      node.associate(pointer, fileAndUrl);
    }
    pointer.incrementUsageCount(1);
    pointer.recursive = recursive;

    root.checkConsistency();
    DelegatingDisposable.registerDisposable(parentDisposable, pointer);
    return pointer;
  }

  @Override
  @NotNull
  public VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer,
                                      @NotNull Disposable parent,
                                      @Nullable VirtualFilePointerListener listener) {
    VirtualFile file = pointer.getFile();
    return file == null ? create(pointer.getUrl(), parent, listener) : create(file, parent, listener);
  }

  private synchronized void assertAllPointersDisposed() {
    for (FilePointerPartNode root : myPointers.values()) {
      List<FilePointerPartNode> left = new ArrayList<>();
      root.addRelevantPointersFrom(null, false, "", left, true);
      List<VirtualFilePointerImpl> pointers = new ArrayList<>();
      for (FilePointerPartNode node : left) {
        node.addAllPointersTo(pointers);
      }
      if (!pointers.isEmpty()) {
        VirtualFilePointerImpl p = pointers.get(0);
        try {
          p.throwDisposalError("Not disposed pointer: "+p);
        }
        finally {
          for (VirtualFilePointerImpl pointer : pointers) {
            pointer.dispose();
          }
        }
      }
    }

    synchronized (myContainers) {
      if (!myContainers.isEmpty()) {
        VirtualFilePointerContainerImpl container = myContainers.iterator().next();
        container.throwDisposalError("Not disposed container");
      }
    }
  }

  @TestOnly
  synchronized void addAllPointersTo(@NotNull Collection<? super VirtualFilePointerImpl> pointers) {
    List<FilePointerPartNode> out = new ArrayList<>();
    addRelevantPointers(null, false, "", out, true);
    for (FilePointerPartNode node : out) {
      node.addAllPointersTo(pointers);
    }
  }

  @Override
  @NotNull
  public VirtualFilePointerContainer createContainer(@NotNull Disposable parent) {
    return createContainer(parent, null);
  }

  @Override
  @NotNull
  public synchronized VirtualFilePointerContainer createContainer(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return registerContainer(parent, new VirtualFilePointerContainerImpl(this, parent, listener));
  }

  @NotNull
  private VirtualFilePointerContainer registerContainer(@NotNull Disposable parent, @NotNull VirtualFilePointerContainerImpl container) {
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

  private List<EventDescriptor> myEvents = Collections.emptyList();
  private List<FilePointerPartNode> myNodesToUpdateUrl = Collections.emptyList();
  private List<FilePointerPartNode> myNodesToFire = Collections.emptyList();

  @Override
  public void before(@NotNull final List<? extends VFileEvent> events) {
    ApplicationManager.getApplication().assertIsDispatchThread(); // guarantees no attempts to get read action lock under "this" lock
    List<FilePointerPartNode> toFireEvents = new ArrayList<>();
    List<FilePointerPartNode> toUpdateUrl = new ArrayList<>();
    VirtualFilePointer[] toFirePointers;
    List<EventDescriptor> eventList;

    synchronized (this) {
      incModificationCount();
      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
          addRelevantPointers(deleteEvent.getFile(), false, "", toFireEvents, true);
        }
        else if (event instanceof VFileCreateEvent) {
          VFileCreateEvent createEvent = (VFileCreateEvent)event;
          String createdFileName = createEvent.getChildName();
          boolean fireSubdirectoryPointers;
          if (createEvent.isDirectory()) {
            // when a new empty directory "/a/b" is created, there's no need to fire any deeper pointers like "/a/b/c/d.txt" - they're not created yet
            // OTOH when refresh found a new directory "/a/b" which is non-empty, we must fire deeper pointers because they may exist already
            fireSubdirectoryPointers = !createEvent.isEmptyDirectory();
          }
          else {
            // if the .jar file created, there may be many files hiding inside
            FileType fileType = myFileTypeManager.getFileTypeByExtension(FileUtilRt.getExtension(createdFileName));
            fireSubdirectoryPointers = fileType instanceof ArchiveFileType;
          }
          addRelevantPointers(createEvent.getParent(), true, createdFileName, toFireEvents, fireSubdirectoryPointers);
        }
        else if (event instanceof VFileCopyEvent) {
          VFileCopyEvent copyEvent = (VFileCopyEvent)event;
          addRelevantPointers(copyEvent.getNewParent(), true, copyEvent.getNewChildName(), toFireEvents, true);
        }
        else if (event instanceof VFileMoveEvent) {
          VFileMoveEvent moveEvent = (VFileMoveEvent)event;
          VirtualFile eventFile = moveEvent.getFile();
          addRelevantPointers(moveEvent.getNewParent(), true, eventFile.getName(), toFireEvents, true);

          List<FilePointerPartNode> nodes = new ArrayList<>();
          addRelevantPointers(eventFile, false, "", nodes, true);
          toFireEvents.addAll(nodes); // files deleted from eventFile and created in moveEvent.getNewParent()
          collectNodes(nodes, toUpdateUrl);
        }
        else if (event instanceof VFilePropertyChangeEvent) {
          VFilePropertyChangeEvent change = (VFilePropertyChangeEvent)event;
          if (VirtualFile.PROP_NAME.equals(change.getPropertyName())
              && !Comparing.equal(change.getOldValue(), change.getNewValue())) {
            VirtualFile eventFile = change.getFile();
            VirtualFile parent = eventFile.getParent(); // e.g. for LightVirtualFiles
            addRelevantPointers(parent, true, change.getNewValue().toString(), toFireEvents, true);

            List<FilePointerPartNode> nodes = new ArrayList<>();
            addRelevantPointers(eventFile, false, "", nodes, true);
            collectNodes(nodes, toUpdateUrl);
          }
        }
      }

      myEvents = eventList = new ArrayList<>();
      toFirePointers = toPointers(toFireEvents);
      if (toFirePointers.length != 0) {
        for (final VirtualFilePointerListener listener : myPointers.keySet()) {
          if (listener == NULL_LISTENER) continue;
          List<VirtualFilePointer> filtered = ContainerUtil.filter(toFirePointers, pointer -> ((VirtualFilePointerImpl)pointer).getListener() == listener);
          if (!filtered.isEmpty()) {
            eventList.add(new EventDescriptor(listener, filtered.toArray(VirtualFilePointer.EMPTY_ARRAY)));
          }
        }
      }
    }

    for (EventDescriptor descriptor : eventList) {
      descriptor.fireBefore();
    }

    if (!toFireEvents.isEmpty()) {
      myBus.syncPublisher(VirtualFilePointerListener.TOPIC).beforeValidityChanged(toFirePointers);
    }

    synchronized (this) {
      myNodesToFire = toFireEvents;
      myNodesToUpdateUrl = toUpdateUrl;
    }

    assertConsistency();
  }

  private static void collectNodes(@NotNull List<? extends FilePointerPartNode> nodes, @NotNull List<? super FilePointerPartNode> toUpdateUrl) {
    for (FilePointerPartNode node : nodes) {
      VirtualFilePointerImpl pointer = node.getAnyPointer();
      if (pointer != null) {
        VirtualFile file = pointer.getFile();
        if (file != null) {
          toUpdateUrl.add(node);
        }
      }
    }
  }

  synchronized void assertConsistency() {
    if (IS_UNDER_UNIT_TEST && !ApplicationInfoImpl.isInStressTest()) {
      for (FilePointerPartNode root : myPointers.values()) {
        root.checkConsistency();
      }
    }
  }

  @Override
  public void after(@NotNull final List<? extends VFileEvent> events) {
    ApplicationManager.getApplication().assertIsDispatchThread(); // guarantees no attempts to get read action lock under "this" lock
    incModificationCount();
    VirtualFilePointer[] pointersToFireArray;
    List<EventDescriptor> eventList;

    synchronized (this) {
      for (FilePointerPartNode node : myNodesToUpdateUrl) {
        String urlBefore = node.myFileAndUrl.second;
        Pair<VirtualFile,String> after = node.update();
        assert after != null : "can't invalidate inside modification";
        String urlAfter = after.second;
        if (URL_COMPARATOR.compare(urlBefore, urlAfter) != 0 || !StringUtil.endsWith(urlAfter, node.part)) {
          List<VirtualFilePointerImpl> myPointers = new SmartList<>();
          node.addAllPointersTo(myPointers);

          // url has changed, reinsert
          int useCount = node.useCount;
          FilePointerPartNode root = node.remove();
          FilePointerPartNode newNode = root.findPointerOrCreate(VfsUtilCore.urlToPath(urlAfter), 0, after, myPointers.size());
          VirtualFilePointer existingPointer = newNode.getAnyPointer();
          if (existingPointer != null) {
            // can happen when e.g. file renamed to the existing file
            // merge two pointers
            for (FilePointerPartNode n = newNode; n != null; n = n.parent) {
              n.pointersUnder += myPointers.size();
            }
          }
          newNode.addAllPointersTo(myPointers);
          Object newMyPointers = myPointers.size() == 1 ? myPointers.get(0) : myPointers.toArray(new VirtualFilePointerImpl[0]);
          newNode.associate(newMyPointers, after);
          newNode.incrementUsageCount(useCount);
        }
      }
      pointersToFireArray = toPointers(myNodesToFire);
      eventList = myEvents;
    }
    for (VirtualFilePointer pointer : pointersToFireArray) {
      ((VirtualFilePointerImpl)pointer).myNode.update();
    }

    for (EventDescriptor event : eventList) {
      event.fireAfter();
    }

    if (pointersToFireArray.length != 0) {
      myBus.syncPublisher(VirtualFilePointerListener.TOPIC).validityChanged(pointersToFireArray);
    }

    synchronized (this) {
      myNodesToUpdateUrl = Collections.emptyList();
      myEvents = Collections.emptyList();
      myNodesToFire = Collections.emptyList();
    }
    assertConsistency();
  }

  synchronized void removeNodeFrom(@NotNull VirtualFilePointerImpl pointer) {
    FilePointerPartNode root = pointer.myNode.remove();
    boolean rootNodeEmpty = root.children.length == 0 ;
    if (rootNodeEmpty) {
      myPointers.remove(ObjectUtils.notNull(pointer.getListener(), NULL_LISTENER));
    }
    pointer.myNode = null;
    assertConsistency();
  }

  @Override
  public long getModificationCount() {
    // depends on PersistentFS.getStructureModificationCount() - because com.intellij.openapi.vfs.impl.FilePointerPartNode.update does
    // depends on its own modification counter - because we need to change both before and after VFS changes
    return super.getModificationCount() + PersistentFS.getInstance().getStructureModificationCount();
  }

  private static class DelegatingDisposable implements Disposable {
    private static final ConcurrentMap<Disposable, DelegatingDisposable> ourInstances = ConcurrentCollectionFactory.createMap(ContainerUtil.identityStrategy());

    private final TObjectIntHashMap<VirtualFilePointerImpl> myCounts = new TObjectIntHashMap<>(ContainerUtil.identityStrategy()); // guarded by this
    private final Disposable myParent;

    private DelegatingDisposable(@NotNull Disposable parent, @NotNull VirtualFilePointerImpl firstPointer) {
      myParent = parent;
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
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (result) {
        if (!result.myCounts.increment(pointer)) {
          result.myCounts.put(pointer, 1);
        }
      }
    }

    @Override
    public void dispose() {
      ourInstances.remove(myParent);
      synchronized (this) {
        myCounts.forEachEntry((pointer, disposeCount) -> {
          int after = pointer.incrementUsageCount(-disposeCount + 1);
          LOG.assertTrue(after > 0, after);
          pointer.dispose();
          return true;
        });
      }
    }
  }

  @NotNull
  @Override
  public VirtualFilePointer createDirectoryPointer(@NotNull String url,
                                                   boolean recursively,
                                                   @NotNull Disposable parent,
                                                   @NotNull VirtualFilePointerListener listener) {
    return create(null, url, parent, listener, true);
  }

  @TestOnly
  synchronized int numberOfPointers() {
    int number = 0;
    for (FilePointerPartNode root : myPointers.values()) {
      number += root.numberOfPointersUnder();
    }
    return number;
  }

  @TestOnly
  synchronized int numberOfListeners() {
    return myPointers.keySet().size();
  }

  @TestOnly
  synchronized int numberOfCachedUrlToIdentity() {
    return myUrlToIdentity.size();
  }

  // tests need to operate deterministic number of pointers, so we clear all of them out of the way during the test execution
  @TestOnly
  void shelveAllPointersIn(@NotNull Runnable runnable) {
    Map<VirtualFilePointerListener, FilePointerPartNode> shelvedPointers;
    synchronized (this) {
      shelvedPointers = new LinkedHashMap<>(myPointers);
      myPointers.clear();
    }
    try {
      runnable.run();
    }
    finally {
      synchronized (this) {
        myPointers.clear();
        myPointers.putAll(shelvedPointers);
      }
    }
  }

  @NotNull
  synchronized Collection<VirtualFilePointer> dumpPointers() {
    Collection<VirtualFilePointer> result = new THashSet<>();
    for (FilePointerPartNode node : myPointers.values()) {
      dumpPointersTo(node, result);
    }
    return result;
  }

  private static void dumpPointersTo(@NotNull FilePointerPartNode node, @NotNull Collection<? super VirtualFilePointer> result) {
    node.addAllPointersTo(result);
    for (FilePointerPartNode child : node.children) {
      dumpPointersTo(child, result);
    }
  }
}