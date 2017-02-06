/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.graph.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author max
 */
public abstract class ModuleManagerImpl extends ModuleManager implements Disposable, PersistentStateComponent<Element> {
  public static final String COMPONENT_NAME = "ProjectModuleManager";

  public static final String ELEMENT_MODULES = "modules";
  public static final String ELEMENT_MODULE = "module";
  public static final String ATTRIBUTE_FILEURL = "fileurl";
  public static final String ATTRIBUTE_FILEPATH = "filepath";
  public static final String ATTRIBUTE_GROUP = "group";
  public static final String IML_EXTENSION = ".iml";

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleManagerImpl");
  private static final Key<String> DISPOSED_MODULE_NAME = Key.create("DisposedNeverAddedModuleName");
  private static final String MODULE_GROUP_SEPARATOR = "/";

  protected final Project myProject;
  protected final MessageBus myMessageBus;
  protected final MessageBusConnection myMessageBusConnection;
  protected volatile ModuleModelImpl myModuleModel = new ModuleModelImpl(this);

  private LinkedHashSet<ModulePath> myModulePathsToLoad;
  private final Set<ModulePath> myFailedModulePaths = new THashSet<>();

  public static ModuleManagerImpl getInstanceImpl(Project project) {
    return (ModuleManagerImpl)getInstance(project);
  }

  public ModuleManagerImpl(@NotNull Project project) {
    myProject = project;
    myMessageBus = project.getMessageBus();

    myMessageBusConnection = myMessageBus.connect();
    myMessageBusConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        if (project == myProject) {
          fireModulesAdded();

          for (Module module : myModuleModel.getModules()) {
            ((ModuleEx)module).projectOpened();
          }
        }
      }

