// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.configurationStore.StateStorageManagerKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.ModuleStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.projectModel.ProjectModelBundle;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.graph.*;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.intellij.openapi.module.impl.ExternalModuleListStorageKt.getFilteredModuleList;

public abstract class ModuleManagerImpl extends ModuleManagerEx implements Disposable, PersistentStateComponent<Element>, ProjectComponent {
  public static final String COMPONENT_NAME = "ProjectModuleManager";

  public static final String ELEMENT_MODULES = "modules";
  public static final String ELEMENT_MODULE = "module";
  public static final String ATTRIBUTE_FILEURL = "fileurl";
  public static final String ATTRIBUTE_FILEPATH = "filepath";
  public static final String ATTRIBUTE_GROUP = "group";
  public static final String IML_EXTENSION = ".iml";

  private static final Logger LOG = Logger.getInstance(ModuleManagerImpl.class);
  private static final Key<String> DISPOSED_MODULE_NAME = Key.create("DisposedNeverAddedModuleName");
  public static final String MODULE_GROUP_SEPARATOR = "/";

  protected final Project myProject;
  protected final MessageBus myMessageBus;
  private final ProjectRootManagerEx myProjectRootManager;
  protected volatile ModuleModelImpl myModuleModel = new ModuleModelImpl(this);

  private Set<ModulePath> myModulePathsToLoad;
  private final Set<ModulePath> myFailedModulePaths = new THashSet<>();
  private final Map<String, UnloadedModuleDescriptionImpl> myUnloadedModules = new LinkedHashMap<>();
  private boolean myModulesLoaded;

  public static ModuleManagerImpl getInstanceImpl(Project project) {
    return (ModuleManagerImpl)getInstance(project);
  }

  public ModuleManagerImpl(@NotNull Project project) {
    myProject = project;
    myMessageBus = project.getMessageBus();
    myProjectRootManager = ProjectRootManagerEx.getInstanceEx(project);
  }

  @Override
  public void projectOpened() {
    fireModulesAdded();

    for (Module module : myModuleModel.getModules()) {
      ((ModuleEx)module).projectOpened();
    }
  }

  @Override
  public void projectClosed() {
    for (Module module : myModuleModel.getModules()) {
      ((ModuleEx)module).projectClosed();
    }
  }

  protected void cleanCachedStuff() {
    myCachedModuleComparator = null;
    myCachedSortedModules = null;
    myCachedModuleProductionGraph = null;
    myCachedModuleTestGraph = null;
  }

  @Override
  public void dispose() {
    myModuleModel.disposeModel();
    myProjectRootManager.assertListenersAreDisposed();
  }

  @Override
  public Element getState() {
    final Element e = new Element("state");
    writeExternal(e, getFilteredModuleList(myProject, getModules(), false));
    return e;
  }

  private static final class ModuleGroupInterner {
    private final Interner<String> groups = new StringInterner();
    private final Map<String[], String[]> paths = new THashMap<>(new TObjectHashingStrategy<String[]>() {
      @Override
      public int computeHashCode(String[] object) {
        return Arrays.hashCode(object);
      }

      @Override
      public boolean equals(String[] o1, String[] o2) {
        return Arrays.equals(o1, o2);
      }
    });

    private void setModuleGroupPath(@NotNull ModifiableModuleModel model, @NotNull Module module, String @Nullable [] group) {
      String[] cached = group == null ? null : paths.get(group);
      if (cached == null && group != null) {
        cached = new String[group.length];
        for (int i = 0; i < group.length; i++) {
          String g = group[i];
          cached[i] = groups.intern(g);
        }
        paths.put(cached, cached);
      }
      model.setModuleGroupPath(module, cached);
    }
  }

  @Override
  public void loadState(@NotNull Element state) {
    Set<ModulePath> files = getPathsToModuleFiles(state);
    Set<ModulePath> externalModules = myProject.getComponent(ExternalModuleListStorage.class).getExternalModules();
    if (externalModules != null) {
      files.addAll(externalModules);
    }
    loadState(files);
  }

  @Override
  public void noStateLoaded() {
    // if there are only external modules, loadState will be not called
    Set<ModulePath> externalModules = myProject.getComponent(ExternalModuleListStorage.class).getExternalModules();
    if (externalModules != null) {
      loadState(new LinkedHashSet<>(externalModules));
    }
  }

