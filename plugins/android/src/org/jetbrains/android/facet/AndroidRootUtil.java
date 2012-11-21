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

import com.android.SdkConstants;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;


/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 16, 2009
 * Time: 3:28:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidRootUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidRootUtil");
  @NonNls public static final String DEFAULT_PROPERTIES_FILE_NAME = "default.properties";

  private AndroidRootUtil() {
  }

  @Nullable
  public static VirtualFile getManifestFile(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getConfiguration().MANIFEST_FILE_RELATIVE_PATH, true);
  }

  @Nullable
  public static VirtualFile getCustomManifestFileForCompiler(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getConfiguration().CUSTOM_COMPILER_MANIFEST, false);
  }

  // DO NOT get PSI or DOM from this file, because it may be excluded (f.ex. it can be in /target/ directory)
  @Nullable
  public static VirtualFile getManifestFileForCompiler(@NotNull AndroidFacet facet) {
    return facet.getConfiguration().USE_CUSTOM_COMPILER_MANIFEST
           ? getCustomManifestFileForCompiler(facet)
           : getManifestFile(facet);
  }

  @Nullable
  public static VirtualFile getResourceDir(@NotNull AndroidFacet facet) {
    String resRelPath = facet.getConfiguration().RES_FOLDER_RELATIVE_PATH;
    return getFileByRelativeModulePath(facet.getModule(), resRelPath, true);
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
    final VirtualFile resourceDir = getResourceDir(facet);
    return resourceDir != null ? resourceDir.getPath() : suggestResourceDirPath(facet);
  }

  @Nullable
  public static VirtualFile getFileByRelativeModulePath(Module module, String relativePath, boolean lookInContentRoot) {
    if (relativePath == null || relativePath.length() == 0) {
      return null;
    }

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
  public static VirtualFile getAssetsDir(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getConfiguration().ASSETS_FOLDER_RELATIVE_PATH, false);
  }

  @Nullable
  public static VirtualFile getProguardCfgFile(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getConfiguration().PROGUARD_CFG_PATH, false);
  }

  @Nullable
  public static VirtualFile getLibsDir(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getConfiguration().LIBS_FOLDER_RELATIVE_PATH, false);
  }

  @Nullable
  public static VirtualFile getAidlGenDir(@NotNull AndroidFacet facet) {
    final String genPath = getAidlGenSourceRootPath(facet);
    return genPath != null
           ? LocalFileSystem.getInstance().findFileByPath(genPath)
           : null;
  }

  @Nullable
  public static VirtualFile getAaptGenDir(@NotNull AndroidFacet facet) {
    final String genPath = getAptGenSourceRootPath(facet);
    return genPath != null
           ? LocalFileSystem.getInstance().findFileByPath(genPath)
           : null;
  }

  @Nullable
  public static VirtualFile getRenderscriptGenDir(@NotNull AndroidFacet facet) {
    final String path = getRenderscriptGenSourceRootPath(facet);
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  @Nullable
  public static VirtualFile getBuildconfigGenDir(@NotNull AndroidFacet facet) {
    final String path = getBuildconfigGenSourceRootPath(facet);
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
                if (!file.exists()) {
                  continue;
                }

                if (file.getFileType() instanceof ArchiveFileType) {
                  if (file.getFileSystem() instanceof JarFileSystem) {
                    VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
                    if (localFile != null) {
                      libraries.add(localFile);
                    }
                  }
                  else {
                    libraries.add(file);
                  }
                }
                else if (file.isDirectory() && !(file.getFileSystem() instanceof JarFileSystem)) {
                  collectClassFilesAndJars(file, libraries, new HashSet<VirtualFile>());
                }
              }
            }
          }
          else if (entry instanceof ModuleOrderEntry) {
            Module depModule = ((ModuleOrderEntry)entry).getModule();
            if (depModule == null) {
              continue;
            }
            final AndroidFacet facet = AndroidFacet.getInstance(depModule);
            final boolean libraryProject = facet != null && facet.getConfiguration().LIBRARY_PROJECT;

            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(depModule);
            if (extension != null) {
              VirtualFile classDir = extension.getCompilerOutputPath();

              if (libraryProject) {
                if (classDir != null) {
                  final VirtualFile packedClassesJar = classDir.findChild(AndroidCommonUtils.CLASSES_JAR_FILE_NAME);
                  if (packedClassesJar != null) {
                    outputDirs.add(packedClassesJar);
                  }
                }
              }
              // do not support android-app->android-app compile dependencies
              else if (facet == null && !outputDirs.contains(classDir) && classDir != null && classDir.exists()) {
                outputDirs.add(classDir);
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

    addAnnotationsJar(module, libs);
    return libs;
  }

  private static void addAnnotationsJar(Module module, OrderedSet<VirtualFile> libs) {
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !sdk.getSdkType().equals(AndroidSdkType.getInstance())) {
      return;
    }

    final String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath == null) {
      return;
    }

    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      return;
    }
    final AndroidPlatform platform = data.getAndroidPlatform();

    if (platform != null && platform.needToAddAnnotationsJarToClasspath()) {
      final String annotationsJarPath = FileUtil.toSystemIndependentName(sdkHomePath) + AndroidSdkUtils.ANNOTATIONS_JAR_RELATIVE_PATH;
      final VirtualFile annotationsJar = LocalFileSystem.getInstance().findFileByPath(annotationsJarPath);

      if (annotationsJar != null) {
        libs.add(annotationsJar);
      }
    }
  }

  @NotNull
  public static Set<VirtualFile> getDependentModules(Module module,
                                                     VirtualFile moduleOutputDir) {
    Set<VirtualFile> files = new HashSet<VirtualFile>();
    fillExternalLibrariesAndModules(module, files, null, new HashSet<Module>(), false);
    files.remove(moduleOutputDir);
    return files;
  }

  @NotNull
  public static VirtualFile[] getResourceOverlayDirs(@NotNull AndroidFacet facet) {
    List<String> overlayFolders = facet.getConfiguration().RES_OVERLAY_FOLDERS;
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String overlayFolder : overlayFolders) {
      VirtualFile overlayDir = getFileByRelativeModulePath(facet.getModule(), overlayFolder, true);
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
  public static String getRenderscriptGenSourceRootPath(@NotNull AndroidFacet facet) {
    // todo: return correct path for mavenized module when it'll be supported
    return getDefaultGenSourceRoot(facet);
  }

  @Nullable
  public static String getBuildconfigGenSourceRootPath(@NotNull AndroidFacet facet) {
    return getAptGenSourceRootPath(facet);
  }

  @Nullable
  private static String getDefaultGenSourceRoot(AndroidFacet facet) {
    final VirtualFile mainContentRoot = getMainContentRoot(facet);
    final String moduleDirPath = mainContentRoot != null ? mainContentRoot.getPath() : null;
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

    final VirtualFile manifestFile = getManifestFile(facet);
    if (manifestFile != null) {
      for (VirtualFile root : contentRoots) {
        if (VfsUtilCore.isAncestor(root, manifestFile, true)) {
          return root;
        }
      }
    }
    return contentRoots[0];
  }

  @Nullable
  public static PropertiesFile findPropertyFile(@NotNull final Module module, @NotNull String propertyFileName) {
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      final VirtualFile vFile = contentRoot.findChild(propertyFileName);
      if (vFile != null) {
        final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          @Nullable
          @Override
          public PsiFile compute() {
            return PsiManager.getInstance(module.getProject()).findFile(vFile);
          }
        });
        if (psiFile instanceof PropertiesFile) {
          return (PropertiesFile)psiFile;
        }
      }
    }
    return null;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static Pair<Properties, VirtualFile> readPropertyFile(@NotNull Module module, @NotNull String propertyFileName) {
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      final Pair<Properties, VirtualFile> result = readPropertyFile(contentRoot, propertyFileName);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public static Pair<Properties, VirtualFile> readProjectPropertyFile(@NotNull Module module) {
    final Pair<Properties, VirtualFile> pair = readPropertyFile(module, SdkConstants.FN_PROJECT_PROPERTIES);
    return pair != null
           ? pair
           : readPropertyFile(module, DEFAULT_PROPERTIES_FILE_NAME);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  private static Pair<Properties, VirtualFile> readPropertyFile(@NotNull VirtualFile contentRoot, @NotNull String propertyFileName) {
    final VirtualFile vFile = contentRoot.findChild(propertyFileName);
    if (vFile != null) {
      final Properties properties = new Properties();
      try {
        properties.load(new FileInputStream(new File(vFile.getPath())));
        return new Pair<Properties, VirtualFile>(properties, vFile);
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return null;
  }

  @Nullable
  public static Pair<Properties, VirtualFile> readProjectPropertyFile(@NotNull VirtualFile contentRoot) {
    final Pair<Properties, VirtualFile> pair = readPropertyFile(contentRoot, SdkConstants.FN_PROJECT_PROPERTIES);
    return pair != null
           ? pair
           : readPropertyFile(contentRoot, DEFAULT_PROPERTIES_FILE_NAME);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static Pair<String, VirtualFile> getPropertyValue(@NotNull Module module,
                                                           @NotNull String propertyFileName,
                                                           @NotNull String propertyKey) {
    final Pair<Properties, VirtualFile> pair = readPropertyFile(module, propertyFileName);
    if (pair != null) {
      final String value = pair.first.getProperty(propertyKey);
      if (value != null) {
        return Pair.create(value, pair.second);
      }
    }
    return null;
  }

  @Nullable
  public static Pair<String, VirtualFile> getProjectPropertyValue(Module module, String propertyName) {
    Pair<String, VirtualFile> result = getPropertyValue(module, SdkConstants.FN_PROJECT_PROPERTIES, propertyName);
    return result != null
           ? result
           : getPropertyValue(module, DEFAULT_PROPERTIES_FILE_NAME, propertyName);
  }

  @Nullable
  public static String getAptGenSourceRootPath(@NotNull AndroidFacet facet) {
    String path = facet.getConfiguration().GEN_FOLDER_RELATIVE_PATH_APT;
    if (path.length() == 0) return null;
    String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? moduleDirPath + path : null;
  }

  @Nullable
  public static String getAidlGenSourceRootPath(@NotNull AndroidFacet facet) {
    String path = facet.getConfiguration().GEN_FOLDER_RELATIVE_PATH_AIDL;
    if (path.length() == 0) return null;
    String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? moduleDirPath + path : null;
  }

  @Nullable
  public static String getApkPath(@NotNull AndroidFacet facet) {
    String path = facet.getConfiguration().APK_PATH;
    if (path.length() == 0) {
      return AndroidCompileUtil.getOutputPackage(facet.getModule());
    }
    String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? FileUtil.toSystemDependentName(moduleDirPath + path) : null;
  }

  @Nullable
  public static String getPathRelativeToModuleDir(@NotNull Module module, @NotNull String path) {
    String moduleDirPath = getModuleDirPath(module);
    if (moduleDirPath == null) {
      return null;
    }
    if (moduleDirPath.equals(path)) {
      return "";
    }
    return FileUtil.getRelativePath(moduleDirPath, path, '/');
  }
}
