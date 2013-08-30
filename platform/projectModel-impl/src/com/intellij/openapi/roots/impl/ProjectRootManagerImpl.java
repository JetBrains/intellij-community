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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

/**
 * @author max
 */
public class ProjectRootManagerImpl extends ProjectRootManagerEx implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootManagerImpl");

  @NonNls public static final String PROJECT_JDK_NAME_ATTR = "project-jdk-name";
  @NonNls public static final String PROJECT_JDK_TYPE_ATTR = "project-jdk-type";

  protected final Project myProject;

  private final EventDispatcher<ProjectJdkListener> myProjectJdkEventDispatcher = EventDispatcher.create(ProjectJdkListener.class);

  private String myProjectSdkName;
  private String myProjectSdkType;

  private long myModificationCount = 0;
  @NonNls private static final String ATTRIBUTE_VERSION = "version";

  private final OrderRootsCache myRootsCache;

  protected boolean myStartupActivityPerformed = false;

  private final RootProviderChangeListener myRootProviderChangeListener = new RootProviderChangeListener();

  protected class BatchSession {
    private int myBatchLevel = 0;
    private boolean myChanged = false;

    private final boolean myFileTypes;

    private BatchSession(final boolean fileTypes) {
      myFileTypes = fileTypes;
    }

    protected void levelUp() {
      if (myBatchLevel == 0) {
        myChanged = false;
      }
      myBatchLevel += 1;
    }

    protected void levelDown() {
      myBatchLevel -= 1;
      if (myChanged && myBatchLevel == 0) {
        try {
          fireChange();
        }
        finally {
          myChanged = false;
        }
      }
    }

    private boolean fireChange() {
      return fireRootsChanged(myFileTypes);
    }

    protected void beforeRootsChanged() {
      if (myBatchLevel == 0 || !myChanged) {
        if (fireBeforeRootsChanged(myFileTypes)) {
          myChanged = true;
        }
      }
    }

    protected void rootsChanged() {
      if (myBatchLevel == 0) {
        if (fireChange()) {
          myChanged = false;
        }
      }
    }
  }

  protected final BatchSession myRootsChanged = new BatchSession(false);
  protected final BatchSession myFileTypesChanged = new BatchSession(true);

  public static ProjectRootManagerImpl getInstanceImpl(Project project) {
    return (ProjectRootManagerImpl)getInstance(project);
  }

  public ProjectRootManagerImpl(Project project,
                                DirectoryIndex directoryIndex) {
    myProject = project;
    myRootsCache = new OrderRootsCache(project);
  }

  @Override
  @NotNull
  public ProjectFileIndex getFileIndex() {
    return ProjectFileIndex.SERVICE.getInstance(myProject);
  }

  private final Map<ModuleRootListener, MessageBusConnection> myListenerAdapters = new HashMap<ModuleRootListener, MessageBusConnection>();

  @Override
  @NotNull
  public List<String> getContentRootUrls() {
    final List<String> result = new ArrayList<String>();
    for (Module module : getModuleManager().getModules()) {
      final String[] urls = ModuleRootManager.getInstance(module).getContentRootUrls();
      ContainerUtil.addAll(result, urls);
    }
    return result;
  }

  @Override
  @NotNull
  public VirtualFile[] getContentRoots() {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Module module : getModuleManager().getModules()) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, contentRoots);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public VirtualFile[] getContentSourceRoots() {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Module module : getModuleManager().getModules()) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      ContainerUtil.addAll(result, sourceRoots);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public List<VirtualFile> getModuleSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    for (Module module : getModuleManager().getModules()) {
      roots.addAll(ModuleRootManager.getInstance(module).getSourceRoots(rootTypes));
    }
    return roots;
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ProjectOrderEnumerator(myProject, myRootsCache);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries(@NotNull Collection<? extends Module> modules) {
    return new ModulesOrderEnumerator(myProject, modules);
  }

  @Override
  public VirtualFile[] getContentRootsFromAllModules() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, files);
    }
    result.add(myProject.getBaseDir());
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public Sdk getProjectSdk() {
    return myProjectSdkName == null ? null : ProjectJdkTable.getInstance().findJdk(myProjectSdkName, myProjectSdkType);
  }

  @Override
  public String getProjectSdkName() {
    return myProjectSdkName;
  }

  @Override
  public void setProjectSdk(Sdk sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (sdk == null) {
      myProjectSdkName = null;
      myProjectSdkType = null;
    }
    else {
      myProjectSdkName = sdk.getName();
      myProjectSdkType = sdk.getSdkType().getName();
    }
    mergeRootsChangesDuring(new Runnable() {
      @Override
      public void run() {
        myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged();
      }
    });
  }

  @Override
  public void setProjectSdkName(String name) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myProjectSdkName = name;

    mergeRootsChangesDuring(new Runnable() {
      @Override
      public void run() {
        myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged();
      }
    });
  }

  @Override
  public void addProjectJdkListener(ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.addListener(listener);
  }

  @Override
  public void removeProjectJdkListener(ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.removeListener(listener);
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "ProjectRootManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    myJdkTableMultiListener = null;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.readExternal(element);
    }
    myProjectSdkName = element.getAttributeValue(PROJECT_JDK_NAME_ATTR);
    myProjectSdkType = element.getAttributeValue(PROJECT_JDK_TYPE_ATTR);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATTRIBUTE_VERSION, "2");
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.writeExternal(element);
    }
    if (myProjectSdkName != null) {
      element.setAttribute(PROJECT_JDK_NAME_ATTR, myProjectSdkName);
    }
    if (myProjectSdkType != null) {
      element.setAttribute(PROJECT_JDK_TYPE_ATTR, myProjectSdkType);
    }
  }

  private boolean myMergedCallStarted = false;
  private boolean myMergedCallHasRootChange = false;
  private int myRootsChangesDepth = 0;

  @Override
  public void mergeRootsChangesDuring(@NotNull Runnable runnable) {
    if (getBatchSession(false).myBatchLevel == 0 && !myMergedCallStarted) {
      LOG.assertTrue(myRootsChangesDepth == 0,
                     "Merged rootsChanged not allowed inside rootsChanged, rootsChanged level == " + myRootsChangesDepth);
      myMergedCallStarted = true;
      myMergedCallHasRootChange = false;
      try {
        runnable.run();
      }
      finally {
        if (myMergedCallHasRootChange) {
          LOG.assertTrue(myRootsChangesDepth == 1, "myMergedCallDepth = " + myRootsChangesDepth);
          getBatchSession(false).rootsChanged();
        }
        myMergedCallStarted = false;
        myMergedCallHasRootChange = false;
      }
    }
    else {
      runnable.run();
    }
  }

  protected void clearScopesCaches() {
    clearScopesCachesForModules();
  }

  @Override
  public void clearScopesCachesForModules() {
    myRootsCache.clearCache();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).dropCaches();
    }
  }

  @Override
  public void makeRootsChange(@NotNull Runnable runnable, boolean filetypes, boolean fireEvents) {
    if (myProject.isDisposed()) return;
    BatchSession session = getBatchSession(filetypes);
    if (fireEvents) session.beforeRootsChanged();
    try {
      runnable.run();
    }
    finally {
      if (fireEvents) session.rootsChanged();
    }
  }

  protected BatchSession getBatchSession(final boolean filetypes) {
    return filetypes ? myFileTypesChanged : myRootsChanged;
  }

  protected boolean isFiringEvent = false;

  private boolean fireBeforeRootsChanged(boolean filetypes) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!filetypes, "Filetypes change is not supported inside merged call");
    }

    if (myRootsChangesDepth++ == 0) {
      if (myMergedCallStarted) {
        myMergedCallHasRootChange = true;
        myRootsChangesDepth++; // blocks all firing until finishRootsChangedOnDemand
      }
      fireBeforeRootsChangeEvent(filetypes);
      return true;
    }

    return false;
  }

  protected void fireBeforeRootsChangeEvent(boolean filetypes) {
  }

  private boolean fireRootsChanged(boolean filetypes) {
    if (myProject.isDisposed()) return false;

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!filetypes, "Filetypes change is not supported inside merged call");
    }

    myRootsChangesDepth--;
    if (myRootsChangesDepth > 0) return false;

    clearScopesCaches();

    myModificationCount++;

    PsiManager psiManager = PsiManager.getInstance(myProject);
    psiManager.dropResolveCaches();
    ((PsiModificationTrackerImpl)psiManager.getModificationTracker()).incCounter();

    fireRootsChangedEvent(filetypes);

    doSynchronizeRoots();

    addRootsToWatch();

    return true;
  }

  protected void fireRootsChangedEvent(boolean filetypes) {
  }

  protected void addRootsToWatch() {
  }

  public Project getProject() {
    return myProject;
  }

  protected void doSynchronizeRoots() {
  }

  public static String extractLocalPath(final String url) {
    final String path = VfsUtilCore.urlToPath(url);
    final int jarSeparatorIndex = path.indexOf(StandardFileSystems.JAR_SEPARATOR);
    if (jarSeparatorIndex > 0) {
      return path.substring(0, jarSeparatorIndex);
    }
    return path;
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  void subscribeToRootProvider(OrderEntry owner, final RootProvider provider) {
    Set<OrderEntry> owners = myRegisteredRootProviders.get(provider);
    if (owners == null) {
      owners = new HashSet<OrderEntry>();
      myRegisteredRootProviders.put(provider, owners);
      provider.addRootSetChangedListener(myRootProviderChangeListener);
    }
    owners.add(owner);
  }

  void unsubscribeFromRootProvider(OrderEntry owner, final RootProvider provider) {
    Set<OrderEntry> owners = myRegisteredRootProviders.get(provider);
    if (owners != null) {
      owners.remove(owner);
      if (owners.isEmpty()) {
        provider.removeRootSetChangedListener(myRootProviderChangeListener);
        myRegisteredRootProviders.remove(provider);
      }
    }
  }

  void addListenerForTable(LibraryTable.Listener libraryListener,
                           final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.addListener(libraryListener);
  }

  void removeListenerForTable(LibraryTable.Listener libraryListener,
                              final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.removeListener(libraryListener);
  }

  private final Map<LibraryTable, LibraryTableMultilistener> myLibraryTableMultilisteners
    = new HashMap<LibraryTable, LibraryTableMultilistener>();

  private class LibraryTableMultilistener implements LibraryTable.Listener {
    final List<LibraryTable.Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private final LibraryTable myLibraryTable;

    private LibraryTableMultilistener(LibraryTable libraryTable) {
      myLibraryTable = libraryTable;
      myLibraryTable.addListener(this);
      myLibraryTableMultilisteners.put(myLibraryTable, this);
    }

    private void addListener(LibraryTable.Listener listener) {
      myListeners.add(listener);
    }

    private void removeListener(LibraryTable.Listener listener) {
      myListeners.remove(listener);
      if (myListeners.isEmpty()) {
        myLibraryTable.removeListener(this);
        myLibraryTableMultilisteners.remove(myLibraryTable);
      }
    }

    @Override
    public void afterLibraryAdded(final Library newLibrary) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryAdded(newLibrary);
          }
        }
      });
    }

    @Override
    public void afterLibraryRenamed(final Library library) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryRenamed(library);
          }
        }
      });
    }

    @Override
    public void beforeLibraryRemoved(final Library library) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.beforeLibraryRemoved(library);
          }
        }
      });
    }

    @Override
    public void afterLibraryRemoved(final Library library) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryRemoved(library);
          }
        }
      });
    }
  }

  private JdkTableMultiListener myJdkTableMultiListener = null;

  private class JdkTableMultiListener implements ProjectJdkTable.Listener {
    final EventDispatcher<ProjectJdkTable.Listener> myDispatcher = EventDispatcher.create(ProjectJdkTable.Listener.class);
    private MessageBusConnection listenerConnection;

    private JdkTableMultiListener(Project project) {
      listenerConnection = project.getMessageBus().connect();
      listenerConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this);
    }

    private void addListener(ProjectJdkTable.Listener listener) {
      myDispatcher.addListener(listener);
    }

    private void removeListener(ProjectJdkTable.Listener listener) {
      myDispatcher.removeListener(listener);
      uninstallListener(true);
    }

    @Override
    public void jdkAdded(final Sdk jdk) {
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          myDispatcher.getMulticaster().jdkAdded(jdk);
        }
      });
    }

    @Override
    public void jdkRemoved(final Sdk jdk) {
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          myDispatcher.getMulticaster().jdkRemoved(jdk);
        }
      });
    }

    @Override
    public void jdkNameChanged(final Sdk jdk, final String previousName) {
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          myDispatcher.getMulticaster().jdkNameChanged(jdk, previousName);
        }
      });
      String currentName = getProjectSdkName();
      if (previousName != null && previousName.equals(currentName)) {
        // if already had jdk name and that name was the name of the jdk just changed
        myProjectSdkName = jdk.getName();
        myProjectSdkType = jdk.getSdkType().getName();
      }
    }

    public void uninstallListener(boolean soft) {
      if (!soft || !myDispatcher.hasListeners()) {
        if (listenerConnection != null) {
          listenerConnection.disconnect();
          listenerConnection = null;
        }
      }
    }
  }

  private final Map<RootProvider, Set<OrderEntry>> myRegisteredRootProviders = new HashMap<RootProvider, Set<OrderEntry>>();

  void addJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
    getJdkTableMultiListener().addListener(jdkTableListener);
  }

  private JdkTableMultiListener getJdkTableMultiListener() {
    if (myJdkTableMultiListener == null) {
      myJdkTableMultiListener = new JdkTableMultiListener(myProject);
    }
    return myJdkTableMultiListener;
  }

  void removeJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
    if (myJdkTableMultiListener == null) return;
    myJdkTableMultiListener.removeListener(jdkTableListener);
  }

  private class RootProviderChangeListener implements RootProvider.RootSetChangedListener {
    private boolean myInsideRootsChange;

    @Override
    public void rootSetChanged(final RootProvider wrapper) {
      if (myInsideRootsChange) return;
      myInsideRootsChange = true;
      try {
        makeRootsChange(EmptyRunnable.INSTANCE, false, true);
      }
      finally {
        myInsideRootsChange = false;
      }
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }
}