  private void loadState(@NotNull Set<ModulePath> modulePaths) {
    boolean isFirstLoadState = myModulePathsToLoad == null;
    myModulePathsToLoad = modulePaths;
    Set<String> unloadedModuleNames = new HashSet<>(UnloadedModulesListStorage.getInstance(myProject).getUnloadedModuleNames());
    Iterator<ModulePath> iterator = myModulePathsToLoad.iterator();
    List<ModulePath> unloadedModulePaths = new ArrayList<>();
    while (iterator.hasNext()) {
      ModulePath modulePath = iterator.next();
      if (unloadedModuleNames.contains(modulePath.getModuleName())) {
        unloadedModulePaths.add(modulePath);
        iterator.remove();
      }
    }
    List<UnloadedModuleDescriptionImpl> unloaded = new ArrayList<>(UnloadedModuleDescriptionImpl.createFromPaths(unloadedModulePaths, this));
    if (!unloaded.isEmpty()) {
      unloadNewlyAddedModulesIfPossible(myModulePathsToLoad, unloaded);
    }
    myUnloadedModules.clear();
    for (UnloadedModuleDescriptionImpl description : unloaded) {
      myUnloadedModules.put(description.getName(), description);
    }

    if (isFirstLoadState) {
      // someone else must call loadModules in a appropriate time (e.g. on projectComponentsInitialized)
      return;
    }

    final ModifiableModuleModel model = getModifiableModel();
    Module[] existingModules = model.getModules();
    ModuleGroupInterner groupInterner = new ModuleGroupInterner();

    Map<String, ModulePath> modulePathMap = new THashMap<>(myModulePathsToLoad.size());
    for (ModulePath modulePath : myModulePathsToLoad) {
      modulePathMap.put(modulePath.getPath(), modulePath);
    }

    for (Module existingModule : existingModules) {
      ModulePath correspondingPath = modulePathMap.remove(existingModule.getModuleFilePath());
      if (correspondingPath == null) {
        model.disposeModule(existingModule);
      }
      else {
        myModulePathsToLoad.remove(correspondingPath);

        String groupStr = correspondingPath.getGroup();
        String[] group = groupStr == null ? null : groupStr.split(MODULE_GROUP_SEPARATOR);
        if (!Arrays.equals(group, model.getModuleGroupPath(existingModule))) {
          groupInterner.setModuleGroupPath(model, existingModule, group);
        }
      }
    }

    loadModules((ModuleModelImpl)model);
    ApplicationManager.getApplication().runWriteAction(model::commit);
    // clear only if successfully loaded
    myModulePathsToLoad.clear();
  }

  protected void unloadNewlyAddedModulesIfPossible(@NotNull Set<ModulePath> modulesToLoad, @NotNull List<UnloadedModuleDescriptionImpl> modulesToUnload) {
  }

  @NotNull
  // returns mutable linked hash set
  public static Set<ModulePath> getPathsToModuleFiles(@NotNull Element element) {
    Set<ModulePath> paths = new LinkedHashSet<>();
    final Element modules = element.getChild(ELEMENT_MODULES);
    if (modules != null) {
      for (final Element moduleElement : modules.getChildren(ELEMENT_MODULE)) {
        final String fileUrlValue = moduleElement.getAttributeValue(ATTRIBUTE_FILEURL);
        final String filepath;
        if (fileUrlValue == null) {
          // support for older formats
          filepath = moduleElement.getAttributeValue(ATTRIBUTE_FILEPATH);
        }
        else {
          filepath = VirtualFileManager.extractPath(fileUrlValue);
        }
        paths.add(new ModulePath(FileUtilRt.toSystemIndependentName(Objects.requireNonNull(filepath)), moduleElement.getAttributeValue(ATTRIBUTE_GROUP)));
      }
    }
    return paths;
  }

