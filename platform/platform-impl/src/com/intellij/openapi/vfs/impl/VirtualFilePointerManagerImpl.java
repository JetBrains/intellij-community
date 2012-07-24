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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.objectTree.ObjectNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements ApplicationComponent, ModificationTracker, BulkFileListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl");
  private final TempFileSystem TEMP_FILE_SYSTEM;
  private final LocalFileSystem LOCAL_FILE_SYSTEM;
  private final JarFileSystem JAR_FILE_SYSTEM;
  private long myVfsModificationCounter;
  // guarded by this
  private final Map<VirtualFilePointerListener, FilePointerPartNode> myPointers = new LinkedHashMap<VirtualFilePointerListener, FilePointerPartNode>();

  // compare by identity because VirtualFilePointerContainer has too smart equals
  // guarded by myContainers
  private final Set<VirtualFilePointerContainerImpl> myContainers = new THashSet<VirtualFilePointerContainerImpl>(TObjectHashingStrategy.IDENTITY);
  @NotNull private final VirtualFileManagerEx myVirtualFileManager;
  @NotNull private final MessageBus myBus;
  private static final Comparator<String> URL_COMPARATOR = SystemInfo.isFileSystemCaseSensitive ? new Comparator<String>() {
    @Override
    public int compare(@NotNull String url1, @NotNull String url2) {
      return url1.compareTo(url2);
    }
  } : new Comparator<String>() {
    @Override
    public int compare(@NotNull String url1, @NotNull String url2) {
      return url1.compareToIgnoreCase(url2);
    }
  };

  VirtualFilePointerManagerImpl(@NotNull VirtualFileManagerEx virtualFileManagerEx,
                                @NotNull MessageBus bus,
                                @NotNull TempFileSystem tempFileSystem,
                                @NotNull LocalFileSystem localFileSystem,
                                @NotNull JarFileSystem jarFileSystem) {
    myVirtualFileManager = virtualFileManagerEx;
    myBus = bus;
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    TEMP_FILE_SYSTEM = tempFileSystem;
    LOCAL_FILE_SYSTEM = localFileSystem;
    JAR_FILE_SYSTEM = jarFileSystem;
  }


  @Override
  public long getModificationCount() {
    return myVfsModificationCounter;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
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
  private static VirtualFilePointer[] toPointers(@NotNull List<FilePointerPartNode> pointers) {
    if (pointers.isEmpty()) return VirtualFilePointer.EMPTY_ARRAY;
    List<VirtualFilePointer> list = ContainerUtil
      .mapNotNull(pointers, new Function<FilePointerPartNode, VirtualFilePointer>() {
        @Override
        public VirtualFilePointer fun(FilePointerPartNode pair) {
          return pair.leaf;
        }
      });

    return list.toArray(new VirtualFilePointer[list.size()]);
  }

  private void addPointersUnder(@NotNull String path, @NotNull List<FilePointerPartNode> out) {
    for (FilePointerPartNode root : myPointers.values()) {
      root.getPointersUnder(path, 0, out);
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
    return create(file, file.getUrl(), parent, listener);
  }

  @NotNull
  private VirtualFilePointer create(@Nullable VirtualFile file,
                                    @NotNull String url,
                                    @NotNull final Disposable parentDisposable,
                                    @Nullable VirtualFilePointerListener listener) {
    String protocol;
    VirtualFileSystem fileSystem;
    if (file == null) {
      protocol = VirtualFileManager.extractProtocol(url);
      fileSystem = myVirtualFileManager.getFileSystem(protocol);
    }
    else {
      protocol = null;
      fileSystem = file.getFileSystem();
    }
    if (fileSystem == TEMP_FILE_SYSTEM) {
      // for tests, recreate always
      VirtualFile found = fileSystem == null ? null : file != null ? file : VirtualFileManager.getInstance().findFileByUrl(url);
      return new IdentityVirtualFilePointer(found, url);
    }
    if (fileSystem != LOCAL_FILE_SYSTEM && fileSystem != JAR_FILE_SYSTEM) {
      // we are unable to track alien file systems for now
      VirtualFile found = fileSystem == null ? null : file != null ? file : VirtualFileManager.getInstance().findFileByUrl(url);
      // if file is null, this pointer will never be alive
      return getOrCreateIdentity(url, found);
    }

    String path;
    if (file == null) {
      path = VirtualFileManager.extractPath(url);
      path = cleanupPath(path, protocol);
      url = VirtualFileManager.constructUrl(protocol, path);
    }
    else {
      path = file.getPath();
      // url has come from VirtualFile.getUrl() and is good enough
    }

    VirtualFilePointerImpl pointer = getOrCreate(parentDisposable, listener, path, Pair.create(file, url));

    register(parentDisposable, pointer);

    return pointer;
  }

  private final Map<String, IdentityVirtualFilePointer> myUrlToIdentity = new THashMap<String, IdentityVirtualFilePointer>();
  @NotNull
  private IdentityVirtualFilePointer getOrCreateIdentity(@NotNull String url, VirtualFile found) {
    IdentityVirtualFilePointer pointer = myUrlToIdentity.get(url);
    if (pointer == null) {
      pointer = new IdentityVirtualFilePointer(found, url);
      myUrlToIdentity.put(url, pointer);
    }
    return pointer;
  }

  private static void register(@NotNull Disposable parentDisposable, @NotNull VirtualFilePointerImpl pointer) {
    ObjectNode<Disposable> node = Disposer.getTree().getNode(pointer);
    if (node == null) {
      Disposer.register(parentDisposable, pointer);
    }
    else if (node.getParent().getObject() == parentDisposable) {
      // already registered, just do not inc the usage count
      pointer.myNode.incrementUsageCount(-1);
    }
    else {
      // already registered but under different parent
      DelegatingDisposable delegating = new DelegatingDisposable(pointer);
      DelegatingDisposable registered = Disposer.findRegisteredObject(parentDisposable, delegating);
      if (registered == null) {
        Disposer.register(parentDisposable, delegating);
      }
      else {
        registered.disposeCount++;
      }
    }
    if (node != null && !node.getChildren().isEmpty()) {
      // VFP is registered in Disposable in a very eccentric way (custom refcounts etc) so it's not a good idea to have it as a Disposable parent
      LOG.error("You must not register disposable having VirtualFilePointer as a parent: "+node.getChildren());
    }
  }

  private static String cleanupPath(String path, @NotNull String protocol) {
    path = FileUtil.toSystemIndependentName(path);

    path = stripTrailingPathSeparator(path, protocol);
    path = removeDoubleSlashes(path);
    return path;
  }

  @NotNull
  private static String removeDoubleSlashes(@NotNull String path) {
    while(true) {
      int i = path.lastIndexOf("//");
      if (i != -1) {
        path = path.substring(0, i) + path.substring(i + 1);
      }
      else {
        break;
      }
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
      myPointers.put(listener, root);
      node = root;
    }
    else {
      node = root.findPointerOrCreate(path, 0, fileAndUrl);
    }

    VirtualFilePointerImpl pointer;
    if (node.leaf == null) {
      pointer = new VirtualFilePointerImpl(listener, parentDisposable, fileAndUrl);
      node.associate(pointer, fileAndUrl);
    }
    else {
      pointer = node.leaf;
    }
    pointer.myNode.incrementUsageCount(1);

    root.checkStructure();
    return pointer;
  }

  @NotNull
  private static String stripTrailingPathSeparator(@NotNull String path, @NotNull String protocol) {
    while (!path.isEmpty() &&
           path.charAt(path.length() - 1) == '/' &&
           !(protocol.equals(JarFileSystem.PROTOCOL) && path.endsWith(JarFileSystem.JAR_SEPARATOR))) {
      path = StringUtil.trimEnd(path, "/");
    }
    return path;
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
      ArrayList<FilePointerPartNode> left = new ArrayList<FilePointerPartNode>();
      root.getPointersUnder("", 0, left);
      if (!left.isEmpty()) {
        VirtualFilePointerImpl p = left.get(0).leaf;
        try {
          p.throwDisposalError("Not disposed pointer: "+p);
        }
        finally {
          for (FilePointerPartNode pair : left) {
            VirtualFilePointerImpl pointer = pair.leaf;
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

  private final Set<VirtualFilePointerImpl> myStoredPointers = new THashSet<VirtualFilePointerImpl>(TObjectHashingStrategy.IDENTITY);
  @TestOnly
  public void storePointers() {
    //assert myStoredPointers.isEmpty() : myStoredPointers;
    myStoredPointers.clear();
    addAllPointers(myStoredPointers);
  }
  @TestOnly
  public void assertPointersAreDisposed() {
    List<VirtualFilePointerImpl> pointers = new ArrayList<VirtualFilePointerImpl>();
    addAllPointers(pointers);
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

  private void addAllPointers(Collection<VirtualFilePointerImpl> pointers) {
    List<FilePointerPartNode> out = new ArrayList<FilePointerPartNode>();
    for (FilePointerPartNode root : myPointers.values()) {
      root.getPointersUnder("", 0, out);
    }
    for (FilePointerPartNode node : out) {
      pointers.add(node.leaf);
    }
  }

  @Override
  public void dispose() {
    assertAllPointersDisposed();
  }

  private void incModificationCounter() {
    myVfsModificationCounter++;
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

      @NonNls
      @NotNull
      public String toString() {
        return "Disposing container " + virtualFilePointerContainer;
      }
    });
    return virtualFilePointerContainer;
  }

  @Nullable private List<EventDescriptor> myEvents = Collections.emptyList();
  @Nullable private List<FilePointerPartNode> myPointersToUpdateUrl = Collections.emptyList();
  @Nullable private List<FilePointerPartNode> myPointersToFire = Collections.emptyList();

  @Override
  public void before(@NotNull final List<? extends VFileEvent> events) {
    List<FilePointerPartNode> toFireEvents = new ArrayList<FilePointerPartNode>();
    List<FilePointerPartNode> toUpdateUrl = new ArrayList<FilePointerPartNode>();
    VirtualFilePointer[] toFirePointers;

    synchronized (this) {
      incModificationCounter();
      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
          String path = deleteEvent.getFile().getPath();
          addPointersUnder(path, toFireEvents);
        }
        else if (event instanceof VFileCreateEvent) {
          final VFileCreateEvent createEvent = (VFileCreateEvent)event;
          String url = createEvent.getPath();
          addPointersUnder(url, toFireEvents);
        }
        else if (event instanceof VFileCopyEvent) {
          final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
          String url = copyEvent.getNewParent().getPath() + "/" + copyEvent.getFile().getName();
          addPointersUnder(url, toFireEvents);
        }
        else if (event instanceof VFileMoveEvent) {
          final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
          List<FilePointerPartNode> nodes = new ArrayList<FilePointerPartNode>();
          addPointersUnder(moveEvent.getFile().getPath(), nodes);
          for (FilePointerPartNode pair : nodes) {
            VirtualFile file = pair.leaf.getFile();
            if (file != null) {
              toUpdateUrl.add(pair);
            }
          }
        }
        else if (event instanceof VFilePropertyChangeEvent) {
          final VFilePropertyChangeEvent change = (VFilePropertyChangeEvent)event;
          if (VirtualFile.PROP_NAME.equals(change.getPropertyName())) {
            List<FilePointerPartNode> nodes = new ArrayList<FilePointerPartNode>();
            addPointersUnder(change.getFile().getPath(), nodes);
            for (FilePointerPartNode pair : nodes) {
              VirtualFile file = pair.leaf.getFile();
              if (file != null) {
                toUpdateUrl.add(pair);
              }
            }
          }
        }
      }

      myEvents = new ArrayList<EventDescriptor>();
      toFirePointers = toPointers(toFireEvents);
      for (final VirtualFilePointerListener listener : myPointers.keySet()) {
        if (listener == null) continue;
        List<VirtualFilePointer> filtered = ContainerUtil.filter(toFirePointers, new Condition<VirtualFilePointer>() {
          @Override
          public boolean value(VirtualFilePointer pointer) {
            return ((VirtualFilePointerImpl)pointer).getListener() == listener;
          }
        });
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

    myPointersToFire = toFireEvents;
    myPointersToUpdateUrl = toUpdateUrl;
  }

  @Override
  public void after(@NotNull final List<? extends VFileEvent> events) {
    incModificationCounter();

    for (FilePointerPartNode node : myPointersToUpdateUrl) {
      synchronized (this) {
        VirtualFilePointerImpl pointer = node.leaf;
        String urlBefore = pointer.getUrlNoUpdate();
        Pair<VirtualFile,String> after = node.update();
        String urlAfter = after.second;
        if (URL_COMPARATOR.compare(urlBefore, urlAfter) != 0) {
          // url has changed, reinsert
          FilePointerPartNode root = myPointers.get(pointer.getListener());
          int useCount = node.useCount;
          node.remove();
          FilePointerPartNode newNode = root.findPointerOrCreate(VfsUtilCore.urlToPath(urlAfter), 0, after);
          VirtualFilePointerImpl existingPointer = newNode.leaf;
          if (existingPointer != null) {
            // can happen when e.g. file renamed to the existing file
            // merge two pointers
            pointer.myNode = newNode;
          }
          else {
            newNode.associate(pointer, after);
          }
          newNode.incrementUsageCount(useCount);
        }
      }
    }

    VirtualFilePointer[] pointersToFireArray = toPointers(myPointersToFire);
    for (VirtualFilePointer pointer : pointersToFireArray) {
      ((VirtualFilePointerImpl)pointer).myNode.update();
    }

    for (EventDescriptor event : myEvents) {
      event.fireAfter();
    }

    if (pointersToFireArray.length != 0) {
      myBus.syncPublisher(VirtualFilePointerListener.TOPIC).validityChanged(pointersToFireArray);
    }

    myPointersToUpdateUrl = Collections.emptyList();
    myEvents = Collections.emptyList();
    myPointersToFire = Collections.emptyList();
    for (FilePointerPartNode root : myPointers.values()) {
      root.checkStructure();
    }
  }

  void removeNode(@NotNull FilePointerPartNode node, VirtualFilePointerListener listener) {
    boolean rootNodeEmpty = node.remove();
    if (rootNodeEmpty) {
      myPointers.remove(listener);
    }
    else {
      myPointers.get(listener).checkStructure();
    }
  }

  private static class DelegatingDisposable implements Disposable {
    @NotNull private final VirtualFilePointerImpl myPointer;
    private int disposeCount = 1;

    private DelegatingDisposable(@NotNull VirtualFilePointerImpl pointer) {
      myPointer = pointer;
    }

    @Override
    public void dispose() {
      if (disposeCount != 1) {
        int after = myPointer.myNode.incrementUsageCount(-disposeCount+1);
        LOG.assertTrue(after > 0, after);
      }
      myPointer.dispose();
    }

    @NonNls
    @NotNull
    @Override
    public String toString() {
      return "D:" + myPointer.toString();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
      DelegatingDisposable that = (DelegatingDisposable)o;
      return myPointer == that.myPointer;
    }

    @Override
    public int hashCode() {
      return myPointer.hashCode();
    }
  }

  @TestOnly
  int numberOfPointers() {
    int number = 0;
    for (FilePointerPartNode root : myPointers.values()) {
      number = root.getPointersUnder();
    }
    return number;
  }
  @TestOnly
  int numberOfListeners() {
    return myPointers.keySet().size();
  }
}