      @Override
      public void projectClosed(Project project) {
        if (project == myProject) {
          for (Module module : myModuleModel.getModules()) {
            ((ModuleEx)module).projectClosed();
          }
        }
      }
    });
  }

  protected void cleanCachedStuff() {
    myCachedModuleComparator = null;
    myCachedSortedModules = null;
  }

  @Override
  public void dispose() {
    myModuleModel.disposeModel();
  }

  @Override
  public Element getState() {
    final Element e = new Element("state");
    writeExternal(e);
    return e;
  }

  private static class ModuleGroupInterner {
    private final StringInterner groups = new StringInterner();
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

    private void setModuleGroupPath(@NotNull ModifiableModuleModel model, @NotNull Module module, @Nullable String[] group) {
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
  public void loadState(Element state) {
    boolean isFirstLoadState = myModulePathsToLoad == null;
    myModulePathsToLoad = getPathsToModuleFiles(state);
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
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
    // clear only if successfully loaded
    myModulePathsToLoad.clear();
  }

  @NotNull
  // returns mutable linked hash set
  public static LinkedHashSet<ModulePath> getPathsToModuleFiles(@NotNull Element element) {
    final LinkedHashSet<ModulePath> paths = new LinkedHashSet<>();
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
        paths.add(new ModulePath(filepath.replace('/', File.separatorChar), moduleElement.getAttributeValue(ATTRIBUTE_GROUP)));
      }
    }
    return paths;
  }

  protected void loadModules(@NotNull ModuleModelImpl moduleModel) {
    myFailedModulePaths.clear();

    if (myModulePathsToLoad == null || myModulePathsToLoad.isEmpty()) {
      return;
    }

    myFailedModulePaths.addAll(myModulePathsToLoad);

    ProgressIndicator globalIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    ProgressIndicator progressIndicator = myProject.isDefault() || globalIndicator == null ? new EmptyProgressIndicator() : globalIndicator;
    progressIndicator.setText("Loading modules...");
    progressIndicator.setText2("");

    List<Module> modulesWithUnknownTypes = new SmartList<>();
    List<ModuleLoadingErrorDescription> errors = Collections.synchronizedList(new ArrayList<>());
    ModuleGroupInterner groupInterner = new ModuleGroupInterner();

    ExecutorService service = AppExecutorUtil.createBoundedApplicationPoolExecutor("modules loader", JobSchedulerImpl.CORES_COUNT);
    List<Pair<Future<Module>, ModulePath>> tasks = new ArrayList<>();
    Set<String> paths = new THashSet<>();
    boolean parallel = Registry.is("parallel.modules.loading");
    for (ModulePath modulePath : myModulePathsToLoad) {
      if (progressIndicator.isCanceled()) {
        break;
      }
      try {
        String path = modulePath.getPath();
        if (!paths.add(path)) continue;
        if (!parallel) {
          tasks.add(Pair.create(null, modulePath));
          continue;
        }
        ThrowableComputable<Module, IOException> computable = moduleModel.loadModuleInternal(path);
        Future<Module> future = service.submit(() -> {
          progressIndicator.setFraction(progressIndicator.getFraction() + myProgressStep);
          try {
            return computable.compute();
          }
          catch (IOException e) {
            reportError(errors, modulePath, e);
          }
          catch (Exception e) {
            LOG.error(e);
          }
          return null;
        });
        tasks.add(Pair.create(future, modulePath));
      }
      catch (IOException e) {
        reportError(errors, modulePath, e);
      }
    }

    for (Pair<Future<Module>, ModulePath> task : tasks) {
      if (progressIndicator.isCanceled()) {
        break;
      }
      try {
        Module module;
        if (parallel) {
          module = task.first.get();
        }
        else {
          module = moduleModel.loadModuleInternal(task.second.getPath()).compute();
          progressIndicator.setFraction(progressIndicator.getFraction() + myProgressStep);
        }
        if (module == null) continue;
        if (isUnknownModuleType(module)) {
          modulesWithUnknownTypes.add(module);
        }
        ModulePath modulePath = task.second;
        final String groupPathString = modulePath.getGroup();
        if (groupPathString != null) {
          // model should be updated too
          groupInterner.setModuleGroupPath(moduleModel, module, groupPathString.split(MODULE_GROUP_SEPARATOR));
        }
        myFailedModulePaths.remove(modulePath);
      }
      catch (IOException e) {
        reportError(errors, task.second, e);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    service.shutdown();

    progressIndicator.checkCanceled();

    Application app = ApplicationManager.getApplication();
    if (app.isInternal() || app.isEAP() || ApplicationInfo.getInstance().getBuild().isSnapshot()) {
      Map<String, Module> track = new THashMap<>();
      for (Module module : moduleModel.getModules()) {
        for (String url : ModuleRootManager.getInstance(module).getContentRootUrls()) {
          Module oldModule = track.put(url, module);
          if (oldModule != null) {
            //Map<String, VirtualFilePointer> track1 = ContentEntryImpl.track;
            //VirtualFilePointer pointer = track1.get(url);
            LOG.error("duplicated content url: " + url);
          }
        }
      }
    }

    onModuleLoadErrors(moduleModel, errors);

    showUnknownModuleTypeNotification(modulesWithUnknownTypes);
  }

  private void reportError(List<ModuleLoadingErrorDescription> errors, ModulePath modulePath, Exception e) {
    errors.add(new ModuleLoadingErrorDescription(ProjectBundle.message("module.cannot.load.error", modulePath.getPath(), e.getMessage()), modulePath, this));
  }

  public int getModulePathsCount() { return myModulePathsToLoad == null ? 0 : myModulePathsToLoad.size(); }

  private double myProgressStep;

  public void setProgressStep(double step) { myProgressStep = step; }

  protected boolean isUnknownModuleType(@NotNull Module module) {
    return false;
  }

  protected void showUnknownModuleTypeNotification(@NotNull List<Module> types) {
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
    if (!modules.isEmpty()) {
      myMessageBus.syncPublisher(ProjectTopics.MODULES).modulesRenamed(myProject, modules, module -> oldNames.get(module));
    }
  }

  private void onModuleLoadErrors(@NotNull ModuleModelImpl moduleModel, @NotNull List<ModuleLoadingErrorDescription> errors) {
    if (errors.isEmpty()) return;

    moduleModel.myModulesCache = null;
    for (ModuleLoadingErrorDescription error : errors) {
      final Module module = moduleModel.getModuleByFilePath(error.getModulePath().getPath());
      if (module != null) {
        moduleModel.myModules.remove(module.getName());
        ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(module), module.getDisposed());
      }
    }

    fireModuleLoadErrors(errors);
  }

  // overridden in Upsource
  protected void fireModuleLoadErrors(@NotNull List<ModuleLoadingErrorDescription> errors) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw new RuntimeException(errors.get(0).getDescription());
    }

    ProjectLoadingErrorsNotifier.getInstance(myProject).registerErrors(errors);
  }

  public void removeFailedModulePath(@NotNull ModulePath modulePath) {
    myFailedModulePaths.remove(modulePath);
  }

  @Override
  @NotNull
  public ModifiableModuleModel getModifiableModel() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return new ModuleModelImpl(myModuleModel);
  }

  private class ModuleSaveItem extends SaveItem {
    private final Module myModule;

    public ModuleSaveItem(@NotNull Module module) {
      myModule = module;
    }

    @Override
    @NotNull
    protected String getModuleName() {
      return myModule.getName();
    }

    @Override
    protected String getGroupPathString() {
      String[] groupPath = getModuleGroupPath(myModule);
      return groupPath != null ? StringUtil.join(groupPath, MODULE_GROUP_SEPARATOR) : null;
    }

    @Override
    @NotNull
    protected String getModuleFilePath() {
      return myModule.getModuleFilePath();
    }
  }

  public void writeExternal(@NotNull Element element) {
    final Module[] collection = getModules();

    List<SaveItem> sorted = new ArrayList<>(collection.length + myFailedModulePaths.size());
    for (Module module : collection) {
      sorted.add(new ModuleSaveItem(module));
    }
    for (ModulePath modulePath : myFailedModulePaths) {
      sorted.add(new ModulePathSaveItem(modulePath));
    }

    if (!sorted.isEmpty()) {
      Collections.sort(sorted, Comparator.comparing(SaveItem::getModuleName));

      Element modules = new Element(ELEMENT_MODULES);
      for (SaveItem saveItem : sorted) {
        saveItem.writeExternal(modules);
      }
      element.addContent(modules);
    }
  }

  @Override
  @NotNull
  public Module newModule(@NotNull String filePath, final String moduleTypeId) {
    incModificationCount();
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.newModule(filePath, moduleTypeId);
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
  @NotNull
  public Module[] getModules() {
    if (myModuleModel.myIsWritable) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    return myModuleModel.getModules();
  }

  private volatile Module[] myCachedSortedModules;

  @Override
  @NotNull
  public Module[] getSortedModules() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    deliverPendingEvents();
    if (myCachedSortedModules == null) {
      myCachedSortedModules = myModuleModel.getSortedModules();
    }
    return myCachedSortedModules;
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
    if (myCachedModuleComparator == null) {
      myCachedModuleComparator = myModuleModel.moduleDependencyComparator();
    }
    return myCachedModuleComparator;
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
    return myModuleModel.moduleGraph(includeTests);
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

  public static void commitModelWithRunnable(@NotNull ModifiableModuleModel model, Runnable runnable) {
    ((ModuleModelImpl)model).commitWithRunnable(runnable);
  }

  @NotNull
  protected abstract ModuleEx createModule(@NotNull String filePath);

  @NotNull
  protected abstract ModuleEx createAndLoadModule(@NotNull String filePath, @NotNull VirtualFile file) throws IOException;

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
    @NotNull
    public Module[] getModules() {
      Module[] cache = myModulesCache;
      if (cache == null) {
        Collection<Module> modules = myModules.values();
        myModulesCache = cache = modules.toArray(new Module[modules.size()]);
      }
      return cache;
    }

    @NotNull
    private Module[] getSortedModules() {
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
        throw new ModuleWithNameAlreadyExists(ProjectBundle.message("module.already.exists.error", newName), newName);
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

    @Override
    @NotNull
    public Module newModule(@NotNull String filePath, final String moduleTypeId) {
      return newModule(filePath, moduleTypeId, null);
    }

    @Override
    @NotNull
    public Module newModule(@NotNull String filePath, @NotNull final String moduleTypeId, @Nullable final Map<String, String> options) {
      assertWritable();
      filePath = FileUtil.toSystemIndependentName(resolveShortWindowsName(filePath));

      ModuleEx module = getModuleByFilePath(filePath);
      if (module == null) {
        module = myManager.createModule(filePath);
        final ModuleEx newModule = module;
        initModule(module, filePath, null, () -> {
          newModule.setOption(Module.ELEMENT_TYPE, moduleTypeId);
          if (options != null) {
            for (Map.Entry<String, String> option : options.entrySet()) {
              newModule.setOption(option.getKey(), option.getValue());
            }
          }
        });
      }
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
    private ModuleEx getModuleByFilePath(@NotNull String filePath) {
      for (Module module : getModules()) {
        if (SystemInfo.isFileSystemCaseSensitive ? module.getModuleFilePath().equals(filePath) : module.getModuleFilePath().equalsIgnoreCase(filePath)) {
          return (ModuleEx)module;
        }
      }
      return null;
    }

    @Override
    @NotNull
    public Module loadModule(@NotNull String filePath) throws IOException, ModuleWithNameAlreadyExists {
      assertWritable();
      try {
        Module module = getModuleByFilePath(filePath);
        if (module != null) return module;
        return loadModuleInternal(filePath).compute();
      }
      catch (FileNotFoundException e) {
        throw e;
      }
      catch (IOException e) {
        throw new IOException(ProjectBundle.message("module.corrupted.file.error", FileUtil.toSystemDependentName(filePath), e.getMessage()), e);
      }
    }

    @NotNull
    private ThrowableComputable<Module, IOException> loadModuleInternal(@NotNull String filePath) throws IOException {
      String resolvedPath = resolveShortWindowsName(filePath);
      Ref<VirtualFile> ref = Ref.create();
      ApplicationManager.getApplication().invokeAndWait(() -> ref.set(StandardFileSystems.local().refreshAndFindFileByPath(resolvedPath)));
      VirtualFile moduleFile = ref.get();
      if (moduleFile == null || !moduleFile.exists()) {
        throw new FileNotFoundException(ProjectBundle.message("module.file.does.not.exist.error", resolvedPath));
      }

      String path = moduleFile.getPath();
      ApplicationManager.getApplication().invokeAndWait(() -> moduleFile.refresh(false, false));
      return () -> ReadAction.compute(() -> {
        if (myManager.myProject.isDisposed()) return null;
        ModuleEx result = myManager.createAndLoadModule(path, moduleFile);
        initModule(result, path, moduleFile, null);
        return result;
      });
    }

    private void initModule(@NotNull ModuleEx module, @NotNull String path, @Nullable VirtualFile file, @Nullable Runnable beforeComponentCreation) {
      module.init(path, file, beforeComponentCreation);
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

    private Comparator<Module> moduleDependencyComparator() {
      DFSTBuilder<Module> builder = new DFSTBuilder<>(moduleGraph(true));
      return builder.comparator();
    }

    private Graph<Module> moduleGraph(final boolean includeTests) {
      return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<Module>() {
        @Override
        public Collection<Module> getNodes() {
          return myModules.values();
        }

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

    private void commitWithRunnable(Runnable runnable) {
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
      final Set<Module> existingModules = new THashSet<>(Arrays.asList(myManager.myModuleModel.getModules()));
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
      return !myModules.equals(myManager.myModuleModel.myModules) || !Comparing.equal(myManager.myModuleModel.myModuleGroupPath, myModuleGroupPath);
    }

    private void disposeModel() {
      Module[] modules = getModules();
      myModulesCache = null;
      for (Module module : modules) {
        Disposer.dispose(module);
      }
      myModules.clear();
      myModuleGroupPath = null;
    }

    @Override
    public String[] getModuleGroupPath(Module module) {
      return myModuleGroupPath == null ? null : myModuleGroupPath.get(module);
    }

    @Override
    public boolean hasModuleGroups() {
      return myModuleGroupPath != null && !myModuleGroupPath.isEmpty();
    }

    @Override
    public void setModuleGroupPath(@NotNull Module module, @Nullable("null means remove") String[] groupPath) {
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
  }

  private void commitModel(final ModuleModelImpl moduleModel, final Runnable runnable) {
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

    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(() -> {
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

      if (runnable != null) {
        runnable.run();
      }

      final Map<Module, String> modulesToNewNamesMap = moduleModel.myModuleToNewName;
      final Set<Module> modulesToBeRenamed = modulesToNewNamesMap.keySet();
      modulesToBeRenamed.removeAll(moduleModel.myModulesToDispose);

      List<Module> modules = new ArrayList<>();
      Map<Module, String> oldNames = ContainerUtil.newHashMap();
      for (final Module module : modulesToBeRenamed) {
        oldNames.put(module, module.getName());
        moduleModel.myModules.remove(module.getName());
        modules.add(module);
        ((ModuleEx)module).rename(modulesToNewNamesMap.get(module), true);
        moduleModel.myModules.put(module.getName(), module);
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
        ((ModuleEx)addedModule).moduleAdded();
        cleanCachedStuff();
        fireModuleAdded(addedModule);
        cleanCachedStuff();
      }
      cleanCachedStuff();
      fireModulesRenamed(modules, oldNames);
      cleanCachedStuff();
    }, false, true);
  }

  public void fireModuleRenamedByVfsEvent(@NotNull final Module module, @NotNull final String oldName) {
    Module moduleInMap = myModuleModel.myModules.remove(oldName);
    LOG.assertTrue(moduleInMap == null || moduleInMap == module);
    myModuleModel.myModules.put(module.getName(), module);
    incModificationCount();

    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(
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

  public void setModuleGroupPath(Module module, String[] groupPath) {
    myModuleModel.setModuleGroupPath(module, groupPath);
  }
}

