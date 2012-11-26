/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.android.SdkConstants;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.IntentFilter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.run.AndroidRunConfiguration;
import org.jetbrains.android.run.AndroidRunConfigurationBase;
import org.jetbrains.android.run.AndroidRunConfigurationType;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author yole, coyote
 */
public class AndroidUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidUtils");

  @NonNls public static final String NAMESPACE_KEY = "android";
  @NonNls public static final String SYSTEM_RESOURCE_PACKAGE = "android";

  // Classes and constants
  @NonNls public static final String VIEW_CLASS_NAME = "android.view.View";
  @NonNls public static final String APPLICATION_CLASS_NAME = "android.app.Application";
  @NonNls public static final String ACTIVITY_BASE_CLASS_NAME = "android.app.Activity";
  @NonNls public static final String R_CLASS_NAME = "R";
  @NonNls public static final String MANIFEST_CLASS_NAME = "Manifest";
  @NonNls public static final String LAUNCH_ACTION_NAME = "android.intent.action.MAIN";
  @NonNls public static final String LAUNCH_CATEGORY_NAME = "android.intent.category.LAUNCHER";
  @NonNls public static final String INSTRUMENTATION_RUNNER_BASE_CLASS = "android.app.Instrumentation";
  @NonNls public static final String SERVICE_CLASS_NAME = "android.app.Service";
  @NonNls public static final String RECEIVER_CLASS_NAME = "android.content.BroadcastReceiver";
  @NonNls public static final String PROVIDER_CLASS_NAME = "android.content.ContentProvider";

  public static final int TIMEOUT = 3000000;

  private static final Key<ConsoleView> CONSOLE_VIEW_KEY = new Key<ConsoleView>("AndroidConsoleView");

  // Properties
  @NonNls public static final String ANDROID_LIBRARY_PROPERTY = "android.library";
  @NonNls public static final String ANDROID_TARGET_PROPERTY = "target";
  @NonNls public static final String ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX = "android.library.reference.";
  @NonNls public static final String TAG_LINEAR_LAYOUT = "LinearLayout";

  private AndroidUtils() {
  }

  @Nullable
  public static <T extends DomElement> T loadDomElement(@NotNull final Module module,
                                                        @NotNull final VirtualFile file,
                                                        @NotNull final Class<T> aClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<T>() {
      @Nullable
      public T compute() {
        if (module.isDisposed()) {
          return null;
        }

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
    final Set<VirtualFile> sourceRoots = new HashSet<VirtualFile>();
    Collections.addAll(sourceRoots, ModuleRootManager.getInstance(module).getSourceRoots());

    while (file != null) {
      if (sourceRoots.contains(file)) {
        return file;
      }
      file = file.getParent();
    }
    return null;
  }

  @Nullable
  public static String computePackageName(@NotNull Module module, VirtualFile file) {
    final Set<VirtualFile> sourceRoots = new HashSet<VirtualFile>();
    Collections.addAll(sourceRoots, ModuleRootManager.getInstance(module).getSourceRoots());

    final VirtualFile projectDir = module.getProject().getBaseDir();
    final List<String> packages = new ArrayList<String>();
    file = file.getParent();

    while (file != null && !Comparing.equal(projectDir, file) && !sourceRoots.contains(file)) {
      packages.add(file.getName());
      file = file.getParent();
    }

    if (file != null && sourceRoots.contains(file)) {
      final StringBuilder packageName = new StringBuilder();

      for (int i = packages.size() - 1; i >= 0; i--) {
        packageName.append(packages.get(i));
        if (i > 0) packageName.append('.');
      }
      return packageName.toString();
    }
    return null;
  }

  public static void addRunConfiguration(@NotNull final AndroidFacet facet, @Nullable final String activityClass, final boolean ask,
                                         @Nullable final TargetSelectionMode targetSelectionMode,
                                         @Nullable final String preferredAvdName) {
    final Module module = facet.getModule();
    final Project project = module.getProject();

    final Runnable r = new Runnable() {
      public void run() {
        final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
        final RunnerAndConfigurationSettings settings = runManager.
          createRunConfiguration(module.getName(), AndroidRunConfigurationType.getInstance().getFactory());
        final AndroidRunConfiguration configuration = (AndroidRunConfiguration)settings.getConfiguration();
        configuration.setModule(module);

        if (activityClass != null) {
          configuration.MODE = AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
          configuration.ACTIVITY_CLASS = activityClass;
        }
        else {
          configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY;
        }

        if (targetSelectionMode != null) {
          configuration.setTargetSelectionMode(targetSelectionMode);
        }
        if (preferredAvdName != null) {
          configuration.PREFERRED_AVD = preferredAvdName;
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
          final String moduleName = facet.getModule().getName();
          final int result = Messages.showYesNoDialog(project, AndroidBundle.message("create.run.configuration.question", moduleName),
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

  public static boolean isAbstract(@NotNull PsiClass c) {
    return (c.isInterface() || c.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  public static void executeCommandOnDevice(@NotNull IDevice device,
                                            @NotNull String command,
                                            @NotNull AndroidOutputReceiver receiver,
                                            boolean infinite)
    throws IOException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
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
    final VirtualFile child = parent.findChild(name);
    return child == null ? parent.createChildDirectory(project, name) : child;
  }

  @Nullable
  public static PsiFile getContainingFile(@NotNull PsiElement element) {
    return element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
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
          final PsiFile file = getContainingFile(element);
          return file != null ? file.getName() : super.getElementText(element);
        }

        @Override
        public String getContainerText(PsiElement element, String name) {
          final PsiFile file = getContainingFile(element);
          final PsiDirectory dir = file != null ? file.getContainingDirectory() : null;
          return dir == null ? "" : '(' + dir.getName() + ')';
        }
      };
      final JBPopup popup = NavigationUtil.getPsiElementPopup(targets, renderer, null);
      popup.show(pointToShowPopup);
    }
  }

  @NotNull
  public static ExecutionStatus executeCommand(@NotNull GeneralCommandLine commandLine,
                                               @Nullable final OutputProcessor processor,
                                               @Nullable WaitingStrategies.Strategy strategy) throws ExecutionException {
    LOG.info(commandLine.getCommandLineString());
    OSProcessHandler handler = new OSProcessHandler(commandLine.createProcess(), "");

    final ProcessAdapter listener = new ProcessAdapter() {
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        if (processor != null) {
          final String message = event.getText();
          processor.onTextAvailable(message);
        }
      }
    };

    if (!(strategy instanceof WaitingStrategies.DoNotWait)) {
      handler.addProcessListener(listener);
    }

    handler.startNotify();
    try {
      if (!(strategy instanceof WaitingStrategies.WaitForever)) {
        if (strategy instanceof WaitingStrategies.WaitForTime) {
          handler.waitFor(((WaitingStrategies.WaitForTime)strategy).getTimeMs());
        }
      }
      else {
        handler.waitFor();
      }
    }
    catch (ProcessCanceledException e) {
      return ExecutionStatus.ERROR;
    }

    if (!handler.isProcessTerminated()) {
      return ExecutionStatus.TIMEOUT;
    }

    if (!(strategy instanceof WaitingStrategies.DoNotWait)) {
      handler.removeProcessListener(listener);
    }
    int exitCode = handler.getProcess().exitValue();
    return exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.ERROR;
  }

  @NotNull
  public static String getSimpleNameByRelativePath(@NotNull String relativePath) {
    relativePath = FileUtil.toSystemIndependentName(relativePath);
    int index = relativePath.lastIndexOf('/');
    if (index < 0) {
      return relativePath;
    }
    return relativePath.substring(index + 1);
  }

  public static void printMessageToConsole(@NotNull Project project, @NotNull String s, @NotNull ConsoleViewContentType contentType) {
    final ConsoleView consoleView = project.getUserData(CONSOLE_VIEW_KEY);

    if (consoleView != null) {
      consoleView.print(s + '\n', contentType);
    }
  }

  public static void activateConsoleToolWindow(@NotNull Project project, @NotNull final Runnable runAfterActivation) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(project);
    final String toolWindowId = AndroidBundle.message("android.console.tool.window.title");

    ToolWindow toolWindow = manager.getToolWindow(toolWindowId);
    if (toolWindow != null) {
      runAfterActivation.run();
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
          ((ToolWindowManagerEx)manager).removeToolWindowManagerListener(this);

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              manager.unregisterToolWindow(toolWindowId);
            }
          });
        }
      }
    };

    toolWindow.show(new Runnable() {
      @Override
      public void run() {
        runAfterActivation.run();
        ((ToolWindowManagerEx)manager).addToolWindowManagerListener(listener);
      }
    });
  }

  @NotNull
  public static AndroidFacet addAndroidFacetInWriteAction(@NotNull final Module module,
                                                          @NotNull final VirtualFile contentRoot,
                                                          final boolean library) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<AndroidFacet>() {
      @Override
      public AndroidFacet compute() {
        return addAndroidFacet(module, contentRoot, library);
      }
    });
  }

  @NotNull
  public static AndroidFacet addAndroidFacet(final Module module, @NotNull VirtualFile contentRoot,
                                             boolean library) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    AndroidFacet facet = model.getFacetByType(AndroidFacet.ID);

    if (facet == null) {
      facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);
      AndroidFacetConfiguration configuration = facet.getConfiguration();
      configuration.init(module, contentRoot);
      if (library) {
        configuration.LIBRARY_PROJECT = true;
      }
      model.addFacet(facet);
    }
    model.commit();

    return facet;
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

  public static int getIntAttrValue(@NotNull final XmlTag tag, @NotNull final String attrName) {
    String value = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return tag.getAttributeValue(attrName, SdkConstants.NS_RESOURCES);
      }
    });
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  public static void collectFiles(@NotNull VirtualFile root, @NotNull Set<VirtualFile> visited, @NotNull Set<VirtualFile> result) {
    if (!visited.add(root)) {
      return;
    }

    if (root.isDirectory()) {
      for (VirtualFile child : root.getChildren()) {
        collectFiles(child, visited, result);
      }
    }
    else {
      result.add(root);
    }
  }

  @Nullable
  public static TargetSelectionMode getDefaultTargetSelectionMode(@NotNull Module module,
                                                                  @NotNull ConfigurationType type,
                                                                  @NonNls ConfigurationType alternativeType) {
    final RunManager runManager = RunManager.getInstance(module.getProject());
    RunConfiguration[] configurations = runManager.getConfigurations(type);

    TargetSelectionMode alternative = null;

    if (configurations.length > 0) {
      for (RunConfiguration configuration : configurations) {
        if (configuration instanceof AndroidRunConfigurationBase) {
          final AndroidRunConfigurationBase runConfig = (AndroidRunConfigurationBase)configuration;
          final TargetSelectionMode targetMode = runConfig.getTargetSelectionMode();

          if (runConfig.getConfigurationModule() == module) {
            return targetMode;
          }
          else {
            alternative = targetMode;
          }
        }
      }
    }

    if (alternative != null) {
      return alternative;
    }
    configurations = runManager.getConfigurations(alternativeType);

    if (configurations.length > 0) {
      for (RunConfiguration configuration : configurations) {
        if (configuration instanceof AndroidRunConfigurationBase) {
          return ((AndroidRunConfigurationBase)configuration).getTargetSelectionMode();
        }
      }
    }
    return null;
  }

  public static boolean equal(@Nullable String s1, @Nullable String s2, boolean distinguishDelimeters) {
    if (s1 == null || s2 == null) {
      return false;
    }
    if (s1.length() != s2.length()) return false;
    for (int i = 0, n = s1.length(); i < n; i++) {
      char c1 = s1.charAt(i);
      char c2 = s2.charAt(i);
      if (distinguishDelimeters || (Character.isLetterOrDigit(c1) && Character.isLetterOrDigit(c2))) {
        if (c1 != c2) return false;
      }
    }
    return true;
  }

  @NotNull
  public static List<AndroidFacet> getApplicationFacets(@NotNull Project project) {
    final List<AndroidFacet> result = new ArrayList<AndroidFacet>();

    for (AndroidFacet facet : ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID)) {
      if (!facet.getConfiguration().LIBRARY_PROJECT) {
        result.add(facet);
      }
    }
    return result;
  }

  @NotNull
  public static List<AndroidFacet> getAndroidLibraryDependencies(@NotNull Module module) {
    final List<AndroidFacet> depFacets = new ArrayList<AndroidFacet>();

    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;

        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          final Module depModule = moduleOrderEntry.getModule();

          if (depModule != null) {
            final AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

            if (depFacet != null && depFacet.getConfiguration().LIBRARY_PROJECT) {
              depFacets.add(depFacet);
            }
          }
        }
      }
    }
    return depFacets;
  }

  @NotNull
  public static List<AndroidFacet> getAllAndroidDependencies(@NotNull Module module, boolean androidLibrariesOnly) {
    final List<AndroidFacet> result = new ArrayList<AndroidFacet>();
    collectAllAndroidDependencies(module, androidLibrariesOnly, result, new HashSet<AndroidFacet>());
    return result;
  }

  private static void collectAllAndroidDependencies(Module module,
                                                    boolean androidLibrariesOnly,
                                                    List<AndroidFacet> result,
                                                    Set<AndroidFacet> visited) {
    final OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    // loop in the inverse order to resolve dependencies on the libraries, so that if a library
    // is required by two higher level libraries it can be inserted in the correct place

    for (int i = entries.length - 1; i >= 0; i--) {
      final OrderEntry orderEntry = entries[i];
      if (orderEntry instanceof ModuleOrderEntry) {
        final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;

        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          final Module depModule = moduleOrderEntry.getModule();

          if (depModule != null) {
            final AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

            if (depFacet != null &&
                (!androidLibrariesOnly || depFacet.getConfiguration().LIBRARY_PROJECT) &&
                visited.add(depFacet)) {
              collectAllAndroidDependencies(depModule, androidLibrariesOnly, result, visited);
              result.add(0, depFacet);
            }
          }
        }
      }
    }
  }

  @NotNull
  public static Set<String> getDepLibsPackages(Module module) {
    final Set<String> result = new HashSet<String>();
    final HashSet<Module> visited = new HashSet<Module>();

    if (visited.add(module)) {
      for (AndroidFacet depFacet : getAllAndroidDependencies(module, true)) {
        final Manifest manifest = depFacet.getManifest();

        if (manifest != null) {
          String aPackage = manifest.getPackage().getValue();
          if (aPackage != null) {
            result.add(aPackage);
          }
        }
      }
    }
    return result;
  }

  public static void checkNewPassword(JPasswordField passwordField, JPasswordField confirmedPasswordField) throws CommitStepException {
    char[] password = passwordField.getPassword();
    char[] confirmedPassword = confirmedPasswordField.getPassword();
    try {
      checkPassword(password);
      if (password.length < 6) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.incorrect.password.length"));
      }
      if (!Arrays.equals(password, confirmedPassword)) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.passwords.not.match.error"));
      }
    }
    finally {
      Arrays.fill(password, '\0');
      Arrays.fill(confirmedPassword, '\0');
    }
  }

  public static void checkPassword(char[] password) throws CommitStepException {
    if (password.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.password.error"));
    }
  }

  public static void checkPassword(JPasswordField passwordField) throws CommitStepException {
    char[] password = passwordField.getPassword();
    try {
      checkPassword(password);
    }
    finally {
      Arrays.fill(password, '\0');
    }
  }

  @NotNull
  public static <T> List<T> toList(@NotNull Enumeration<T> enumeration) {
    final List<T> result = new ArrayList<T>();
    while (enumeration.hasMoreElements()) {
      result.add(enumeration.nextElement());
    }
    return result;
  }

  public static void reportError(@NotNull Project project, @NotNull String message) {
    reportError(project, message, CommonBundle.getErrorTitle());
  }

  public static void reportError(@NotNull Project project, @NotNull String message, @NotNull String title) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IncorrectOperationException(message);
    }
    else {
      Messages.showErrorDialog(project, message, title);
    }
  }

  public static void showStackStace(@NotNull final Project project, @NotNull Throwable[] throwables) {
    final StringBuilder messageBuilder = new StringBuilder();

    for (Throwable t : throwables) {
      if (messageBuilder.length() > 0) {
        messageBuilder.append("\n\n");
      }
      messageBuilder.append(AndroidCommonUtils.getStackTrace(t));
    }

    final DialogWrapper wrapper = new DialogWrapper(project, false) {

      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        final JTextArea textArea = new JTextArea(messageBuilder.toString());
        textArea.setEditable(false);
        textArea.setRows(40);
        textArea.setColumns(70);
        panel.add(ScrollPaneFactory.createScrollPane(textArea));
        return panel;
      }
    };
    wrapper.setTitle("Stack trace");
    wrapper.show();
  }

  public static boolean isValidPackageName(@NotNull String name) {
    int index = 0;
    while (true) {
      int index1 = name.indexOf('.', index);
      if (index1 < 0) index1 = name.length();
      if (!isIdentifier(name.substring(index, index1))) return false;
      if (index1 == name.length()) return true;
      index = index1 + 1;
    }
  }

  public static boolean isIdentifier(@NotNull String candidate) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
    lexer.start(candidate);
    if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }
}
