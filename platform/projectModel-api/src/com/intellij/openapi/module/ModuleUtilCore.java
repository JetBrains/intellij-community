// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.PathUtilRt;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.Graph;
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceModelLegacyBridge;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.platform.workspace.jps.entities.ExtensionsKt.collectTransitivelyDependentModules;

public class ModuleUtilCore {
  public static final Key<Module> KEY_MODULE = new Key<>("Module");

  @ApiStatus.Internal
  protected ModuleUtilCore() {
  }

  public static boolean projectContainsFile(@NotNull Project project, @NotNull VirtualFile file, boolean isLibraryElement) {
    ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
    if (isLibraryElement) {
      List<OrderEntry> orders = projectFileIndex.getOrderEntriesForFile(file);
      for (OrderEntry orderEntry : orders) {
        if (orderEntry instanceof JdkOrderEntry || orderEntry instanceof LibraryOrderEntry) {
          return true;
        }
      }
      return false;
    }
    else {
      return projectFileIndex.isInContent(file);
    }
  }

  public static @NotNull String getModuleNameInReadAction(@NotNull Module module) {
    return ReadAction.compute(module::getName);
  }

  public static boolean isModuleDisposed(@NotNull PsiElement element) {
    if (!element.isValid()) return true;
    Project project = element.getProject();
    ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
    PsiFile file = element.getContainingFile();
    if (file == null) return true;
    VirtualFile vFile = file.getVirtualFile();
    Module module = vFile == null ? null : projectFileIndex.getModuleForFile(vFile);
    // element may be in library
    return module == null ? !projectFileIndex.isInLibraryClasses(vFile) : module.isDisposed();
  }

  /**
   * @return module where {@code containingFile} is located,
   * null for project files outside module content roots or library files
   */
  public static @Nullable Module findModuleForFile(@Nullable PsiFile containingFile) {
    if (containingFile != null) {
      VirtualFile vFile = containingFile.getVirtualFile();
      if (vFile != null) {
        return findModuleForFile(vFile, containingFile.getProject());
      }
    }
    return null;
  }

  /**
   * @return module where {@code file} is located,
   * null for project files outside module content roots or library files
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable Module findModuleForFile(@NotNull VirtualFile file, @NotNull Project project) {
    if (project.isDefault()) {
      return null;
    }
    return ReadAction.compute(() -> ProjectFileIndex.getInstance(project).getModuleForFile(file));
  }

  /**
   * Return module where containing file of the {@code element} is located.
   * <br>
   * For {@link com.intellij.psi.PsiDirectory}, corresponding virtual file is checked directly.
   * If this virtual file belongs to a library or SDK and this library/SDK is attached to exactly one module, then this module will be returned.
   */
  public static @Nullable Module findModuleForPsiElement(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    PsiElement highestPsi = containingFile == null ? element : containingFile;
    if (!highestPsi.isValid()) {
      return null;
    }
    Project project = highestPsi.getProject();
    if (project.isDefault()) return null;
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

    if (element instanceof PsiFileSystemItem && (!(element instanceof PsiFile) || element.getContext() == null)) {
      VirtualFile vFile = ((PsiFileSystemItem)element).getVirtualFile();
      if (vFile == null) {
        vFile = containingFile == null ? null : containingFile.getOriginalFile().getVirtualFile();
        if (vFile == null) {
          return element.getUserData(KEY_MODULE);
        }
      }

      if (fileIndex.isInLibrary(vFile)) {
        List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.isEmpty()) {
          return null;
        }

        if (orderEntries.size() == 1 && orderEntries.get(0) instanceof LibraryOrSdkOrderEntry) {
          return orderEntries.get(0).getOwnerModule();
        }

        Optional<Module> module = orderEntries
          .stream()
          .filter(entry -> entry instanceof LibraryOrSdkOrderEntry)
          .map(OrderEntry::getOwnerModule)
          .min(ModuleManager.getInstance(project).moduleDependencyComparator());
        //there may be no LibraryOrSdkOrderEntry if the file is located under both module source root and a library root
        if (module.isPresent()) {
          return module.get();
        }
      }

      return fileIndex.getModuleForFile(vFile);
    }
    if (containingFile != null) {
      PsiElement context;
      while ((context = containingFile.getContext()) != null) {
        PsiFile file = context.getContainingFile();
        if (file == null) break;
        containingFile = file;
      }

      if (containingFile.getUserData(KEY_MODULE) != null) {
        return containingFile.getUserData(KEY_MODULE);
      }

      PsiFile originalFile = containingFile.getOriginalFile();
      if (originalFile.getUserData(KEY_MODULE) != null) {
        return originalFile.getUserData(KEY_MODULE);
      }

      VirtualFile virtualFile = originalFile.getVirtualFile();
      if (virtualFile != null) {
        return fileIndex.getModuleForFile(virtualFile);
      }
    }

