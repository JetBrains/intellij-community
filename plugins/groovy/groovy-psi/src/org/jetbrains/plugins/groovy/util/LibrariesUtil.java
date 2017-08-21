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

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public class LibrariesUtil {
  public static final String SOME_GROOVY_CLASS = "org.codehaus.groovy.control.CompilationUnit";

  private LibrariesUtil() {
  }

  public static Library[] getLibrariesByCondition(final Module module, final Condition<Library> condition) {
    if (module == null) return Library.EMPTY_ARRAY;
    final ArrayList<Library> libraries = new ArrayList<>();

    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      populateOrderEntries(module, condition, libraries, false, new THashSet<>());
    }
    finally {
      accessToken.finish();
    }

    return libraries.toArray(new Library[libraries.size()]);
  }

  private static void populateOrderEntries(@NotNull Module module, Condition<Library> condition, ArrayList<Library> libraries, boolean exportedOnly, Set<Module> visited) {
    if (!visited.add(module)) {
      return;
    }

    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry)entry;
        if (exportedOnly && !libEntry.isExported()) {
          continue;
        }

        Library library = libEntry.getLibrary();
        if (condition.value(library)) {
          libraries.add(library);
        }
      }
      else if (entry instanceof ModuleOrderEntry) {
        final Module dep = ((ModuleOrderEntry)entry).getModule();
        if (dep != null) {
          populateOrderEntries(dep, condition, libraries, true, visited);
        }
      }
    }
  }

  public static Library[] getGlobalLibraries(Condition<Library> condition) {
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    List<Library> libs = ContainerUtil.findAll(table.getLibraries(), condition);
    return libs.toArray(new Library[libs.size()]);
  }

  @NotNull
  public static String getGroovyLibraryHome(Library library) {
    final VirtualFile[] classRoots = library.getFiles(OrderRootType.CLASSES);
    final String home = getGroovyLibraryHome(classRoots);
    return home == null ? "" : home;
  }

  public static boolean hasGroovySdk(@Nullable Module module) {
    return module != null && getGroovyHomePath(module) != null;
  }

  @Nullable
  public static VirtualFile findJarWithClass(@NotNull Module module, final String classQName) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    for (PsiClass psiClass : JavaPsiFacade.getInstance(module.getProject()).findClasses(classQName, scope)) {
      VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
      final VirtualFile local = getLocalFor(virtualFile);
      if (local != null) {
        return local;
      }
    }
    return null;
  }

  private static VirtualFile getLocalFor(VirtualFile virtualFile) {
    if (virtualFile != null) {
      VirtualFileSystem fileSystem = virtualFile.getFileSystem();
      if (fileSystem instanceof ArchiveFileSystem) {
        return ((ArchiveFileSystem)fileSystem).getLocalByEntry(virtualFile);
      }
    }

    return null;
  }

  @Nullable
  public static String getGroovyHomePath(@NotNull Module module) {
    if (!DumbService.isDumb(module.getProject())) {
      final VirtualFile local = findJarWithClass(module, SOME_GROOVY_CLASS);
      if (local != null) {
        final VirtualFile parent = local.getParent();
        if (parent != null) {
          if (("lib".equals(parent.getName()) || "embeddable".equals(parent.getName())) && parent.getParent() != null) {
            return parent.getParent().getPath();
          }
          return parent.getPath();
        }
      }
    }

    final String home = getGroovyLibraryHome(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots());
    return StringUtil.isEmpty(home) ? null : home;
  }

  @Nullable
  private static String getGroovySdkHome(VirtualFile[] classRoots) {
    for (VirtualFile file : classRoots) {
      final String name = file.getName();
      if (GroovyConfigUtils.GROOVY_JAR_PATTERN.matcher(name).matches()) {
        String jarPath = file.getPresentableUrl();
        File realFile = new File(jarPath);
        if (realFile.exists()) {
          File parentFile = realFile.getParentFile();
          if (parentFile != null) {
            if ("lib".equals(parentFile.getName())) {
              return parentFile.getParent();
            }
            return parentFile.getPath();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getEmbeddableGroovyJar(VirtualFile[] classRoots) {
    for (VirtualFile file : classRoots) {
      final String name = file.getName();
      if (GroovyConfigUtils.matchesGroovyAll(name)) {
        String jarPath = file.getPresentableUrl();
        File realFile = new File(jarPath);
        if (realFile.exists()) {
          return realFile.getPath();
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getGroovyLibraryHome(VirtualFile[] classRoots) {
    final String sdkHome = getGroovySdkHome(classRoots);
    if (sdkHome != null) {
      return sdkHome;
    }

    final String embeddable = getEmbeddableGroovyJar(classRoots);
    if (embeddable != null) {
      final File emb = new File(embeddable);
      if (emb.exists()) {
        final File parent = emb.getParentFile();
        if ("embeddable".equals(parent.getName()) || "lib".equals(parent.getName())) {
          return parent.getParent();
        }
        return parent.getPath();
      }
    }
    return null;
  }

  @NotNull
  public static VirtualFile getLocalFile(@NotNull VirtualFile libFile) {
    VirtualFile local = getLocalFor(libFile);
    if (local != null) {
      return local;
    }
    return libFile;
  }

  public static void placeEntryToCorrectPlace(ModifiableRootModel model, LibraryOrderEntry addedEntry) {
    final OrderEntry[] order = model.getOrderEntries();
    //place library after module sources
    assert order[order.length - 1] == addedEntry;
    int insertionPoint = -1;
    for (int i = 0; i < order.length - 1; i++) {
      if (order[i] instanceof ModuleSourceOrderEntry) {
        insertionPoint = i + 1;
        break;
      }
    }
    if (insertionPoint >= 0) {
      for (int i = order.length - 1; i > insertionPoint; i--) {
        order[i] = order[i - 1];
      }
      order[insertionPoint] = addedEntry;
      model.rearrangeOrderEntries(order);
    }
  }

  public static File[] getFilesInDirectoryByPattern(String dirPath, final Pattern pattern) {
    File distDir = new File(dirPath);
    File[] files = distDir.listFiles((dir, name) -> pattern.matcher(name).matches());
    return files != null ? files : new File[0];
  }
}
