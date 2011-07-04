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
package org.jetbrains.android.util;

import com.android.ddmlib.*;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.actions.AndroidEnableDdmsAction;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.IntentFilter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.run.AndroidRunConfiguration;
import org.jetbrains.android.run.AndroidRunConfigurationType;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.EmptySdkLog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author yole, coyote
 */
public class AndroidUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidUtils");

  public static final String CLASSES_FILE_NAME = "classes.dex";

  public static final Icon ANDROID_ICON = IconLoader.getIcon("/icons/android.png");
  public static final Icon DDMS_ICON = IconLoader.getIcon("/icons/ddms.png");
  public static final Icon RESTART_LOGCAT_ICON = IconLoader.getIcon("/icons/restartLogcat.png");
  public static final String NAMESPACE_KEY = "android";
  public static final String SYSTEM_RESOURCE_PACKAGE = "android";
  public static final String R_JAVA_FILENAME = "R.java";
  public static final String ANDROID_PACKAGE = "android";
  public static final String VIEW_CLASS_NAME = ANDROID_PACKAGE + ".view.View";
  public static final String APPLICATION_CLASS_NAME = "android.app.Application";
  public static final String PREFERENCE_CLASS_NAME = ANDROID_PACKAGE + ".preference.Preference";
  public static final String ANIMATION_PACKAGE = "android.view.animation";
  public static final String INTERPOLATOR_CLASS_NAME = ANIMATION_PACKAGE + ".Interpolator";

  // tools
  public static final String APK_BUILDER = SystemInfo.isWindows ? "apkbuilder.bat" : "apkbuilder";
  public static final String EMULATOR = SystemInfo.isWindows ? "emulator.exe" : "emulator";
  public static final String ADB = SystemInfo.isWindows ? "adb.exe" : "adb";
  public static final String NAMESPACE_PREFIX = "http://schemas.android.com/apk/res/";
  public static final String ACTIVITY_BASE_CLASS_NAME = "android.app.Activity";
  public static final String R_CLASS_NAME = "R";
  public static final String R_CLASS_FILE_NAME = "R.class";
  public static final String LAUNCH_ACTION_NAME = "android.intent.action.MAIN";
  public static final String LAUNCH_CATEGORY_NAME = "android.intent.category.LAUNCHER";
  public static final String INSTRUMENTATION_RUNNER_BASE_CLASS = "android.app.Instrumentation";
  public static final Icon ANDROID_ICON_24 = IconLoader.getIcon("/icons/android24.png");
  public static final String EXT_NATIVE_LIB = "so";
  @NonNls public static final String RES_OVERLAY_DIR_NAME = "res-overlay";

  public static final int TIMEOUT = 3000000;

  private static final Key<ConsoleView> CONSOLE_VIEW_KEY = new Key<ConsoleView>("AndroidConsoleView");

  @NonNls public static final String ANDROID_LIBRARY_PROPERTY = "android.library";
  @NonNls public static final String ANDROID_TARGET_PROPERTY = "target";
  @NonNls public static final String ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX = "android.library.reference.";

  private AndroidUtils() {
  }

  public static ISdkLog getSdkLog(@NotNull final Object o) {
    if (!(o instanceof Component || o instanceof Project)) {
      throw new IllegalArgumentException();
    }
    return new ISdkLog() {
      public void warning(String warningFormat, Object... args) {
        if (warningFormat != null) {
          LOG.warn(String.format(warningFormat, args));
        }
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

      public void printf(String msgFormat, Object... args) {
        if (msgFormat != null) {
          LOG.info(String.format(msgFormat, args));
        }
      }
    };
  }

  public static String toolPath(@NotNull String toolFileName) {
    return SdkConstants.OS_SDK_TOOLS_FOLDER + toolFileName;
  }

  @Nullable
  public static <T extends DomElement> T loadDomElement(@NotNull final Module module,
                                                        @NotNull final VirtualFile file,
                                                        @NotNull final Class<T> aClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<T>() {
      @Nullable
      public T compute() {
        Project project = module.getProject();
        if (project.isDisposed()) return null;
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null || !(psiFile instanceof XmlFile)) {
          return null;
        }
        DomManager domManager = DomManager.getDomManager(project);
        DomFileElement<T> element = domManager.getFileElement((XmlFile)psiFile, aClass);
        if (element == null) return null;
        return element.getRootElement();
      }
    });
  }

  @Nullable
  public static VirtualFile findSourceRoot(@NotNull Module module, VirtualFile file) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    Set<VirtualFile> sourceRoots = new HashSet<VirtualFile>();
    Collections.addAll(sourceRoots, rootManager.getSourceRoots());
    while (file != null) {
      if (sourceRoots.contains(file)) {
        return file;
      }
      file = file.getParent();
    }
    return null;
  }

  @Nullable
  public static String getPackageName(@NotNull Module module, VirtualFile file) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    Set<VirtualFile> sourceRoots = new HashSet<VirtualFile>();
    Collections.addAll(sourceRoots, rootManager.getSourceRoots());
    VirtualFile projectDir = module.getProject().getBaseDir();
    List<String> packages = new ArrayList<String>();
    file = file.getParent();
    while (file != null && projectDir != file && !sourceRoots.contains(file)) {
      packages.add(file.getName());
      file = file.getParent();
    }
    if (file != null && sourceRoots.contains(file)) {
      StringBuilder packageName = new StringBuilder();
      for (int i = packages.size() - 1; i >= 0; i--) {
        packageName.append(packages.get(i));
        if (i > 0) packageName.append('.');
      }
      return packageName.toString();
    }
    return null;
  }

  public static boolean contains2Ids(String packageName) {
    return packageName.split("\\.").length >= 2;
  }

  public static boolean isRClassFile(@NotNull AndroidFacet facet, @NotNull PsiFile file) {
    if (file.getName().equals(R_JAVA_FILENAME) && file instanceof PsiJavaFile) {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      Manifest manifest = facet.getManifest();
      if (manifest == null) return false;

      String manifestPackage = manifest.getPackage().getValue();
      if (manifestPackage != null) {
        if (javaFile.getPackageName().equals(manifestPackage)) return true;
      }
      for (String aPackage : getDepLibsPackages(facet.getModule())) {
        if (javaFile.getPackageName().equals(aPackage)) return true;
      }
    }
    return false;
  }

  @Nullable
  public static XmlAttributeValue getNameAttrValue(XmlTag tag) {
    XmlAttribute attribute = tag.getAttribute("name");
    return attribute != null ? attribute.getValueElement() : null;
  }

  @Nullable
  public static String getLocalXmlNamespace(AndroidFacet facet) {
    final Manifest manifest = facet.getManifest();
    if (manifest != null) {
      String aPackage = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        public String compute() {
          return manifest.getPackage().getValue();
        }
      });
      if (aPackage != null && aPackage.length() != 0) {
        return NAMESPACE_PREFIX + aPackage;
      }
    }
    return null;
  }

  public static boolean isActivityLaunchable(@NotNull Module module, PsiClass c) {
    Activity activity = AndroidDomUtil.getActivityDomElementByClass(module, c);
    if (activity != null) {
      for (IntentFilter filter : activity.getIntentFilters()) {
        if (AndroidDomUtil.containsAction(filter, LAUNCH_ACTION_NAME)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void addRunConfiguration(final Project project,
                                         final AndroidFacet facet,
                                         @Nullable final String activityClass,
                                         boolean ask) {
    final Runnable r = new Runnable() {
      public void run() {
        RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
        Module module = facet.getModule();
        RunnerAndConfigurationSettings settings = runManager
          .createRunConfiguration(module.getName(), AndroidRunConfigurationType.getInstance().getFactory());
        AndroidRunConfiguration configuration = (AndroidRunConfiguration)settings.getConfiguration();
        configuration.setModule(module);
        if (activityClass != null) {
          configuration.MODE = AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
          configuration.ACTIVITY_CLASS = activityClass;
        }
        else {
          configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY;
        }
        runManager.addConfiguration(settings, false);
        runManager.setActiveConfiguration(settings);
      }
    };
    if (!ask) {
      r.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          String moduleName = facet.getModule().getName();
          int result = Messages.showYesNoDialog(project, AndroidBundle.message("create.run.configuration.question", moduleName),
                                                AndroidBundle.message("create.run.configuration.title"), Messages.getQuestionIcon());
          if (result == 0) {
            r.run();
          }
        }
      });
    }
  }

  @Nullable
  public static String getDefaultActivityName(@NotNull Manifest manifest) {
    Application application = manifest.getApplication();
    if (application != null) {
      for (Activity activity : application.getActivities()) {
        for (IntentFilter filter : activity.getIntentFilters()) {
          if (AndroidDomUtil.containsAction(filter, LAUNCH_ACTION_NAME) && AndroidDomUtil.containsCategory(filter, LAUNCH_CATEGORY_NAME)) {
            PsiClass c = activity.getActivityClass().getValue();
            return c != null ? c.getQualifiedName() : null;
          }
        }
      }
    }
    return null;
  }

  public static boolean isAbstract(PsiClass c) {
    return (c.isInterface() || c.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  @SuppressWarnings({"DuplicateThrows"})
  public static void executeCommand(IDevice device, String command, AndroidOutputReceiver receiver, boolean infinite) throws IOException,
                                                                                                                             TimeoutException,
                                                                                                                             AdbCommandRejectedException,
                                                                                                                             ShellCommandUnresponsiveException {
    int attempt = 0;
    while (attempt < 5) {
      if (infinite) {
        device.executeShellCommand(command, receiver, 0);
      }
      else {
        device.executeShellCommand(command, receiver, TIMEOUT);
      }
      if (infinite && !receiver.isCancelled()) {
        attempt++;
      }
      else if (receiver.isTryAgain()) {
        attempt++;
      }
      else {
        break;
      }
      receiver.invalidate();
    }
  }

  public static VirtualFile createChildDirectoryIfNotExist(Project project, VirtualFile parent, String name) throws IOException {
    VirtualFile child = parent.findChild(name);
    if (child == null) {
      return parent.createChildDirectory(project, name);
    }
    return child;
  }

  @NotNull
  public static PsiFile getFileTarget(@NotNull PsiElement target) {
    return target instanceof PsiFile ? (PsiFile)target : target.getContainingFile();
  }

  public static void navigateTo(@NotNull PsiElement[] targets, @Nullable RelativePoint pointToShowPopup) {
    if (targets.length == 0) {
      final JComponent renderer = HintUtil.createErrorLabel("Empty text");
      final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(renderer, renderer).createPopup();
      if (pointToShowPopup != null) {
        popup.show(pointToShowPopup);
      }
      return;
    }
    if (targets.length == 1 || pointToShowPopup == null) {
      PsiNavigateUtil.navigate(targets[0]);
    }
    else {
      DefaultPsiElementCellRenderer renderer = new DefaultPsiElementCellRenderer() {
        @Override
        public String getElementText(PsiElement element) {
          return getFileTarget(element).getName();
        }

        @Override
        public String getContainerText(PsiElement element, String name) {
          PsiDirectory dir = getFileTarget(element).getContainingDirectory();
          return dir == null ? "" : '(' + dir.getName() + ')';
        }
      };
      final JBPopup popup = NavigationUtil.getPsiElementPopup(targets, renderer, null);
      popup.show(pointToShowPopup);
    }
  }

  public static boolean executeCommand(GeneralCommandLine commandLine, final StringBuilder messageBuilder) throws ExecutionException {
    LOG.info(commandLine.getCommandLineString());
    OSProcessHandler handler = new OSProcessHandler(commandLine.createProcess(), "");
    handler.addProcessListener(new ProcessAdapter() {
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        messageBuilder.append(event.getText());
      }
    });
    handler.startNotify();
    try {
      handler.waitFor();
    }
    catch (ProcessCanceledException e) {
      return false;
    }
    String message = messageBuilder.toString();
    LOG.info(message);
    int exitCode = handler.getProcess().exitValue();
    return exitCode == 0;
  }

  public static void runExternalToolInSeparateThread(@NotNull final Project project,
                                                     @NotNull final GeneralCommandLine commandLine) {
    runExternalToolInSeparateThread(project, commandLine, null);
  }

  public static void runExternalToolInSeparateThread(@NotNull final Project project,
                                                     @NotNull final GeneralCommandLine commandLine,
                                                     @Nullable final ProcessHandler processHandler) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        runExternalTool(project, commandLine, false, processHandler);
      }
    });
  }

  public static void runExternalTool(final Project project,
                                     GeneralCommandLine commandLine,
                                     boolean printOutputToAndroidConsole,
                                     ProcessHandler processHandler) {
    String[] commands = commandLine.getCommands();
    String command = StringUtil.join(commands, " ");
    LOG.info("Execute: " + command);

    StringBuilder messageBuilder = new StringBuilder();
    String result;
    boolean success = false;
    try {
      success = executeCommand(commandLine, messageBuilder);
      result = messageBuilder.toString();
    }
    catch (ExecutionException e) {
      result = e.getMessage();
    }

    if (result != null) {
      if (printOutputToAndroidConsole) {
        final ConsoleViewContentType contentType = success ?
                                                   ConsoleViewContentType.NORMAL_OUTPUT :
                                                   ConsoleViewContentType.ERROR_OUTPUT;
        final String finalResult = result;
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            printMessageToConsole(project, finalResult, contentType);
          }
        });
      }
      else if (processHandler != null) {
        processHandler.notifyTextAvailable(result + '\n', ProcessOutputTypes.STDOUT);
      }
    }
  }

  public static String getSimpleNameByRelativePath(String relativePath) {
    relativePath = FileUtil.toSystemIndependentName(relativePath);
    int index = relativePath.lastIndexOf('/');
    if (index < 0) {
      return relativePath;
    }
    return relativePath.substring(index + 1);
  }

  @Nullable
  public static Sdk findAppropriateAndroidPlatform(IAndroidTarget target, AndroidSdk sdk) {
    String targetName = target.getName();
    if (targetName == null) {
      return null;
    }
    for (Sdk library : ProjectJdkTable.getInstance().getAllJdks()) {
      String homePath = library.getHomePath();
      if (homePath != null && library.getSdkType().equals(AndroidSdkType.getInstance())) {
        AndroidSdk sdk1 = AndroidSdk.parse(homePath, new EmptySdkLog());
        if (sdk1 != null && sdk1.equals(sdk)) {
          AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)library.getSdkAdditionalData();
          if (data != null) {
            IAndroidTarget target1 = data.getBuildTarget(sdk1);
            if (target1 != null && target.hashString().equals(target1.hashString())) {
              return library;
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<AndroidFacet> getAndroidDependencies(Module module, boolean androidLibrariesOnly) {
    List<AndroidFacet> depFacets = new ArrayList<AndroidFacet>();
    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          Module depModule = moduleOrderEntry.getModule();
          if (depModule != null) {
            AndroidFacet depFacet = AndroidFacet.getInstance(depModule);
            if (depFacet != null && (!androidLibrariesOnly || depFacet.getConfiguration().LIBRARY_PROJECT)) {
              depFacets.add(depFacet);
            }
          }
        }
      }
    }
    return depFacets;
  }

  public static List<AndroidFacet> getAllAndroidDependencies(Module module, boolean androidLibrariesOnly) {
    List<AndroidFacet> result = new ArrayList<AndroidFacet>();
    collectAllAndroidDependencies(module, androidLibrariesOnly, result, new HashSet<AndroidFacet>());
    return result;
  }

  private static void collectAllAndroidDependencies(Module module,
                                                    boolean androidLibrariesOnly,
                                                    List<AndroidFacet> result,
                                                    Set<AndroidFacet> visited) {
    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          Module depModule = moduleOrderEntry.getModule();
          if (depModule != null) {
            AndroidFacet depFacet = AndroidFacet.getInstance(depModule);
            if (depFacet != null && (!androidLibrariesOnly || depFacet.getConfiguration().LIBRARY_PROJECT)) {
              if (visited.add(depFacet)) {
                collectAllAndroidDependencies(depModule, androidLibrariesOnly, result, visited);
                result.add(0, depFacet);
              }
            }
          }
        }
      }
    }
  }

  public static Set<String> getDepLibsPackages(Module module) {
    Set<String> result = new HashSet<String>();
    collectDepLibsPackages(module, result, new HashSet<Module>());
    return result;
  }

  private static void collectDepLibsPackages(Module module, Collection<String> result, Set<Module> visited) {
    if (!visited.add(module)) {
      return;
    }
    for (AndroidFacet depFacet : getAllAndroidDependencies(module, true)) {
      Manifest manifest = depFacet.getManifest();
      if (manifest != null) {
        String aPackage = manifest.getPackage().getValue();
        if (aPackage != null) {
          result.add(aPackage);
        }
      }
    }
  }

  public static void collectModulesDependingOn(Module module, Set<Module> result) {
    if (!result.add(module)) {
      return;
    }
    for (Module mod : ModuleManager.getInstance(module.getProject()).getModules()) {
      if (ModuleRootManager.getInstance(mod).isDependsOn(module)) {
        collectModulesDependingOn(mod, result);
      }
    }
  }

  public static void printMessageToConsole(@NotNull Project project, @NotNull String s, @NotNull ConsoleViewContentType contentType) {
    activateConsoleToolWindow(project);
    final ConsoleView consoleView = project.getUserData(CONSOLE_VIEW_KEY);

    if (consoleView != null) {
      consoleView .print(s + '\n', contentType);
    }
  }

  private static void activateConsoleToolWindow(@NotNull Project project) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(project);
    final String toolWindowId = AndroidBundle.message("android.console.tool.window.title");

    ToolWindow toolWindow = manager.getToolWindow(toolWindowId);
    if (toolWindow != null) {
      return;
    }

    toolWindow = manager.registerToolWindow(toolWindowId, true, ToolWindowAnchor.BOTTOM);
    final ConsoleView console = new ConsoleViewImpl(project, false);
    project.putUserData(CONSOLE_VIEW_KEY, console);
    toolWindow.getContentManager().addContent(new ContentImpl(console.getComponent(), "", false));

    final ToolWindowManagerListener listener = new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        ToolWindow window = manager.getToolWindow(toolWindowId);
        if (window != null && !window.isVisible()) {
          manager.unregisterToolWindow(toolWindowId);
          ((ToolWindowManagerEx)manager).removeToolWindowManagerListener(this);
        }
      }
    };

    toolWindow.show(new Runnable() {
      @Override
      public void run() {
        ((ToolWindowManagerEx)manager).addToolWindowManagerListener(listener);
      }
    });
  }

  @Nullable
  public static AndroidDebugBridge getDebugBridge(Project project) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      final AndroidDebugBridge debugBridge = facet.getDebugBridge();
      if (debugBridge != null) {
        return debugBridge;
      }
    }
    return null;
  }

  public static boolean activateDdmsIfNeccessary(@NotNull AndroidFacet facet) {
    return activateDdmsIfNecessary(facet.getModule().getProject(),
                                   facet.getDebugBridge());
  }

  public static boolean activateDdmsIfNecessary(Project project, AndroidDebugBridge bridge) {
    final boolean ddmsEnabled = AndroidEnableDdmsAction.isDdmsEnabled();
    boolean shouldRestartDdms = !ddmsEnabled;

    if (ddmsEnabled && bridge != null && isDdmsCorrupted(bridge)) {
      shouldRestartDdms = true;
      AndroidEnableDdmsAction.setDdmsEnabled(project, false);
    }

    if (shouldRestartDdms) {
      if (!ddmsEnabled) {
        int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.ddms.disabled.error"),
                                              AndroidBundle.message("android.ddms.disabled.dialog.title"),
                                              Messages.getQuestionIcon());
        if (result != 0) {
          return false;
        }
      }
      AndroidEnableDdmsAction.setDdmsEnabled(project, true);
    }
    return true;
  }

  public static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    // todo: find other way to check if debug service is available

    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      Client[] clients = devices[0].getClients();

      if (clients.length == 0) {
        return true;
      }

      if (clients.length > 0) {
        ClientData clientData = clients[0].getClientData();
        return clientData == null || clientData.getVmIdentifier() == null;
      }
    }
    return false;
  }

  public static void addAndroidFacetIfNecessary(@NotNull final Module module,
                                                        @NotNull final VirtualFile contentRoot,
                                                        final boolean library) {
    if (AndroidFacet.getInstance(module) == null) {
      addAndroidFacet(module, contentRoot, library);
    }
  }

  @NotNull
  public static AndroidFacet addAndroidFacet(@NotNull final Module module, @NotNull final VirtualFile contentRoot, final boolean library) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<AndroidFacet>() {
      @Override
      public AndroidFacet compute() {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        final AndroidFacet facet = addAndroidFacet(model, contentRoot, library);
        model.commit();
        return facet;
      }
    });
  }

  @NotNull
  public static AndroidFacet addAndroidFacet(ModifiableRootModel rootModel, VirtualFile contentRoot, boolean library) {
    Module module = rootModel.getModule();
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);
    AndroidFacetConfiguration configuration = facet.getConfiguration();
    configuration.init(module, contentRoot);
    if (library) {
      configuration.LIBRARY_PROJECT = true;
    }
    model.addFacet(facet);
    model.commit();
    return facet;
  }

  @Nullable
  public static PropertiesFile findPropertyFile(@NotNull final Module module, @NotNull String propertyFileName) {
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      final VirtualFile vFile = contentRoot.findChild(propertyFileName);
      if (vFile != null) {
        final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
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

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static Pair<Properties, VirtualFile> readPropertyFile(@NotNull VirtualFile contentRoot, @NotNull String propertyFileName) {
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

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static String getPropertyValue(@NotNull Module module, @NotNull String propertyFileName, @NotNull String propertyKey) {
    final Pair<Properties, VirtualFile> pair = readPropertyFile(module, propertyFileName);
    if (pair != null) {
      final String value = pair.first.getProperty(propertyKey);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile findFileByAbsoluteOrRelativePath(@Nullable VirtualFile baseDir, @NotNull String path) {
    VirtualFile libDir = LocalFileSystem.getInstance().findFileByPath(path);
    if (libDir != null) {
      return libDir;
    }
    else if (baseDir != null) {
      return LocalFileSystem.getInstance().findFileByPath(baseDir.getPath() + '/' + path);
    }
    return null;
  }
}
