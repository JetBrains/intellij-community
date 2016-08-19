/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

@State(name = "ProjectRootManager")
public class ProjectRootManagerImpl extends ProjectRootManagerEx implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootManagerImpl");

  @NonNls public static final String PROJECT_JDK_NAME_ATTR = "project-jdk-name";
  @NonNls public static final String PROJECT_JDK_TYPE_ATTR = "project-jdk-type";

  protected final Project myProject;

  private final EventDispatcher<ProjectJdkListener> myProjectJdkEventDispatcher = EventDispatcher.create(ProjectJdkListener.class);

  private String myProjectSdkName;
  private String myProjectSdkType;

  @NonNls private static final String ATTRIBUTE_VERSION = "version";

  private final OrderRootsCache myRootsCache;

  protected boolean myStartupActivityPerformed;

  private final RootProviderChangeListener myRootProviderChangeListener = new RootProviderChangeListener();

  protected class BatchSession {
    private int myBatchLevel;
    private boolean myChanged;

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
        AccessToken token = WriteAction.start();
        try {
          fireChange();
        }
        finally {
          try {
            myChanged = false;
          }
          finally {
            token.finish();
          }
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

  public ProjectRootManagerImpl(Project project) {
    myProject = project;
    myRootsCache = new OrderRootsCache(project);
    myJdkTableMultiListener = new JdkTableMultiListener(project);
  }

  @Override
  @NotNull
  public ProjectFileIndex getFileIndex() {
    return ProjectFileIndex.SERVICE.getInstance(myProject);
  }

  @Override
  @NotNull
  public List<String> getContentRootUrls() {
    final List<String> result = new ArrayList<>();
    for (Module module : getModuleManager().getModules()) {
      final String[] urls = ModuleRootManager.getInstance(module).getContentRootUrls();
      ContainerUtil.addAll(result, urls);
    }
    return result;
  }

  @Override
  @NotNull
  public VirtualFile[] getContentRoots() {
    final List<VirtualFile> result = new ArrayList<>();
    for (Module module : getModuleManager().getModules()) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, contentRoots);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public VirtualFile[] getContentSourceRoots() {
    final List<VirtualFile> result = new ArrayList<>();
    for (Module module : getModuleManager().getModules()) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      ContainerUtil.addAll(result, sourceRoots);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public List<VirtualFile> getModuleSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    List<VirtualFile> roots = new ArrayList<>();
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
    return new ModulesOrderEnumerator(modules);
  }

  @Override
  public VirtualFile[] getContentRootsFromAllModules() {
    List<VirtualFile> result = new ArrayList<>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, files);
    }
    ContainerUtil.addIfNotNull(result, myProject.getBaseDir());
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
    projectJdkChanged();
  }

  private void projectJdkChanged() {
    mergeRootsChangesDuring(() -> myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged());
    Sdk sdk = getProjectSdk();
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.projectSdkChanged(sdk);
    }
  }

  @Override
  public void setProjectSdkName(String name) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myProjectSdkName = name;

    projectJdkChanged();
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
  public void loadState(Element element) {
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.readExternal(element);
    }
    myProjectSdkName = element.getAttributeValue(PROJECT_JDK_NAME_ATTR);
    myProjectSdkType = element.getAttributeValue(PROJECT_JDK_TYPE_ATTR);
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("state");
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

    if (element.getAttributes().size() == 1) {
      // remove empty element to not write defaults
      element.removeAttribute(ATTRIBUTE_VERSION);
    }
    return element;
  }

  private boolean myMergedCallStarted;
  private boolean myMergedCallHasRootChange;
  private int myRootsChangesDepth;

  @Override
  public void mergeRootsChangesDuring(@NotNull Runnable runnable) {
    if (getBatchSession(false).myBatchLevel == 0 && !myMergedCallStarted) {
      if (myRootsChangesDepth != 0) {
        int depth = myRootsChangesDepth;
        myRootsChangesDepth = 0;
        LOG.error("Merged rootsChanged not allowed inside rootsChanged, rootsChanged level == " + depth);
      }
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
  public void makeRootsChange(@NotNull Runnable runnable, boolean fileTypes, boolean fireEvents) {
    if (myProject.isDisposed()) return;
    BatchSession session = getBatchSession(fileTypes);
    try {
      if (fireEvents) session.beforeRootsChanged();
      runnable.run();
    }
    finally {
      if (fireEvents) session.rootsChanged();
    }
  }

  protected BatchSession getBatchSession(final boolean fileTypes) {
    return fileTypes ? myFileTypesChanged : myRootsChanged;
  }

  protected boolean isFiringEvent;

  private boolean fireBeforeRootsChanged(boolean fileTypes) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!fileTypes, "File types change is not supported inside merged call");
    }

    if (myRootsChangesDepth++ == 0) {
      if (myMergedCallStarted) {
        myMergedCallHasRootChange = true;
        myRootsChangesDepth++; // blocks all firing until finishRootsChangedOnDemand
      }
      fireBeforeRootsChangeEvent(fileTypes);
      return true;
    }

    return false;
  }

  protected void fireBeforeRootsChangeEvent(boolean fileTypes) {
  }

  private boolean fireRootsChanged(boolean fileTypes) {
    if (myProject.isDisposed()) return false;

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!fileTypes, "File types change is not supported inside merged call");
    }

    myRootsChangesDepth--;
    if (myRootsChangesDepth > 0) return false;
    if (myRootsChangesDepth < 0) {
      LOG.info("Restoring from roots change start/finish mismatch: ", new Throwable());
      myRootsChangesDepth = 0;
    }

    clearScopesCaches();

    incModificationCount();

    PsiManager psiManager = PsiManager.getInstance(myProject);
    psiManager.dropResolveCaches();
    ((PsiModificationTrackerImpl)psiManager.getModificationTracker()).incCounter();

    fireRootsChangedEvent(fileTypes);

    doSynchronizeRoots();

    addRootsToWatch();

    return true;
  }

  protected void fireRootsChangedEvent(boolean fileTypes) {
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
    final int jarSeparatorIndex = path.indexOf(URLUtil.JAR_SEPARATOR);
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
      owners = new HashSet<>();
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
    synchronized (myLibraryTableListenersLock) {
      LibraryTableMultiListener multiListener = myLibraryTableMultiListeners.get(libraryTable);
      if (multiListener == null) {
        multiListener = new LibraryTableMultiListener();
        libraryTable.addListener(multiListener);
        myLibraryTableMultiListeners.put(libraryTable, multiListener);
      }
      multiListener.addListener(libraryListener);
    }
  }

  void removeListenerForTable(LibraryTable.Listener libraryListener,
                              final LibraryTable libraryTable) {
    synchronized (myLibraryTableListenersLock) {
      LibraryTableMultiListener multiListener = myLibraryTableMultiListeners.get(libraryTable);
      if (multiListener != null) {
        boolean last = multiListener.removeListener(libraryListener);
        if (last) {
          libraryTable.removeListener(multiListener);
          myLibraryTableMultiListeners.remove(libraryTable);
        }
      }
    }
  }

  private final Object myLibraryTableListenersLock = new Object();
  private final Map<LibraryTable, LibraryTableMultiListener> myLibraryTableMultiListeners = new HashMap<>();

  private class LibraryTableMultiListener implements LibraryTable.Listener {
    private final Set<LibraryTable.Listener> myListeners = new LinkedHashSet<>();
    private LibraryTable.Listener[] myListenersArray;

    private synchronized void addListener(LibraryTable.Listener listener) {
      myListeners.add(listener);
      myListenersArray = null;
    }

    private synchronized boolean removeListener(LibraryTable.Listener listener) {
      myListeners.remove(listener);
      myListenersArray = null;
      return myListeners.isEmpty();
    }

    @Override
    public void afterLibraryAdded(final Library newLibrary) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.afterLibraryAdded(newLibrary);
        }
      });
    }

    private synchronized LibraryTable.Listener[] getListeners() {
      if (myListenersArray == null) {
        myListenersArray = myListeners.toArray(new LibraryTable.Listener[myListeners.size()]);
      }
      return myListenersArray;
    }

    @Override
    public void afterLibraryRenamed(final Library library) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.afterLibraryRenamed(library);
        }
      });
    }

    @Override
    public void beforeLibraryRemoved(final Library library) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.beforeLibraryRemoved(library);
        }
      });
    }

    @Override
    public void afterLibraryRemoved(final Library library) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.afterLibraryRemoved(library);
        }
      });
    }
  }

  private final JdkTableMultiListener myJdkTableMultiListener;

  private class JdkTableMultiListener implements ProjectJdkTable.Listener {
    private final Set<ProjectJdkTable.Listener> myListeners = new LinkedHashSet<>();
    private final MessageBusConnection listenerConnection;
    private ProjectJdkTable.Listener[] myListenersArray;

    private JdkTableMultiListener(Project project) {
      listenerConnection = project.getMessageBus().connect();
      listenerConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this);
    }

    private synchronized void addListener(ProjectJdkTable.Listener listener) {
      myListeners.add(listener);
      myListenersArray = null;
    }

    private synchronized void removeListener(ProjectJdkTable.Listener listener) {
      myListeners.remove(listener);
      myListenersArray = null;
    }

    private synchronized ProjectJdkTable.Listener[] getListeners() {
      if (myListenersArray == null) {
        myListenersArray = myListeners.toArray(new ProjectJdkTable.Listener[myListeners.size()]);
      }
      return myListenersArray;
    }

    @Override
    public void jdkAdded(final Sdk jdk) {
      mergeRootsChangesDuring(() -> {
        for (ProjectJdkTable.Listener listener : getListeners()) {
          listener.jdkAdded(jdk);
        }
      });
    }

    @Override
    public void jdkRemoved(final Sdk jdk) {
      mergeRootsChangesDuring(() -> {
        for (ProjectJdkTable.Listener listener : getListeners()) {
          listener.jdkRemoved(jdk);
        }
      });
    }

    @Override
    public void jdkNameChanged(final Sdk jdk, final String previousName) {
      mergeRootsChangesDuring(() -> {
        for (ProjectJdkTable.Listener listener : getListeners()) {
          listener.jdkNameChanged(jdk, previousName);
        }
      });
      String currentName = getProjectSdkName();
      if (previousName != null && previousName.equals(currentName)) {
        // if already had jdk name and that name was the name of the jdk just changed
        myProjectSdkName = jdk.getName();
        myProjectSdkType = jdk.getSdkType().getName();
      }
    }
  }

  private final Map<RootProvider, Set<OrderEntry>> myRegisteredRootProviders = new HashMap<>();

  void addJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
    myJdkTableMultiListener.addListener(jdkTableListener);
  }

  void removeJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
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
}
