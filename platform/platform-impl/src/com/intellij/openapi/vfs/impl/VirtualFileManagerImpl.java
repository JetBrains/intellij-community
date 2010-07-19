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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VirtualFileManagerImpl extends VirtualFileManagerEx implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFileManagerImpl");

  private final ArrayList<VirtualFileSystem> myFileSystems = new ArrayList<VirtualFileSystem>();
  private final Map<String, VirtualFileSystem> myProtocolToSystemMap = new HashMap<String, VirtualFileSystem>();

  private final EventDispatcher<VirtualFileListener> myVirtualFileListenerMulticaster = EventDispatcher.create(VirtualFileListener.class);
  private final List<VirtualFileManagerListener> myVirtualFileManagerListeners = ContainerUtil.createEmptyCOWList();
  private final EventDispatcher<ModificationAttemptListener> myModificationAttemptListenerMulticaster = EventDispatcher.create(ModificationAttemptListener.class);

  @NonNls private static final String USER_HOME = "user.home";
  private int myRefreshCount = 0;
  private final ManagingFS myPersistence;

  public VirtualFileManagerImpl(VirtualFileSystem[] fileSystems, MessageBus bus, ManagingFS persistence) {
    myPersistence = persistence;
    for (VirtualFileSystem fileSystem : fileSystems) {
      registerFileSystem(fileSystem);
    }

    if (LOG.isDebugEnabled()) {
      addVirtualFileListener(new LoggingListener());
    }

    bus.connect().subscribe(VFS_CHANGES, new BulkVirtualFileListenerAdapter(myVirtualFileListenerMulticaster.getMulticaster()));
  }

  @NotNull
  public String getComponentName() {
    return "VirtualFileManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void registerFileSystem(VirtualFileSystem fileSystem) {
    myFileSystems.add(fileSystem);
    if (!(fileSystem instanceof NewVirtualFileSystem)) {
      fileSystem.addVirtualFileListener(myVirtualFileListenerMulticaster.getMulticaster());
    }
    myProtocolToSystemMap.put(fileSystem.getProtocol(), fileSystem);
  }

  public void unregisterFileSystem(VirtualFileSystem fileSystem) {
    myFileSystems.remove(fileSystem);
    fileSystem.removeVirtualFileListener(myVirtualFileListenerMulticaster.getMulticaster());
    myProtocolToSystemMap.remove(fileSystem.getProtocol());
  }

  public VirtualFileSystem[] getFileSystems() {
    return myFileSystems.toArray(new VirtualFileSystem[myFileSystems.size()]);
  }

  public VirtualFileSystem getFileSystem(String protocol) {
    return myProtocolToSystemMap.get(protocol);
  }

  public void refresh(boolean asynchronous) {
    refresh(asynchronous, null);
  }

  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }

    for (VirtualFileSystem fileSystem : myFileSystems) {
      if (fileSystem instanceof NewVirtualFileSystem) {
        ((NewVirtualFileSystem)fileSystem).refreshWithoutFileWatcher(asynchronous);
      }
      else {
        fileSystem.refresh(asynchronous);
      }
    }
  }

  public void refresh(boolean asynchronous, final Runnable postAction) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }

    RefreshQueue.getInstance().refresh(asynchronous, true, postAction, myPersistence.getLocalRoots()); // TODO: Get an idea how to deliver chnages from local FS to jar fs before they go refresh

    //final VirtualFile[] managedRoots = ManagingFS.getInstance().getRoots();
    //for (int i = 0; i < managedRoots.length; i++) {
    //  VirtualFile root = managedRoots[i];
    //  boolean last = i + 1 == managedRoots.length;
    //  RefreshQueue.getInstance().refresh(asynchronous, true, last ? postAction : null, root);
    //}

    for (VirtualFileSystem fileSystem : myFileSystems) {
      if (!(fileSystem instanceof NewVirtualFileSystem)) {
        fileSystem.refresh(asynchronous);
      }
    }
  }

  public VirtualFile findFileByUrl(@NotNull String url) {
    String protocol = extractProtocol(url);
    if (protocol == null) return null;
    VirtualFileSystem fileSystem = myProtocolToSystemMap.get(protocol);
    if (fileSystem == null) return null;
    return fileSystem.findFileByPath(extractPath(url));
  }

  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    String protocol = extractProtocol(url);
    if (protocol == null) return null;
    VirtualFileSystem fileSystem = myProtocolToSystemMap.get(protocol);
    if (fileSystem == null) return null;
    String path = extractPath(url);
    return fileSystem.refreshAndFindFileByPath(path);
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.addListener(listener);
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
    myVirtualFileListenerMulticaster.addListener(listener, parentDisposable);
  }

  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.removeListener(listener);
  }

  public void addModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
    myModificationAttemptListenerMulticaster.addListener(listener);
  }

  public void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
    myModificationAttemptListenerMulticaster.removeListener(listener);
  }

  public void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final ModificationAttemptEvent event = new ModificationAttemptEvent(this, files);
    myModificationAttemptListenerMulticaster.getMulticaster().readOnlyModificationAttempt(event);
  }

  public void addVirtualFileManagerListener(VirtualFileManagerListener listener) {
    myVirtualFileManagerListeners.add(listener);
  }

  public void removeVirtualFileManagerListener(VirtualFileManagerListener listener) {
    myVirtualFileManagerListeners.remove(listener);
  }

  public void fireBeforeRefreshStart(boolean asynchronous) {
    if (myRefreshCount++ == 0) {
      for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
        try {
          listener.beforeRefreshStart(asynchronous);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  public void fireAfterRefreshFinish(boolean asynchronous) {
    if (--myRefreshCount == 0) {
      for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
        try {
          listener.afterRefreshFinish(asynchronous);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  private static String convertLocalPathToUrl(@NonNls @NotNull String path) {
    if (path.startsWith("~")) {
      path = System.getProperty(USER_HOME) + path.substring(1);
    }

    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      if (path.endsWith(":/")) { // instead of getting canonical path - see below
        path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
      }
    }

    if (path.length() == 0) {
      try {
        path = new File("").getCanonicalPath();
      }
      catch (IOException e) {
        return null;
      }
    }

    if (SystemInfo.isWindows) {
      if (path.charAt(0) == '/') path = path.substring(1); //hack over new File(path).toUrl().getFile()
      if (path.contains("~")) {
        try {
          path = new File(path.replace('/', File.separatorChar)).getCanonicalPath().replace(File.separatorChar, '/');
        }
        catch (IOException e) {
          return null;
        }
      }
    }

    return LocalFileSystem.PROTOCOL_PREFIX + path.replace(File.separatorChar, '/');
  }

  private static class LoggingListener implements VirtualFileListener {
    public void propertyChanged(VirtualFilePropertyEvent event) {
      LOG.debug("propertyChanged: file = " + event.getFile().getUrl() + ", propertyName = " + event.getPropertyName() + ", oldValue = " +
                event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
    }

    public void contentsChanged(VirtualFileEvent event) {
      LOG.debug("contentsChanged: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());
    }

    public void fileCreated(VirtualFileEvent event) {
      LOG.debug("fileCreated: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());
    }

    public void fileDeleted(VirtualFileEvent event) {
      final VirtualFile parent = event.getParent();  
      LOG.debug("fileDeleted: file = " + event.getFile().getName() + ", parent = " + (parent != null ? parent.getUrl() : null) +
                ", requestor = " + event.getRequestor());
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      LOG.debug("fileMoved: file = " + event.getFile().getUrl() + ", oldParent = " + event.getOldParent() + ", newParent = " +
                event.getNewParent() + ", requestor = " + event.getRequestor());
    }

    public void fileCopied(VirtualFileCopyEvent event) {
      LOG.debug("fileCopied: file = " + event.getFile().getUrl() + "originalFile = " + event.getOriginalFile().getUrl() + ", requestor = " +
                event.getRequestor());
    }

    public void beforeContentsChange(VirtualFileEvent event) {
      LOG.debug("beforeContentsChange: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());
    }

    public void beforePropertyChange(VirtualFilePropertyEvent event) {
      LOG.debug("beforePropertyChange: file = " + event.getFile().getUrl() + ", propertyName = " + event.getPropertyName() +
                ", oldValue = " + event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      LOG.debug("beforeFileDeletion: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());

      LOG.assertTrue(event.getFile().isValid());
    }

    public void beforeFileMovement(VirtualFileMoveEvent event) {
      LOG.debug("beforeFileMovement: file = " + event.getFile().getUrl() + ", oldParent = " + event.getOldParent() + ", newParent = " +
                event.getNewParent() + ", requestor = " + event.getRequestor());
    }
  }

  public long getModificationCount() {
    return myPersistence.getCheapFileSystemModificationCount();
  }
}
