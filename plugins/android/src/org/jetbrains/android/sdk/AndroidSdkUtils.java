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
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.OrderRoot;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 26, 2009
 * Time: 8:01:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidSdkUtils {
  public static final String DEFAULT_PLATFORM_NAME_PROPERTY = "AndroidPlatformName";
  @NonNls public static final String ANDROID_HOME_ENV = "ANDROID_HOME";

  private AndroidSdkUtils() {
  }

  public static boolean isAndroidSdk(@NotNull String path) {
    path = FileUtil.toSystemDependentName(path);
    SdkManager manager = SdkManager.createManager(path, new EmptySdkLog());
    return manager != null;
  }

  public static boolean isAndroidPlatform(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    if (parent == null) {
      return false;
    }
    VirtualFile sdkDir = parent.getParent();
    if (sdkDir == null) {
      return false;
    }
    AndroidSdk sdk = AndroidSdk.parse(sdkDir.getPath(), new EmptySdkLog());
    return sdk != null && sdk.findTargetByLocation(file.getPath()) != null;
  }

  @Nullable
  private static VirtualFile getPlatformDir(@NotNull IAndroidTarget target) {
    String platformPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    VirtualFile platformDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(platformPath);
    if (platformDir == null) return null;
    return platformDir;
  }

  public static VirtualFile[] chooseAndroidSdkPath(@NotNull Component parent) {
    return FileChooser.chooseFiles(parent, new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return super.isFileSelectable(file) && isAndroidSdk(file.getPath()) || isAndroidPlatform(file);
      }
    });
  }

  public static List<OrderRoot> getLibraryRootsForTarget(@NotNull IAndroidTarget target, @Nullable String sdkPath) {
    List<OrderRoot> result = new ArrayList<OrderRoot>();
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) return result;

    VirtualFile androidJar = platformDir.findChild(SdkConstants.FN_FRAMEWORK_LIBRARY);
    if (androidJar == null) return result;
    VirtualFile androidJarRoot = JarFileSystem.getInstance().findFileByPath(androidJar.getPath() + JarFileSystem.JAR_SEPARATOR);
    result.add(new OrderRoot(OrderRootType.CLASSES, androidJarRoot));

    IAndroidTarget.IOptionalLibrary[] libs = target.getOptionalLibraries();
    if (libs != null) {
      for (IAndroidTarget.IOptionalLibrary lib : libs) {
        VirtualFile libRoot = JarFileSystem.getInstance().findFileByPath(lib.getJarPath() + JarFileSystem.JAR_SEPARATOR);
        result.add(new OrderRoot(OrderRootType.CLASSES, libRoot));
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
      list.add(new OrderRoot(JavadocOrderRootType.getInstance(), javadocDir));
    }

    VirtualFile sourcesDir = dir.findChild(SdkConstants.FD_SOURCES);
    if (sourcesDir != null) {
      list.add(new OrderRoot(OrderRootType.SOURCES, sourcesDir));
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
}
