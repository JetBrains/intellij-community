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

package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkUtils {
  public static final String DEFAULT_PLATFORM_NAME_PROPERTY = "AndroidPlatformName";
  @NonNls public static final String ANDROID_HOME_ENV = "ANDROID_HOME";

  private AndroidSdkUtils() {
  }

  public static boolean isAndroidSdk(@NotNull String path) {
    return createSdkManager(path, new EmptySdkLog()) != null;
  }

  @Nullable
  private static VirtualFile getPlatformDir(@NotNull IAndroidTarget target) {
    String platformPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    VirtualFile platformDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(platformPath);
    if (platformDir == null) return null;
    return platformDir;
  }

  public static List<OrderRoot> getLibraryRootsForTarget(@NotNull IAndroidTarget target, @Nullable String sdkPath) {
    List<OrderRoot> result = new ArrayList<OrderRoot>();
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) return result;

    VirtualFile androidJar = platformDir.findChild(SdkConstants.FN_FRAMEWORK_LIBRARY);
    if (androidJar == null) return result;
    VirtualFile androidJarRoot = JarFileSystem.getInstance().findFileByPath(androidJar.getPath() + JarFileSystem.JAR_SEPARATOR);
    if (androidJarRoot != null) {
      result.add(new OrderRoot(androidJarRoot, OrderRootType.CLASSES));
    }

    IAndroidTarget.IOptionalLibrary[] libs = target.getOptionalLibraries();
    if (libs != null) {
      for (IAndroidTarget.IOptionalLibrary lib : libs) {
        VirtualFile libRoot = JarFileSystem.getInstance().findFileByPath(lib.getJarPath() + JarFileSystem.JAR_SEPARATOR);
        if (libRoot != null) {
          result.add(new OrderRoot(libRoot, OrderRootType.CLASSES));
        }
      }
    }
    VirtualFile targetDir = platformDir;
    if (!target.isPlatform()) {
      targetDir = LocalFileSystem.getInstance().findFileByPath(target.getLocation());
    }
    if (targetDir != null) {
      addJavaDocAndSources(result, targetDir);
    }
    VirtualFile sdkDir = sdkPath != null ? LocalFileSystem.getInstance().findFileByPath(sdkPath) : null;
    if (sdkDir != null) {
      addJavaDocAndSources(result, sdkDir);
    }
    return result;
  }

  @Nullable
  private static VirtualFile findJavadocDir(@NotNull VirtualFile dir) {
    VirtualFile docsDir = dir.findChild(SdkConstants.FD_DOCS);

    if (docsDir != null) {
      VirtualFile referenceDir = docsDir.findChild(SdkConstants.FD_DOCS_REFERENCE);
      if (referenceDir != null) {
        return referenceDir;
      }
    }
    return null;
  }

  private static void addJavaDocAndSources(@NotNull List<OrderRoot> list, @NotNull VirtualFile dir) {
    VirtualFile javadocDir = findJavadocDir(dir);
    if (javadocDir != null) {
      list.add(new OrderRoot(javadocDir, JavadocOrderRootType.getInstance()));
    }

    VirtualFile sourcesDir = dir.findChild(SdkConstants.FD_SOURCES);
    if (sourcesDir != null) {
      list.add(new OrderRoot(sourcesDir, OrderRootType.SOURCES));
    }
  }

  public static String getPresentableTargetName(@NotNull IAndroidTarget target) {
    IAndroidTarget parentTarget = target.getParent();
    if (parentTarget != null) {
      return target.getName() + " (" + parentTarget.getVersionName() + ')';
    }
    return target.getName();
  }

  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target, @NotNull String sdkPath, boolean addRoots) {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    String sdkName = SdkConfigurationUtil.createUniqueSdkName(AndroidSdkType.SDK_NAME, Arrays.asList(table.getAllJdks()));

    final Sdk sdk = table.createSdk(sdkName, SdkType.findInstance(AndroidSdkType.class));

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkPath);
    sdkModificator.commitChanges();


    final Sdk javaSdk = tryToChooseJavaSdk();
    setUpSdk(sdk, javaSdk, table.getAllJdks(), target, addRoots);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });
    return sdk;
  }

  @Nullable
  private static Sdk tryToChooseJavaSdk() {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (isApplicableJdk(sdk)) {
        return sdk;
      }
    }
    return null;
  }

  public static String chooseNameForNewLibrary(IAndroidTarget target) {
    if (target.isPlatform()) {
      return target.getName() + " Platform";
    }
    IAndroidTarget parentTarget = target.getParent();
    if (parentTarget != null) {
      return "Android " + parentTarget.getVersionName() + ' ' + target.getName();
    }
    return "Android " + target.getName();
  }

  public static String getTargetPresentableName(IAndroidTarget target) {
    return target.isPlatform() ?
           target.getName() :
           target.getName() + " (" + target.getVersionName() + ')';
  }

  public static void setUpSdk(Sdk androidSdk, @Nullable Sdk javaSdk, Sdk[] allSdks, IAndroidTarget target, boolean addRoots) {
    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(androidSdk, javaSdk);

    data.setBuildTarget(target);

    String sdkName = chooseNameForNewLibrary(target);
    sdkName = SdkConfigurationUtil.createUniqueSdkName(sdkName, Arrays.asList(allSdks));

    final SdkModificator sdkModificator = androidSdk.getSdkModificator();

    sdkModificator.setName(sdkName);

    if (javaSdk != null) {
      sdkModificator.setVersionString(javaSdk.getVersionString());
    }
    sdkModificator.setSdkAdditionalData(data);

    if (addRoots) {
      for (OrderRoot orderRoot : getLibraryRootsForTarget(target, androidSdk.getHomePath())) {
        sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
      }
    }

    sdkModificator.commitChanges();
  }

  public static boolean isApplicableJdk(Sdk jdk) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    return version == JavaSdkVersion.JDK_1_5 || version == JavaSdkVersion.JDK_1_6;
  }

  public static boolean targetHasId(@NotNull IAndroidTarget target, @NotNull String id) {
    return id.equals(target.getVersion().getApiString()) || id.equals(target.getVersionName());
  }

  @NotNull
  public static Collection<String> getAndroidSdkPathsFromExistingPlatforms() {
    final List<Sdk> androidSdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());
    final Set<String> result = new HashSet<String>(androidSdks.size());

    for (Sdk androidSdk : androidSdks) {
      final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)androidSdk.getSdkAdditionalData();
      if (data != null) {
        final AndroidPlatform androidPlatform = data.getAndroidPlatform();
        if (androidPlatform != null) {
          result.add(FileUtil.toSystemIndependentName(androidPlatform.getSdk().getLocation()));
        }
      }
    }

    return result;
  }

  private static boolean tryToSetAndroidPlatform(Module module, Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.parse(sdk);
    if (platform != null) {
      setSdk(module, sdk);
      return true;
    }
    return false;
  }

  private static void setSdk(Module module, Sdk sdk) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    model.setSdk(sdk);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

  private static void setupPlatform(@NotNull Module module) {
    if (tryToImportFromPropertyFiles(module)) {
      return;
    }

    PropertiesComponent component = PropertiesComponent.getInstance();
    if (component.isValueSet(DEFAULT_PLATFORM_NAME_PROPERTY)) {
      String defaultPlatformName = component.getValue(DEFAULT_PLATFORM_NAME_PROPERTY);
      Sdk defaultLib = ProjectJdkTable.getInstance().findJdk(defaultPlatformName, AndroidSdkType.getInstance().getName());
      if (defaultLib != null && tryToSetAndroidPlatform(module, defaultLib)) {
        return;
      }
    }
    for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance())) {
      if (tryToSetAndroidPlatform(module, sdk)) {
        component.setValue(DEFAULT_PLATFORM_NAME_PROPERTY, sdk.getName());
        return;
      }
    }
  }

  @Nullable
  private static Sdk findSuitableAndroidSdk(@NotNull String targetHashString, @Nullable String sdkDir) {
    final List<Sdk> androidSdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());
    for (Sdk sdk : androidSdks) {
      final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (data != null) {
        final AndroidPlatform androidPlatform = data.getAndroidPlatform();
        if (androidPlatform != null) {
          final String baseDir = FileUtil.toSystemIndependentName(androidPlatform.getSdk().getLocation());
          if ((sdkDir == null || FileUtil.pathsEqual(baseDir, sdkDir)) &&
              targetHashString.equals(androidPlatform.getTarget().hashString())) {
            return sdk;
          }
        }
      }
    }
    return null;
  }

  private static boolean tryToImportFromPropertyFiles(@NotNull Module module) {
    final String targetHashString = AndroidUtils.getProjectPropertyValue(module, "target");
    if (targetHashString == null) {
      return false;
    }

    String sdkDir = AndroidUtils.getPropertyValue(module, SdkConstants.FN_LOCAL_PROPERTIES, "sdk.dir");
    if (sdkDir != null) {
      sdkDir = FileUtil.toSystemIndependentName(sdkDir);
    }

    final Sdk sdk = findSuitableAndroidSdk(targetHashString, sdkDir);
    if (sdk != null) {
      setSdk(module, sdk);
      return true;
    }

    if (sdkDir != null && tryToCreateAndSetAndroidSdk(module, sdkDir, targetHashString)) {
      return true;
    }

    final String androidHomeValue = System.getenv(ANDROID_HOME_ENV);
    if (androidHomeValue != null &&
        tryToCreateAndSetAndroidSdk(module, FileUtil.toSystemIndependentName(androidHomeValue), targetHashString)) {
      return true;
    }

    for (String dir : getAndroidSdkPathsFromExistingPlatforms()) {
      if (tryToCreateAndSetAndroidSdk(module, dir, targetHashString)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryToCreateAndSetAndroidSdk(@NotNull Module module, @NotNull String baseDir, @NotNull String targetHashString) {
    final AndroidSdk sdkObject = AndroidSdk.parse(baseDir, new EmptySdkLog());
    if (sdkObject != null) {
      final IAndroidTarget target = sdkObject.findTargetByHashString(targetHashString);
      if (target != null) {
        final Sdk androidSdk = createNewAndroidPlatform(target, sdkObject.getLocation(), true);
        if (androidSdk != null) {
          setSdk(module, androidSdk);
          return true;
        }
      }
    }
    return false;
  }

  public static void setupAndroidPlatformInNeccessary(Module module) {
    Sdk currentSdk = ModuleRootManager.getInstance(module).getSdk();
    if (currentSdk == null || !(currentSdk.getSdkType().equals(AndroidSdkType.getInstance()))) {
      setupPlatform(module);
    }
  }

  public static void openModuleDependenciesConfigurable(final Module module) {
    ProjectSettingsService.getInstance(module.getProject()).openModuleDependenciesSettings(module, null);
  }

  @Nullable
  public static SdkManager createSdkManager(@NotNull String path, @NotNull ISdkLog log) {
    path = FileUtil.toSystemDependentName(path);

    final File f = new File(path);
    if (!f.exists() || !f.isDirectory()) {
      return null;
    }

    final File platformsDir = new File(f, SdkConstants.FD_PLATFORMS);
    if (!platformsDir.exists() || !platformsDir.isDirectory()) {
      return null;
    }

    return SdkManager.createManager(path + File.separatorChar, log);
  }
}
