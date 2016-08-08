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

package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class LibraryUtil {
  private LibraryUtil() {
  }

  public static boolean isClassAvailableInLibrary(final Library library, final String fqn) {
    return isClassAvailableInLibrary(library.getFiles(OrderRootType.CLASSES), fqn);
  }

  public static boolean isClassAvailableInLibrary(VirtualFile[] files, final String fqn) {
    return isClassAvailableInLibrary(Arrays.asList(files), fqn);
  }

  public static boolean isClassAvailableInLibrary(List<VirtualFile> files, final String fqn) {
    for (VirtualFile file : files) {
      if (findInFile(file, new StringTokenizer(fqn, "."))) return true;
    }
    return false;
  }

  @Nullable
  public static Library findLibraryByClass(final String fqn, @Nullable Project project) {
    if (project != null) {
      final LibraryTable projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
      Library library = findInTable(projectTable, fqn);
      if (library != null) {
        return library;
      }
    }
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    return findInTable(table, fqn);
  }


  private static boolean findInFile(VirtualFile file, final StringTokenizer tokenizer) {
    if (!tokenizer.hasMoreTokens()) return true;
    @NonNls StringBuilder name = new StringBuilder(tokenizer.nextToken());
    if (!tokenizer.hasMoreTokens()) {
      name.append(".class");
    }
    final VirtualFile child = file.findChild(name.toString());
    return child != null && findInFile(child, tokenizer);
  }

  @Nullable
  private static Library findInTable(LibraryTable table, String fqn) {
    for (Library library : table.getLibraries()) {
      if (isClassAvailableInLibrary(library, fqn)) {
        return library;
      }
    }
    return null;
  }

  public static Library createLibrary(final LibraryTable libraryTable, @NonNls final String baseName) {
    String name = baseName;
    int count = 2;
    while (libraryTable.getLibraryByName(name) != null) {
      name = baseName + " (" + count++ + ")";
    }
    return libraryTable.createLibrary(name);
  }

  public static VirtualFile[] getLibraryRoots(final Project project) {
    return getLibraryRoots(project, true, true);
  }

  public static VirtualFile[] getLibraryRoots(final Project project, final boolean includeSourceFiles, final boolean includeJdk) {
    return getLibraryRoots(ModuleManager.getInstance(project).getModules(), includeSourceFiles, includeJdk);
  }

  public static VirtualFile[] getLibraryRoots(final Module[] modules, final boolean includeSourceFiles, final boolean includeJdk) {
    Set<VirtualFile> roots = new HashSet<>();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrderEntry){
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            VirtualFile[] files = includeSourceFiles ? library.getFiles(OrderRootType.SOURCES) : null;
            if (files == null || files.length == 0) {
              files = library.getFiles(OrderRootType.CLASSES);
            }
            ContainerUtil.addAll(roots, files);
          }
        } else if (includeJdk && entry instanceof JdkOrderEntry) {
          JdkOrderEntry jdkEntry = (JdkOrderEntry)entry;
          VirtualFile[] files = includeSourceFiles ? jdkEntry.getRootFiles(OrderRootType.SOURCES) : null;
          if (files == null || files.length == 0) {
            files = jdkEntry.getRootFiles(OrderRootType.CLASSES);
          }
          ContainerUtil.addAll(roots, files);
        }
      }
    }
    return VfsUtilCore.toVirtualFileArray(roots);
  }

  @Nullable
  public static Library findLibrary(@NotNull Module module, @NotNull final String name) {
    final Ref<Library> result = Ref.create(null);
    OrderEnumerator.orderEntries(module).forEachLibrary(library -> {
      if (name.equals(library.getName())) {
        result.set(library);
        return false;
      }
      return true;
    });
    return result.get();
  }

   @Nullable
  public static OrderEntry findLibraryEntry(VirtualFile file, final Project project) {
    List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry || entry instanceof JdkOrderEntry) {
        return entry;
      }
    }
    return null;
  }

  @NotNull
  public static String getPresentableName(@NotNull Library library) {
    final String name = library.getName();
    if (name != null) {
      return name;
    }
    String[] urls = library.getUrls(OrderRootType.CLASSES);
    if (urls.length > 0) {
      return PathUtil.getFileName(VfsUtilCore.urlToPath(urls[0]));
    }
    return "Empty Library";
  }
}
