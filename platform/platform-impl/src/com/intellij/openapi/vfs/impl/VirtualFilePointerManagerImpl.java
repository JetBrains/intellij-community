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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
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
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements ApplicationComponent, ModificationTracker, BulkFileListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl");
  private final TempFileSystem TEMP_FILE_SYSTEM;
  private final LocalFileSystem LOCAL_FILE_SYSTEM;
  private final JarFileSystem JAR_FILE_SYSTEM;
  // guarded by this
  private final Map<VirtualFilePointerListener, FilePointerPartNode> myPointers = new LinkedHashMap<>();

  // compare by identity because VirtualFilePointerContainer has too smart equals
  // guarded by myContainers
  private final Set<VirtualFilePointerContainerImpl> myContainers = ContainerUtil.newIdentityTroveSet();
  @NotNull private final VirtualFileManager myVirtualFileManager;
  @NotNull private final MessageBus myBus;
  private static final Comparator<String> URL_COMPARATOR = SystemInfo.isFileSystemCaseSensitive ? (Comparator<String>)(url1, url2) -> url1.compareTo(url2)
                                                                                                : (Comparator<String>)(url1, url2) -> url1.compareToIgnoreCase(url2);

  VirtualFilePointerManagerImpl(@NotNull VirtualFileManager virtualFileManager,
                                @NotNull MessageBus bus,
                                @NotNull TempFileSystem tempFileSystem,
                                @NotNull LocalFileSystem localFileSystem,
                                @NotNull JarFileSystem jarFileSystem) {
    myVirtualFileManager = virtualFileManager;
    myBus = bus;
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    TEMP_FILE_SYSTEM = tempFileSystem;
    LOCAL_FILE_SYSTEM = localFileSystem;
    JAR_FILE_SYSTEM = jarFileSystem;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    assertAllPointersDisposed();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "VirtualFilePointerManager";
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
  private static VirtualFilePointer[] toPointers(@NotNull List<FilePointerPartNode> nodes) {
    if (nodes.isEmpty()) return VirtualFilePointer.EMPTY_ARRAY;
    List<VirtualFilePointer> list = new ArrayList<>(nodes.size());
    for (FilePointerPartNode node : nodes) {
      node.addAllPointersTo(list);
    }
    return list.toArray(new VirtualFilePointer[list.size()]);
  }

  @TestOnly
  VirtualFilePointer[] getPointersUnder(VirtualFile parent, String childName) {
    List<FilePointerPartNode> nodes = new ArrayList<>();
    addPointersUnder(parent, true, childName, nodes);
    return toPointers(nodes);
  }

  private void addPointersUnder(VirtualFile parent,
                                boolean separator,
                                @NotNull CharSequence childName,
                                @NotNull List<FilePointerPartNode> out) {
    for (FilePointerPartNode root : myPointers.values()) {
      root.addPointersUnder(parent, separator, childName, out);
    }
  }

  @Override
  @NotNull
  public synchronized VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return create(null, url, parent, listener);
  }

  @Override
  @NotNull
  public synchronized VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    return create(file, null, parent, listener);
  }

  @NotNull
  private VirtualFilePointer create(@Nullable("null means the pointer will be created from the (not null) url") VirtualFile file,
                                    @Nullable("null means url has to be computed from the (not-null) file path") String url,
                                    @NotNull Disposable parentDisposable,
                                    @Nullable VirtualFilePointerListener listener) {
    VirtualFileSystem fileSystem;
    String protocol;
    String path;
    if (file == null) {
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
      return new IdentityVirtualFilePointer(found, url);
    }

    boolean isJar = fileSystem == JAR_FILE_SYSTEM;
    if (fileSystem != LOCAL_FILE_SYSTEM && !isJar) {
      // we are unable to track alien file systems for now
      VirtualFile found = fileSystem == null ? null : file != null ? file : VirtualFileManager.getInstance().findFileByUrl(url);
      // if file is null, this pointer will never be alive
      return getOrCreateIdentity(url, found);
    }

    if (file == null) {
      String cleanPath = cleanupPath(path, isJar);
      // if newly created path is the same as substringed from url one then the url did not change, we can reuse it
      //noinspection StringEquality
      if (cleanPath != path) {
        url = VirtualFileManager.constructUrl(protocol, cleanPath);
        path = cleanPath;
      }
    }
    // else url has come from VirtualFile.getPath() and is good enough

    VirtualFilePointerImpl pointer = getOrCreate(parentDisposable, listener, path, Pair.create(file, url));
    DelegatingDisposable.registerDisposable(parentDisposable, pointer);
    return pointer;
  }

  private final Map<String, IdentityVirtualFilePointer> myUrlToIdentity = new THashMap<>();
  @NotNull
  private IdentityVirtualFilePointer getOrCreateIdentity(@NotNull String url, @Nullable VirtualFile found) {
    IdentityVirtualFilePointer pointer = myUrlToIdentity.get(url);
    if (pointer == null) {
      pointer = new IdentityVirtualFilePointer(found, url);
      myUrlToIdentity.put(url, pointer);
    }
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
  private VirtualFilePointerImpl getOrCreate(@NotNull Disposable parentDisposable,
                                             @Nullable VirtualFilePointerListener listener,
                                             @NotNull String path,
                                             @NotNull Pair<VirtualFile, String> fileAndUrl) {
    FilePointerPartNode root = myPointers.get(listener);
    FilePointerPartNode node;
    if (root == null) {
      root = new FilePointerPartNode(path, null, fileAndUrl);
      root.pointersUnder++;
      myPointers.put(listener, root);
      node = root;
    }
    else {
      node = root.findPointerOrCreate(path, 0, fileAndUrl, 1);
    }

    VirtualFilePointerImpl pointer = node.getAnyPointer();
    if (pointer == null) {
      pointer = new VirtualFilePointerImpl(listener, parentDisposable, fileAndUrl);
      node.associate(pointer, fileAndUrl);
    }
    pointer.myNode.incrementUsageCount(1);

    root.checkConsistency();
    return pointer;
  }

  @Override
  @NotNull
  public synchronized VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer,
                                                   @NotNull Disposable parent,
                                                   @Nullable VirtualFilePointerListener listener) {
    VirtualFile file = pointer.getFile();
    return file == null ? create(pointer.getUrl(), parent, listener) : create(file, parent, listener);
  }

  private synchronized void assertAllPointersDisposed() {
    for (Map.Entry<VirtualFilePointerListener, FilePointerPartNode> entry : myPointers.entrySet()) {
      FilePointerPartNode root = entry.getValue();
      List<FilePointerPartNode> left = new ArrayList<>();
      List<VirtualFilePointerImpl> pointers = new ArrayList<>();
      root.addPointersUnder(null, false, "", left);
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

  private final Set<VirtualFilePointerImpl> myStoredPointers = ContainerUtil.newIdentityTroveSet();

  @TestOnly
  public void storePointers() {
    myStoredPointers.clear();
    addAllPointersTo(myStoredPointers);
  }

  @TestOnly
  public void assertPointersAreDisposed() {
    List<VirtualFilePointerImpl> pointers = new ArrayList<>();
    addAllPointersTo(pointers);
    try {
      for (VirtualFilePointerImpl pointer : pointers) {
        if (!myStoredPointers.contains(pointer)) {
          pointer.throwDisposalError("Virtual pointer hasn't been disposed: "+pointer);
        }
      }
    }
    finally {
      myStoredPointers.clear();
    }
  }

  @TestOnly
  private void addAllPointersTo(@NotNull Collection<VirtualFilePointerImpl> pointers) {
    List<FilePointerPartNode> out = new ArrayList<>();
    for (FilePointerPartNode root : myPointers.values()) {
      root.addPointersUnder(null, false, "", out);
    }
    for (FilePointerPartNode node : out) {
      node.addAllPointersTo(pointers);
    }
  }

  @Override
  public void dispose() {
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
  private VirtualFilePointerContainer registerContainer(@NotNull Disposable parent, @NotNull final VirtualFilePointerContainerImpl virtualFilePointerContainer) {
    synchronized (myContainers) {
      myContainers.add(virtualFilePointerContainer);
    }
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        Disposer.dispose(virtualFilePointerContainer);
        boolean removed;
        synchronized (myContainers) {
          removed = myContainers.remove(virtualFilePointerContainer);
        }
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          assert removed;
        }
      }

      @Override
      @NonNls
      @NotNull
      public String toString() {
        return "Disposing container " + virtualFilePointerContainer;
      }
    });
    return virtualFilePointerContainer;
  }

  private List<EventDescriptor> myEvents = Collections.emptyList();
  private List<FilePointerPartNode> myNodesToUpdateUrl = Collections.emptyList();
  private List<FilePointerPartNode> myNodesToFire = Collections.emptyList();

  @Override
  public void before(@NotNull final List<? extends VFileEvent> events) {
    List<FilePointerPartNode> toFireEvents = new ArrayList<>();
    List<FilePointerPartNode> toUpdateUrl = new ArrayList<>();
    VirtualFilePointer[] toFirePointers;

    synchronized (this) {
      incModificationCount();
      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
          addPointersUnder(deleteEvent.getFile(), false, "", toFireEvents);

        }
        else if (event instanceof VFileCreateEvent) {
          final VFileCreateEvent createEvent = (VFileCreateEvent)event;
          addPointersUnder(createEvent.getParent(), true, createEvent.getChildName(), toFireEvents);
        }
        else if (event instanceof VFileCopyEvent) {
          final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
          addPointersUnder(copyEvent.getNewParent(), true, copyEvent.getFile().getName(), toFireEvents);
        }
        else if (event instanceof VFileMoveEvent) {
          final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
          VirtualFile eventFile = moveEvent.getFile();
          addPointersUnder(moveEvent.getNewParent(), true, eventFile.getName(), toFireEvents);

          List<FilePointerPartNode> nodes = new ArrayList<>();
          addPointersUnder(eventFile, false, "", nodes);
          for (FilePointerPartNode node : nodes) {
            VirtualFilePointerImpl pointer = node.getAnyPointer();
            VirtualFile file = pointer == null ? null : pointer.getFile();
            if (file != null) {
              toUpdateUrl.add(node);
            }
          }
        }
        else if (event instanceof VFilePropertyChangeEvent) {
          final VFilePropertyChangeEvent change = (VFilePropertyChangeEvent)event;
          if (VirtualFile.PROP_NAME.equals(change.getPropertyName())
              && !Comparing.equal(change.getOldValue(), change.getNewValue())) {
            VirtualFile eventFile = change.getFile();
            VirtualFile parent = eventFile.getParent(); // e.g. for LightVirtualFiles
            addPointersUnder(parent, true, change.getNewValue().toString(), toFireEvents);

            List<FilePointerPartNode> nodes = new ArrayList<>();
            addPointersUnder(eventFile, false, "", nodes);
            for (FilePointerPartNode node : nodes) {
              VirtualFilePointerImpl pointer = node.getAnyPointer();
              VirtualFile file = pointer == null ? null : pointer.getFile();
              if (file != null) {
                toUpdateUrl.add(node);
              }
            }
          }
        }
      }

      myEvents = new ArrayList<>();
      toFirePointers = toPointers(toFireEvents);
      for (final VirtualFilePointerListener listener : myPointers.keySet()) {
        if (listener == null) continue;
        List<VirtualFilePointer> filtered = ContainerUtil.filter(toFirePointers,
                                                                 pointer -> ((VirtualFilePointerImpl)pointer).getListener() == listener);
        if (!filtered.isEmpty()) {
          EventDescriptor event = new EventDescriptor(listener, filtered.toArray(new VirtualFilePointer[filtered.size()]));
          myEvents.add(event);
        }
      }
    }

    for (EventDescriptor descriptor : myEvents) {
      descriptor.fireBefore();
    }

    if (!toFireEvents.isEmpty()) {
      myBus.syncPublisher(VirtualFilePointerListener.TOPIC).beforeValidityChanged(toFirePointers);
    }

    myNodesToFire = toFireEvents;
    myNodesToUpdateUrl = toUpdateUrl;
  }

  @Override
  public void after(@NotNull final List<? extends VFileEvent> events) {
    incModificationCount();

    for (FilePointerPartNode node : myNodesToUpdateUrl) {
      synchronized (this) {
        String urlBefore = node.myFileAndUrl.second;
        Pair<VirtualFile,String> after = node.update();
        String urlAfter = after.second;
        if (URL_COMPARATOR.compare(urlBefore, urlAfter) != 0) {
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
          VirtualFilePointerImpl[] newMyPointers = myPointers.toArray(new VirtualFilePointerImpl[myPointers.size()]);
          newNode.associate(newMyPointers, after);
          newNode.incrementUsageCount(useCount);
        }
      }
    }

    VirtualFilePointer[] pointersToFireArray = toPointers(myNodesToFire);
    for (VirtualFilePointer pointer : pointersToFireArray) {
      ((VirtualFilePointerImpl)pointer).myNode.update();
    }

    for (EventDescriptor event : myEvents) {
      event.fireAfter();
    }

    if (pointersToFireArray.length != 0) {
      myBus.syncPublisher(VirtualFilePointerListener.TOPIC).validityChanged(pointersToFireArray);
    }

    myNodesToUpdateUrl = Collections.emptyList();
    myEvents = Collections.emptyList();
    myNodesToFire = Collections.emptyList();
    for (FilePointerPartNode root : myPointers.values()) {
      root.checkConsistency();
    }
  }

  void removeNode(@NotNull FilePointerPartNode node, VirtualFilePointerListener listener) {
    FilePointerPartNode root = node.remove();
    boolean rootNodeEmpty = root.children.length == 0 ;
    if (rootNodeEmpty) {
      myPointers.remove(listener);
    }
    else {
      myPointers.get(listener).checkConsistency();
    }
  }

  private static class DelegatingDisposable implements Disposable {
    private static final ConcurrentMap<Disposable, DelegatingDisposable> ourInstances =
      ContainerUtil.newConcurrentMap(ContainerUtil.<Disposable>identityStrategy());
    private final TObjectIntHashMap<VirtualFilePointerImpl> myCounts = new TObjectIntHashMap<>();
    private final Disposable myParent;

    private DelegatingDisposable(@NotNull Disposable parent) {
      myParent = parent;
    }

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
        myCounts.forEachEntry(new TObjectIntProcedure<VirtualFilePointerImpl>() {
          @Override
          public boolean execute(VirtualFilePointerImpl pointer, int disposeCount) {
            int after = pointer.myNode.incrementUsageCount(-disposeCount + 1);
            LOG.assertTrue(after > 0, after);
            pointer.dispose();
            return true;
          }
        });
      }
    }
  }

  @TestOnly
  int numberOfPointers() {
    int number = 0;
    for (FilePointerPartNode root : myPointers.values()) {
      number = root.numberOfPointersUnder();
    }
    return number;
  }
  @TestOnly
  int numberOfListeners() {
    return myPointers.keySet().size();
  }
}