    return element.getUserData(KEY_MODULE);
  }

  //ignores export flag
  public static void getDependencies(@NotNull Module module, @NotNull Set<? super Module> modules) {
    if (modules.contains(module)) return;
    modules.add(module);
    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (Module dependency : dependencies) {
      getDependencies(dependency, modules);
    }
  }

  /**
   * <h3>Obsolescence notice</h3>
   * This method uses
   * {@link com.intellij.platform.workspace.jps.entities.ExtensionsKt#collectTransitivelyDependentModules(ModuleEntity, EntityStorage)},
   * and remains for compatibility. 
   * <p>
   *   
   * Collect transitive dependent modules.
   *
   * @param module to find dependencies on
   * @param result resulted set
   */
  @ApiStatus.Obsolete(since = "2025.1")
  public static void collectModulesDependsOn(@NotNull Module module, @NotNull Set<? super Module> result) {
    var project = module.getProject();
    var legacyBridge = project.getService(WorkspaceModelLegacyBridge.class);
    var moduleEntity = legacyBridge.findModuleEntity(module);
    if (moduleEntity == null) return; // error?

    var tmpSet = collectTransitivelyDependentModules(moduleEntity, WorkspaceModel.getInstance(project).getCurrentSnapshot());
    ProgressManager.checkCanceled();
    for (var dependentModule : tmpSet) {
      var legacyModule = legacyBridge.findLegacyModule(dependentModule);
      if (legacyModule != null)
        result.add(legacyModule);
    }
  }

  public static @NotNull List<Module> getAllDependentModules(@NotNull Module module) {
    List<Module> list = new ArrayList<>();
    Graph<Module> graph = ModuleManager.getInstance(module.getProject()).moduleGraph();
    for (Iterator<Module> i = graph.getOut(module); i.hasNext(); ) {
      list.add(i.next());
    }
    return list;
  }

  public static boolean visitMeAndDependentModules(@NotNull Module module, @NotNull ModuleVisitor visitor) {
    if (!visitor.visit(module)) {
      return false;
    }
    List<Module> list = getAllDependentModules(module);
    for (Module dependentModule : list) {
      if (!visitor.visit(dependentModule)) {
        return false;
      }
    }
    return true;
  }

  public static boolean moduleContainsFile(@NotNull Module module, @NotNull VirtualFile file, boolean isLibraryElement) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    if (isLibraryElement) {
      OrderEntry orderEntry = moduleRootManager.getFileIndex().getOrderEntryForFile(file);
      return orderEntry instanceof JdkOrderEntry || orderEntry instanceof LibraryOrderEntry;
    }
    else {
      return moduleRootManager.getFileIndex().isInContent(file);
    }
  }

  public static boolean isModuleFile(@NotNull Module module, @NotNull VirtualFile file) {
    return VfsUtilCore.pathEqualsTo(file, module.getModuleFilePath());
  }

  public static boolean isModuleDir(@NotNull Module module, @NotNull VirtualFile dir) {
    return VfsUtilCore.pathEqualsTo(dir, getModuleDirPath(module));
  }

  public static @NotNull String getModuleDirPath(@NotNull Module module) {
    return PathUtilRt.getParentPath(module.getModuleFilePath());
  }

  @FunctionalInterface
  public interface ModuleVisitor {
    /**
     * @param module module to be visited.
     * @return false to stop visiting.
     */
    boolean visit(@NotNull Module module);
  }
}
