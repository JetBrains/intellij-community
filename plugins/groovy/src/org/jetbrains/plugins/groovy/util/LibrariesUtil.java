/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class LibrariesUtil {

  public static Library[] getLibrariesByCondition(final Module module, final Condition<Library> condition) {
    if (module == null) return new Library[0];
    final ArrayList<Library> libraries = new ArrayList<Library>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        ModifiableRootModel model = manager.getModifiableModel();
        for (OrderEntry entry : model.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            LibraryOrderEntry libEntry = (LibraryOrderEntry) entry;
            Library library = libEntry.getLibrary();
            if (condition.value(library)) {
              libraries.add(library);
            }
          }
        }
      }
    });

    return libraries.toArray(new Library[libraries.size()]);
  }

  public static Library[] getLibraries(Condition<Library> condition) {
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    List<Library> libs = ContainerUtil.findAll(table.getLibraries(), condition);
    return libs.toArray(new Library[libs.size()]);
  }

  public static String[] getLibNames(Library[] libraries) {
    return ContainerUtil.map2Array(libraries, String.class, new Function<Library, String>() {
      public String fun(Library library) {
        return library.getName();
      }
    });
  }

  @NotNull
  public static String getGroovyOrGrailsLibraryHome(Library library) {
    String path = "";
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      if (file.getName().matches(GroovyConfigUtils.GROOVY_JAR_PATTERN)) {
        String jarPath = file.getPresentableUrl();
        File realFile = new File(jarPath);
        if (realFile.exists()) {
          File parentFile = realFile.getParentFile();
          if (parentFile != null) {
            File libHome = parentFile.getParentFile();
            if (libHome != null) {
              path = libHome.getPath();
            }
          }
        }
      }
    }
    return path;
  }

  public static void addLibrary(Library library, Module module) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    if (!libraryReferenced(rootManager, library)) {
      final ModifiableRootModel moduleModel = rootManager.getModifiableModel();
      final LibraryOrderEntry addedEntry = moduleModel.addLibraryEntry(library);
      final OrderEntry[] order = moduleModel.getOrderEntries();

      //place library before jdk
      assert order[order.length - 1] == addedEntry;
      int insertionPoint = - -1;
      for (int i = 0; i < order.length - 1; i++) {
        if (order[i] instanceof JdkOrderEntry) {
          insertionPoint = i;
          break;
        }
      }
      if (insertionPoint >= 0) {
        for (int i = order.length - 1; i > insertionPoint; i--) {
          order[i] = order[i - 1];
        }
        order[insertionPoint] = addedEntry;

        moduleModel.rearrangeOrderEntries(order);
      }
      moduleModel.commit();
    }
  }

  public static void addLibraryToReferringModules(FacetTypeId<?> facetID, Library library) {
    for (Project prj : ProjectManager.getInstance().getOpenProjects())
      for (Module module : ModuleManager.getInstance(prj).getModules()) {
        if (FacetManager.getInstance(module).getFacetByType(facetID) != null) {
          addLibrary(library, module);
        }
      }
  }

  public static String generateNewLibraryName(String version, String prefix) {
    List<Object> libNames = ContainerUtil.map(GroovyConfigUtils.getGroovyLibraries(), new Function<Library, Object>() {
      public Object fun(Library library) {
        return library.getName();
      }
    });
    String originalName = prefix + version;
    String newName = originalName;
    int index = 1;
    while (libNames.contains(newName)) {
      newName = originalName + " (" + index + ")";
      index++;
    }
    return newName;
  }

  @Nullable
  public static Library getLibraryByName(String name) {
    if (name == null) return null;
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    return table.getLibraryByName(name);
  }

  public static void placeEntryToCorrectPlace(ModifiableRootModel model, LibraryOrderEntry addedEntry) {
    final OrderEntry[] order = model.getOrderEntries();
    //place library before jdk
    assert order[order.length - 1] == addedEntry;
    int insertionPoint = -1;
    for (int i = 0; i < order.length - 1; i++) {
      if (order[i] instanceof JdkOrderEntry) {
        insertionPoint = i;
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

  public static boolean libraryReferenced(ModuleRootManager rootManager, Library library) {
    final OrderEntry[] entries = rootManager.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry) entry).getLibrary())) return true;
    }
    return false;
  }
}
