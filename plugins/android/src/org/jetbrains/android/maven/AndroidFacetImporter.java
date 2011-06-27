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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.*;

import java.io.File;
import java.util.*;


/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetImporter extends FacetImporter<AndroidFacet, AndroidFacetConfiguration, AndroidFacetType> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.maven.AndroidFacetImporter");

  public AndroidFacetImporter() {
    super("com.jayway.maven.plugins.android.generation2", "maven-android-plugin", FacetType.findInstance(AndroidFacetType.class), "Android");
  }

  @Override
  public boolean isApplicable(MavenProject mavenProject) {
    return ArrayUtil.find(getSupportedPackagingTypes(), mavenProject.getPackaging()) >= 0 &&
           super.isApplicable(mavenProject);
  }


  @Override
  public void getSupportedPackagings(Collection<String> result) {
    result.addAll(Arrays.asList(getSupportedPackagingTypes()));
  }

  @NotNull
  private static String[] getSupportedPackagingTypes() {
    return new String[] {"apk", AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE};
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    result.add(AndroidMavenUtil.APKSOURCES_DEPENDENCY_TYPE);
    result.add(AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE);
  }

  @Override
  protected void setupFacet(AndroidFacet facet, MavenProject mavenProject) {
    String mavenProjectDirPath = FileUtil.toSystemIndependentName(mavenProject.getDirectory());
    facet.getConfiguration().init(facet.getModule(), mavenProjectDirPath);
    AndroidMavenProviderImpl.setPathsToDefault(mavenProject, facet.getModule(), facet.getConfiguration());
    AndroidMavenProviderImpl.configureAaptCompilation(mavenProject, facet.getModule(), facet.getConfiguration(),
                                                        AndroidMavenProviderImpl.hasApkSourcesDependency(mavenProject));

    if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(mavenProject.getPackaging())) {
      facet.getConfiguration().LIBRARY_PROJECT = true;
    }
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
    configureAndroidPlatform(facet, mavenProject, modelsProvider);
  }

  private void configureAndroidPlatform(AndroidFacet facet, MavenProject project, MavenModifiableModelsProvider modelsProvider) {
    final Sdk currentSdk = modelsProvider.getRootModel(facet.getModule()).getSdk();
    if (currentSdk != null && isAppropriateSdk(currentSdk, project)) {
      return;
    }

    Sdk platformLib = findOrCreateAndroidPlatform(project);
    if (platformLib != null) {
      modelsProvider.getRootModel(facet.getModule()).setSdk(platformLib);
    }
  }

  private boolean isAppropriateSdk(@NotNull Sdk sdk, MavenProject mavenProject) {
    if (!(sdk.getSdkType() == AndroidSdkType.getInstance())) {
      return false;
    }

    final String platformId = getPlatformFromConfig(mavenProject);
    if (platformId == null) {
      return false;
    }

    final AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (sdkAdditionalData == null) {
      return false;
    }

    final AndroidPlatform androidPlatform = sdkAdditionalData.getAndroidPlatform();
    if (androidPlatform == null) {
      return false;
    }

    return AndroidSdkUtils.targetHasId(androidPlatform.getTarget(), platformId);
  }

  @Nullable
  private Sdk findOrCreateAndroidPlatform(MavenProject project) {
    String sdkPath = System.getenv("ANDROID_HOME");
    LOG.info("android home: " + sdkPath);

    if (sdkPath != null) {
      final Sdk sdk = findOrCreateAndroidPlatform(project, sdkPath);
      if (sdk != null) {
        return sdk;
      }
    }

    final Collection<String> candidates = suggestAndroidSdkPaths();
    LOG.info("suggested sdks: " + candidates);

    for (String candidate : candidates) {
      final Sdk sdk = findOrCreateAndroidPlatform(project, candidate);
      if (sdk != null) {
        return sdk;
      }
    }
    return null;
  }

  @Nullable
  private Sdk findOrCreateAndroidPlatform(MavenProject project, String sdkPath) {
    if (sdkPath != null) {
      final String apiLevel = getPlatformFromConfig(project);

      if (apiLevel == null) {
        return null;
      }

      AndroidSdk sdk = AndroidSdk.parse(sdkPath, new EmptySdkLog());
      if (sdk != null) {
        IAndroidTarget target = sdk.findTargetByApiLevel(apiLevel);
        if (target != null) {
          Sdk library = AndroidUtils.findAppropriateAndroidPlatform(target, sdk);
          if (library == null) {
            library = AndroidSdkUtils.createNewAndroidPlatform(target, sdkPath, true);
          }
          return library;
        }
      }
    }
    return null;
  }

  @Nullable
  private String getPlatformFromConfig(MavenProject project) {
    Element sdkRoot = getConfig(project, "sdk");
    if (sdkRoot != null) {
      Element platform = sdkRoot.getChild("platform");
      if (platform != null) {
        return platform.getValue();
      }
    }
    return null;
  }

  @NotNull
  private static Collection<String> suggestAndroidSdkPaths() {
    final List<Sdk> androidSdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());
    final Set<String> result = new HashSet<String>(androidSdks.size());

    for (Sdk androidSdk : androidSdks) {
      final VirtualFile sdkHome = androidSdk.getHomeDirectory();

      if (sdkHome != null && sdkHome.exists() && sdkHome.isValid() && sdkHome.isDirectory()) {
        final String path = sdkHome.getPath();
        if (path != null) {
          result.add(path);
        }
      }
    }

    return result;
  }

  private void configurePaths(AndroidFacet facet, MavenProject project) {
    Module module = facet.getModule();
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    AndroidFacetConfiguration configuration = facet.getConfiguration();

    String resFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceDirectory", true, true);
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
      String resOverlayFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceOverlayDirectory", true, true);
      if (resOverlayFolderRelPath != null) {
        configuration.RES_OVERLAY_FOLDERS = new String[]{'/' + resOverlayFolderRelPath};
      }
    }

    String resFolderForCompilerRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceDirectory", false, true);
    if (resFolderForCompilerRelPath != null && !resFolderForCompilerRelPath.equals(resFolderRelPath)) {
      if (!configuration.USE_CUSTOM_APK_RESOURCE_FOLDER) {
        // it may be already configured in setupFacet()
        configuration.USE_CUSTOM_APK_RESOURCE_FOLDER = true;
        configuration.CUSTOM_APK_RESOURCE_FOLDER = '/' + resFolderForCompilerRelPath;
      }
      configuration.RUN_PROCESS_RESOURCES_MAVEN_TASK = true;
    }

    String assetsFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "assetsDirectory", false, true);
    if (assetsFolderRelPath != null) {
      configuration.ASSETS_FOLDER_RELATIVE_PATH = '/' + assetsFolderRelPath;
    }

    String manifestFileRelPath = getPathFromConfig(module, project, moduleDirPath, "androidManifestFile", true, false);
    if (manifestFileRelPath != null) {
      configuration.MANIFEST_FILE_RELATIVE_PATH = '/' + manifestFileRelPath;
    }

    String manifestFileForCompilerRelPath = getPathFromConfig(module, project, moduleDirPath, "androidManifestFile", false, false);
    if (manifestFileForCompilerRelPath != null && !manifestFileForCompilerRelPath.equals(manifestFileRelPath)) {
      configuration.USE_CUSTOM_COMPILER_MANIFEST = true;
      configuration.CUSTOM_COMPILER_MANIFEST = '/' + manifestFileForCompilerRelPath;
      configuration.RUN_PROCESS_RESOURCES_MAVEN_TASK = true;
    }

    String nativeLibsFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "nativeLibrariesDirectory", false, true);
    if (nativeLibsFolderRelPath != null) {
      configuration.LIBS_FOLDER_RELATIVE_PATH = '/' + nativeLibsFolderRelPath;
    }
  }

  @Nullable
  private String getPathFromConfig(Module module,
                                   MavenProject project,
                                   String moduleDirPath,
                                   String configTagName,
                                   boolean inResourceDir,
                                   boolean directory) {
    String resourceDir = findConfigValue(project, configTagName);
    if (resourceDir != null) {
      String path = makePath(project, resourceDir);
      if (inResourceDir) {
        MyResourceProcessor processor = new MyResourceProcessor(path, directory);
        AndroidMavenProviderImpl.processResources(module, project, processor);
        if (processor.myResult != null) {
          path = processor.myResult.getPath();
        }
      }
      String resFolderRelPath = getRelativePath(moduleDirPath, path);
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

  private static class MyResourceProcessor implements AndroidMavenProviderImpl.ResourceProcessor {
    private final String myResourceOutputPath;
    private final boolean myDirectory;

    private VirtualFile myResult;

    private MyResourceProcessor(String resourceOutputPath, boolean directory) {
      myResourceOutputPath = resourceOutputPath;
      myDirectory = directory;
    }

    @Override
    public boolean process(@NotNull VirtualFile resource, String outputPath) {
      if (resource.isDirectory() != myDirectory) {
        return false;
      }
      if (outputPath.endsWith("/")) {
        outputPath = outputPath.substring(0, outputPath.length() - 1);
      }
      if (FileUtil.pathsEqual(outputPath, myResourceOutputPath)) {
        myResult = resource;
        return true;
      }
      return false;
    }
  }
}
