// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

@State(name = "ProjectRootManager")
public class ProjectRootManagerImpl extends ProjectRootManagerEx implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(ProjectRootManagerImpl.class);
  private static final ProjectExtensionPointName<ProjectExtension> EP_NAME = new ProjectExtensionPointName<>("com.intellij.projectExtension");

  private static final String PROJECT_JDK_NAME_ATTR = "project-jdk-name";
  private static final String PROJECT_JDK_TYPE_ATTR = "project-jdk-type";
  private static final String ATTRIBUTE_VERSION = "version";

  protected final Project myProject;

  private final EventDispatcher<ProjectJdkListener> myProjectJdkEventDispatcher = EventDispatcher.create(ProjectJdkListener.class);

  private String myProjectSdkName;
  private String myProjectSdkType;

  private final OrderRootsCache myRootsCache;

  protected boolean myStartupActivityPerformed;
  private boolean myStateLoaded;

  private final RootProvider.RootSetChangedListener myRootProviderChangeListener = new RootProviderChangeListener();

  @ApiStatus.Internal
  public abstract class BatchSession<Change> {
    private final boolean myFileTypes;
    private int myBatchLevel;
    private boolean myChanged;
    private Change myChanges;

    private BatchSession(final boolean fileTypes) {
      myFileTypes = fileTypes;
    }

    void levelUp() {
      if (myBatchLevel == 0) {
        myChanged = false;
        myChanges = null;
      }
      myBatchLevel += 1;
    }

    void levelDown() {
      myBatchLevel -= 1;
      if (myChanged && myBatchLevel == 0) {
        try {
          // todo make sure it should be not null here
          if (myChanges == null) {
            myChanges = getGenericChange();
          }
          WriteAction.run(() -> fireRootsChanged(myChanges));
        }
        finally {
          myChanged = false;
          myChanges = null;
        }
      }
    }

    public void beforeRootsChanged() {
      if (myBatchLevel == 0 || !myChanged) {
        fireBeforeRootsChanged(myFileTypes);
        myChanged = true;
      }
    }

    public void rootsChanged(@NotNull Change change) {
      myChanges = myChanges == null ? change : accumulate(myChanges, change);

      if (myBatchLevel == 0 && myChanged) {
        if (fireRootsChanged(myChanges)) {
          myChanged = false;
          myChanges = null;
        }
      }
    }

    public void rootsChanged() {
     rootsChanged(getGenericChange());
    }

    protected abstract boolean fireRootsChanged(@NotNull Change change);

    @NotNull
    protected abstract Change accumulate(@NotNull Change current, @NotNull Change change);

    @NotNull
    protected abstract Change getGenericChange();
  }

  @ApiStatus.Internal
  public enum RootsChangeType {
    ROOTS_REMOVED, ROOTS_ADDED, GENERIC
  }

  @ApiStatus.Internal
  public BatchSession<RootsChangeType> getRootsChanged() {
    return myRootsChanged;
  }

  protected final BatchSession<RootsChangeType> myRootsChanged = new BatchSession<>(false) {
    @Override
    protected boolean fireRootsChanged(@NotNull ProjectRootManagerImpl.RootsChangeType cause) {
      return ProjectRootManagerImpl.this.fireRootsChanged(false, cause);
    }

    @Override
    protected @NotNull ProjectRootManagerImpl.RootsChangeType accumulate(@NotNull ProjectRootManagerImpl.RootsChangeType current, @NotNull ProjectRootManagerImpl.RootsChangeType cause) {
      if (current == RootsChangeType.GENERIC || cause == RootsChangeType.GENERIC) {
        return RootsChangeType.GENERIC;
      }
      if (current != cause) return RootsChangeType.GENERIC;
      return current;
    }

    @Override
    protected @NotNull ProjectRootManagerImpl.RootsChangeType getGenericChange() {
      return RootsChangeType.GENERIC;
    }
  };

  protected final BatchSession<Boolean> myFileTypesChanged = new BatchSession<>(true) {
    @Override
    protected boolean fireRootsChanged(@NotNull Boolean aBoolean) {
      return ProjectRootManagerImpl.this.fireRootsChanged(true, null);
    }

    @Override
    protected @NotNull Boolean accumulate(@NotNull Boolean current, @NotNull Boolean change) {
      return current || change;
    }

    @Override
    protected @NotNull Boolean getGenericChange() {
      return Boolean.TRUE;
    }
  };
  private final VirtualFilePointerListener myEmptyRootsValidityChangedListener = new VirtualFilePointerListener(){};

  public static ProjectRootManagerImpl getInstanceImpl(Project project) {
    return (ProjectRootManagerImpl)getInstance(project);
  }

  public ProjectRootManagerImpl(@NotNull Project project) {
    myProject = project;
    myRootsCache = new OrderRootsCache(project);
    myJdkTableMultiListener = new JdkTableMultiListener(project);
  }

  @Override
  @NotNull
  public ProjectFileIndex getFileIndex() {
    return ProjectFileIndex.getInstance(myProject);
  }

  @Override
  @NotNull
  public List<String> getContentRootUrls() {
    Module[] modules = getModuleManager().getModules();
    List<String> result = new ArrayList<>(modules.length);
    for (Module module : modules) {
      ContainerUtil.addAll(result, ModuleRootManager.getInstance(module).getContentRootUrls());
    }
    return result;
  }

  @Override
  public VirtualFile @NotNull [] getContentRoots() {
    Module[] modules = getModuleManager().getModules();
    List<VirtualFile> result = new ArrayList<>(modules.length);
    for (Module module : modules) {
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      if (modules.length == 1) {
        return contentRoots;
      }

      ContainerUtil.addAll(result, contentRoots);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public VirtualFile @NotNull [] getContentSourceRoots() {
    Module[] modules = getModuleManager().getModules();
    List<VirtualFile> result = new ArrayList<>(modules.length);
    for (Module module : modules) {
      ContainerUtil.addAll(result, ModuleRootManager.getInstance(module).getSourceRoots());
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public List<VirtualFile> getModuleSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    Module[] modules = getModuleManager().getModules();
    List<VirtualFile> roots = new ArrayList<>(modules.length);
    for (Module module : modules) {
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
  public VirtualFile @NotNull [] getContentRootsFromAllModules() {
    Module[] modules = getModuleManager().getSortedModules();
    List<VirtualFile> result = new ArrayList<>(modules.length + 1);
    for (Module module : modules) {
      Collections.addAll(result, ModuleRootManager.getInstance(module).getContentRoots());
    }
    ContainerUtil.addIfNotNull(result, myProject.getBaseDir());
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public Sdk getProjectSdk() {
    if (myProjectSdkName == null) {
      return null;
    }

    ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    if (myProjectSdkType == null) {
      return projectJdkTable.findJdk(myProjectSdkName);
    }
    else {
      return projectJdkTable.findJdk(myProjectSdkName, myProjectSdkType);
    }
  }

  @Nullable
  @Override
  public String getProjectSdkName() {
    return myProjectSdkName;
  }

  @Nullable
  @Override
  public String getProjectSdkTypeName() {
    return myProjectSdkType;
  }

  @Override
  public void setProjectSdk(@Nullable Sdk sdk) {
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

  protected void projectJdkChanged() {
    incModificationCount();
    mergeRootsChangesDuring(getActionToRunWhenProjectJdkChanges());
    Sdk sdk = getProjectSdk();
    for (ProjectExtension extension : EP_NAME.getExtensions(myProject)) {
      extension.projectSdkChanged(sdk);
    }
  }

  @NotNull
  protected Runnable getActionToRunWhenProjectJdkChanges() {
    return () -> myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged();
  }

  @Override
  @Deprecated
  public void setProjectSdkName(@NotNull String name) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myProjectSdkName = name;
    myProjectSdkType = null;

    projectJdkChanged();
  }

  @Override
  public void setProjectSdkName(@NotNull String name, @NotNull String sdkTypeName) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myProjectSdkName = name;
    myProjectSdkType = sdkTypeName;

    projectJdkChanged();
  }

  @Override
  public void addProjectJdkListener(@NotNull ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.addListener(listener);
  }

  @Override
  public void removeProjectJdkListener(@NotNull ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.removeListener(listener);
  }

  @Override
  public void loadState(@NotNull Element element) {
    for (ProjectExtension extension : EP_NAME.getExtensions(myProject)) {
      extension.readExternal(element);
    }
    myProjectSdkName = element.getAttributeValue(PROJECT_JDK_NAME_ATTR);
    myProjectSdkType = element.getAttributeValue(PROJECT_JDK_TYPE_ATTR);

    if (myStateLoaded) {
      Application app = ApplicationManager.getApplication();
      if (app != null) {
        app.invokeLater(() -> app.runWriteAction(() -> projectJdkChanged()), app.getNoneModalityState());
      }
    } else {
      myStateLoaded = true;
    }
  }

  @Override
  public void noStateLoaded() {
    myStateLoaded = true;
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    element.setAttribute(ATTRIBUTE_VERSION, "2");
    for (ProjectExtension extension : EP_NAME.getExtensions(myProject)) {
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

  @Override
  public void mergeRootsChangesDuring(@NotNull Runnable runnable) {
    BatchSession<?> batchSession = myRootsChanged;
    batchSession.levelUp();
    try {
      runnable.run();
    }
    finally {
      batchSession.levelDown();
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
      ModuleRootManagerEx.getInstanceEx(module).dropCaches();
    }
  }

  @Override
  public void makeRootsChange(@NotNull Runnable runnable, boolean fileTypes, boolean fireEvents) {
    if (myProject.isDisposed()) return;
    BatchSession<?> session = fileTypes ? myFileTypesChanged : myRootsChanged;
    try {
      if (fireEvents) session.beforeRootsChanged();
      runnable.run();
    }
    finally {
      if (fireEvents) session.rootsChanged();
    }
  }

  protected boolean isFiringEvent;

  private void fireBeforeRootsChanged(boolean fileTypes) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    fireBeforeRootsChangeEvent(fileTypes);
  }

  @ApiStatus.Internal
  protected void fireBeforeRootsChangeEvent(boolean fileTypes) { }

  private boolean fireRootsChanged(boolean fileTypes, @Nullable ProjectRootManagerImpl.RootsChangeType cause) {
    if (myProject.isDisposed()) return false;

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    clearScopesCaches();

    incModificationCount();

    fireRootsChangedEvent(fileTypes, cause);

    return true;
  }

  @ApiStatus.Internal
  protected void fireRootsChangedEvent(boolean fileTypes,
                                       @Nullable ProjectRootManagerImpl.RootsChangeType cause) { }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public static String extractLocalPath(@NotNull String url) {
    String path = VfsUtilCore.urlToPath(url);
    int separatorIndex = path.indexOf(URLUtil.JAR_SEPARATOR);
    return separatorIndex > 0 ? path.substring(0, separatorIndex) : path;
  }

  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  void subscribeToRootProvider(@NotNull OrderEntry owner, @NotNull RootProvider provider) {
    synchronized (myRegisteredRootProviders) {
      Set<OrderEntry> owners = myRegisteredRootProviders.get(provider);
      if (owners == null) {
        owners = new HashSet<>();
        myRegisteredRootProviders.put(provider, owners);
        provider.addRootSetChangedListener(myRootProviderChangeListener);
      }
      owners.add(owner);
    }
  }

  void unsubscribeFromRootProvider(@NotNull OrderEntry owner, @NotNull RootProvider provider) {
    synchronized (myRegisteredRootProviders) {
      Set<OrderEntry> owners = myRegisteredRootProviders.get(provider);
      if (owners != null) {
        owners.remove(owner);
        if (owners.isEmpty()) {
          provider.removeRootSetChangedListener(myRootProviderChangeListener);
          myRegisteredRootProviders.remove(provider);
        }
      }
    }
  }

  void addListenerForTable(@NotNull LibraryTable.Listener libraryListener, @NotNull LibraryTable libraryTable) {
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

  void removeListenerForTable(@NotNull LibraryTable.Listener libraryListener, @NotNull LibraryTable libraryTable) {
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

  private static class ListenerContainer<T> {
    private final Set<T> myListeners = new LinkedHashSet<>();
    private final T @NotNull [] myEmptyArray;
    private T[] myListenersArray;

    private ListenerContainer(T @NotNull [] emptyArray) {
      myEmptyArray = emptyArray;
    }

    synchronized void addListener(@NotNull T listener) {
      myListeners.add(listener);
      myListenersArray = null;
    }

    synchronized boolean removeListener(@NotNull T listener) {
      myListeners.remove(listener);
      myListenersArray = null;
      return myListeners.isEmpty();
    }

    synchronized T @NotNull [] getListeners() {
      if (myListenersArray == null) {
        myListenersArray = myListeners.toArray(myEmptyArray);
      }
      return myListenersArray;
    }
  }

  private final class LibraryTableMultiListener extends ListenerContainer<LibraryTable.Listener> implements LibraryTable.Listener {
    private LibraryTableMultiListener() {
      super(new LibraryTable.Listener[0]);
    }

    @Override
    public void afterLibraryAdded(@NotNull final Library newLibrary) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.afterLibraryAdded(newLibrary);
        }
      });
    }

    @Override
    public void afterLibraryRenamed(@NotNull Library library, @Nullable String oldName) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.afterLibraryRenamed(library, oldName);
        }
      });
    }

    @Override
    public void beforeLibraryRemoved(@NotNull final Library library) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.beforeLibraryRemoved(library);
        }
      });
    }

    @Override
    public void afterLibraryRemoved(@NotNull final Library library) {
      incModificationCount();
      mergeRootsChangesDuring(() -> {
        for (LibraryTable.Listener listener : getListeners()) {
          listener.afterLibraryRemoved(library);
        }
      });
    }
  }

  private final JdkTableMultiListener myJdkTableMultiListener;

  private final class JdkTableMultiListener extends ListenerContainer<ProjectJdkTable.Listener> implements ProjectJdkTable.Listener {
    private JdkTableMultiListener(@NotNull Project project) {
      super(new ProjectJdkTable.Listener[0]);

      project.getMessageBus().connect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this);
    }

    @Override
    public void jdkAdded(@NotNull final Sdk jdk) {
      mergeRootsChangesDuring(() -> {
        for (ProjectJdkTable.Listener listener : getListeners()) {
          listener.jdkAdded(jdk);
        }
      });
    }

    @Override
    public void jdkRemoved(@NotNull final Sdk jdk) {
      mergeRootsChangesDuring(() -> {
        for (ProjectJdkTable.Listener listener : getListeners()) {
          listener.jdkRemoved(jdk);
        }
      });
    }

    @Override
    public void jdkNameChanged(@NotNull final Sdk jdk, @NotNull final String previousName) {
      mergeRootsChangesDuring(() -> {
        for (ProjectJdkTable.Listener listener : getListeners()) {
          listener.jdkNameChanged(jdk, previousName);
        }
      });
      String currentName = getProjectSdkName();
      if (previousName.equals(currentName)) {
        // if already had jdk name and that name was the name of the jdk just changed
        myProjectSdkName = jdk.getName();
        myProjectSdkType = jdk.getSdkType().getName();
      }
    }
  }

  private final Map<RootProvider, Set<OrderEntry>> myRegisteredRootProviders = new IdentityHashMap<>();

  void addJdkTableListener(@NotNull ProjectJdkTable.Listener jdkTableListener, @NotNull Disposable parent) {
    myJdkTableMultiListener.addListener(jdkTableListener);
    Disposer.register(parent, () -> myJdkTableMultiListener.removeListener(jdkTableListener));
  }

  @Override
  public void assertListenersAreDisposed() {
    synchronized (myRegisteredRootProviders) {
      if (!myRegisteredRootProviders.isEmpty()) {
        StringBuilder details = new StringBuilder();
        int count = 0;
        for (Map.Entry<RootProvider, Set<OrderEntry>> entry : myRegisteredRootProviders.entrySet()) {
          if (count++ >= 10) {
            details.append(myRegisteredRootProviders.entrySet().size() - 10).append(" more providers.\n");
            break;
          }
          details.append(" ").append(entry.getKey()).append(" referenced by ").append(entry.getValue().size()).append(" order entries:\n");
          for (OrderEntry orderEntry : entry.getValue()) {
            details.append("   ").append(orderEntry);
            if (orderEntry instanceof RootModelComponentBase) {
              details.append(", isDisposed = ").append(((RootModelComponentBase)orderEntry).isDisposed());
              details.append(", root model = ").append(((RootModelComponentBase)orderEntry).getRootModel());
              details.append(", module.isDisposed = ").append(((RootModelComponentBase)orderEntry).getRootModel().getModule().isDisposed());
            }
            details.append("\n");
          }
        }
        LOG.error("Listeners for " + myRegisteredRootProviders.size() + " root providers in " + myProject + " aren't disposed:" + details);
        for (RootProvider provider : myRegisteredRootProviders.keySet()) {
          provider.removeRootSetChangedListener(myRootProviderChangeListener);
        }
      }
    }
  }

  private class RootProviderChangeListener implements RootProvider.RootSetChangedListener {
    private boolean myInsideRootsChange;

    @Override
    public void rootSetChanged(@NotNull final RootProvider wrapper) {
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
  public void markRootsForRefresh() {

  }

  @NotNull
  public VirtualFilePointerListener getRootsValidityChangedListener() {
    return myEmptyRootsValidityChangedListener;
  }
}