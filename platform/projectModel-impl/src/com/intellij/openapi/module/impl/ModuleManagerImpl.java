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

package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public abstract class ModuleManagerImpl extends ModuleManager implements ProjectComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleManagerImpl");
  private static final Key<String> DISPOSED_MODULE_NAME = Key.create("DisposedNeverAddedModuleName");
  private static final String IML_EXTENSION = ".iml";
  protected final Project myProject;
  protected final MessageBus myMessageBus;
  protected volatile ModuleModelImpl myModuleModel = new ModuleModelImpl();

  @NonNls public static final String COMPONENT_NAME = "ProjectModuleManager";
  private static final String MODULE_GROUP_SEPARATOR = "/";
  private List<ModulePath> myModulePaths;
  private final List<ModulePath> myFailedModulePaths = new ArrayList<>();
  @NonNls public static final String ELEMENT_MODULES = "modules";
  @NonNls public static final String ELEMENT_MODULE = "module";
  @NonNls private static final String ATTRIBUTE_FILEURL = "fileurl";
  @NonNls public static final String ATTRIBUTE_FILEPATH = "filepath";
  @NonNls private static final String ATTRIBUTE_GROUP = "group";

  public static ModuleManagerImpl getInstanceImpl(Project project) {
    return (ModuleManagerImpl)getInstance(project);
  }

  public ModuleManagerImpl(Project project, MessageBus messageBus) {
    myProject = project;
    myMessageBus = messageBus;
  }

  protected void cleanCachedStuff() {
    myCachedModuleComparator = null;
    myCachedSortedModules = null;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
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
    List<ModulePath> prevPaths = myModulePaths;
    readExternal(state);
    if (prevPaths != null) {
      final ModifiableModuleModel model = getModifiableModel();

      Module[] existingModules = model.getModules();

      ModuleGroupInterner groupInterner = new ModuleGroupInterner();
      for (Module existingModule : existingModules) {
        ModulePath correspondingPath = findCorrespondingPath(existingModule);
        if (correspondingPath == null) {
          model.disposeModule(existingModule);
        }
        else {
          myModulePaths.remove(correspondingPath);

          String groupStr = correspondingPath.getModuleGroup();
          String[] group = groupStr == null ? null : groupStr.split(MODULE_GROUP_SEPARATOR);
          if (!Arrays.equals(group, model.getModuleGroupPath(existingModule))) {
            groupInterner.setModuleGroupPath(model, existingModule, group);
          }
        }
      }

      loadModules((ModuleModelImpl)model);

      ApplicationManager.getApplication().runWriteAction(() -> model.commit());
    }
  }

  private ModulePath findCorrespondingPath(@NotNull Module existingModule) {
    for (ModulePath modulePath : myModulePaths) {
      if (modulePath.getPath().equals(existingModule.getModuleFilePath())) return modulePath;
    }

    return null;
  }

  public static final class ModulePath {
    private final String myPath;
    private final String myModuleGroup;

    public ModulePath(String path, String moduleGroup) {
      myPath = path;
      myModuleGroup = moduleGroup;
    }

    public String getPath() {
      return myPath;
    }

    public String getModuleGroup() {
      return myModuleGroup;
    }
  }

  @NotNull
  public static ModulePath[] getPathsToModuleFiles(@NotNull Element element) {
    final List<ModulePath> paths = new ArrayList<>();
    final Element modules = element.getChild(ELEMENT_MODULES);
    if (modules != null) {
      for (final Element moduleElement : modules.getChildren(ELEMENT_MODULE)) {
        final String fileUrlValue = moduleElement.getAttributeValue(ATTRIBUTE_FILEURL);
        final String filepath;
        if (fileUrlValue != null) {
          filepath = VirtualFileManager.extractPath(fileUrlValue).replace('/', File.separatorChar);
        }
        else {
          // [dsl] support for older formats
          filepath = moduleElement.getAttributeValue(ATTRIBUTE_FILEPATH).replace('/', File.separatorChar);
        }
        final String group = moduleElement.getAttributeValue(ATTRIBUTE_GROUP);
        paths.add(new ModulePath(filepath, group));
      }
    }
    return paths.toArray(new ModulePath[paths.size()]);
  }

  public void readExternal(@NotNull Element element) {
    myModulePaths = new ArrayList<>(Arrays.asList(getPathsToModuleFiles(element)));
  }

  protected void loadModules(@NotNull ModuleModelImpl moduleModel) {
    if (myModulePaths == null || myModulePaths.isEmpty()) {
      return;
    }
    ModuleGroupInterner groupInterner = new ModuleGroupInterner();

    final ProgressIndicator progressIndicator = myProject.isDefault() ? null : ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setText("Loading modules...");
      progressIndicator.setText2("");
    }
    myFailedModulePaths.clear();
    myFailedModulePaths.addAll(myModulePaths);
    final List<Module> modulesWithUnknownTypes = new ArrayList<>();
    List<ModuleLoadingErrorDescription> errors = new ArrayList<>();

    for (ModulePath modulePath : myModulePaths) {
      if (progressIndicator != null) {
        progressIndicator.setFraction(progressIndicator.getFraction() + myProgressStep);
      }
      try {
        final Module module = moduleModel.loadModuleInternal(modulePath.getPath());
        if (isUnknownModuleType(module)) {
          modulesWithUnknownTypes.add(module);
        }
        final String groupPathString = modulePath.getModuleGroup();
        if (groupPathString != null) {
          final String[] groupPath = groupPathString.split(MODULE_GROUP_SEPARATOR);

          groupInterner.setModuleGroupPath(moduleModel, module, groupPath); //model should be updated too
        }
        myFailedModulePaths.remove(modulePath);
      }
      catch (IOException e) {
        errors
          .add(ModuleLoadingErrorDescription.create(ProjectBundle.message("module.cannot.load.error", modulePath.getPath(), e.getMessage()),
                                                    modulePath, this));
      }
      catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
        errors.add(ModuleLoadingErrorDescription.create(moduleWithNameAlreadyExists.getMessage(), modulePath, this));
      }
    }

    onModuleLoadErrors(errors);

    showUnknownModuleTypeNotification(modulesWithUnknownTypes);
  }

  public int getModulePathsCount() { return myModulePaths == null ? 0 : myModulePaths.size(); }

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

  protected void onModuleLoadErrors(@NotNull List<ModuleLoadingErrorDescription> errors) {
    if (errors.isEmpty()) return;

    myModuleModel.myModulesCache = null;
    for (ModuleLoadingErrorDescription error : errors) {
      final Module module = myModuleModel.getModuleByFilePath(FileUtil.toSystemIndependentName(error.getModulePath().getPath()));
      if (module != null) {
        myModuleModel.myModules.remove(module.getName());
        ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(module), module.getDisposed());
      }
    }

    fireModuleLoadErrors(errors);
  }

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


  private abstract static class SaveItem {
    @NotNull
    protected abstract String getModuleName();
    protected abstract String getGroupPathString();
    @NotNull
    protected abstract String getModuleFilePath();

    public final void writeExternal(@NotNull Element parentElement) {
      Element moduleElement = new Element(ELEMENT_MODULE);
      final String moduleFilePath = getModuleFilePath();
      final String url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, moduleFilePath);
      moduleElement.setAttribute(ATTRIBUTE_FILEURL, url);
      // [dsl] support for older builds
      moduleElement.setAttribute(ATTRIBUTE_FILEPATH, moduleFilePath);

      final String groupPath = getGroupPathString();
      if (groupPath != null) {
        moduleElement.setAttribute(ATTRIBUTE_GROUP, groupPath);
      }
      parentElement.addContent(moduleElement);
    }
  }

  private class ModuleSaveItem extends SaveItem{
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
      return myModule.getModuleFilePath().replace(File.separatorChar, '/');
    }
  }

  private static class ModulePathSaveItem extends SaveItem{
    private final ModulePath myModulePath;
    private final String myFilePath;
    private final String myName;

    private ModulePathSaveItem(@NotNull ModulePath modulePath) {
      myModulePath = modulePath;
      myFilePath = modulePath.getPath().replace(File.separatorChar, '/');

      final int slashIndex = myFilePath.lastIndexOf('/');
      final int startIndex = slashIndex >= 0 && slashIndex + 1 < myFilePath.length() ? slashIndex + 1 : 0;
      final int endIndex = myFilePath.endsWith(IML_EXTENSION)
                           ? myFilePath.length() - IML_EXTENSION.length()
                           : myFilePath.length();
      myName = myFilePath.substring(startIndex, endIndex);
    }

    @Override
    @NotNull
    protected String getModuleName() {
      return myName;
    }

    @Override
    protected String getGroupPathString() {
      return myModulePath.getModuleGroup();
    }

    @Override
    @NotNull
    protected String getModuleFilePath() {
      return myFilePath;
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
      Collections.sort(sorted, (item1, item2) -> item1.getModuleName().compareTo(item2.getModuleName()));

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
  public Module loadModule(@NotNull String filePath) throws IOException, JDOMException, ModuleWithNameAlreadyExists {
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
    ApplicationManager.getApplication().assertReadAccessAllowed();
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
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.getModuleDependentModules(module);
  }

  @Override
  public boolean isModuleDependent(@NotNull Module module, @NotNull Module onModule) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.isModuleDependent(module, onModule);
  }

  @Override
  public void projectOpened() {
    fireModulesAdded();

    myModuleModel.projectOpened();
  }

  protected void fireModulesAdded() {
    for (final Module module : myModuleModel.myModules.values()) {
      fireModuleAddedInWriteAction(module);
    }
  }

  protected void fireModuleAddedInWriteAction(@NotNull final Module module) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ((ModuleEx)module).moduleAdded();
      fireModuleAdded(module);
    });
  }

  @Override
  public void projectClosed() {
    myModuleModel.projectClosed();
  }

  public static void commitModelWithRunnable(@NotNull ModifiableModuleModel model, Runnable runnable) {
    ((ModuleModelImpl)model).commitWithRunnable(runnable);
  }

  @NotNull
  protected abstract ModuleEx createModule(@NotNull String filePath);

  @NotNull
  protected abstract ModuleEx createAndLoadModule(@NotNull String filePath) throws IOException;

  class ModuleModelImpl implements ModifiableModuleModel {
    final Map<String, Module> myModules = new LinkedHashMap<>();
    private volatile Module[] myModulesCache;

    private final List<Module> myModulesToDispose = new ArrayList<>();
    private final Map<Module, String> myModuleToNewName = new HashMap<>();
    private final Map<String, Module> myNewNameToModule = new HashMap<>();
    private boolean myIsWritable;
    private Map<Module, String[]> myModuleGroupPath;

    private ModuleModelImpl() {
      myIsWritable = false;
    }

    private ModuleModelImpl(@NotNull ModuleModelImpl that) {
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
        module = createModule(filePath);
        final ModuleEx newModule = module;
        initModule(module, filePath, () -> {
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
    private String resolveShortWindowsName(@NotNull String filePath) {
      try {
        return FileUtil.resolveShortWindowsName(filePath);
      }
      catch (IOException ignored) {
        return filePath;
      }
    }

    @Nullable
    private ModuleEx getModuleByFilePath(@NotNull String filePath) {
      for (Module module : myModules.values()) {
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
        return loadModuleInternal(filePath);
      }
      catch (FileNotFoundException e) {
        throw e;
      }
      catch (IOException e) {
        throw new IOException(ProjectBundle.message("module.corrupted.file.error", FileUtil.toSystemDependentName(filePath), e.getMessage()), e);
      }
    }

    @NotNull
    private Module loadModuleInternal(@NotNull String filePath) throws ModuleWithNameAlreadyExists, IOException {
      filePath = resolveShortWindowsName(filePath);
      final VirtualFile moduleFile = StandardFileSystems.local().findFileByPath(filePath);
      if (moduleFile == null || !moduleFile.exists()) {
        throw new FileNotFoundException(ProjectBundle.message("module.file.does.not.exist.error", filePath));
      }

      String path = moduleFile.getPath();
      ModuleEx module = getModuleByFilePath(path);
      if (module == null) {
        ApplicationManager.getApplication().invokeAndWait(() -> moduleFile.refresh(false, false), ModalityState.defaultModalityState());
        module = createAndLoadModule(path);
        initModule(module, path, null);
      }
      return module;
    }

    private void initModule(@NotNull ModuleEx module, @NotNull String path, @Nullable Runnable beforeComponentCreation) {
      module.init(path, beforeComponentCreation);
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
      return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Module>() {
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

    @NotNull private List<Module> getModuleDependentModules(Module module) {
      List<Module> result = new ArrayList<>();
      for (Module aModule : myModules.values()) {
        if (isModuleDependent(aModule, module)) {
          result.add(aModule);
        }
      }
      return result;
    }

    private boolean isModuleDependent(Module module, Module onModule) {
      return ModuleRootManager.getInstance(module).isDependsOn(onModule);
    }

    @Override
    public void commit() {
      ModifiableRootModel[] rootModels = new ModifiableRootModel[0];
      ModifiableModelCommitter.multiCommit(rootModels, this);
    }

    private void commitWithRunnable(Runnable runnable) {
      commitModel(this, runnable);
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
      final Set<Module> set = new HashSet<>();
      set.addAll(myModuleModel.myModules.values());
      for (Module thisModule : myModules.values()) {
        if (!set.contains(thisModule)) {
          Disposer.dispose(thisModule);
        }
      }
      for (Module moduleToDispose : myModulesToDispose) {
        if (!set.contains(moduleToDispose)) {
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
      return !myModules.equals(myModuleModel.myModules) || !Comparing.equal(myModuleModel.myModuleGroupPath, myModuleGroupPath);
    }

    private void disposeModel() {
      myModulesCache = null;
      for (final Module module : myModules.values()) {
        Disposer.dispose(module);
      }
      myModules.clear();
      myModuleGroupPath = null;
    }

    public void projectOpened() {
      for (final Module aCollection : myModules.values()) {
        ModuleEx module = (ModuleEx)aCollection;
        module.projectOpened();
      }
    }

    public void projectClosed() {
      for (Module aCollection : myModules.values()) {
        ModuleEx module = (ModuleEx)aCollection;
        module.projectClosed();
      }
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
    final Collection<Module> oldModules = myModuleModel.myModules.values();
    final Collection<Module> newModules = moduleModel.myModules.values();
    final List<Module> removedModules = new ArrayList<>(oldModules);
    removedModules.removeAll(newModules);
    final List<Module> addedModules = new ArrayList<>(newModules);
    addedModules.removeAll(oldModules);

    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(() -> {
      for (Module removedModule : removedModules) {
        fireBeforeModuleRemoved(removedModule);
        cleanCachedStuff();
      }

      List<Module> neverAddedModules = new ArrayList<>(moduleModel.myModulesToDispose);
      neverAddedModules.removeAll(myModuleModel.myModules.values());
      for (final Module neverAddedModule : neverAddedModules) {
        neverAddedModule.putUserData(DISPOSED_MODULE_NAME, neverAddedModule.getName());
        Disposer.dispose(neverAddedModule);
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
        ((ModuleEx)module).rename(modulesToNewNamesMap.get(module));
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

    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(
      () -> fireModulesRenamed(Collections.singletonList(module), Collections.singletonMap(module, oldName)), false, true);
  }

  @Override
  public String[] getModuleGroupPath(@NotNull Module module) {
    return myModuleModel.getModuleGroupPath(module);
  }

  public void setModuleGroupPath(Module module, String[] groupPath) {
    myModuleModel.setModuleGroupPath(module, groupPath);
  }
}

