/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ModuleUtilCore {
  public static final Key<Module> KEY_MODULE = new Key<Module>("Module");

  public static boolean projectContainsFile(final Project project, VirtualFile file, boolean isLibraryElement) {
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    if (isLibraryElement) {
      List<OrderEntry> orders = projectFileIndex.getOrderEntriesForFile(file);
      for(OrderEntry orderEntry:orders) {
        if (orderEntry instanceof ModuleJdkOrderEntry || orderEntry instanceof JdkOrderEntry ||
            orderEntry instanceof LibraryOrderEntry) {
          return true;
        }
      }
      return false;
    }
    else {
      return projectFileIndex.isInContent(file);
    }
  }

  public static String getModuleNameInReadAction(@NotNull final Module module) {
    return new ReadAction<String>(){
      @Override
      protected void run(@NotNull final Result<String> result) throws Throwable {
        result.setResult(module.getName());
      }
    }.execute().getResultObject();
  }

  public static boolean isModuleDisposed(PsiElement element) {
    if (!element.isValid()) return true;
    final Project project = element.getProject();
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    final PsiFile file = element.getContainingFile();
    if (file == null) return true;
    VirtualFile vFile = file.getVirtualFile();
    final Module module = vFile == null ? null : projectFileIndex.getModuleForFile(vFile);
    // element may be in library
    return module == null ? !projectFileIndex.isInLibraryClasses(vFile) : module.isDisposed();
  }

  @Nullable
  public static Module findModuleForFile(@NotNull VirtualFile file, @NotNull Project project) {
    return ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(file);
  }

  @Nullable
  public static Module findModuleForPsiElement(@NotNull PsiElement element) {
    if (!element.isValid()) {
      return null;
    }
    PsiFile containingFile = element.getContainingFile();
    if (containingFile != null && !containingFile.isValid()) return null;

    Project project = (containingFile == null ? element : containingFile).getProject();
    if (project.isDefault()) return null;
    final ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);

    if (element instanceof PsiFileSystemItem && (!(element instanceof PsiFile) || element.getContext() == null)) {
      VirtualFile vFile = ((PsiFileSystemItem)element).getVirtualFile();
      if (vFile == null) {
        vFile = containingFile == null ? null : containingFile.getOriginalFile().getVirtualFile();
        if (vFile == null) {
          return element.getUserData(KEY_MODULE);
        }
      }
      if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
        final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.isEmpty()) {
          return null;
        }
        if (orderEntries.size() == 1) {
          return orderEntries.get(0).getOwnerModule();
        }
        Set<Module> modules = new HashSet<Module>();
        for (OrderEntry orderEntry : orderEntries) {
          modules.add(orderEntry.getOwnerModule());
        }
        final Module[] candidates = modules.toArray(new Module[modules.size()]);
        Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
        return candidates[0];
      }
      return fileIndex.getModuleForFile(vFile);
    }
    if (containingFile != null) {
      PsiElement context;
      while ((context = containingFile.getContext()) != null) {
        final PsiFile file = context.getContainingFile();
        if (file == null) break;
        containingFile = file;
      }

      if (containingFile.getUserData(KEY_MODULE) != null) {
        return containingFile.getUserData(KEY_MODULE);
      }

      final PsiFile originalFile = containingFile.getOriginalFile();
      if (originalFile.getUserData(KEY_MODULE) != null) {
        return originalFile.getUserData(KEY_MODULE);
      }

      final VirtualFile virtualFile = originalFile.getVirtualFile();
      if (virtualFile != null) {
        return fileIndex.getModuleForFile(virtualFile);
      }
    }

    return element.getUserData(KEY_MODULE);
  }

  //ignores export flag
  public static void getDependencies(@NotNull Module module, Set<Module> modules) {
    if (modules.contains(module)) return;
    modules.add(module);
    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (Module dependency : dependencies) {
      getDependencies(dependency, modules);
    }
  }

  /**
   * collect transitive module dependants
   * @param module to find dependencies on
   * @param result resulted set
   */
  public static void collectModulesDependsOn(@NotNull final Module module, final Set<Module> result) {
    if (result.contains(module)) return;
    result.add(module);
    final ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
    final List<Module> dependentModules = moduleManager.getModuleDependentModules(module);
    for (final Module dependentModule : dependentModules) {
      final OrderEntry[] orderEntries = ModuleRootManager.getInstance(dependentModule).getOrderEntries();
      for (OrderEntry o : orderEntries) {
        if (o instanceof ModuleOrderEntry) {
          final ModuleOrderEntry orderEntry = (ModuleOrderEntry)o;
          if (orderEntry.getModule() == module) {
            if (orderEntry.isExported()) {
              collectModulesDependsOn(dependentModule, result);
            } else {
              result.add(dependentModule);
            }
            break;
          }
        }
      }
    }
  }

  @NotNull
  public static List<Module> getAllDependentModules(@NotNull Module module) {
    final ArrayList<Module> list = new ArrayList<Module>();
    final Graph<Module> graph = ModuleManager.getInstance(module.getProject()).moduleGraph();
    for (Iterator<Module> i = graph.getOut(module); i.hasNext();) {
      list.add(i.next());
    }
    return list;
  }

  public static boolean visitMeAndDependentModules(@NotNull final Module module, final ModuleVisitor visitor) {
    if (!visitor.visit(module)) {
      return false;
    }
    final List<Module> list = getAllDependentModules(module);
    for (Module dependentModule : list) {
      if (!visitor.visit(dependentModule)) {
        return false;
      }
    }
    return true;
  }

  public static boolean moduleContainsFile(@NotNull final Module module, VirtualFile file, boolean isLibraryElement) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    if (isLibraryElement) {
      OrderEntry orderEntry = moduleRootManager.getFileIndex().getOrderEntryForFile(file);
      return orderEntry instanceof ModuleJdkOrderEntry || orderEntry instanceof JdkOrderEntry ||
             orderEntry instanceof LibraryOrderEntry;
    }
    else {
      return moduleRootManager.getFileIndex().isInContent(file);
    }
  }

  public static boolean isModuleFile(@NotNull Module module, @NotNull VirtualFile file) {
    return FileUtil.namesEqual(file.getPath(), module.getModuleFilePath());
  }

  public static boolean isModuleDir(@NotNull Module module, @NotNull VirtualFile dir) {
    return FileUtil.namesEqual(dir.getPath(), getModuleDirPath(module));
  }

  @NotNull
  public static String getModuleDirPath(@NotNull Module module) {
    return PathUtilRt.getParentPath(module.getModuleFilePath());
  }

  public interface ModuleVisitor {
    /**
     *
     * @param module module to be visited.
     * @return false to stop visiting.
     */
    boolean visit(final Module module);
  }
}