  protected void loadModules(@NotNull ModuleModelImpl moduleModel) {
    myFailedModulePaths.clear();

    if (myModulePathsToLoad == null || myModulePathsToLoad.isEmpty()) {
      myModulesLoaded = true;
      return;
    }

    myFailedModulePaths.addAll(myModulePathsToLoad);

    ProgressIndicator globalIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    ProgressIndicator progressIndicator = myProject.isDefault() || globalIndicator == null ? new EmptyProgressIndicator() : globalIndicator;
    progressIndicator.setText(ProjectModelBundle.message("progress.text.loading.modules"));
    progressIndicator.setText2("");

    List<ModuleLoadingErrorDescription> errors = Collections.synchronizedList(new ArrayList<>());

    boolean isParallel = Registry.is("parallel.modules.loading") && !ApplicationManager.getApplication().isDispatchThread();
    ExecutorService service = isParallel ? AppExecutorUtil.createBoundedApplicationPoolExecutor("ModuleManager Loader", Math.min(2, Runtime.getRuntime().availableProcessors()))
                                         : ConcurrencyUtil.newSameThreadExecutorService();
    List<Pair<Future<Module>, ModulePath>> tasks = new ArrayList<>();
    Set<String> paths = new THashSet<>();
    for (ModulePath modulePath : myModulePathsToLoad) {
      if (progressIndicator.isCanceled()) {
        break;
      }

      String path = modulePath.getPath();
      if (!paths.add(path)) {
        continue;
      }

      tasks.add(new Pair<>(service.submit(() -> {
        progressIndicator.setFraction(progressIndicator.getFraction() + myProgressStep);
        return ProgressManager.getInstance().runProcess(() -> {
          try {
            return myProject.isDisposed() ? null : loadModuleInternal(path, this);
          }
          catch (IOException e) {
            reportError(errors, modulePath, e);
          }
          catch (ProcessCanceledException ignore) {
          }
          catch (Exception e) {
            LOG.error(e);
          }
          return null;
        }, ProgressWrapper.wrap(progressIndicator));
      }), modulePath));
    }

    ModuleGroupInterner groupInterner = new ModuleGroupInterner();
    List<Module> modulesWithUnknownTypes = new SmartList<>();
    for (Pair<Future<Module>, ModulePath> task : tasks) {
      if (progressIndicator.isCanceled()) {
        break;
      }

      try {
        Module module = task.first.get();
        if (module == null) {
          continue;
        }

        moduleModel.addModule(module);

        if (isUnknownModuleType(module)) {
          modulesWithUnknownTypes.add(module);
        }
        ModulePath modulePath = task.second;
        String groupPathString = modulePath.getGroup();
        if (groupPathString != null) {
          // model should be updated too
          groupInterner.setModuleGroupPath(moduleModel, module, groupPathString.split(MODULE_GROUP_SEPARATOR));
        }
        myFailedModulePaths.remove(modulePath);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    service.shutdown();

    progressIndicator.checkCanceled();

    myModulesLoaded = true;

    Application app = ApplicationManager.getApplication();
    if (app.isInternal() || app.isEAP() || ApplicationInfo.getInstance().getBuild().isSnapshot()) {
      Map<String, Module> track = new THashMap<>();
      for (Module module : moduleModel.getModules()) {
        for (String url : ModuleRootManager.getInstance(module).getContentRootUrls()) {
          Module oldModule = track.put(url, module);
          if (oldModule != null) {
            LOG.warn("Module '" + module.getName() + "' and module '" + oldModule.getName() + "' have the same content root: " + url);
          }
        }
      }
    }

    onModuleLoadErrors(moduleModel, errors);

    showUnknownModuleTypeNotification(modulesWithUnknownTypes);
  }

  private static @NotNull Module loadModuleInternal(@NotNull String filePath, @NotNull ModuleManagerImpl manager) throws IOException {
    // we cannot call refreshAndFindFileByPath during module init under read action because it is forbidden
    VirtualFile virtualFile = StandardFileSystems.local().refreshAndFindFileByPath(filePath);
    if (virtualFile != null) {
      // otherwise virtualFile.contentsToByteArray() will query expensive FileTypeManager.getInstance()).getByFile()
      virtualFile.setCharset(StandardCharsets.UTF_8, null, false);
    }
    return ReadAction.compute(() -> {
      ModuleEx module = manager.createAndLoadModule(filePath);
      initModule(module, () -> ((ModuleStore)module.getService(IComponentStore.class)).setPath(filePath, virtualFile, false));
      return module;
    });
  }

  private static void initModule(@NotNull ModuleEx module, @NotNull Runnable beforeComponentCreation) {
    try {
      module.init(beforeComponentCreation);
    }
    catch (Throwable e) {
      disposeModuleLater(module);
      throw e;
    }
  }

  private void reportError(@NotNull List<? super ModuleLoadingErrorDescription> errors, @NotNull ModulePath modulePath, @NotNull Exception e) {
    errors.add(new ModuleLoadingErrorDescription(ProjectModelBundle.message("module.cannot.load.error", modulePath.getPath(), e.getMessage()), modulePath, this));
  }

  public int getModulePathsCount() { return myModulePathsToLoad == null ? 0 : myModulePathsToLoad.size(); }

  public boolean areModulesLoaded() {
    return myModulesLoaded;
  }

  private double myProgressStep;

  public void setProgressStep(double step) { myProgressStep = step; }

  protected boolean isUnknownModuleType(@NotNull Module module) {
    return false;
  }

  protected void showUnknownModuleTypeNotification(@NotNull List<? extends Module> types) {
  }

  protected void fireModuleAdded(@NotNull Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).moduleAdded(myProject, module);
  }

  protected void fireModuleRemoved(@NotNull Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).moduleRemoved(myProject, module);
  }

