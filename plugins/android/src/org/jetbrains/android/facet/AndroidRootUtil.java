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
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.android.compiler.AndroidCompileUtil;
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
  private static String suggestResourceDirPath(@NotNull AndroidFacet facet) {
    final Module module = facet.getModule();
    
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 0) {
      return null;
    }
    
    VirtualFile root = contentRoots[0];

    if (contentRoots.length > 1) {
      final String moduleFileParentDirPath = FileUtil.toSystemIndependentName(new File(module.getModuleFilePath()).getParent());
      final VirtualFile moduleFileParentDir = LocalFileSystem.getInstance().findFileByPath(moduleFileParentDirPath);
      if (moduleFileParentDir != null) {
        for (VirtualFile contentRoot : contentRoots) {
          if (contentRoot == moduleFileParentDir) {
            root = contentRoot;
          }
        }
      }
    }
    return root.getPath() + facet.getConfiguration().RES_FOLDER_RELATIVE_PATH;
  }
  
  @Nullable
  public static String getResourceDirPath(@NotNull AndroidFacet facet) {
    final VirtualFile resourceDir = getResourceDir(facet.getModule());
    return resourceDir != null ? resourceDir.getPath() : suggestResourceDirPath(facet);
  }

  @Nullable
  public static VirtualFile getFileByRelativeModulePath(Module module, String relativePath, boolean lookInContentRoot) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

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

  @Nullable
  public static VirtualFile getRenderscriptGenDir(@NotNull Module module) {
    final String path = getRenderscriptGenSourceRootPath(module);
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  // works even if there is no Android facet in a module

  @Nullable
  public static VirtualFile getStandartGenDir(@NotNull Module module) {
    return getFileByRelativeModulePath(module, '/' + SdkConstants.FD_GEN_SOURCES, false);
  }

  private static void collectClassFilesAndJars(@NotNull VirtualFile root,
                                               @NotNull Set<VirtualFile> result,
                                               @NotNull Set<VirtualFile> visited) {
    if (!visited.add(root)) {
      return;
    }
    for (VirtualFile child : root.getChildren()) {
      if (child.exists()) {
        if (child.isDirectory()) {
          collectClassFilesAndJars(child, result, visited);
        }
        else if ("jar".equals(child.getExtension()) || "class".equals(child.getExtension())) {
          if (child.getFileSystem() instanceof JarFileSystem) {
            final VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(child);
            if (localFile != null) {
              result.add(localFile);
            }
          }
          else {
            result.add(child);
          }
        }
      }
    }
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
                  if (file.isDirectory()) {
                    collectClassFilesAndJars(file, libraries, new HashSet<VirtualFile>());
                  }
                  else {
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
          }
          else if (entry instanceof ModuleOrderEntry) {
            Module depModule = ((ModuleOrderEntry)entry).getModule();
            if (depModule == null || AndroidCompileUtil.isGenModule(depModule)) {
              continue;
            }
            final AndroidFacet facet = AndroidFacet.getInstance(depModule);
            final boolean libraryProject = facet != null && facet.getConfiguration().LIBRARY_PROJECT;

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
            fillExternalLibrariesAndModules(depModule, outputDirs, libraries, visited, !libraryProject || exportedLibrariesOnly);
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

  @Nullable
  public static String getRenderscriptGenSourceRootPath(@NotNull Module module) {
    // todo: return correct path for mavenized module
    final String moduleDirPath = getModuleDirPath(module);
    return moduleDirPath != null
           ? moduleDirPath + '/' + SdkConstants.FD_GEN_SOURCES
           : null;
  }
  
  @Nullable
  public static VirtualFile getMainContentRoot(@NotNull AndroidFacet facet) {
    final Module module = facet.getModule();
    
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 0) {
      return null;
    }

    final VirtualFile manifestFile = getManifestFile(module);
    if (manifestFile != null) {
      for (VirtualFile root : contentRoots) {
        if (VfsUtilCore.isAncestor(root, manifestFile, true)) {
          return root;
        }
      }
    }
    return contentRoots[0];
  }
}
