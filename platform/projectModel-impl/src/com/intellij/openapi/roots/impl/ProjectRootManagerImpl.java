// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

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
import com.intellij.openapi.project.RootsChangeIndexingInfo;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
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

  @ApiStatus.Internal
  public abstract class BatchSession<Change, ChangeList> {
    private final boolean myFileTypes;
    private int myBatchLevel;
    private int myPendingRootsChanged;
    private boolean myChanged;
    private ChangeList myChanges;

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
            myChanges = initiateChangelist(getGenericChange());
          }
          myPendingRootsChanged--;
          WriteAction.run(() -> fireRootsChanged(myChanges));
        }
        finally {
          if (myPendingRootsChanged == 0) {
            myChanged = false;
            myChanges = null;
          }
        }
      }
    }

    public void beforeRootsChanged() {
      if (myBatchLevel == 0 || !myChanged) {
        fireBeforeRootsChanged(myFileTypes);
        myPendingRootsChanged++;
        myChanged = true;
      }
    }

    public void rootsChanged(@NotNull Change change) {
      myChanges = myChanges == null ? initiateChangelist(change) : accumulate(myChanges, change);

      if (myBatchLevel == 0 && myChanged) {
        myPendingRootsChanged--;
        if (fireRootsChanged(myChanges) && myPendingRootsChanged == 0) {
          myChanged = false;
          myChanges = null;
        }
      }
    }

    public void rootsChanged() {
      rootsChanged(getGenericChange());
    }

    protected abstract boolean fireRootsChanged(@NotNull ChangeList change);

    protected abstract @NotNull ChangeList initiateChangelist(@NotNull Change change);

    @NotNull
    protected abstract ChangeList accumulate(@NotNull ChangeList current, @NotNull Change change);

    @NotNull
    protected abstract Change getGenericChange();
  }

  @ApiStatus.Internal
  public BatchSession<RootsChangeIndexingInfo, List<RootsChangeIndexingInfo>> getRootsChanged() {
    return myRootsChanged;
  }

  protected final BatchSession<RootsChangeIndexingInfo, List<RootsChangeIndexingInfo>>
    myRootsChanged = new BatchSession<>(false) {
    @Override
    protected boolean fireRootsChanged(@NotNull List<RootsChangeIndexingInfo> changes) {
      return ProjectRootManagerImpl.this.fireRootsChanged(false, changes);
    }

    @Override
    protected @NotNull List<RootsChangeIndexingInfo> accumulate(@NotNull List<RootsChangeIndexingInfo> currentPair,
                                                                @NotNull RootsChangeIndexingInfo cause) {
      currentPair.add(cause);
      return currentPair;
    }

    @Override
    protected @NotNull RootsChangeIndexingInfo getGenericChange() {
      return RootsChangeIndexingInfo.TOTAL_REINDEX;
    }

    @Override
    protected @NotNull List<RootsChangeIndexingInfo> initiateChangelist(@NotNull RootsChangeIndexingInfo info) {
      return new SmartList<>(info);
    }
  };

  protected final BatchSession<Boolean, Boolean> myFileTypesChanged = new BatchSession<>(true) {
    @Override
    protected boolean fireRootsChanged(@NotNull Boolean aBoolean) {
      return ProjectRootManagerImpl.this.fireRootsChanged(true, Collections.emptyList());
    }

    @Override
    protected @NotNull Boolean accumulate(@NotNull Boolean current, @NotNull Boolean change) {
      return current || change;
    }

    @Override
    protected @NotNull Boolean getGenericChange() {
      return Boolean.TRUE;
    }

    @Override
    protected @NotNull Boolean initiateChangelist(@NotNull Boolean aBoolean) {
      return aBoolean;
    }
  };
  private final VirtualFilePointerListener myEmptyRootsValidityChangedListener = new VirtualFilePointerListener(){};

  public static ProjectRootManagerImpl getInstanceImpl(Project project) {
    return (ProjectRootManagerImpl)getInstance(project);
  }

  public ProjectRootManagerImpl(@NotNull Project project) {
    myProject = project;
    myRootsCache = getOrderRootsCache(project);
    project.getMessageBus().connect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      @Override
      public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
        String currentName = getProjectSdkName();
        if (previousName.equals(currentName)) {
          // if already had jdk name and that name was the name of the jdk just changed
          myProjectSdkName = jdk.getName();
          myProjectSdkType = jdk.getSdkType().getName();
        }
      }
    });
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
    fireJdkChanged();
  }

  private void fireJdkChanged() {
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

    Application app = ApplicationManager.getApplication();
    if (app != null) {
      Runnable runnable = myStateLoaded ?
                          () -> projectJdkChanged() :
                          // Prevent root changed event during startup to improve startup performance
                          () -> fireJdkChanged();
      app.invokeLater(() -> app.runWriteAction(runnable), app.getNoneModalityState());
    }
    myStateLoaded = true;
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
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    BatchSession<?, ?> batchSession = myRootsChanged;
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
    BatchSession<?, ?> session = fileTypes ? myFileTypesChanged : myRootsChanged;
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

  private boolean fireRootsChanged(boolean fileTypes, @NotNull List<? extends RootsChangeIndexingInfo> indexingInfos) {
    if (myProject.isDisposed()) return false;

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    clearScopesCaches();

    incModificationCount();

    fireRootsChangedEvent(fileTypes, indexingInfos);

    return true;
  }

  @ApiStatus.Internal
  protected void fireRootsChangedEvent(boolean fileTypes, @NotNull List<? extends RootsChangeIndexingInfo> indexingInfos) { }

  @ApiStatus.Internal
  protected OrderRootsCache getOrderRootsCache(@NotNull Project project) {
    return new OrderRootsCache(project);
  }

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

  @Override
  public void markRootsForRefresh() {

  }

  @NotNull
  public VirtualFilePointerListener getRootsValidityChangedListener() {
    return myEmptyRootsValidityChangedListener;
  }
}