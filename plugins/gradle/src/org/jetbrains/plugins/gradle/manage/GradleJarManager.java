/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.GradleJar;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryId;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 12/13/12 1:04 PM
 */
public class GradleJarManager {

  @NotNull private final PlatformFacade myPlatformFacade;

  public GradleJarManager(@NotNull PlatformFacade facade) {
    myPlatformFacade = facade;
  }

  public void importJar(@NotNull final GradleJar jar, @NotNull final Project project) {
    GradleUtil.executeProjectChangeAction(project, jar, new Runnable() {
      @Override
      public void run() {
        LibraryTable table = myPlatformFacade.getProjectLibraryTable(project);
        Library library = table.getLibraryByName(jar.getLibraryId().getLibraryName());
        if (library == null) {
          return;
        }
        Library.ModifiableModel model = library.getModifiableModel();
        try {
          for (VirtualFile file : model.getFiles(OrderRootType.CLASSES)) {
            if (jar.getPath().equals(file.getPath())) {
              return;
            }
          }

          VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(jar.getPath()));
          if (virtualFile == null) {
            //GradleLog.LOG.warn(
            //  String.format("Can't find %s of the library '%s' at path '%s'", entry.getKey(), libraryName, file.getAbsolutePath())
            //);
            return;
          }
          if (virtualFile.isDirectory()) {
            model.addRoot(virtualFile, OrderRootType.CLASSES);
          }
          else {
            VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
            if (jarRoot == null) {
              GradleLog.LOG.warn(String.format(
                "Can't parse contents of the jar file at path '%s' for the library '%s''", jar.getPath(), library.getName()
              ));
              return;
            }
            model.addRoot(jarRoot, OrderRootType.CLASSES);
          }
        }
        finally {
          model.commit();
        } 
      }
    });
  }

  public void removeJars(@NotNull Collection<GradleJar> jars, @NotNull Project project) {
    if (jars.isEmpty()) {
      return;
    }
    Map<GradleLibraryId, List<GradleJar>> jarsByLibraries = ContainerUtilRt.newHashMap();
    for (GradleJar jar : jars) {
      List<GradleJar> list = jarsByLibraries.get(jar.getLibraryId());
      if (list == null) {
        jarsByLibraries.put(jar.getLibraryId(), list = ContainerUtilRt.newArrayList());
      }
      list.add(jar);
    }

    LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
    for (Map.Entry<GradleLibraryId, List<GradleJar>> entry : jarsByLibraries.entrySet()) {
      Library library = libraryTable.getLibraryByName(entry.getKey().getLibraryName());
      if (library == null) {
        continue;
      }
      Set<GradleJar> libraryJars = ContainerUtilRt.newHashSet(entry.getValue());
      for (GradleJar jar : entry.getValue()) {
        boolean valid = false;
        for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
          if (jar.getPath().equals(GradleUtil.getLocalFileSystemPath(file))) {
            valid = true;
            break;
          }
        }
        if (!valid) {
          libraryJars.remove(jar);
        }
      }

      if (!libraryJars.isEmpty()) {
        removeLibraryJars(libraryJars, project);
      }
    }
  }

  /**
   * Removes given jars from IDE project structure assuming that they belong to the same library.
   * 
   * @param jars     jars to remove
   * @param project  current project
   */
  private void removeLibraryJars(@NotNull final Set<GradleJar> jars, @NotNull final Project project) {
    GradleUtil.executeProjectChangeAction(project, jars, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        GradleLibraryId libraryId = jars.iterator().next().getLibraryId();
        Library library = libraryTable.getLibraryByName(libraryId.getLibraryName());
        if (library == null) {
          return;
        }
        Set<String> pathsToRemove = ContainerUtil.map2Set(jars, new Function<GradleJar, String>() {
          @Override
          public String fun(GradleJar jar) {
            return jar.getPath();
          }
        });
        Library.ModifiableModel model = library.getModifiableModel();
        try {
          for (VirtualFile file : model.getFiles(OrderRootType.CLASSES)) {
            if (pathsToRemove.contains(GradleUtil.getLocalFileSystemPath(file))) {
              model.removeRoot(file.getUrl(), OrderRootType.CLASSES);
            }
          }
        }
        finally {
          model.commit();
        }
      }
    });
  }
}
