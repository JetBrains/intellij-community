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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.objectTree.ObjectNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.*;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl");

  // guarded by this
  private final Map<VirtualFilePointerListener, TreeMap<String, VirtualFilePointerImpl>> myUrlToPointerMaps = new LinkedHashMap<VirtualFilePointerListener, TreeMap<String, VirtualFilePointerImpl>>();

  // compare by identity because VirtualFilePointerContainer has too smart equals
  // guarded by myContainers
  private final Set<VirtualFilePointerContainerImpl> myContainers = new THashSet<VirtualFilePointerContainerImpl>(TObjectHashingStrategy.IDENTITY);
  private final VirtualFileManagerEx myVirtualFileManager;
  private MessageBus myBus;
  private static final Comparator<String> COMPARATOR = SystemInfo.isFileSystemCaseSensitive ? new Comparator<String>() {
    public int compare(@NotNull String url1, @NotNull String url2) {
      return url1.compareTo(url2);
    }
  } : new Comparator<String>() {
    public int compare(@NotNull String url1, @NotNull String url2) {
      return url1.compareToIgnoreCase(url2);
    }
  };

  VirtualFilePointerManagerImpl(@NotNull VirtualFileManagerEx virtualFileManagerEx, MessageBus bus) {
    myVirtualFileManager = virtualFileManagerEx;
    myBus = bus;
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new VFSEventsProcessor());
  }

  synchronized void clearPointerCaches(String url, VirtualFilePointerListener listener) {
    TreeMap<String, VirtualFilePointerImpl> urlToPointer = myUrlToPointerMaps.get(listener);
    if (urlToPointer == null && ApplicationManager.getApplication().isUnitTestMode()) return;
    assert urlToPointer != null;
    urlToPointer.remove(VfsUtil.urlToPath(url));
    if (urlToPointer.isEmpty()) {
      myUrlToPointerMaps.remove(listener);
    }
  }

  private class EventDescriptor {
    private final VirtualFilePointerListener myListener;
    private final VirtualFilePointer[] myPointers;

    private EventDescriptor(@NotNull VirtualFilePointerListener listener, @NotNull List<VirtualFilePointer> pointers) {
      myListener = listener;
      synchronized (VirtualFilePointerManagerImpl.this) {
        Collection<VirtualFilePointerImpl> set = myUrlToPointerMaps.get(listener).values();
        ArrayList<VirtualFilePointer> result = new ArrayList<VirtualFilePointer>(pointers);
        result.retainAll(set);
        myPointers = result.isEmpty() ? VirtualFilePointer.EMPTY_ARRAY  : result.toArray(new VirtualFilePointer[result.size()]);
      }
    }

    public void fireBefore() {
      if (myPointers.length != 0) {
        myListener.beforeValidityChanged(myPointers);
      }
    }

    public void fireAfter() {
      if (myPointers.length != 0) {
        myListener.validityChanged(myPointers);
      }
    }
  }

  private List<VirtualFilePointer> getPointersUnder(String path, boolean allowSameFSOptimization) {
    final List<VirtualFilePointer> pointers = new ArrayList<VirtualFilePointer>();
    final boolean urlFromJarFS = allowSameFSOptimization && path.indexOf(JarFileSystem.JAR_SEPARATOR) > 0;
    for (TreeMap<String, VirtualFilePointerImpl> urlToPointer : myUrlToPointerMaps.values()) {
      for (String pointerUrl : urlToPointer.keySet()) {
        final boolean pointerFromJarFS = allowSameFSOptimization && pointerUrl.indexOf(JarFileSystem.JAR_SEPARATOR) > 0;
        if (urlFromJarFS != pointerFromJarFS) {
          continue; // optimization: consider pointers from the same FS as the url specified
        }
        if (startsWith(path, pointerUrl)) {
          VirtualFilePointer pointer = urlToPointer.get(pointerUrl);
          if (pointer != null) {
            pointers.add(pointer);
          }
        }
      }
    }
    return pointers;
  }

  private static boolean startsWith(final String url, final String pointerUrl) {
    String urlSuffix = stripSuffix(url);
    String pointerPrefix = stripToJarPrefix(pointerUrl);
    if (urlSuffix.length() > 0) {
      return Comparing.equal(stripToJarPrefix(url), pointerPrefix, SystemInfo.isFileSystemCaseSensitive) &&
             StringUtil.startsWith(urlSuffix, stripSuffix(pointerUrl));
    }

    return FileUtil.startsWith(pointerPrefix, stripToJarPrefix(url));
  }

  private static String stripToJarPrefix(String url) {
    int separatorIndex = url.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return url;
    return url.substring(0, separatorIndex);
  }

  private static String stripSuffix(String url) {
    int separatorIndex = url.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return "";
    return url.substring(separatorIndex + JarFileSystem.JAR_SEPARATOR.length());
  }

  @TestOnly
  public synchronized void cleanupForNextTest() {
    myUrlToPointerMaps.clear();
    myContainers.clear();
  }

  /**
   * @see #create(String, com.intellij.openapi.Disposable, com.intellij.openapi.vfs.pointers.VirtualFilePointerListener)
   */
  @Deprecated
  public synchronized VirtualFilePointer create(String url, VirtualFilePointerListener listener) {
    return create(url, this, listener);
  }

  @NotNull
  public synchronized VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent,VirtualFilePointerListener listener) {
    return create(null, url, parent, listener);
  }

  /**
   * @see #create(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.Disposable, com.intellij.openapi.vfs.pointers.VirtualFilePointerListener)
   */
  @Deprecated
  public synchronized VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener) {
    return create(file, this, listener);
  }

  @NotNull
  public synchronized VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, VirtualFilePointerListener listener) {
    return create(file, file.getUrl(), parent,listener);
  }

  @NotNull
  private VirtualFilePointer create(VirtualFile file, @NotNull String url, @NotNull final Disposable parentDisposable, VirtualFilePointerListener listener) {
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
    if (fileSystem == TempFileSystem.getInstance()) {
      // for tests, recreate always since
      VirtualFile found = fileSystem == null ? null : file != null ? file : VirtualFileManager.getInstance().findFileByUrl(url);
      return new IdentityVirtualFilePointer(found, url);
    }
    if (fileSystem != LocalFileSystem.getInstance() && fileSystem != JarFileSystem.getInstance()) {
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

    VirtualFilePointerImpl pointer = getOrCreate(file, url, parentDisposable, listener, path);

    int newCount = pointer.incrementUsageCount();

    if (newCount == 1) {
      Disposer.register(parentDisposable, pointer);
    }
    else {
      //already registered
      register(parentDisposable, pointer);
    }

    return pointer;
  }

  private final Map<String, IdentityVirtualFilePointer> myUrlToIdentity = new THashMap<String, IdentityVirtualFilePointer>();
  private IdentityVirtualFilePointer getOrCreateIdentity(@NotNull String url, VirtualFile found) {
    IdentityVirtualFilePointer pointer = myUrlToIdentity.get(url);
    if (pointer == null) {
      pointer = new IdentityVirtualFilePointer(found, url);
      myUrlToIdentity.put(url, pointer);
    }
    return pointer;
  }

  private static void register(Disposable parentDisposable, VirtualFilePointerImpl pointer) {
    DelegatingDisposable delegating = new DelegatingDisposable(pointer);
    DelegatingDisposable registered = Disposer.findRegisteredObject(parentDisposable, delegating);
    if (registered == null) {
      Disposer.register(parentDisposable, delegating);
    }
    else {
      registered.disposeCount++;
    }
  }

  private static String cleanupPath(String path, String protocol) {
    path = FileUtil.toSystemIndependentName(path);

    path = stripTrailingPathSeparator(path, protocol);
    path = removeDoubleSlashes(path);
    return path;
  }

  private static String removeDoubleSlashes(String path) {
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

  private synchronized VirtualFilePointerImpl getOrCreate(VirtualFile file, @NotNull String url, Disposable parentDisposable, VirtualFilePointerListener listener, String path) {
    TreeMap<String, VirtualFilePointerImpl> urlToPointer = myUrlToPointerMaps.get(listener);
    if (urlToPointer == null) {
      urlToPointer = new TreeMap<String, VirtualFilePointerImpl>(COMPARATOR);
      myUrlToPointerMaps.put(listener, urlToPointer);
    }
    VirtualFilePointerImpl pointer = urlToPointer.get(path);

    if (pointer == null) {
      pointer = new VirtualFilePointerImpl(file, url, myVirtualFileManager, listener, parentDisposable);
      urlToPointer.put(path, pointer);
    }
    return pointer;
  }

  private static String stripTrailingPathSeparator(String path, String protocol) {
    while (path.endsWith("/") && !(protocol.equals(JarFileSystem.PROTOCOL) && path.endsWith(JarFileSystem.JAR_SEPARATOR))) {
      path = StringUtil.trimEnd(path, "/");
    }
    return path;
  }

  /**
   * @see #duplicate(com.intellij.openapi.vfs.pointers.VirtualFilePointer, com.intellij.openapi.Disposable, com.intellij.openapi.vfs.pointers.VirtualFilePointerListener)
   */
  @Deprecated
  public synchronized VirtualFilePointer duplicate(VirtualFilePointer pointer, VirtualFilePointerListener listener) {
    return duplicate(pointer, this, listener);
  }

  @NotNull
  public synchronized VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer, @NotNull Disposable parent,
                                                   VirtualFilePointerListener listener) {
    VirtualFile file = pointer.getFile();
    return file == null ? create(pointer.getUrl(), parent, listener) : create(file, parent, listener);
  }

  /**
   * Does nothing. To cleanup pointer correctly, just pass Disposable during its creation
   * @see #create(String, com.intellij.openapi.Disposable, com.intellij.openapi.vfs.pointers.VirtualFilePointerListener)
   */
  @Deprecated
  public synchronized void kill(VirtualFilePointer pointer, final VirtualFilePointerListener listener) {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    Disposer.dispose(this);
    assertPointersDisposed();
  }

  public synchronized void assertPointersDisposed() {
    for (Map.Entry<VirtualFilePointerListener, TreeMap<String, VirtualFilePointerImpl>> entry : myUrlToPointerMaps.entrySet()) {
      VirtualFilePointerListener listener = entry.getKey();
      TreeMap<String, VirtualFilePointerImpl> map = entry.getValue();
      for (VirtualFilePointerImpl pointer : map.values()) {
        myUrlToPointerMaps.clear();
        pointer.throwNotDisposedError("Not disposed pointer: listener="+listener);
      }
    }

    //if (myListenerToPointersMap.isEmpty()) {
    //  System.err.println("All pointers are disposed");
    //}
    synchronized (myContainers) {
      if (!myContainers.isEmpty()) {
        VirtualFilePointerContainerImpl container = myContainers.iterator().next();
        myContainers.clear();
        throw new RuntimeException("Not disposed container " + container);
      }
    }
  }

  public void dispose() {
  }

  @NotNull
  public String getComponentName() {
    return "SmartVirtualPointerManager";
  }

  private void cleanContainerCaches() {
    synchronized (myContainers) {
      for (VirtualFilePointerContainerImpl container : myContainers) {
        container.dropCaches();
      }
    }
  }

  /**
   * @see #createContainer(com.intellij.openapi.Disposable)
   */
  @Deprecated
  public synchronized VirtualFilePointerContainer createContainer() {
    return createContainer(this);
  }

  /**
   * @see #createContainer(com.intellij.openapi.Disposable, com.intellij.openapi.vfs.pointers.VirtualFilePointerListener)
   */
  @Deprecated
  public synchronized VirtualFilePointerContainer createContainer(final VirtualFilePointerFactory factory) {
    final VirtualFilePointerContainerImpl virtualFilePointerContainer = new VirtualFilePointerContainerImpl(this, this, null){
      @Override
      protected VirtualFilePointer create(@NotNull VirtualFile file) {
        return factory.create(file);
      }

      @Override
      protected VirtualFilePointer create(@NotNull String url) {
        return factory.create(url);
      }

      @Override
      protected VirtualFilePointer duplicate(@NotNull VirtualFilePointer virtualFilePointer) {
        return factory.duplicate(virtualFilePointer);
      }
    };
    return registerContainer(this, virtualFilePointerContainer);
  }

  @NotNull
  public VirtualFilePointerContainer createContainer(@NotNull Disposable parent) {
    return createContainer(parent, null);
  }

  @NotNull
  public synchronized VirtualFilePointerContainer createContainer(@NotNull Disposable parent, VirtualFilePointerListener listener) {
    return registerContainer(parent, new VirtualFilePointerContainerImpl(this, parent, listener));
  }

  private VirtualFilePointerContainer registerContainer(@NotNull Disposable parent, @NotNull final VirtualFilePointerContainerImpl virtualFilePointerContainer) {
    synchronized (myContainers) {
      myContainers.add(virtualFilePointerContainer);
    }
    Disposer.register(parent, new Disposable() {
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

      public String toString() {
        return "Disposing container " + virtualFilePointerContainer;
      }
    });
    return virtualFilePointerContainer;
  }

  private class VFSEventsProcessor implements BulkFileListener {
    private List<EventDescriptor> myEvents = null;
    private List<String> myUrlsToUpdate = null;
    private List<VirtualFilePointer> myPointersToUdate = null;

    public void before(final List<? extends VFileEvent> events) {
      cleanContainerCaches();
      List<VirtualFilePointer> toFireEvents = new ArrayList<VirtualFilePointer>();
      List<String> toUpdateUrl = new ArrayList<String>();

      synchronized (VirtualFilePointerManagerImpl.this) {
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
            String url = deleteEvent.getFile().getPath();
            toFireEvents.addAll(getPointersUnder(url, true));
          }
          else if (event instanceof VFileCreateEvent) {
            final VFileCreateEvent createEvent = (VFileCreateEvent)event;
            String url = createEvent.getPath();
            toFireEvents.addAll(getPointersUnder(url, false));
          }
          else if (event instanceof VFileCopyEvent) {
            final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
            String url = copyEvent.getNewParent().getPath() + "/" + copyEvent.getFile().getName();
            toFireEvents.addAll(getPointersUnder(url, false));
          }
          else if (event instanceof VFileMoveEvent) {
            final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
            List<VirtualFilePointer> pointers = getPointersUnder(moveEvent.getFile().getPath(), false);
            for (VirtualFilePointer pointer : pointers) {
              VirtualFile file = pointer.getFile();
              if (file != null) {
                toUpdateUrl.add(file.getPath());
              }
            }
          }
          else if (event instanceof VFilePropertyChangeEvent) {
            final VFilePropertyChangeEvent change = (VFilePropertyChangeEvent)event;
            if (VirtualFile.PROP_NAME.equals(change.getPropertyName())) {
              List<VirtualFilePointer> pointers = getPointersUnder(change.getFile().getPath(), false);
              for (VirtualFilePointer pointer : pointers) {
                VirtualFile file = pointer.getFile();
                if (file != null) {
                  toUpdateUrl.add(file.getPath());
                }
              }
            }
          }
        }

        myEvents = new ArrayList<EventDescriptor>();
        for (VirtualFilePointerListener listener : myUrlToPointerMaps.keySet()) {
          if (listener == null) continue;
          EventDescriptor event = new EventDescriptor(listener, toFireEvents);
          myEvents.add(event);
        }
      }

      for (EventDescriptor event : myEvents) {
        event.fireBefore();
      }

      if (!toFireEvents.isEmpty()) {
        VirtualFilePointer[] arr = toFireEvents.toArray(new VirtualFilePointer[toFireEvents.size()]);
        myBus.syncPublisher(VirtualFilePointerListener.TOPIC).beforeValidityChanged(arr);
      }

      myPointersToUdate = toFireEvents;
      myUrlsToUpdate = toUpdateUrl;
    }

    public void after(final List<? extends VFileEvent> events) {
      cleanContainerCaches();

      if (myUrlsToUpdate == null) {
        return;
      }
      for (String url : myUrlsToUpdate) {
        synchronized (VirtualFilePointerManagerImpl.this) {
          for (TreeMap<String, VirtualFilePointerImpl> urlToPointer : myUrlToPointerMaps.values()) {
            VirtualFilePointerImpl pointer = urlToPointer.remove(url);
            if (pointer != null) {
              String path = VfsUtil.urlToPath(pointer.getUrl());
              urlToPointer.put(path, pointer);
            }
          }
        }
      }

      for (VirtualFilePointer pointer : myPointersToUdate) {
        ((VirtualFilePointerImpl)pointer).update();
      }

      for (EventDescriptor event : myEvents) {
        event.fireAfter();
      }

      if (!myPointersToUdate.isEmpty()) {
        VirtualFilePointer[] arr = myPointersToUdate.toArray(new VirtualFilePointer[myPointersToUdate.size()]);
        myBus.syncPublisher(VirtualFilePointerListener.TOPIC).beforeValidityChanged(arr);
      }

      myUrlsToUpdate = null;
      myEvents = null;
      myPointersToUdate = null;
    }
  }

  private static class DelegatingDisposable implements Disposable {
    private final VirtualFilePointerImpl myPointer;
    private int disposeCount = 1;

    private DelegatingDisposable(@NotNull VirtualFilePointerImpl pointer) {
      myPointer = pointer;
    }

    public void dispose() {
      myPointer.useCount -= disposeCount-1;
      LOG.assertTrue(myPointer.useCount > 0);
      myPointer.dispose();
    }

    @Override
    public String toString() {
      return "D:" + myPointer.toString();
    }

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
  public int countPointers() {
    int result = 0;
    for (TreeMap<String, VirtualFilePointerImpl> map : myUrlToPointerMaps.values()) {
      result += map.values().size();
    }
    return result;
  }

  @TestOnly
  public int countDupContainers() {
    Map<VirtualFilePointerContainer,Integer> c = new THashMap<VirtualFilePointerContainer,Integer>();
    for (VirtualFilePointerContainerImpl container : myContainers) {
      Integer count = c.get(container);
      if (count == null) count = 0;
      count++;
      c.put(container, count);
    }
    int i = 0;
    for (Integer count : c.values()) {
      if (count > 1) {
        i++;
      }
    }
    return i;
  }
  
  @TestOnly
  public static int countMaxRefCount() {
    int result = 0;
    for (Disposable disposable : Disposer.getTree().getRootObjects()) {
      result = calcMaxRefCount(disposable, result);
    }
    return result;
  }

  private static int calcMaxRefCount(Disposable disposable, int result) {
    if (disposable instanceof DelegatingDisposable) {
      result = Math.max(((DelegatingDisposable)disposable).disposeCount, result);
    }

    for (ObjectNode<Disposable> node : Disposer.getTree().getNode(disposable).getChildren()) {
      result = calcMaxRefCount(node.getObject(), result);
    }
    return result;
  }

  
}
