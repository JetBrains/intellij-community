// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class LibrariesUtil {
  public static final String SOME_GROOVY_CLASS = "org.codehaus.groovy.control.CompilationUnit";
  @NlsSafe private static final String LIB = "lib";
  @NlsSafe private static final String EMBEDDABLE = "embeddable";

  private LibrariesUtil() {
  }

  public static Library[] getLibrariesByCondition(final Module module, final Condition<? super Library> condition) {
    if (module == null) return Library.EMPTY_ARRAY;
    final ArrayList<Library> libraries = new ArrayList<>();

    ApplicationManager.getApplication().runReadAction(() -> populateOrderEntries(module, condition, libraries, false, new HashSet<>()));

    return libraries.toArray(Library.EMPTY_ARRAY);
  }

  private static void populateOrderEntries(@NotNull Module module, Condition<? super Library> condition, ArrayList<? super Library> libraries, boolean exportedOnly, Set<? super Module> visited) {
    if (!visited.add(module)) {
      return;
    }

    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry libEntry) {
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

  public static Library[] getGlobalLibraries(Condition<? super Library> condition) {
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    List<Library> libs = ContainerUtil.findAll(table.getLibraries(), condition);
    return libs.toArray(Library.EMPTY_ARRAY);
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
          if ((LIB.equals(parent.getName()) || EMBEDDABLE.equals(parent.getName())) && parent.getParent() != null) {
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
            if (LIB.equals(parentFile.getName())) {
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
        if (EMBEDDABLE.equals(parent.getName()) || LIB.equals(parent.getName())) {
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
      System.arraycopy(order, insertionPoint, order, insertionPoint + 1, order.length - 1 - insertionPoint);
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
