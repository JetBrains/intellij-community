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
package org.jetbrains.android.maven;

import com.android.sdklib.IAndroidTarget;
import com.intellij.facet.FacetType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.sdk.AndroidLibraryManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.EmptySdkLog;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetImporter extends FacetImporter<AndroidFacet, AndroidFacetConfiguration, AndroidFacetType> {
  public AndroidFacetImporter() {
    super("com.jayway.maven.plugins.android.generation2", "maven-android-plugin", FacetType.findInstance(AndroidFacetType.class), "Android");
  }

  @Override
  public boolean isApplicable(MavenProject mavenProject) {
    return "apk".equalsIgnoreCase(mavenProject.getPackaging()) && super.isApplicable(mavenProject);
  }


  @Override
  public void getSupportedPackagings(Collection<String> result) {
    result.add("apk");
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    result.add(AndroidMavenUtil.APKSOURCES_DEPENDENCY_TYPE);
  }

  @Override
  protected void setupFacet(AndroidFacet facet, MavenProject mavenProject) {
    AndroidMavenProviderImpl.setPathsToDefault(mavenProject, facet.getModule(), facet.getConfiguration());
    AndroidMavenProviderImpl.configureAaptCompilation(mavenProject, facet.getModule(), facet.getConfiguration(),
                                                        AndroidMavenProviderImpl.hasApkSourcesDependency(mavenProject));
  }

  @Override
  protected void reimportFacet(MavenModifiableModelsProvider modelsProvider,
                               Module module,
                               MavenRootModelAdapter rootModel,
                               AndroidFacet facet,
                               MavenProjectsTree mavenTree,
                               MavenProject mavenProject,
                               MavenProjectChanges changes,
                               Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    configurePaths(facet, mavenProject);
    configureAndroidPlatform(facet, mavenProject);
  }

  private void configureAndroidPlatform(AndroidFacet facet, MavenProject project) {
    Library platformLib = findOrCreateAndroidPlatform(project);
    if (platformLib != null) {
      AndroidPlatform platform = AndroidPlatform.parse(platformLib, null, null);
      if (platform != null) {
        facet.getConfiguration().setAndroidPlatform(platform);
      }
    }
    facet.getConfiguration().ADD_ANDROID_LIBRARY = false;
  }

  @Nullable
  private Library findOrCreateAndroidPlatform(MavenProject project) {
    String sdkPath = System.getenv("ANDROID_HOME");
    String apiLevel = null;
    if (sdkPath != null) {
      Element sdkRoot = getConfig(project, "sdk");
      if (sdkRoot != null) {
        Element platform = sdkRoot.getChild("platform");
        if (platform != null) {
          apiLevel = platform.getValue();
        }
      }
      AndroidSdk sdk = AndroidSdk.parse(sdkPath, new EmptySdkLog());
      if (sdk != null) {
        IAndroidTarget target = apiLevel != null ? sdk.findTargetByApiLevel(apiLevel) : sdk.getNewerPlatformTarget();
        if (target != null) {
          Library library = AndroidUtils.findAppropriateAndroidPlatform(target, sdk);
          if (library == null) {
            final LibraryTable.ModifiableModel model = LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
            AndroidLibraryManager manager = new AndroidLibraryManager(model);
            library = manager.createNewAndroidPlatform(target, sdkPath);
            manager.apply();
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                model.commit();
              }
            });
          }
          return library;
        }
      }
    }
    return null;
  }

  private void configurePaths(AndroidFacet facet, MavenProject project) {
    String modulePath = facet.getModule().getModuleFilePath();
    String moduleDirPath = FileUtil.toSystemIndependentName(new File(modulePath).getParent());
    AndroidFacetConfiguration configuration = facet.getConfiguration();

    String resFolderRelPath = getPathFromConfig(project, moduleDirPath, "resourceDirectory");
    if (resFolderRelPath != null) {
      configuration.RES_FOLDER_RELATIVE_PATH = '/' + resFolderRelPath;
    }

    Element resourceOverlayDirectories = getConfig(project, "resourceOverlayDirectories");
    if (resourceOverlayDirectories != null) {
      List<String> dirs = new ArrayList<String>();
      for (Object child : resourceOverlayDirectories.getChildren()) {
        String dir = ((Element)child).getTextTrim();
        if (dir != null && dir.length() > 0) {
          String relativePath = getRelativePath(moduleDirPath, makePath(project, dir));
          if (relativePath != null && relativePath.length() > 0) {
            dirs.add('/' + relativePath);
          }
        }
      }
      if (dirs.size() > 0) {
        configuration.RES_OVERLAY_FOLDERS = ArrayUtil.toStringArray(dirs);
      }
    }
    else {
      String resOverlayFolderRelPath = getPathFromConfig(project, moduleDirPath, "resourceOverlayDirectory");
      if (resOverlayFolderRelPath != null) {
        configuration.RES_OVERLAY_FOLDERS = new String[]{'/' + resOverlayFolderRelPath};
      }
    }

    String assetsFolderRelPath = getPathFromConfig(project, moduleDirPath, "assetsDirectory");
    if (assetsFolderRelPath != null) {
      configuration.ASSETS_FOLDER_RELATIVE_PATH = '/' + assetsFolderRelPath;
    }

    String manifestFileRelPath = getPathFromConfig(project, moduleDirPath, "androidManifestFile");
    if (manifestFileRelPath != null) {
      configuration.MANIFEST_FILE_RELATIVE_PATH = '/' + manifestFileRelPath;
    }

    String nativeLibsFolderRelPath = getPathFromConfig(project, moduleDirPath, "nativeLibrariesDirectory");
    if (nativeLibsFolderRelPath != null) {
      configuration.LIBS_FOLDER_RELATIVE_PATH = '/' + nativeLibsFolderRelPath;
    }
  }

  @Nullable
  private String getPathFromConfig(MavenProject project, String moduleDirPath, String configTagName) {
    String resourceDir = findConfigValue(project, configTagName);
    if (resourceDir != null) {
      String resFolderRelPath = getRelativePath(moduleDirPath, makePath(project, resourceDir));
      if (resFolderRelPath != null) {
        return resFolderRelPath;
      }
    }
    return null;
  }

  @Nullable
  private static String getRelativePath(String basePath, String absPath) {
    absPath = FileUtil.toSystemIndependentName(absPath);
    if (VfsUtil.isAncestor(new File(basePath), new File(absPath), true)) {
      return FileUtil.getRelativePath(basePath, absPath, '/');
    }
    return null;
  }

  @Override
  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/combined-resources");
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/extracted-dependencies");
  }
}
