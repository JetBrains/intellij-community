// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements Disposable, BulkFileListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl");
  private static final Comparator<String> URL_COMPARATOR = SystemInfo.isFileSystemCaseSensitive ? String::compareTo : String::compareToIgnoreCase;

  private final TempFileSystem TEMP_FILE_SYSTEM;
  private final LocalFileSystem LOCAL_FILE_SYSTEM;
  private final Map<VirtualFilePointerListener, FilePointerPartNode> myPointers = new LinkedHashMap<>();  // guarded by this
  // compare by identity because VirtualFilePointerContainer has too smart equals
  private final Set<VirtualFilePointerContainerImpl> myContainers = ContainerUtil.newIdentityTroveSet();  // guarded by myContainers
  @NotNull private final VirtualFileManager myVirtualFileManager;
  @NotNull private final MessageBus myBus;

  VirtualFilePointerManagerImpl(@NotNull VirtualFileManager virtualFileManager,
                                @NotNull MessageBus bus,
                                @NotNull TempFileSystem tempFileSystem,
                                @NotNull LocalFileSystem localFileSystem) {
    myVirtualFileManager = virtualFileManager;
    myBus = bus;
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    TEMP_FILE_SYSTEM = tempFileSystem;
    LOCAL_FILE_SYSTEM = localFileSystem;
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
    }

    private void fireBefore() {
      if (myPointers.length != 0) {
        myListener.beforeValidityChanged(myPointers);
      }
    }

    private void fireAfter() {
      if (myPointers.length != 0) {
        myListener.validityChanged(myPointers);
      }
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
  synchronized VirtualFilePointer[] getPointersUnder(VirtualFile parent, String childName) {
    List<FilePointerPartNode> nodes = new ArrayList<>();
    addRelevantPointers(parent, true, childName, nodes);
    return toPointers(nodes);
  }

  private void addRelevantPointers(VirtualFile parent,
                                   boolean separator,
                                   @NotNull CharSequence childName,
                                   @NotNull List<FilePointerPartNode> out) {
    for (FilePointerPartNode root : myPointers.values()) {
      root.addRelevantPointersFrom(parent, separator, childName, out);
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

    if (fileSystem == TEMP_FILE_SYSTEM) {
      // for tests, recreate always
      VirtualFile found = file == null ? VirtualFileManager.getInstance().findFileByUrl(url) : file;
      return found == null ? new LightFilePointer(url) : new LightFilePointer(found);
    }

    boolean isJar = fileSystem instanceof VfpCapableArchiveFileSystem;
    if (fileSystem != LOCAL_FILE_SYSTEM && !isJar) {
      // we are unable to track alien file systems for now
      VirtualFile found = fileSystem == null ? null : file != null ? file : VirtualFileManager.getInstance().findFileByUrl(url);
      // if file is null, this pointer will never be alive
      return getOrCreateIdentity(url, found, recursive, parentDisposable, listener);
    }

    if (file == null) {
      String cleanPath = cleanupPath(path, isJar);
      // if newly created path is the same as the one extracted from url then the url did not change, we can reuse it
      //noinspection StringEquality
      if (cleanPath != path) {
        //noinspection ConstantConditions (when FS and protocol are null, the previous 'if' is true)
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
                                                                      boolean recursive, @NotNull Disposable parentDisposable,
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

  @NotNull
  private static String cleanupPath(@NotNull String path, boolean isJar) {
    path = FileUtil.normalize(path);
    path = trimTrailingSeparators(path, isJar);
    return path;
  }

  private static String trimTrailingSeparators(@NotNull String path, boolean isJar) {
    while (StringUtil.endsWithChar(path, '/') && !(isJar && path.endsWith(JarFileSystem.JAR_SEPARATOR))) {
      path = StringUtil.trimEnd(path, "/");
    }
    return path;
  }

  @NotNull
  private synchronized VirtualFilePointerImpl getOrCreate(@NotNull String path,
                                                          @Nullable VirtualFile file,
                                                          @NotNull String url,
                                                          boolean recursive, @NotNull Disposable parentDisposable,
                                                          @Nullable VirtualFilePointerListener listener) {
    FilePointerPartNode root = myPointers.get(listener);
    FilePointerPartNode node;
    Pair<VirtualFile, String> fileAndUrl = Pair.create(file, url);
    if (root == null) {
      root = new FilePointerPartNode(path, null, fileAndUrl, 1);
      myPointers.put(listener, root);
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
    for (Map.Entry<VirtualFilePointerListener, FilePointerPartNode> entry : myPointers.entrySet()) {
      FilePointerPartNode root = entry.getValue();
      List<FilePointerPartNode> left = new ArrayList<>();
      root.addRelevantPointersFrom(null, false, "", left);
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
    for (FilePointerPartNode root : myPointers.values()) {
      root.addRelevantPointersFrom(null, false, "", out);
    }
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
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
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
          final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
          addRelevantPointers(deleteEvent.getFile(), false, "", toFireEvents);
        }
        else if (event instanceof VFileCreateEvent) {
          final VFileCreateEvent createEvent = (VFileCreateEvent)event;
          addRelevantPointers(createEvent.getParent(), true, createEvent.getChildName(), toFireEvents);
        }
        else if (event instanceof VFileCopyEvent) {
          final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
          addRelevantPointers(copyEvent.getNewParent(), true, copyEvent.getFile().getName(), toFireEvents);
        }
        else if (event instanceof VFileMoveEvent) {
          final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
          VirtualFile eventFile = moveEvent.getFile();
          addRelevantPointers(moveEvent.getNewParent(), true, eventFile.getName(), toFireEvents);

          List<FilePointerPartNode> nodes = new ArrayList<>();
          addRelevantPointers(eventFile, false, "", nodes);
          toFireEvents.addAll(nodes); // files deleted from eventFile and created in moveEvent.getNewParent()
          collectNodes(nodes, toUpdateUrl);
        }
        else if (event instanceof VFilePropertyChangeEvent) {
          final VFilePropertyChangeEvent change = (VFilePropertyChangeEvent)event;
          if (VirtualFile.PROP_NAME.equals(change.getPropertyName())
              && !Comparing.equal(change.getOldValue(), change.getNewValue())) {
            VirtualFile eventFile = change.getFile();
            VirtualFile parent = eventFile.getParent(); // e.g. for LightVirtualFiles
            addRelevantPointers(parent, true, change.getNewValue().toString(), toFireEvents);

            List<FilePointerPartNode> nodes = new ArrayList<>();
            addRelevantPointers(eventFile, false, "", nodes);
            collectNodes(nodes, toUpdateUrl);
          }
        }
      }

      myEvents = eventList = new ArrayList<>();
      toFirePointers = toPointers(toFireEvents);
      for (final VirtualFilePointerListener listener : myPointers.keySet()) {
        if (listener == null) continue;
        List<VirtualFilePointer> filtered =
          ContainerUtil.filter(toFirePointers, pointer -> ((VirtualFilePointerImpl)pointer).getListener() == listener);
        if (!filtered.isEmpty()) {
          eventList.add(new EventDescriptor(listener, filtered.toArray(VirtualFilePointer.EMPTY_ARRAY)));
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

  private static void collectNodes(List<? extends FilePointerPartNode> nodes, List<? super FilePointerPartNode> toUpdateUrl) {
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
    for (FilePointerPartNode root : myPointers.values()) {
      root.checkConsistency();
    }
  }

  @Override
  public void after(@NotNull final List<? extends VFileEvent> events) {
    ApplicationManager.getApplication().assertIsDispatchThread(); // guarantees no attempts to get read action lock under "this" lock
    incModificationCount();

    synchronized (this) {
      for (FilePointerPartNode node : myNodesToUpdateUrl) {
        String urlBefore = node.myFileAndUrl.second;
        Pair<VirtualFile,String> after = node.update();
        assert after != null : "can't invalidate inside modification";
        String urlAfter = after.second;
        if (URL_COMPARATOR.compare(urlBefore, urlAfter) != 0 || !urlAfter.endsWith(node.part)) {
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
    }

    VirtualFilePointer[] pointersToFireArray;
    List<EventDescriptor> eventList;
    synchronized (this) {
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
      myPointers.remove(pointer.getListener());
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

    private final TObjectIntHashMap<VirtualFilePointerImpl> myCounts = new TObjectIntHashMap<>(); // guarded by this
    private final Disposable myParent;

    private DelegatingDisposable(@NotNull Disposable parent) {
      myParent = parent;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static void registerDisposable(@NotNull Disposable parentDisposable, @NotNull VirtualFilePointerImpl pointer) {
      DelegatingDisposable result = ourInstances.get(parentDisposable);
      if (result == null) {
        DelegatingDisposable newDisposable = new DelegatingDisposable(parentDisposable);
        result = ConcurrencyUtil.cacheOrGet(ourInstances, parentDisposable, newDisposable);
        if (result == newDisposable) {
          Disposer.register(parentDisposable, result);
        }
      }
      synchronized (result) {
        result.myCounts.put(pointer, result.myCounts.get(pointer) + 1);
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
      number = root.numberOfPointersUnder();
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
}