  protected void fireBeforeModuleRemoved(@NotNull Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).beforeModuleRemoved(myProject, module);
  }

  protected void fireModulesRenamed(@NotNull List<Module> modules, @NotNull final Map<Module, String> oldNames) {
    if (modules.isEmpty()) {
      return;
    }

    try {
      for (Module module : getModules()) {
        ModuleRootManagerImpl moduleRootManager = ObjectUtils.tryCast(ModuleRootManager.getInstance(module), ModuleRootManagerImpl.class);
        if (moduleRootManager != null) {
          // platform in any case will check that iml is actually modified
          moduleRootManager.stateChanged();
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }

    myMessageBus.syncPublisher(ProjectTopics.MODULES).modulesRenamed(myProject, modules, oldNames::get);
  }

  private void onModuleLoadErrors(@NotNull ModuleModelImpl moduleModel, @NotNull List<? extends ModuleLoadingErrorDescription> errors) {
    if (errors.isEmpty()) return;

    moduleModel.myModulesCache = null;
    for (ModuleLoadingErrorDescription error : errors) {
      final Module module = moduleModel.getModuleByFilePath(error.getModulePath().getPath());
      if (module != null) {
        moduleModel.myModules.remove(module.getName());
        disposeModuleLater(module);
      }
    }

    fireModuleLoadErrors(errors);
  }

  private static void disposeModuleLater(@NotNull Module module) {
    ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(module), module.getDisposed());
  }

  // overridden in Upsource
  protected void fireModuleLoadErrors(@NotNull List<? extends ModuleLoadingErrorDescription> errors) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(errors.get(0).getDescription());
    }

    ProjectLoadingErrorsNotifier.getInstance(myProject).registerErrors(errors);
  }

  void removeFailedModulePath(@NotNull ModulePath modulePath) {
    myFailedModulePaths.remove(modulePath);
    incModificationCount();
  }

  @Override
  @NotNull
  public ModifiableModuleModel getModifiableModel() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return new ModuleModelImpl(myModuleModel);
  }

  private static class ModuleSaveItem extends SaveItem {
    private final Module myModule;
    private final ModuleManager myModuleManager;

    ModuleSaveItem(@NotNull Module module, @NotNull ModuleManager moduleManager) {
      myModule = module;
      myModuleManager = moduleManager;
    }

    @Override
    @NotNull
    protected String getModuleName() {
      return myModule.getName();
    }

    @Override
    protected String getGroupPathString() {
      String[] groupPath = myModuleManager.getModuleGroupPath(myModule);
      return groupPath != null ? StringUtil.join(groupPath, MODULE_GROUP_SEPARATOR) : null;
    }

    @Override
    @NotNull
    protected String getModuleFilePath() {
      return myModule.getModuleFilePath();
    }
  }

  public void writeExternal(@NotNull Element element, @NotNull List<? extends Module> collection) {
    writeExternal(element, collection, this);
  }

  @ApiStatus.Internal
  public static void writeExternal(@NotNull Element element,
                                   @NotNull List<? extends Module> collection,
                                   ModuleManagerEx moduleManager) {
    Collection<ModulePath> failedModulePaths = moduleManager.getFailedModulePaths();
    Collection<UnloadedModuleDescription> unloadedModuleDescriptions = moduleManager.getUnloadedModuleDescriptions();

    List<SaveItem> sorted = new ArrayList<>(collection.size() + failedModulePaths.size() + unloadedModuleDescriptions.size());
    for (Module module : collection) {
      sorted.add(new ModuleSaveItem(module, moduleManager));
    }
    for (ModulePath modulePath : failedModulePaths) {
      sorted.add(new ModulePathSaveItem(modulePath));
    }
    for (UnloadedModuleDescription description : unloadedModuleDescriptions) {
      sorted.add(new ModulePathSaveItem(((UnloadedModuleDescriptionImpl)description).getModulePath()));
    }

    if (!sorted.isEmpty()) {
      sorted.sort(Comparator.comparing(SaveItem::getModuleName));

      Element modules = new Element(ELEMENT_MODULES);
      for (SaveItem saveItem : sorted) {
        saveItem.writeExternal(modules);
      }
      element.addContent(modules);
    }
  }

  @Override
  @NotNull
  public Module newModule(@NotNull String filePath, @NotNull final String moduleTypeId) {
    incModificationCount();
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.newModule(filePath, moduleTypeId);
    modifiableModel.commit();
    return module;
  }

  @Override
  @NotNull
  public Module newNonPersistentModule(@NotNull String moduleName, @NotNull String id) {
    incModificationCount();
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.newNonPersistentModule(moduleName, id);
    modifiableModel.commit();
    return module;
  }

  @Override
  @NotNull
  public Module loadModule(@NotNull String filePath) throws IOException, ModuleWithNameAlreadyExists {
    incModificationCount();
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.loadModule(filePath);
    modifiableModel.commit();
    return module;
  }

  @Override
  public void disposeModule(@NotNull final Module module) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableModuleModel modifiableModel = getModifiableModel();
      modifiableModel.disposeModule(module);
      modifiableModel.commit();
    });
  }

  @Override
  public Module @NotNull [] getModules() {
    ModuleModelImpl model = myModuleModel;
    if (model.myIsWritable) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    return model.getModules();
  }

  private volatile Module[] myCachedSortedModules;
  private volatile Graph<Module> myCachedModuleProductionGraph;
  private volatile Graph<Module> myCachedModuleTestGraph;

  @Override
  public Module @NotNull [] getSortedModules() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    deliverPendingEvents();
    Module[] sortedModules = myCachedSortedModules;
    if (sortedModules == null) {
      myCachedSortedModules = sortedModules = myModuleModel.getSortedModules();
    }
    return sortedModules;
  }

  @Override
  public Module findModuleByName(@NotNull String name) {
    return myModuleModel.findModuleByName(name);
  }

  private volatile Comparator<Module> myCachedModuleComparator;

  @Override
  @NotNull
  public Comparator<Module> moduleDependencyComparator() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    deliverPendingEvents();
    Comparator<Module> cachedModuleComparator = myCachedModuleComparator;
    if (cachedModuleComparator == null) {
      myCachedModuleComparator = cachedModuleComparator = myModuleModel.moduleDependencyComparator();
    }
    return cachedModuleComparator;
  }

  protected void deliverPendingEvents() {
  }

  @Override
  @NotNull
  public Graph<Module> moduleGraph() {
    return moduleGraph(true);
  }

  @NotNull
  @Override
  public Graph<Module> moduleGraph(boolean includeTests) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Graph<Module> graph = includeTests ? myCachedModuleTestGraph : myCachedModuleProductionGraph;
    if (graph != null) return graph;

    graph = myModuleModel.moduleGraph(includeTests);
    if (includeTests) {
      myCachedModuleTestGraph = graph;
    }
    else {
      myCachedModuleProductionGraph = graph;
    }

    return graph;
  }

  @Override
  @NotNull
  public List<Module> getModuleDependentModules(@NotNull Module module) {
    List<Module> result = new SmartList<>();
    for (Module aModule : getModules()) {
      if (isModuleDependsOn(aModule, module)) {
        result.add(aModule);
      }
    }
    return result;
  }

  @Override
  public boolean isModuleDependent(@NotNull Module module, @NotNull Module onModule) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return isModuleDependsOn(module, onModule);
  }

  private static boolean isModuleDependsOn(@NotNull Module module, @NotNull Module onModule) {
    return ModuleRootManager.getInstance(module).isDependsOn(onModule);
  }

  protected void fireModulesAdded() {
    for (Module module : myModuleModel.getModules()) {
      fireModuleAddedInWriteAction((ModuleEx)module);
    }
  }

  protected void fireModuleAddedInWriteAction(@NotNull ModuleEx module) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!module.isLoaded()) {
        module.moduleAdded();
        fireModuleAdded(module);
      }
    });
  }

  public static void commitModelWithRunnable(@NotNull ModifiableModuleModel model, @NotNull Runnable runnable) {
    ((ModuleModelImpl)model).commitWithRunnable(runnable);
  }

  @NotNull
  protected abstract ModuleEx createModule(@NotNull String filePath);

  protected ModuleEx createNonPersistentModule(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  protected abstract ModuleEx createAndLoadModule(@NotNull String filePath) throws IOException;

  static class ModuleModelImpl implements ModifiableModuleModel {
    final Map<String, Module> myModules = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile Module[] myModulesCache;

    private final List<Module> myModulesToDispose = new ArrayList<>();
    private final Map<Module, String> myModuleToNewName = new HashMap<>();
    private final Map<String, Module> myNewNameToModule = new HashMap<>();
    private boolean myIsWritable;
    private Map<Module, String[]> myModuleGroupPath;

    private final ModuleManagerImpl myManager;

    private ModuleModelImpl(@NotNull ModuleManagerImpl manager) {
      myManager = manager;
      myIsWritable = false;
    }

    @SuppressWarnings("CopyConstructorMissesField")
    private ModuleModelImpl(@NotNull ModuleModelImpl that) {
      myManager = that.myManager;
      myModules.putAll(that.myModules);
      final Map<Module, String[]> groupPath = that.myModuleGroupPath;
      if (groupPath != null){
        myModuleGroupPath = new THashMap<>();
        myModuleGroupPath.putAll(that.myModuleGroupPath);
      }
      myIsWritable = true;
    }

    private void assertWritable() {
      LOG.assertTrue(myIsWritable, "Attempt to modify committed ModifiableModuleModel");
    }

    @Override
    public Module @NotNull [] getModules() {
      Module[] cache = myModulesCache;
      if (cache == null) {
        Collection<Module> modules = myModules.values();
        myModulesCache = cache = modules.toArray(Module.EMPTY_ARRAY);
      }
      return cache;
    }

    private Module @NotNull [] getSortedModules() {
      Module[] allModules = getModules().clone();
      Arrays.sort(allModules, moduleDependencyComparator());
      return allModules;
    }

    @Override
    public void renameModule(@NotNull Module module, @NotNull String newName) throws ModuleWithNameAlreadyExists {
      final Module oldModule = getModuleByNewName(newName);
      myNewNameToModule.remove(myModuleToNewName.get(module));
      if(module.getName().equals(newName)){ // if renaming to itself, forget it altogether
        myModuleToNewName.remove(module);
        myNewNameToModule.remove(newName);
      } else {
        myModuleToNewName.put(module, newName);
        myNewNameToModule.put(newName, module);
      }

      if (oldModule != null) {
        throw new ModuleWithNameAlreadyExists(ProjectModelBundle.message("module.already.exists.error", newName), newName);
      }
    }

    @Override
    public Module getModuleToBeRenamed(@NotNull String newName) {
      return myNewNameToModule.get(newName);
    }

    private Module getModuleByNewName(@NotNull String newName) {
      final Module moduleToBeRenamed = getModuleToBeRenamed(newName);
      if (moduleToBeRenamed != null) {
        return moduleToBeRenamed;
      }
      final Module moduleWithOldName = findModuleByName(newName);
      return myModuleToNewName.get(moduleWithOldName) == null ? moduleWithOldName : null;
    }

    @Override
    public String getNewName(@NotNull Module module) {
      return myModuleToNewName.get(module);
    }

    @NotNull
    @Override
    public String getActualName(@NotNull Module module) {
      return ObjectUtils.notNull(getNewName(module), module.getName());
    }

    @Override
    @NotNull
    public Module newModule(@NotNull String filePath, @NotNull final String moduleTypeId) {
      return newModule(filePath, moduleTypeId, null);
    }

    @Override
    @NotNull
    public Module newNonPersistentModule(@NotNull String moduleName, @NotNull final String moduleTypeId) {
      assertWritable();

      ModuleEx module = myManager.createNonPersistentModule(moduleName);
      initModule(module, () -> {
        module.setModuleType(moduleTypeId);
      });
      addModule(module);
      return module;
    }

    @Override
    @NotNull
    public Module newModule(@NotNull String filePath, @NotNull final String moduleTypeId, @Nullable final Map<String, String> options) {
      assertWritable();
      filePath = FileUtil.toSystemIndependentName(resolveShortWindowsName(filePath));

      ModuleEx module = getModuleByFilePath(filePath);
      if (module != null) {
        return module;
      }

      module = myManager.createModule(filePath);
      final ModuleEx newModule = module;
      String finalFilePath = filePath;
      initModule(module, () -> {
        ((ModuleStore)newModule.getService(IComponentStore.class)).setPath(finalFilePath, null, true);

        newModule.setModuleType(moduleTypeId);
        if (options != null) {
          for (Map.Entry<String, String> option : options.entrySet()) {
            //noinspection deprecation
            newModule.setOption(option.getKey(), option.getValue());
          }
        }
      });
      addModule(module);
      return module;
    }

    @NotNull
    private static String resolveShortWindowsName(@NotNull String filePath) {
      try {
        return FileUtil.resolveShortWindowsName(filePath);
      }
      catch (IOException ignored) {
        return filePath;
      }
    }

    @Nullable
    private ModuleEx getModuleByFilePath(@NotNull @SystemIndependent String filePath) {
      for (Module module : getModules()) {
        if (SystemInfo.isFileSystemCaseSensitive ? module.getModuleFilePath().equals(filePath) : module.getModuleFilePath().equalsIgnoreCase(filePath)) {
          return (ModuleEx)module;
        }
      }
      return null;
    }

    @Override
    public @NotNull Module loadModule(@NotNull @SystemIndependent String filePath) throws IOException {
      assertWritable();
      String resolvedPath = FileUtilRt.toSystemIndependentName(resolveShortWindowsName(filePath));
      try {
        Module module = getModuleByFilePath(resolvedPath);
        if (module == null) {
          module = loadModuleInternal(resolvedPath, myManager);
          addModule(module);
        }
        return module;
      }
      catch (FileNotFoundException e) {
        throw e;
      }
      catch (IOException e) {
        throw new IOException(ProjectModelBundle.message("module.corrupted.file.error", FileUtilRt.toSystemDependentName(resolvedPath), e.getMessage()), e);
      }
    }

    private void addModule(@NotNull Module module) {
      myModulesCache = null;
      myModules.put(module.getName(), module);
    }

    @Override
    public void disposeModule(@NotNull Module module) {
      assertWritable();
      myModulesCache = null;
      if (myModules.remove(module.getName()) != null) {
        myModulesToDispose.add(module);
      }
      if (myModuleGroupPath != null){
        myModuleGroupPath.remove(module);
      }
    }

    @Override
    public Module findModuleByName(@NotNull String name) {
      Module module = myModules.get(name);
      if (module != null && !module.isDisposed()) {
        return module;
      }
      return null;
    }

    @NotNull
    private Comparator<Module> moduleDependencyComparator() {
      DFSTBuilder<Module> builder = new DFSTBuilder<>(moduleGraph(true));
      return builder.comparator();
    }

    @NotNull
    private Graph<Module> moduleGraph(final boolean includeTests) {
      return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<Module>() {
        @NotNull
        @Override
        public Collection<Module> getNodes() {
          return myModules.values();
        }

        @NotNull
        @Override
        public Iterator<Module> getIn(Module m) {
          Module[] dependentModules = ModuleRootManager.getInstance(m).getDependencies(includeTests);
          return Arrays.asList(dependentModules).iterator();
        }
      }));
    }

    @Override
    public void commit() {
      ModifiableModelCommitter.multiCommit(Collections.emptyList(), this);
    }

    private void commitWithRunnable(@NotNull Runnable runnable) {
      myManager.commitModel(this, runnable);
      clearRenamingStuff();
    }

    private void clearRenamingStuff() {
      myModuleToNewName.clear();
      myNewNameToModule.clear();
    }

    @Override
    public void dispose() {
      assertWritable();
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      final Set<Module> existingModules = ContainerUtil.set(myManager.myModuleModel.getModules());
      for (Module thisModule : getModules()) {
        if (!existingModules.contains(thisModule)) {
          Disposer.dispose(thisModule);
        }
      }
      for (Module moduleToDispose : myModulesToDispose) {
        if (!existingModules.contains(moduleToDispose)) {
          Disposer.dispose(moduleToDispose);
        }
      }
      clearRenamingStuff();
    }

    @Override
    public boolean isChanged() {
      if (!myIsWritable) {
        return false;
      }
      ModuleModelImpl model = myManager.myModuleModel;
      return !myModules.equals(model.myModules)
             || !Comparing.equal(model.myModuleGroupPath, myModuleGroupPath)
             || !myModuleToNewName.isEmpty();
    }

    private void disposeModel() {
      Module[] modules = getModules();
      myModulesCache = null;
      // clear module list before disposing to avoid getModules() returning already disposed modules
      myModules.clear();
      for (Module module : modules) {
        Disposer.dispose(module);
      }
      myModuleGroupPath = null;
    }

    @Override
    public String[] getModuleGroupPath(@NotNull Module module) {
      return myModuleGroupPath == null ? null : myModuleGroupPath.get(module);
    }

    @Override
    public boolean hasModuleGroups() {
      return myModuleGroupPath != null && !myModuleGroupPath.isEmpty();
    }

    @Override
    public void setModuleGroupPath(@NotNull Module module, String @Nullable("null means remove") [] groupPath) {
      if (myModuleGroupPath == null) {
        myModuleGroupPath = new THashMap<>();
      }
      if (groupPath == null) {
        myModuleGroupPath.remove(module);
      }
      else {
        myModuleGroupPath.put(module, groupPath);
      }
    }

    @NotNull
    @Override
    public Project getProject() {
      return myManager.myProject;
    }
  }

  private void commitModel(@NotNull ModuleModelImpl moduleModel, @NotNull Runnable runnable) {
    myModuleModel.myModulesCache = null;
    incModificationCount();
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final Collection<Module> oldModules = Arrays.asList(myModuleModel.getModules());
    final Collection<Module> newModules = Arrays.asList(moduleModel.getModules());

    final Collection<Module> addedModules;
    final Collection<Module> removedModules;
    if (oldModules.isEmpty()) {
      addedModules = newModules;
      removedModules = Collections.emptyList();
    }
    else {
      addedModules = new THashSet<>(newModules);
      addedModules.removeAll(oldModules);

      removedModules = new THashSet<>(oldModules);
      removedModules.removeAll(newModules);
    }

    myProjectRootManager.makeRootsChange(() -> {
      for (Module removedModule : removedModules) {
        fireBeforeModuleRemoved(removedModule);
        cleanCachedStuff();
      }

      if (!moduleModel.myModulesToDispose.isEmpty()) {
        List<Module> neverAddedModules = new ArrayList<>(moduleModel.myModulesToDispose);
        neverAddedModules.removeAll(oldModules);
        for (final Module neverAddedModule : neverAddedModules) {
          neverAddedModule.putUserData(DISPOSED_MODULE_NAME, neverAddedModule.getName());
          Disposer.dispose(neverAddedModule);
        }
      }

      runnable.run();

      final Map<Module, String> modulesToNewNamesMap = moduleModel.myModuleToNewName;
      final Set<Module> modulesToBeRenamed = modulesToNewNamesMap.keySet();
      modulesToBeRenamed.removeAll(moduleModel.myModulesToDispose);

      List<Module> modules = new ArrayList<>();
      Map<Module, String> oldNames = new HashMap<>();
      for (final Module module : modulesToBeRenamed) {
        oldNames.put(module, module.getName());
        moduleModel.myModules.remove(module.getName());
        modules.add(module);
        ((ModuleEx)module).rename(modulesToNewNamesMap.get(module), true);
        moduleModel.myModules.put(module.getName(), module);
        myUnloadedModules.remove(module.getName());
      }

      moduleModel.myIsWritable = false;
      myModuleModel = moduleModel;

      for (Module module : removedModules) {
        fireModuleRemoved(module);
        cleanCachedStuff();
        Disposer.dispose(module);
        cleanCachedStuff();
      }

      for (Module addedModule : addedModules) {
        myUnloadedModules.remove(addedModule.getName());
        ((ModuleEx)addedModule).moduleAdded();
        cleanCachedStuff();
        fireModuleAdded(addedModule);
        cleanCachedStuff();
      }
      cleanCachedStuff();
      fireModulesRenamed(modules, oldNames);
      cleanCachedStuff();
      UnloadedModulesListStorage unloadedModulesListStorage = UnloadedModulesListStorage.getInstance(myProject);
      setUnloadedModuleNames(ContainerUtil.filter(unloadedModulesListStorage.getUnloadedModuleNames(), myUnloadedModules::containsKey));
    }, false, true);
  }

  public void fireModuleRenamedByVfsEvent(@NotNull final Module module, @NotNull final String oldName) {
    Module moduleInMap = myModuleModel.myModules.remove(oldName);
    LOG.assertTrue(moduleInMap == null || moduleInMap == module);
    myModuleModel.myModules.put(module.getName(), module);
    incModificationCount();

    myProjectRootManager.makeRootsChange(
      () -> fireModulesRenamed(Collections.singletonList(module), Collections.singletonMap(module, oldName)), false, true);
  }

  @Override
  public String[] getModuleGroupPath(@NotNull Module module) {
    return myModuleModel.getModuleGroupPath(module);
  }

  @Override
  public boolean hasModuleGroups() {
    return myModuleModel.hasModuleGroups();
  }

  @NotNull
  @Override
  public Collection<ModuleDescription> getAllModuleDescriptions() {
    Module[] modules = getModules();
    List<ModuleDescription> descriptions = new ArrayList<>(modules.length + myUnloadedModules.size());
    for (Module module : modules) {
      descriptions.add(new LoadedModuleDescriptionImpl(module));
    }
    descriptions.addAll(myUnloadedModules.values());
    return descriptions;
  }

  @NotNull
  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModuleDescriptions() {
    return Collections.unmodifiableCollection(myUnloadedModules.values());
  }

  @Override
  public Collection<ModulePath> getFailedModulePaths() {
    return Collections.unmodifiableSet(myFailedModulePaths);
  }

  @Nullable
  @Override
  public UnloadedModuleDescription getUnloadedModuleDescription(@NotNull String moduleName) {
    return myUnloadedModules.get(moduleName);
  }

  @NotNull
  @Override
  public ModuleGrouper getModuleGrouper(@Nullable ModifiableModuleModel model) {
    return ModuleGroupersKt.createGrouper(myProject, model);
  }

  @Override
  public void setUnloadedModules(@NotNull List<String> unloadedModuleNames) {
    if (myUnloadedModules.keySet().equals(new HashSet<>(unloadedModuleNames))) {
      //optimization
      return;
    }

    setUnloadedModuleNames(unloadedModuleNames);

    final ModifiableModuleModel model = getModifiableModel();
    Map<String, UnloadedModuleDescriptionImpl> toLoad = new LinkedHashMap<>(myUnloadedModules);
    myUnloadedModules.clear();
    for (String name : unloadedModuleNames) {
      if (toLoad.containsKey(name)) {
        myUnloadedModules.put(name, toLoad.remove(name));
      }
      else {
        Module module = findModuleByName(name);
        if (module != null) {
          LoadedModuleDescriptionImpl description = new LoadedModuleDescriptionImpl(module);
          ModuleSaveItem saveItem = new ModuleSaveItem(module, this);
          ModulePath modulePath = new ModulePath(saveItem.getModuleFilePath(), saveItem.getGroupPathString());
          VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
          List<VirtualFilePointer> contentRoots = ContainerUtil.map(ModuleRootManager.getInstance(module).getContentRootUrls(), url -> pointerManager.create(url, this, null));
          UnloadedModuleDescriptionImpl unloadedModuleDescription = new UnloadedModuleDescriptionImpl(modulePath, description.getDependencyModuleNames(), contentRoots);
          // we need to save module configuration before unloading, otherwise its settings will be lost
          StateStorageManagerKt.saveComponentManager(module);
          model.disposeModule(module);
          myUnloadedModules.put(name, unloadedModuleDescription);
        }
      }
    }
    List<ModulePath> oldFailedPaths = new ArrayList<>(myFailedModulePaths);
    myModulePathsToLoad = toLoad.values().stream().map(
      UnloadedModuleDescriptionImpl::getModulePath).collect(Collectors.toCollection(LinkedHashSet::new));
    loadModules((ModuleModelImpl)model);
    ApplicationManager.getApplication().runWriteAction(model::commit);
    myFailedModulePaths.addAll(oldFailedPaths);
    myModulePathsToLoad.clear();
  }

  @Override
  public void removeUnloadedModules(@NotNull Collection<? extends UnloadedModuleDescription> unloadedModules) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    for (UnloadedModuleDescription module : unloadedModules) {
      myUnloadedModules.remove(module.getName());
    }
    setUnloadedModuleNames(new ArrayList<>(myUnloadedModules.keySet()));
  }

  protected void setUnloadedModuleNames(@NotNull List<String> unloadedModuleNames) {
    UnloadedModulesListStorage.getInstance(myProject).setUnloadedModuleNames(unloadedModuleNames);
  }

  public void setModuleGroupPath(@NotNull Module module, String @Nullable [] groupPath) {
    myModuleModel.setModuleGroupPath(module, groupPath);
  }
}

