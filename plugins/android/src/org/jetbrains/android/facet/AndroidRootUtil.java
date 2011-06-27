/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.facet;

import com.android.sdklib.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 16, 2009
 * Time: 3:28:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidRootUtil {
  private AndroidRootUtil() {
  }

  @Nullable
  public static VirtualFile getManifestFile(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet == null ? null : getFileByRelativeModulePath(module, facet.getConfiguration().MANIFEST_FILE_RELATIVE_PATH, true);
  }

  @Nullable
  public static VirtualFile getCustomManifestFileForCompiler(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getConfiguration().CUSTOM_COMPILER_MANIFEST, false);
  }

  @Nullable
  public static VirtualFile getManifestFileForCompiler(AndroidFacet facet) {
    return facet.getConfiguration().USE_CUSTOM_COMPILER_MANIFEST
           ? getCustomManifestFileForCompiler(facet)
           : getManifestFile(facet.getModule());
  }

  @Nullable
  public static VirtualFile getResourceDir(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    String resRelPath = facet.getConfiguration().RES_FOLDER_RELATIVE_PATH;
    return getFileByRelativeModulePath(module, resRelPath, true);
  }

  @Nullable
  public static VirtualFile getFileByRelativeModulePath(Module module, String relativePath, boolean lookInContentRoot) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

    /*if (contentRoots.length == 1) {
      String absPath = FileUtil.toSystemIndependentName(contentRoots[0].getPath() + relativePath);
      return LocalFileSystem.getInstance().findFileByPath(absPath);
    }*/

    String moduleDirPath = new File(module.getModuleFilePath()).getParent();
    if (moduleDirPath != null) {
      String absPath = FileUtil.toSystemIndependentName(moduleDirPath + relativePath);
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absPath);
      if (file != null) {
        return file;
      }
    }

    if (lookInContentRoot) {
      for (VirtualFile contentRoot : contentRoots) {
        String absPath = FileUtil.toSystemIndependentName(contentRoot.getPath() + relativePath);
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absPath);
        if (file != null) {
          return file;
        }
      }
    }
    return null;
  }

  /*@Nullable
  private static VirtualFile getManifestBrotherDir(@NotNull Module module, @NotNull String path) {
    VirtualFile manifestFile = getManifestFile(module);
    if (manifestFile == null) return null;
    VirtualFile parent = manifestFile.getParent();
    if (parent != null) {
      return parent.findChild(path);
    }
    return parent;
  }*/

  @Nullable
  public static VirtualFile getAssetsDir(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet == null ? null : getFileByRelativeModulePath(module, facet.getConfiguration().ASSETS_FOLDER_RELATIVE_PATH, false);
  }

  @Nullable
  public static VirtualFile getLibsDir(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet == null ? null : getFileByRelativeModulePath(module, facet.getConfiguration().LIBS_FOLDER_RELATIVE_PATH, false);
  }

  @Nullable
  public static VirtualFile getAidlGenDir(@NotNull Module module, @Nullable AndroidFacet facet) {
    if (facet == null) {
      facet = AndroidFacet.getInstance(module);
    }
    if (facet != null) {
      LocalFileSystem lfs = LocalFileSystem.getInstance();
      String genPath = facet.getAidlGenSourceRootPath();
      if (genPath != null) {
        return lfs.findFileByPath(genPath);
      }
    }
    return null;
  }

  // works even if there is no Android facet in a module

  @Nullable
  public static VirtualFile getStandartGenDir(@NotNull Module module) {
    return getFileByRelativeModulePath(module, '/' + SdkConstants.FD_GEN_SOURCES, false);
  }

  private static void fillExternalLibrariesAndModules(final Module module,
                                                      final Set<VirtualFile> outputDirs,
                                                      @Nullable final Set<VirtualFile> libraries,
                                                      final Set<Module> visited,
                                                      final boolean exportedLibrariesOnly) {
    if (!visited.add(module)) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        for (OrderEntry entry : manager.getOrderEntries()) {
          if (!(entry instanceof ExportableOrderEntry) || ((ExportableOrderEntry)entry).getScope() != DependencyScope.COMPILE) {
            continue;
          }
          if (libraries != null && entry instanceof LibraryOrderEntry) {
            final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
            final Library library = libraryOrderEntry.getLibrary();
            if (library != null && (!exportedLibrariesOnly || libraryOrderEntry.isExported())) {
              for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
                if (file.exists()) {
                  if (file.getFileSystem() instanceof JarFileSystem) {
                    VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
                    if (localFile != null) libraries.add(localFile);
                  }
                  else {
                    libraries.add(file);
                  }
                }
              }
            }
          }
          else if (entry instanceof ModuleOrderEntry) {
            Module depModule = ((ModuleOrderEntry)entry).getModule();
            if (depModule == null) {
              continue;
            }
            AndroidFacet facet = AndroidFacet.getInstance(depModule);
            if (facet == null || !facet.getConfiguration().LIBRARY_PROJECT) {
              CompilerModuleExtension extension = CompilerModuleExtension.getInstance(depModule);
              if (extension != null) {
                VirtualFile classDir = extension.getCompilerOutputPath();
                if (!outputDirs.contains(classDir) && classDir != null && classDir.exists()) {
                  outputDirs.add(classDir);
                }
                VirtualFile classDirForTests = extension.getCompilerOutputPathForTests();
                if (!outputDirs.contains(classDirForTests) && classDirForTests != null && classDirForTests.exists()) {
                  outputDirs.add(classDirForTests);
                }
              }
              fillExternalLibrariesAndModules(depModule, outputDirs, libraries, visited, true);
            }
            else {
              fillExternalLibrariesAndModules(depModule, outputDirs, libraries, visited, exportedLibrariesOnly);
            }
          }
        }
      }
    });
  }

  @NotNull
  public static List<VirtualFile> getExternalLibraries(Module module) {
    Set<VirtualFile> files = new HashSet<VirtualFile>();
    OrderedSet<VirtualFile> libs = new OrderedSet<VirtualFile>();
    fillExternalLibrariesAndModules(module, files, libs, new HashSet<Module>(), false);
    return libs;
  }

  @NotNull
  public static Set<VirtualFile> getDependentModules(Module module, VirtualFile moduleOutputDir) {
    Set<VirtualFile> files = new HashSet<VirtualFile>();
    fillExternalLibrariesAndModules(module, files, null, new HashSet<Module>(), false);
    files.remove(moduleOutputDir);
    return files;
  }

  @NotNull
  public static VirtualFile[] getResourceOverlayDirs(Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return VirtualFile.EMPTY_ARRAY;
    }
    String[] overlayFolders = facet.getConfiguration().RES_OVERLAY_FOLDERS;
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String overlayFolder : overlayFolders) {
      VirtualFile overlayDir = getFileByRelativeModulePath(module, overlayFolder, true);
      if (overlayDir != null) {
        result.add(overlayDir);
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  @Nullable
  public static String getModuleDirPath(Module module) {
    String moduleFilePath = module.getModuleFilePath();
    String moduleDirPath = new File(moduleFilePath).getParent();
    if (moduleDirPath != null) {
      moduleDirPath = FileUtil.toSystemIndependentName(moduleDirPath);
    }
    return moduleDirPath;
  }
}
