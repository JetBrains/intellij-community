/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.components.StateStorageException;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public abstract class ModuleManagerImpl extends ModuleManager implements ProjectComponent, PersistentStateComponent<Element>, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleManagerImpl");
  public static final Key<String> DISPOSED_MODULE_NAME = Key.create("DisposedNeverAddedModuleName");
  private static final String IML_EXTENSION = ".iml";
  protected final Project myProject;
  protected final MessageBus myMessageBus;
  protected volatile ModuleModelImpl myModuleModel = new ModuleModelImpl();

  @NonNls public static final String COMPONENT_NAME = "ProjectModuleManager";
  private static final String MODULE_GROUP_SEPARATOR = "/";
  private List<ModulePath> myModulePaths;
  private final List<ModulePath> myFailedModulePaths = new ArrayList<ModulePath>();
  @NonNls public static final String ELEMENT_MODULES = "modules";
  @NonNls public static final String ELEMENT_MODULE = "module";
  @NonNls private static final String ATTRIBUTE_FILEURL = "fileurl";
  @NonNls public static final String ATTRIBUTE_FILEPATH = "filepath";
  @NonNls private static final String ATTRIBUTE_GROUP = "group";
  private long myModificationCount;

  public static ModuleManagerImpl getInstanceImpl(Project project) {
    return (ModuleManagerImpl)getInstance(project);
  }

  protected void cleanCachedStuff() {
    myCachedModuleComparator = null;
    myCachedSortedModules = null;
  }

  public ModuleManagerImpl(Project project, MessageBus messageBus) {
    myProject = project;
    myMessageBus = messageBus;
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
  public long getModificationCount() {
    return myModificationCount;
  }

  @Override
  public Element getState() {
    final Element e = new Element("state");
    writeExternal(e);
    return e;
  }

  private static class ModuleGroupInterner {
    private final StringInterner groups = new StringInterner();
    private final Map<String[], String[]> paths = new THashMap<String[], String[]>(new TObjectHashingStrategy<String[]>() {
      @Override
      public int computeHashCode(String[] object) {
        return Arrays.hashCode(object);
      }

      @Override
      public boolean equals(String[] o1, String[] o2) {
        return Arrays.equals(o1, o2);
      }
    });

    private void setModuleGroupPath(@NotNull ModifiableModuleModel model, Module module, @Nullable String[] group) {
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
      ModifiableModuleModel model = getModifiableModel();

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

      model.commit();
    }
  }

  private ModulePath findCorrespondingPath(final Module existingModule) {
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
  public static ModulePath[] getPathsToModuleFiles(Element element) {
    final List<ModulePath> paths = new ArrayList<ModulePath>();
    final Element modules = element.getChild(ELEMENT_MODULES);
    if (modules != null) {
      for (final Object value : modules.getChildren(ELEMENT_MODULE)) {
        Element moduleElement = (Element)value;
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

  public void readExternal(final Element element) {
    myModulePaths = new ArrayList<ModulePath>(Arrays.asList(getPathsToModuleFiles(element)));
  }

  protected void loadModules(final ModuleModelImpl moduleModel) {
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
    final List<Module> modulesWithUnknownTypes = new ArrayList<Module>();
    List<ModuleLoadingErrorDescription> errors = new ArrayList<ModuleLoadingErrorDescription>();

    for (int i = 0; i < myModulePaths.size(); i++) {
      ModulePath modulePath = myModulePaths.get(i);
      if (progressIndicator != null) {
        progressIndicator.setFraction((double) i / myModulePaths.size());
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
      catch (final IOException e) {
        errors.add(ModuleLoadingErrorDescription.create(ProjectBundle.message("module.cannot.load.error", modulePath.getPath(), e.getMessage()),
                                                     modulePath, this));
      }
      catch (final ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
        errors.add(ModuleLoadingErrorDescription.create(moduleWithNameAlreadyExists.getMessage(), modulePath, this));
      }
      catch (StateStorageException e) {
        errors.add(ModuleLoadingErrorDescription.create(ProjectBundle.message("module.cannot.load.error", modulePath.getPath(), e.getMessage()),
                                                     modulePath, this));
      }
    }

    fireErrors(errors);

    showUnknownModuleTypeNotification(modulesWithUnknownTypes);

    if (progressIndicator != null) {
      progressIndicator.setIndeterminate(true);
    }
  }

  protected boolean isUnknownModuleType(Module module) {
    return false;
  }

  protected void showUnknownModuleTypeNotification(List<Module> types) {
  }

  protected void fireModuleAdded(Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).moduleAdded(myProject, module);
  }

  protected void fireModuleRemoved(Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).moduleRemoved(myProject, module);
  }

  protected void fireBeforeModuleRemoved(Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).beforeModuleRemoved(myProject, module);
  }

  protected void fireModulesRenamed(List<Module> modules, final Map<Module, String> oldNames) {
    if (!modules.isEmpty()) {
      myMessageBus.syncPublisher(ProjectTopics.MODULES).modulesRenamed(myProject, modules, new Function<Module, String>() {
        @Override
        public String fun(Module module) {
          return oldNames.get(module);
        }
      });
    }
  }

  private void fireErrors(final List<ModuleLoadingErrorDescription> errors) {
    if (errors.isEmpty()) return;

    myModuleModel.myModulesCache = null;
    for (ModuleLoadingErrorDescription error : errors) {
      final Module module = myModuleModel.myPathToModule.remove(FileUtil.toSystemIndependentName(error.getModulePath().getPath()));
      if (module != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Disposer.dispose(module);
          }
        });
      }
    }

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

    protected abstract String getModuleName();
    protected abstract String getGroupPathString();
    protected abstract String getModuleFilePath();

    public final void writeExternal(Element parentElement) {
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

    public ModuleSaveItem(Module module) {
      myModule = module;
    }

    @Override
    protected String getModuleName() {
      return myModule.getName();
    }

    @Override
    protected String getGroupPathString() {
      String[] groupPath = getModuleGroupPath(myModule);
      return groupPath != null ? StringUtil.join(groupPath, MODULE_GROUP_SEPARATOR) : null;
    }

    @Override
    protected String getModuleFilePath() {
      return myModule.getModuleFilePath().replace(File.separatorChar, '/');
    }
  }

  private static class ModulePathSaveItem extends SaveItem{
    private final ModulePath myModulePath;
    private final String myFilePath;
    private final String myName;

    public ModulePathSaveItem(ModulePath modulePath) {
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
    protected String getModuleName() {
      return myName;
    }

    @Override
    protected String getGroupPathString() {
      return myModulePath.getModuleGroup();
    }

    @Override
    protected String getModuleFilePath() {
      return myFilePath;
    }
  }

  public void writeExternal(Element element) {
    final Element modules = new Element(ELEMENT_MODULES);
    final Module[] collection = getModules();

    ArrayList<SaveItem> sorted = new ArrayList<SaveItem>(collection.length + myFailedModulePaths.size());
    for (Module module : collection) {
      sorted.add(new ModuleSaveItem(module));
    }
    for (ModulePath modulePath : myFailedModulePaths) {
      sorted.add(new ModulePathSaveItem(modulePath));
    }
    Collections.sort(sorted, new Comparator<SaveItem>() {
      @Override
      public int compare(SaveItem item1, SaveItem item2) {
        return item1.getModuleName().compareTo(item2.getModuleName());
      }
    });
    for (SaveItem saveItem : sorted) {
      saveItem.writeExternal(modules);
    }

    element.addContent(modules);
  }

  @Override
  @NotNull
  public Module newModule(@NotNull String filePath, final String moduleTypeId) {
    myModificationCount++;
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.newModule(filePath, moduleTypeId);
    modifiableModel.commit();
    return module;
  }

  @Override
  @NotNull
  public Module loadModule(@NotNull String filePath) throws InvalidDataException,
                                                   IOException,
                                                   JDOMException,
                                                   ModuleWithNameAlreadyExists {
    myModificationCount++;
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.loadModule(filePath);
    modifiableModel.commit();
    return module;
  }

  @Override
  public void disposeModule(@NotNull final Module module) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModifiableModuleModel modifiableModel = getModifiableModel();
        modifiableModel.disposeModule(module);
        modifiableModel.commit();
      }
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

  private Module[] myCachedSortedModules = null;

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

  private Comparator<Module> myCachedModuleComparator = null;

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
  @NotNull public List<Module> getModuleDependentModules(@NotNull Module module) {
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
    for (final Module module : myModuleModel.myPathToModule.values()) {
      fireModuleAddedInWriteAction(module);
    }
  }

  protected void fireModuleAddedInWriteAction(final Module module) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ((ModuleEx)module).moduleAdded();
        fireModuleAdded(module);
      }
    });
  }

  @Override
  public void projectClosed() {
    myModuleModel.projectClosed();
  }

  public static void commitModelWithRunnable(ModifiableModuleModel model, Runnable runnable) {
    ((ModuleModelImpl)model).commitWithRunnable(runnable);
  }

  protected abstract ModuleEx createModule(String filePath);

  protected abstract ModuleEx createAndLoadModule(String filePath) throws IOException;

  class ModuleModelImpl implements ModifiableModuleModel {
    final Map<String, Module> myPathToModule = new LinkedHashMap<String, Module>(new EqualityPolicy.ByHashingStrategy<String>(FilePathHashingStrategy.create()));
    private Module[] myModulesCache;

    private final List<Module> myModulesToDispose = new ArrayList<Module>();
    private final Map<Module, String> myModuleToNewName = new HashMap<Module, String>();
    private final Map<String, Module> myNewNameToModule = new HashMap<String, Module>();
    private boolean myIsWritable;
    private Map<Module, String[]> myModuleGroupPath;

    ModuleModelImpl() {
      myIsWritable = false;
    }

    ModuleModelImpl(ModuleModelImpl that) {
      myPathToModule.putAll(that.myPathToModule);
      final Map<Module, String[]> groupPath = that.myModuleGroupPath;
      if (groupPath != null){
        myModuleGroupPath = new THashMap<Module, String[]>();
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
      if (myModulesCache == null) {
        Collection<Module> modules = myPathToModule.values();
        myModulesCache = modules.toArray(new Module[modules.size()]);
      }
      return myModulesCache;
    }

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

    public Module getModuleByNewName(String newName) {
      final Module moduleToBeRenamed = getModuleToBeRenamed(newName);
      if (moduleToBeRenamed != null) {
        return moduleToBeRenamed;
      }
      final Module moduleWithOldName = findModuleByName(newName);
      if (myModuleToNewName.get(moduleWithOldName) == null) {
        return moduleWithOldName;
      }
      else {
        return null;
      }
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
    public Module newModule(@NotNull String filePath,
                            final String moduleTypeId,
                            @Nullable Map<String, String> options) {
      assertWritable();
      filePath = resolveShortWindowsName(filePath);

      ModuleEx module = getModuleByFilePath(filePath);
      if (module == null) {
        module = createModule(filePath);
        module.setOption(Module.ELEMENT_TYPE, moduleTypeId);
        if (options != null) {
          for ( Map.Entry<String,String> option : options.entrySet()) {
            module.setOption(option.getKey(),option.getValue());
          }
        }
        module.loadModuleComponents();
        initModule(module);
      }
      return module;
    }

    private String resolveShortWindowsName(String filePath) {
      try {
        return FileUtil.resolveShortWindowsName(filePath);
      }
      catch (IOException ignored) {
        return filePath;
      }
    }

    @Nullable
    private ModuleEx getModuleByFilePath(String filePath) {
      return (ModuleEx)myPathToModule.get(filePath);
    }

    @Override
    @NotNull
    public Module loadModule(@NotNull String filePath) throws InvalidDataException, IOException, ModuleWithNameAlreadyExists {
      assertWritable();
      try {
        return loadModuleInternal(filePath);
      }
      catch (StateStorageException e) {
        throw new IOException(ProjectBundle.message("module.corrupted.file.error", FileUtil.toSystemDependentName(filePath), e.getMessage()));
      }
    }

    private Module loadModuleInternal(String filePath)
      throws ModuleWithNameAlreadyExists, IOException, StateStorageException {

      final VirtualFile moduleFile = StandardFileSystems.local().findFileByPath(resolveShortWindowsName(filePath));
      if (moduleFile == null || !moduleFile.exists()) {
        throw new IOException(ProjectBundle.message("module.file.does.not.exist.error", filePath));
      }

      final String name = moduleFile.getName();

      if (name.endsWith(IML_EXTENSION)) {
        final String moduleName = name.substring(0, name.length() - 4);
        for (Module module : myPathToModule.values()) {
          if (module.getName().equals(moduleName)) {
            throw new ModuleWithNameAlreadyExists(ProjectBundle.message("module.already.exists.error", moduleName), moduleName);
          }
        }
      }

      ModuleEx module = getModuleByFilePath(moduleFile.getPath());
      if (module == null) {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            moduleFile.refresh(false, false);
          }
        }, ModalityState.defaultModalityState());
        module = createAndLoadModule(moduleFile.getPath());
        module.loadModuleComponents();
        initModule(module);
      }
      return module;
    }

    private void initModule(ModuleEx module) {
      String path = module.getModuleFilePath();
      myModulesCache = null;
      myPathToModule.put(path, module);
      module.init();
    }

    @Override
    public void disposeModule(@NotNull Module module) {
      assertWritable();
      myModulesCache = null;
      if (myPathToModule.values().contains(module)) {
        myPathToModule.remove(module.getModuleFilePath());
        myModulesToDispose.add(module);
      }
      if (myModuleGroupPath != null){
        myModuleGroupPath.remove(module);
      }
    }

    @Override
    public Module findModuleByName(@NotNull String name) {
      for (Module module : myPathToModule.values()) {
        if (!module.isDisposed() && module.getName().equals(name)) {
          return module;
        }
      }
      return null;
    }

    private Comparator<Module> moduleDependencyComparator() {
      DFSTBuilder<Module> builder = new DFSTBuilder<Module>(moduleGraph(true));
      return builder.comparator();
    }

    private Graph<Module> moduleGraph(final boolean includeTests) {
      return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Module>() {
        @Override
        public Collection<Module> getNodes() {
          return myPathToModule.values();
        }

        @Override
        public Iterator<Module> getIn(Module m) {
          Module[] dependentModules = ModuleRootManager.getInstance(m).getDependencies(includeTests);
          return Arrays.asList(dependentModules).iterator();
        }
      }));
    }

    @NotNull private List<Module> getModuleDependentModules(Module module) {
      List<Module> result = new ArrayList<Module>();
      for (Module aModule : myPathToModule.values()) {
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

    public void commitWithRunnable(Runnable runnable) {
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
      final Collection<Module> list = myModuleModel.myPathToModule.values();
      final Collection<Module> thisModules = myPathToModule.values();
      for (Module thisModule : thisModules) {
        if (!list.contains(thisModule)) {
          Disposer.dispose(thisModule);
        }
      }
      for (Module moduleToDispose : myModulesToDispose) {
        if (!list.contains(moduleToDispose)) {
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
      Set<Module> thisModules = new HashSet<Module>(myPathToModule.values());
      Set<Module> thatModules = new HashSet<Module>(myModuleModel.myPathToModule.values());
      return !thisModules.equals(thatModules) || !Comparing.equal(myModuleModel.myModuleGroupPath, myModuleGroupPath);
    }

    private void disposeModel() {
      myModulesCache = null;
      for (final Module module : myPathToModule.values()) {
        Disposer.dispose(module);
      }
      myPathToModule.clear();
      myModuleGroupPath = null;
    }

    public void projectOpened() {
      final Collection<Module> collection = myPathToModule.values();
      for (final Module aCollection : collection) {
        ModuleEx module = (ModuleEx)aCollection;
        module.projectOpened();
      }
    }

    public void projectClosed() {
      final Collection<Module> collection = myPathToModule.values();
      for (final Module aCollection : collection) {
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
        myModuleGroupPath = new THashMap<Module, String[]>();
      }
      if (groupPath == null) {
        myModuleGroupPath.remove(module);
      }
      else {
        myModuleGroupPath.put(module, groupPath);
      }
    }

    @Override
    public void setModuleFilePath(@NotNull Module module, String oldPath, String newFilePath) {
      myPathToModule.remove(oldPath);
      myPathToModule.put(newFilePath, module);
    }
  }

  private void commitModel(final ModuleModelImpl moduleModel, final Runnable runnable) {
    myModuleModel.myModulesCache = null;
    myModificationCount++;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final Collection<Module> oldModules = myModuleModel.myPathToModule.values();
    final Collection<Module> newModules = moduleModel.myPathToModule.values();
    final List<Module> removedModules = new ArrayList<Module>(oldModules);
    removedModules.removeAll(newModules);
    final List<Module> addedModules = new ArrayList<Module>(newModules);
    addedModules.removeAll(oldModules);

    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(new Runnable() {
      @Override
      public void run() {
        for (Module removedModule : removedModules) {
          fireBeforeModuleRemoved(removedModule);
          cleanCachedStuff();
        }

        List<Module> neverAddedModules = new ArrayList<Module>(moduleModel.myModulesToDispose);
        neverAddedModules.removeAll(myModuleModel.myPathToModule.values());
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

        List<Module> modules = new ArrayList<Module>();
        Map<Module, String> oldNames = ContainerUtil.newHashMap();
        for (final Module module : modulesToBeRenamed) {
          oldNames.put(module, module.getName());
          moduleModel.myPathToModule.remove(module.getModuleFilePath());
          modules.add(module);
          ((ModuleEx)module).rename(modulesToNewNamesMap.get(module));
          moduleModel.myPathToModule.put(module.getModuleFilePath(), module);
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
      }
    }, false, true);
  }

  void fireModuleRenamedByVfsEvent(@NotNull final Module module, @NotNull final String oldName) {
    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(new Runnable() {
      @Override
      public void run() {
        fireModulesRenamed(Collections.singletonList(module), Collections.singletonMap(module, oldName));
      }
    }, false, true);
  }

  @Override
  public String[] getModuleGroupPath(@NotNull Module module) {
    return myModuleModel.getModuleGroupPath(module);
  }

  public void setModuleGroupPath(Module module, String[] groupPath) {
    myModuleModel.setModuleGroupPath(module, groupPath);
  }
}

