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

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author peter
 */
public class MvcModuleStructureUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.mvc.MvcModuleStructureUtil");
  @NonNls public static final String PLUGINS_DIRECTORY = "plugins";
  @NonNls public static final String APPLICATION_PROPERTIES = "application.properties";
  public static final Key<String> LAST_MVC_VERSION = Key.create("LAST_MVC_VERSION");

  private MvcModuleStructureUtil() {
  }

  @Nullable
  public static ContentEntry findContentEntry(ModuleRootModel rootModel, VirtualFile root) {
    for (ContentEntry entry : rootModel.getContentEntries()) {
      if (Comparing.equal(entry.getFile(), root)) {
        return entry;
      }
    }

    return null;
  }

  @Nullable
  private static Consumer<ModifiableRootModel> addSourceRootsAndLibDirectory(@NotNull final VirtualFile root,
                                                                             final MvcProjectStructure structure) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(structure.myModule);

    Map<VirtualFile, JpsModuleSourceRootType<?>> sourceRoots = new HashMap<>();
    for (ContentEntry entry : moduleRootManager.getContentEntries()) {
      for (SourceFolder folder : entry.getSourceFolders()) {
        sourceRoots.put(folder.getFile(), folder.getRootType());
      }
    }

    root.refresh(false, true);

    final List<Consumer<ContentEntry>> actions = ContainerUtil.newArrayList();

    for (Map.Entry<JpsModuleSourceRootType<?>, Collection<String>> entry : structure.getSourceFolders().entrySet()) {
      JpsModuleSourceRootType<?> rootType = entry.getKey();

      for (String src : entry.getValue()) {
        addSourceFolder(root, src, rootType, actions, sourceRoots);
      }
    }

    for (final String src : structure.getInvalidSourceFolders()) {
      removeSrcFolderFromRoots(root.findFileByRelativePath(src), actions, sourceRoots);
    }

    for (final VirtualFile excluded : structure.getExcludedFolders(root)) {
      if (moduleRootManager.getFileIndex().isInContent(excluded)) {
        actions.add(contentEntry -> contentEntry.addExcludeFolder(excluded));
      }
    }

    final Consumer<ModifiableRootModel> modifyLib = addJarDirectory(root, structure.myModule, structure.getUserLibraryName());

    if (actions.isEmpty() && modifyLib == null && findContentEntry(moduleRootManager, root) != null) {
      return null;
    }

    return model -> {
      ContentEntry contentEntry = findContentEntry(model, root);
      if (contentEntry == null) {
        contentEntry = model.addContentEntry(root);
      }

      for (final Consumer<ContentEntry> action : actions) {
        action.consume(contentEntry);
      }
      if (modifyLib != null) {
        modifyLib.consume(model);
      }
    };
  }

  public static void removeSrcFolderFromRoots(final VirtualFile file,
                                              @NotNull List<Consumer<ContentEntry>> actions,
                                              @NotNull Map<VirtualFile, JpsModuleSourceRootType<?>> sourceRoots) {
    removeSrcFolderFromRoots(file, actions, sourceRoots.keySet());
  }

  public static void removeSrcFolderFromRoots(final VirtualFile file,
                                              @NotNull List<Consumer<ContentEntry>> actions,
                                              @NotNull Collection<VirtualFile> sourceRoots) {
    if (sourceRoots.contains(file)) {
      actions.add(contentEntry -> {
        SourceFolder[] folders = contentEntry.getSourceFolders();
        for (SourceFolder folder : folders) {
          if (Comparing.equal(folder.getFile(), file)) {
            contentEntry.removeSourceFolder(folder);
          }
        }
      });
    }
  }

  @Nullable
  public static Consumer<ModifiableRootModel> addJarDirectory(VirtualFile root, Module module, final String libName) {
    final VirtualFile libDir = root.findFileByRelativePath("lib");
    if (libDir == null || !libDir.isDirectory() || ProjectRootManager.getInstance(module.getProject()).getFileIndex().isExcluded(libDir)) {
      return null;
    }

    final Library library = findUserLibrary(module, libName);
    if (library != null && library.isJarDirectory(libDir.getUrl())) {
      return null;
    }

    return model -> {
      Library.ModifiableModel libModel = modifyDefaultLibrary(model, libName);
      libModel.addJarDirectory(libDir, false);
      libModel.commit();
    };
  }

  public static Library.ModifiableModel modifyDefaultLibrary(ModifiableRootModel model, String libName) {
    LibraryTable libTable = model.getModuleLibraryTable();

    for (Library library : libTable.getLibraries()) {
      if (library.getName() != null && library.getName().startsWith(libName)) {
        return library.getModifiableModel();
      }
    }

    Library library = LibraryUtil.createLibrary(libTable, libName + " (" + model.getModule().getName() + ')');

    for (OrderEntry entry : model.getOrderEntries()) {
      if (!(entry instanceof LibraryOrderEntry)) continue;

      LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
      if (libraryEntry.isModuleLevel() && libraryEntry.getLibrary() == library) {
        libraryEntry.setExported(true);
      }
    }

    return library.getModifiableModel();
  }

  private static void addSourceFolder(@NotNull VirtualFile root,
                                      @NotNull String relativePath,
                                      final JpsModuleSourceRootType<?> rootType,
                                      List<Consumer<ContentEntry>> actions,
                                      Map<VirtualFile, JpsModuleSourceRootType<?>> sourceRoots) {
    final VirtualFile src = root.findFileByRelativePath(relativePath);
    if (src == null) {
      return;
    }

    JpsModuleSourceRootType<?> existingRootType = sourceRoots.get(src);

    if (rootType == JavaSourceRootType.TEST_SOURCE && (existingRootType != null && existingRootType != JavaSourceRootType.TEST_SOURCE)) { // see http://youtrack.jetbrains.net/issue/IDEA-70642
      actions.add(entry -> {
        for (SourceFolder folder : entry.getSourceFolders()) {
          if (Comparing.equal(folder.getFile(), src)) {
            entry.removeSourceFolder(folder);
            entry.addSourceFolder(src, rootType);
            break;
          }
        }
      });
      return;
    }

    actions.add(contentEntry -> contentEntry.addSourceFolder(src, rootType));
  }

  public static void updateModuleStructure(final Module module, MvcProjectStructure structure, @NotNull VirtualFile root) {
    final Pair<Collection<Consumer<ModifiableRootModel>>, Collection<Consumer<ModifiableFacetModel>>> actions =
      getUpdateProjectStructureActions(Collections.singletonList(root), structure);

    // update module
    if (!actions.first.isEmpty()) {
      ModuleRootModificationUtil.updateModel(module, model -> {
        for (final Consumer<ModifiableRootModel> action : actions.first) {
          action.consume(model);
        }
      });
    }

    // update facets
    if (!actions.second.isEmpty()) {
      final Application application = ApplicationManager.getApplication();
      final ModifiableFacetModel model = application.runReadAction(new Computable<ModifiableFacetModel>() {
        @Override
        public ModifiableFacetModel compute() {
          return FacetManager.getInstance(module).createModifiableModel();
        }
      });
      for (Consumer<ModifiableFacetModel> action : actions.second) {
        action.consume(model);
      }
      application.invokeAndWait(() -> application.runWriteAction(() -> model.commit()), application.getDefaultModalityState());
    }
  }

  private static boolean checkValidity(VirtualFile pluginDir) {
    pluginDir.refresh(false, false);
    return pluginDir.isValid();
  }

  private static Pair<Collection<Consumer<ModifiableRootModel>>, Collection<Consumer<ModifiableFacetModel>>> getUpdateProjectStructureActions(
    Collection<VirtualFile> appRoots,
    MvcProjectStructure structure) {
    for (final VirtualFile appRoot : ModuleRootManager.getInstance(structure.myModule).getContentRoots()) {
      appRoot.refresh(false, false);
    }

    Collection<Consumer<ModifiableRootModel>> actions = ContainerUtil.newArrayList();
    removeInvalidSourceRoots(actions, structure);
    cleanupDefaultLibrary(structure.myModule, actions, appRoots, structure.getUserLibraryName());
    moveupLibrariesFromMavenPlugin(structure.myModule, actions);

    List<VirtualFile> rootsToFacetSetup = new ArrayList<>(appRoots.size());
    for (VirtualFile appRoot : appRoots) {
      if (checkValidity(appRoot)) {
        ContainerUtil.addIfNotNull(actions, addSourceRootsAndLibDirectory(appRoot, structure));
        rootsToFacetSetup.add(appRoot);
      }
    }

    Collection<Consumer<ModifiableFacetModel>> facetActions = ContainerUtil.newArrayList();
    structure.setupFacets(facetActions, rootsToFacetSetup);

    return Pair.create(actions, facetActions);
  }

  @Nullable
  private static OrderEntry[] moveupLibrariesFromMavenPlugin(ModuleRootModel moduleRootModel) {
    LibraryOrderEntry newestLibrary = null;
    int firstLibraryIndex = 0;
    int newestLibraryIndex = 0;

    OrderEntry[] orderEntries = moduleRootModel.getOrderEntries();
    for (int i = 0; i < orderEntries.length; i++) {
      if (orderEntries[i] instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntries[i];
        String libraryName = libraryEntry.getLibraryName();
        if (libraryName != null && libraryName.contains("slf4j-api")) {
          if (newestLibrary == null) {
            newestLibrary = libraryEntry;
            firstLibraryIndex = i;
            newestLibraryIndex = i;
          }
          else {
            if (libraryName.compareTo(newestLibrary.getLibraryName()) > 0) {
              newestLibraryIndex = i;
              newestLibrary = libraryEntry;
            }
          }
        }
      }
    }

    if (firstLibraryIndex == newestLibraryIndex) return null;

    OrderEntry[] res = orderEntries.clone();
    ArrayUtil.swap(res, firstLibraryIndex, newestLibraryIndex);
    return res;
  }

  private static void moveupLibrariesFromMavenPlugin(final Module module, Collection<Consumer<ModifiableRootModel>> actions) {
    if (moveupLibrariesFromMavenPlugin(ModuleRootManager.getInstance(module)) != null) {
      actions.add(modifiableRootModel -> {
        OrderEntry[] orderEntries = moveupLibrariesFromMavenPlugin(modifiableRootModel);
        if (orderEntries != null) {
          modifiableRootModel.rearrangeOrderEntries(orderEntries);
        }
      });
    }
  }

  private static void removeInvalidSourceRoots(Collection<Consumer<ModifiableRootModel>> actions, MvcProjectStructure structure) {
    final Set<SourceFolder> toRemove = ContainerUtil.newTroveSet();
    final Set<String> toRemoveContent = ContainerUtil.newTroveSet();
    for (ContentEntry entry : ModuleRootManager.getInstance(structure.myModule).getContentEntries()) {
      final VirtualFile file = entry.getFile();
      if (file == null || !structure.isValidContentRoot(file)) {
        toRemoveContent.add(entry.getUrl());
      }
      else {
        for (SourceFolder folder : entry.getSourceFolders()) {
          if (folder.getFile() == null) {
            toRemove.add(folder);
          }
        }
      }
    }

    if (!toRemove.isEmpty() || !toRemoveContent.isEmpty()) {
      actions.add(model -> {
        for (ContentEntry entry : model.getContentEntries()) {
          if (toRemoveContent.remove(entry.getUrl())) {
            model.removeContentEntry(entry);
          }
          else {
            for (SourceFolder folder : entry.getSourceFolders()) {
              if (toRemove.remove(folder)) {
                entry.removeSourceFolder(folder);
              }
            }
          }
        }
      });
    }
  }

  public static void cleanupDefaultLibrary(Module module,
                                           Collection<Consumer<ModifiableRootModel>> actions,
                                           Collection<VirtualFile> appRoots,
                                           final String libName) {
    final Library library = findUserLibrary(module, libName);
    if (library == null) {
      return;
    }

    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

    final List<String> toRemoveUrls = new ArrayList<>();

    for (String url : library.getUrls(OrderRootType.CLASSES)) {
      VirtualFile virtualFile = virtualFileManager.findFileByUrl(url);

      if (virtualFile == null) {
        toRemoveUrls.add(url);
      }
      else {
        if (library.isJarDirectory(url)) {
          if (!virtualFile.getName().equals("lib") || !appRoots.contains(virtualFile.getParent())) {
            toRemoveUrls.add(url);
          }
        }
      }
    }

    if (!toRemoveUrls.isEmpty()) {
      actions.add(model -> {
        final Library.ModifiableModel modifiableModel = modifyDefaultLibrary(model, libName);
        for (String url : toRemoveUrls) {
          modifiableModel.removeRoot(url, OrderRootType.CLASSES);
        }
        modifiableModel.commit();
      });
    }
  }

  public static boolean hasModulesWithSupport(Project project, final MvcFramework framework) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (framework.hasSupport(module)) {
        return true;
      }
    }

    return false;
  }

  public static List<Module> getAllModulesWithSupport(Project project, MvcFramework framework) {
    List<Module> modules = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (framework.hasSupport(module)) {
        modules.add(module);
      }
    }
    return modules;
  }

  @Nullable
  private static Library extractNonModuleLibraries(List<Library> result,
                                                   ModuleRootManager rootManager,
                                                   boolean providedOnly,
                                                   String userLibraryName) {
    Library userLibrary = null;

    for (OrderEntry entry : rootManager.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
        Library library = libraryEntry.getLibrary();

        if (library != null) {
          String libraryName = libraryEntry.getLibraryName();
          if (libraryName != null && libraryName.startsWith(userLibraryName)) {
            userLibrary = library;
          }
          else {
            if (library.getTable() != null && (!providedOnly || !libraryEntry.getScope().isForProductionRuntime())) {
              result.add(library);
            }
          }
        }
      }
    }

    return userLibrary;
  }

  private static Set<String> getJarUrls(@Nullable Library library) {
    if (library == null) return Collections.emptySet();

    Set<String> res = new HashSet<>();

    for (String url : library.getUrls(OrderRootType.CLASSES)) {
      if (!library.isJarDirectory(url)) {
        res.add(url);
      }
    }

    return res;
  }

  public static void syncAuxModuleSdk(@NotNull Module appModule, @NotNull Module pluginsModule, @NotNull final MvcFramework framework) {
    final ModuleRootManager auxRootManager = ModuleRootManager.getInstance(pluginsModule);
    final ModuleRootManager appRootManager = ModuleRootManager.getInstance(appModule);

    final boolean isSdkEquals = Comparing.equal(auxRootManager.getSdk(), appRootManager.getSdk());

    List<Library> appLibraries = new ArrayList<>();
    Library appUserLibrary = extractNonModuleLibraries(appLibraries, appRootManager, false, framework.getUserLibraryName());

    List<Library> auxLibraries = new ArrayList<>();
    Library auxUserLibrary = extractNonModuleLibraries(auxLibraries, auxRootManager, false, framework.getUserLibraryName());

    final boolean isLibrariesEquals = appLibraries.equals(auxLibraries) && getJarUrls(auxUserLibrary).equals(getJarUrls(appUserLibrary));

    if (!isSdkEquals || !isLibrariesEquals) {
      ModuleRootModificationUtil.updateModel(pluginsModule, model -> {
        if (!isSdkEquals) {
          copySdk(appRootManager, model);
        }

        if (!isLibrariesEquals) {
          copyUserLibraries(appRootManager, model, framework);
        }
      });
    }
  }

  @Nullable
  public static PropertiesFile findApplicationProperties(@NotNull Module module, MvcFramework framework) {
    VirtualFile root = framework.findAppRoot(module);
    if (root == null) return null;

    VirtualFile appChild = root.findChild(APPLICATION_PROPERTIES);
    if (appChild == null || !appChild.isValid()) return null;

    PsiManager manager = PsiManager.getInstance(module.getProject());
    PsiFile psiFile = manager.findFile(appChild);
    if (psiFile instanceof PropertiesFile) {
      return (PropertiesFile)psiFile;
    }
    return null;
  }

  public static void removeAuxiliaryModule(Module toRemove) {
    List<ModifiableRootModel> usingModels = new SmartList<>();

    Project project = toRemove.getProject();
    ModuleManager moduleManager = ModuleManager.getInstance(project);

    for (Module module : moduleManager.getModules()) {
      if (module == toRemove) {
        continue;
      }

      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry && toRemove == ((ModuleOrderEntry)entry).getModule()) {
          usingModels.add(moduleRootManager.getModifiableModel());
          break;
        }
      }
    }

    final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();

    ModuleDeleteProvider.removeModule(toRemove, null, usingModels, moduleModel);

    ModifiableRootModel[] rootModels = usingModels.toArray(new ModifiableRootModel[usingModels.size()]);
    ModifiableModelCommitter.multiCommit(rootModels, moduleModel);
  }

  @NotNull
  public static Module createAuxiliaryModule(@NotNull Module appModule, final String moduleName, final MvcFramework framework) {
    ModuleManager moduleManager = ModuleManager.getInstance(appModule.getProject());
    final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
    final String moduleFilePath = new File(appModule.getModuleFilePath()).getParent() + "/" + moduleName + ".iml";
    final VirtualFile existing = LocalFileSystem.getInstance().findFileByPath(moduleFilePath);
    if (existing != null) {
      try {
        existing.delete("Grails/Griffon plugins maintenance");
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    moduleModel.newModule(moduleFilePath, StdModuleTypes.JAVA.getId());
    moduleModel.commit();

    Module pluginsModule = moduleManager.findModuleByName(moduleName);
    assert pluginsModule != null;

    ModifiableRootModel newRootModel = ModuleRootManager.getInstance(pluginsModule).getModifiableModel();
    ModifiableRootModel appModel = ModuleRootManager.getInstance(appModule).getModifiableModel();

    copySdkAndLibraries(appModel, newRootModel, framework);

    newRootModel.commit();
    appModel.commit();

    return pluginsModule;
  }

  public static void ensureDependency(@NotNull Module from, @NotNull Module to, boolean exported) {
    if (!from.equals(to) && !hasDependency(from, to)) {
      ModuleRootModificationUtil.addDependency(from, to, DependencyScope.COMPILE, exported);
    }
  }

  public static boolean hasDependency(@NotNull Module from, @NotNull Module to) {
    for (OrderEntry entry : ModuleRootManager.getInstance(from).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        if (to == moduleOrderEntry.getModule()) {
          return true;
        }
      }
    }
    return false;
  }

  public static void removeDependency(@NotNull Module from, @NotNull Module to) {
    if (!from.equals(to) && hasDependency(from, to)) {
      final ModifiableRootModel fromModel = ModuleRootManager.getInstance(from).getModifiableModel();
      for (OrderEntry entry : fromModel.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
          if (to == moduleOrderEntry.getModule()) {
            fromModel.removeOrderEntry(moduleOrderEntry);
          }
        }
      }
      fromModel.commit();
    }
  }

  public static void copySdk(ModuleRootModel from, ModifiableRootModel to) {
    if (from.isSdkInherited()) {
      to.inheritSdk();
    }
    else {
      to.setSdk(from.getSdk());
    }
  }

  public static void copySdkAndLibraries(ModuleRootModel from, ModifiableRootModel to, @NotNull MvcFramework framework) {
    copySdk(from, to);
    copyUserLibraries(from, to, framework);
  }

  public static void copyUserLibraries(ModuleRootModel from, ModifiableRootModel to, @NotNull MvcFramework framework) {
    Library userLibraryTo = null;

    for (OrderEntry entry : to.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
        Library library = libraryEntry.getLibrary();

        if (library != null) {
          String libraryName = libraryEntry.getLibraryName();
          if (libraryName != null && libraryName.startsWith(framework.getUserLibraryName())) {
            userLibraryTo = library;
          }
          else {
            if (library.getTable() != null && (!libraryEntry.getScope().isForProductionRuntime() || framework.isSDKLibrary(library))) {
              to.removeOrderEntry(entry);
            }
          }
        }
      }
    }

    Library userLibraryFrom = null;

    for (OrderEntry entry : from.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
        Library library = libraryEntry.getLibrary();

        if (library != null) {
          String libraryName = library.getName();
          if (libraryName != null && libraryName.startsWith(framework.getUserLibraryName())) {
            userLibraryFrom = library;
          }
          else {
            if (library.getTable() != null) {
              LibraryOrderEntry libraryOrderEntry = to.addLibraryEntry(library);
              libraryOrderEntry.setScope(DependencyScope.PROVIDED);
            }
          }
        }
      }
    }

    if (userLibraryTo == null) {
      if (userLibraryFrom == null) return;

      userLibraryTo = to.getModuleLibraryTable().createLibrary(framework.getUserLibraryName() + " (" + to.getModule().getName() + ')');
    }
    else {
      OrderEntry[] orderEntries = to.getOrderEntries().clone();

      for (int i = 0; i < orderEntries.length; i++) {
        OrderEntry orderEntry = orderEntries[i];
        if (orderEntry instanceof LibraryOrderEntry) {
          if (userLibraryTo == ((LibraryOrderEntry)orderEntry).getLibrary()) {
            System.arraycopy(orderEntries, i + 1, orderEntries, i, orderEntries.length - i - 1);
            orderEntries[orderEntries.length - 1] = orderEntry;
            to.rearrangeOrderEntries(orderEntries);
            break;
          }
        }
      }
    }

    Library.ModifiableModel model = userLibraryTo.getModifiableModel();
    for (String url : model.getUrls(OrderRootType.CLASSES)) {
      if (!model.isJarDirectory(url)) {
        model.removeRoot(url, OrderRootType.CLASSES);
      }
    }

    if (userLibraryFrom != null) {
      for (String url : userLibraryFrom.getUrls(OrderRootType.CLASSES)) {
        if (!userLibraryFrom.isJarDirectory(url)) {
          model.addRoot(url, OrderRootType.CLASSES);
        }
      }
    }

    model.commit();
  }

  public static Consumer<ModifiableRootModel> removeStaleContentEntries(final Collection<VirtualFile> pluginDirs) {
    return modifiableRootModel -> {
      for (final ContentEntry entry : modifiableRootModel.getContentEntries()) {
        if (!pluginDirs.contains(entry.getFile())) {
          modifiableRootModel.removeContentEntry(entry);
        }
      }
    };
  }

  public static void updateAuxModuleStructure(Module auxModule, Collection<VirtualFile> pluginDirs, MvcFramework framework) {
    final MvcProjectStructure structure = framework.createProjectStructure(auxModule, true);
    Pair<Collection<Consumer<ModifiableRootModel>>, Collection<Consumer<ModifiableFacetModel>>> actions =
      getUpdateProjectStructureActions(pluginDirs, structure);
    for (final ContentEntry root : ModuleRootManager.getInstance(auxModule).getContentEntries()) {
      if (!pluginDirs.contains(root.getFile())) {
        actions.first.add(removeStaleContentEntries(pluginDirs));
        break;
      }
    }

    if (!actions.first.isEmpty()) {
      actions.first.add(exportDefaultLibrary(structure.getUserLibraryName()));
    }

    if (!actions.first.isEmpty()) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(auxModule).getModifiableModel();
      for (final Consumer<ModifiableRootModel> pluginsUpdateAction : actions.first) {
        pluginsUpdateAction.consume(model);
      }
      model.commit();
    }

    if (!actions.second.isEmpty()) {
      final ModifiableFacetModel model = FacetManager.getInstance(auxModule).createModifiableModel();
      for (Consumer<ModifiableFacetModel> action : actions.second) {
        action.consume(model);
      }
      model.commit();
    }
  }

  public static Consumer<ModifiableRootModel> exportDefaultLibrary(final String libraryName) {
    return modifiableRootModel -> {
      for (final OrderEntry entry : modifiableRootModel.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
          String lName = libraryOrderEntry.getLibraryName();
          if (lName != null && lName.startsWith(libraryName)) {
            libraryOrderEntry.setExported(true);
          }
        }
      }
    };
  }

  private static boolean hasChildDirectory(VirtualFile file) {
    for (VirtualFile virtualFile : file.getChildren()) {
      if (virtualFile.isDirectory()) return true;
    }

    return false;
  }

  public static void updateGlobalPluginModule(@NotNull Project project, @NotNull MvcFramework framework) {
    MultiMap<VirtualFile, Module> map = new MultiMap<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (framework.hasSupport(module)) {
        VirtualFile globalPluginsDir = refreshAndFind(framework.getGlobalPluginsDir(module));
        if (globalPluginsDir != null && hasChildDirectory(globalPluginsDir)) {
          map.putValue(globalPluginsDir, module);
        }
      }
    }

    Map<VirtualFile, Module> globalAuxModules = new HashMap<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (framework.isGlobalPluginModule(module)) {
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

        VirtualFile parent = null;

        if (contentRoots.length > 0) {
          parent = contentRoots[0].getParent();
          if (!map.containsKey(parent)) {
            parent = null;
          }
          else {
            for (int i = 1; i < contentRoots.length; i++) {
              if (!Comparing.equal(parent, contentRoots[i].getParent())) {
                parent = null;
                break;
              }
            }
          }
        }

        if (parent == null) {
          removeAuxiliaryModule(module);
        }
        else {
          globalAuxModules.put(parent, module);
        }
      }
    }

    for (VirtualFile virtualFile : map.keySet()) {
      if (!globalAuxModules.containsKey(virtualFile)) {
        Module appModule = map.get(virtualFile).iterator().next();
        Module module =
          createAuxiliaryModule(appModule, generateUniqueModuleName(project, framework.getGlobalPluginsModuleName()), framework);
        globalAuxModules.put(virtualFile, module);
      }
    }

    assert map.size() == globalAuxModules.size();

    for (VirtualFile virtualFile : map.keySet()) {
      List<VirtualFile> pluginRoots = new ArrayList<>();

      for (VirtualFile child : virtualFile.getChildren()) {
        if (child.isDirectory()) {
          pluginRoots.add(child);
        }
      }

      assert !pluginRoots.isEmpty();

      Module auxModule = globalAuxModules.get(virtualFile);

      updateAuxModuleStructure(auxModule, pluginRoots, framework);

      for (Module appModule : map.get(virtualFile)) {
        ensureDependency(appModule, auxModule, false);

        Module commonPluginsModule = framework.findCommonPluginsModule(appModule);
        if (commonPluginsModule != null) {
          ensureDependency(commonPluginsModule, auxModule, false);
        }
      }
    }
  }

  private static String generateUniqueModuleName(@NotNull Project project, String prefix) {
    ModuleManager manager = ModuleManager.getInstance(project);
    int i = 0;
    do {
      String res = i == 0 ? prefix : prefix + i;
      i++;

      if (manager.findModuleByName(res) == null) return res;
    }
    while (true);
  }

  @Nullable
  public static Module updateAuxiliaryPluginsModuleRoots(Module appModule, MvcFramework framework) {
    Module commonPluginsModule = framework.findCommonPluginsModule(appModule);

    Set<VirtualFile> pluginRoots = new HashSet<>();

    VirtualFile globalPluginsDir = refreshAndFind(framework.getGlobalPluginsDir(appModule));

    for (VirtualFile pluginRoot : framework.getCommonPluginRoots(appModule, true)) {
      if (checkValidity(pluginRoot)) {
        if (globalPluginsDir == null || !VfsUtil.isAncestor(globalPluginsDir, pluginRoot, true)) {
          pluginRoots.add(pluginRoot);
        }
      }
    }

    if (pluginRoots.isEmpty()) {
      if (commonPluginsModule != null) {
        removeAuxiliaryModule(commonPluginsModule);
      }
      return null;
    }

    if (commonPluginsModule == null) {
      commonPluginsModule = createAuxiliaryModule(appModule, framework.getCommonPluginsModuleName(appModule), framework);
    }

    ensureDependency(appModule, commonPluginsModule, false);
    updateAuxModuleStructure(commonPluginsModule, pluginRoots, framework);

    return commonPluginsModule;
  }

  public static Library findUserLibrary(@NotNull Module module, @NotNull final String name) {
    CommonProcessors.FindProcessor<Library> processor = new CommonProcessors.FindProcessor<Library>() {
      @Override
      protected boolean accept(Library library) {
        String libraryName = library.getName();
        return libraryName != null && libraryName.startsWith(name);
      }
    };

    OrderEnumerator.orderEntries(module).forEachLibrary(processor);

    return processor.getFoundValue();
  }

  @Nullable
  public static VirtualFile refreshAndFind(@Nullable File file) {
    return findFile(file, true);
  }

  @Nullable
  public static VirtualFile findFile(@Nullable File file, boolean refresh) {
    if (file == null) return null;

    if (refresh) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    }
    else {
      return LocalFileSystem.getInstance().findFileByIoFile(file);
    }
  }

  public static boolean isEnabledStructureUpdate() {
    return !Boolean.parseBoolean(System.getProperty("grails.disable.structure.update"));
  }
}
