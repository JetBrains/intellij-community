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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.ILogger;
import com.intellij.CommonBundle;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.OSProcessManager;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.actions.AndroidRunDdmsAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.logcat.AndroidLogcatToolWindowFactory;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkUtils");

  public static final String DEFAULT_PLATFORM_NAME_PROPERTY = "AndroidPlatformName";
  @NonNls public static final String ANDROID_HOME_ENV = "ANDROID_HOME";

  private AndroidSdkUtils() {
  }

  @Nullable
  private static VirtualFile getPlatformDir(@NotNull IAndroidTarget target) {
    String platformPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    VirtualFile platformDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(platformPath);
    if (platformDir == null) return null;
    return platformDir;
  }

  @NotNull
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

    // todo: replace it by target.getPath(SOURCES) when it'll be up to date
    final VirtualFile sourcesDir = sdkDir.findChild(SdkConstants.FD_PKG_SOURCES);
    if (sourcesDir != null && sourcesDir.isDirectory()) {
      final VirtualFile platformSourcesDir = sourcesDir.findChild(platformDir.getName());
      if (platformSourcesDir != null && platformSourcesDir.isDirectory()) {
        result.add(new OrderRoot(platformSourcesDir, OrderRootType.SOURCES));
      }
    }

    final String resFolderPath = target.getPath(IAndroidTarget.RESOURCES);

    if (resFolderPath != null) {
      final VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(resFolderPath);

      if (resFolder != null) {
        result.add(new OrderRoot(resFolder, OrderRootType.CLASSES));
      }
    }

    if (sdkPath != null) {
      // todo: check if we should do it for new android platforms (api_level >= 15)
      final VirtualFile annotationsJar = JarFileSystem.getInstance()
        .findFileByPath(
          FileUtil.toSystemIndependentName(sdkPath) + AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH + JarFileSystem.JAR_SEPARATOR);
      if (annotationsJar != null) {
        result.add(new OrderRoot(annotationsJar, OrderRootType.CLASSES));
      }
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

  @NotNull
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

  public static void setUpSdk(@NotNull Sdk androidSdk,
                              @Nullable Sdk javaSdk,
                              @NotNull Sdk[] allSdks,
                              @NotNull IAndroidTarget target,
                              boolean addRoots) {
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

  public static boolean isApplicableJdk(@NotNull Sdk jdk) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    return version == JavaSdkVersion.JDK_1_5 || version == JavaSdkVersion.JDK_1_6 || version == JavaSdkVersion.JDK_1_7;
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
          result.add(FileUtil.toSystemIndependentName(androidPlatform.getSdkData().getLocation()));
        }
      }
    }

    return result;
  }

  private static boolean tryToSetAndroidPlatform(Module module, Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.parse(sdk);
    if (platform != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
      return true;
    }
    return false;
  }

  private static void setupPlatform(@NotNull Module module) {
    final String targetHashString = getTargetHashStringFromProperyFile(module);
    if (targetHashString != null && findAndSetSdkWithHashString(module, targetHashString)) {
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
          final String baseDir = FileUtil.toSystemIndependentName(androidPlatform.getSdkData().getLocation());
          if ((sdkDir == null || FileUtil.pathsEqual(baseDir, sdkDir)) &&
              targetHashString.equals(androidPlatform.getTarget().hashString())) {
            return sdk;
          }
        }
      }
    }
    return null;
  }

  private static String getTargetHashStringFromProperyFile(Module module) {
    final Pair<String, VirtualFile> targetProp = AndroidRootUtil.getProjectPropertyValue(module, AndroidUtils.ANDROID_TARGET_PROPERTY);
    return targetProp != null ? targetProp.getFirst() : null;
  }

  private static boolean findAndSetSdkWithHashString(Module module, String targetHashString) {
    final Pair<String, VirtualFile> sdkDirProperty = AndroidRootUtil.getPropertyValue(module, SdkConstants.FN_LOCAL_PROPERTIES, "sdk.dir");
    String sdkDir = sdkDirProperty != null ? sdkDirProperty.getFirst() : null;
    if (sdkDir != null) {
      sdkDir = FileUtil.toSystemIndependentName(sdkDir);
    }

    final Sdk sdk = findSuitableAndroidSdk(targetHashString, sdkDir);
    if (sdk != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
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
    final AndroidSdkData sdkData = AndroidSdkData.parse(baseDir, new EmptySdkLog());
    if (sdkData != null) {
      final IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
      if (target != null) {
        final Sdk androidSdk = createNewAndroidPlatform(target, sdkData.getLocation(), true);
        if (androidSdk != null) {
          ModuleRootModificationUtil.setModuleSdk(module, androidSdk);
          return true;
        }
      }
    }
    return false;
  }

  public static void setupAndroidPlatformInNecessary(@NotNull Module module, boolean forceImportFromProperties) {
    Sdk currentSdk = ModuleRootManager.getInstance(module).getSdk();

    if (currentSdk == null || !(currentSdk.getSdkType().equals(AndroidSdkType.getInstance()))) {
      setupPlatform(module);
    }
    else if (forceImportFromProperties) {
      final SdkAdditionalData data = currentSdk.getSdkAdditionalData();

      if (data instanceof AndroidSdkAdditionalData) {
        final AndroidPlatform platform = ((AndroidSdkAdditionalData)data).getAndroidPlatform();

        if (platform != null) {
          final String targetHashString = getTargetHashStringFromProperyFile(module);
          final String currentTargetHashString = platform.getTarget().hashString();

          if (targetHashString != null && !targetHashString.equals(currentTargetHashString)) {
            findAndSetSdkWithHashString(module, targetHashString);
          }
        }
      }
    }
  }

  public static void openModuleDependenciesConfigurable(final Module module) {
    ProjectSettingsService.getInstance(module.getProject()).openModuleDependenciesSettings(module, null);
  }

  @NotNull
  public static ILogger getSdkLog(@NotNull final Object o) {
    if (!(o instanceof Component || o instanceof Project)) {
      throw new IllegalArgumentException();
    }

    return new ILogger() {
      public void warning(String warningFormat, Object... args) {
        if (warningFormat != null) {
          LOG.warn(String.format(warningFormat, args));
        }
      }

      @Override
      public void info(@NonNull String msgFormat, Object... args) {
        if (msgFormat != null) {
          LOG.debug(String.format(msgFormat, args));
        }
      }

      @Override
      public void verbose(@NonNull String msgFormat, Object... args) {
      }

      public void error(Throwable t, String errorFormat, Object... args) {
        if (t != null) {
          LOG.info(t);
        }
        if (errorFormat != null) {
          String message = String.format(errorFormat, args);
          LOG.info(message);
          if (o instanceof Project) {
            Messages.showErrorDialog((Project)o, message, CommonBundle.getErrorTitle());
          }
          else {
            Messages.showErrorDialog((Component)o, message, CommonBundle.getErrorTitle());
          }
        }
      }
    };
  }

  @Nullable
  public static Sdk findAppropriateAndroidPlatform(@NotNull IAndroidTarget target, @NotNull AndroidSdkData sdkData) {
    for (Sdk library : ProjectJdkTable.getInstance().getAllJdks()) {
      final String homePath = library.getHomePath();

      if (homePath != null && library.getSdkType().equals(AndroidSdkType.getInstance())) {
        final AndroidSdkData sdkData1 = AndroidSdkData.parse(homePath, new EmptySdkLog());

        if (sdkData1 != null && sdkData1.equals(sdkData)) {
          final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)library.getSdkAdditionalData();

          if (data != null) {
            final IAndroidTarget target1 = data.getBuildTarget(sdkData1);

            if (target1 != null && target.hashString().equals(target1.hashString())) {
              return library;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      final AndroidDebugBridge debugBridge = facet.getDebugBridge();
      if (debugBridge != null) {
        return debugBridge;
      }
    }
    return null;
  }

  public static boolean activateDdmsIfNecessary(@NotNull Project project, @NotNull Computable<AndroidDebugBridge> bridgeProvider) {
    if (AndroidEnableAdbServiceAction.isAdbServiceEnabled()) {
      final AndroidDebugBridge bridge = bridgeProvider.compute();
      if (bridge != null && isDdmsCorrupted(bridge)) {
        LOG.info("DDMLIB is corrupted and will be restarted");
        restartDdmlib(project);
      }
    }
    else {
      final OSProcessHandler ddmsProcessHandler = AndroidRunDdmsAction.getDdmsProcessHandler();
      if (ddmsProcessHandler != null) {
        final int r = Messages
          .showYesNoDialog(project, "Monitor will be closed to activate ADB service. Continue?", "ADB activation", Messages.getQuestionIcon());

        if (r != Messages.YES) {
          return false;
        }

        final Runnable destroyingRunnable = new Runnable() {
          @Override
          public void run() {
            if (!ddmsProcessHandler.isProcessTerminated()) {
              OSProcessManager.getInstance().killProcessTree(ddmsProcessHandler.getProcess());
              ddmsProcessHandler.waitFor();
            }
          }
        };
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(destroyingRunnable, "Closing Monitor", true, project)) {
          return false;
        }

        AndroidEnableAdbServiceAction.setAdbServiceEnabled(project, true);
        return true;
      }

      int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.ddms.disabled.error"),
                                            AndroidBundle.message("android.ddms.disabled.dialog.title"),
                                            Messages.getQuestionIcon());
      if (result != 0) {
        return false;
      }
      AndroidEnableAdbServiceAction.setAdbServiceEnabled(project, true);
    }
    return true;
  }

  public static boolean canDdmsBeCorrupted(@NotNull AndroidDebugBridge bridge) {
    return isDdmsCorrupted(bridge) || allDevicesAreEmpty(bridge);
  }

  private static boolean allDevicesAreEmpty(@NotNull AndroidDebugBridge bridge) {
    for (IDevice device : bridge.getDevices()) {
      if (device.getClients().length > 0) {
        return false;
      }
    }
    return true;
  }

  public static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    // todo: find other way to check if debug service is available

    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      for (IDevice device : devices) {
        Client[] clients = device.getClients();

        if (clients.length > 0) {
          ClientData clientData = clients[0].getClientData();
          return clientData == null || clientData.getVmIdentifier() == null;
        }
      }
    }
    return false;
  }

  public static void restartDdmlib(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AndroidLogcatToolWindowFactory.TOOL_WINDOW_ID);
    boolean hidden = false;
    if (toolWindow != null && toolWindow.isVisible()) {
      hidden = true;
      toolWindow.hide(null);
    }
    AndroidSdkData.terminateDdmlib();
    if (hidden) {
      toolWindow.show(null);
    }
  }
}
