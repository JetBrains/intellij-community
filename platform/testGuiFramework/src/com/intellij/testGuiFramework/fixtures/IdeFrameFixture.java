/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.util.io.FileUtil.getRelativePath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class IdeFrameFixture extends ComponentFixture<IdeFrameFixture, IdeFrameImpl> {
  @NotNull private final File myProjectPath;

  private EditorFixture myEditor;
  private MainToolbarFixture myToolbar;
  private NavigationBarFixture myNavBar;

  @NotNull
  public static IdeFrameFixture find(@NotNull final Robot robot, @Nullable final File projectPath, @Nullable final String projectName, long timeoutInSeconds) {
    final GenericTypeMatcher<IdeFrameImpl> matcher = new GenericTypeMatcher<IdeFrameImpl>(IdeFrameImpl.class) {
      @Override
      protected boolean isMatching(@NotNull IdeFrameImpl frame) {
        Project project = frame.getProject();
        if (projectPath == null && project != null) return true;
        if (project != null &&
            PathManager.getAbsolutePath(projectPath.getPath()).equals(PathManager.getAbsolutePath(project.getBasePath()))) {
          return projectName == null || projectName.equals(project.getName());
        }
        return false;
      }
    };

    try {


      pause(new Condition("IdeFrame " + (projectPath != null ? quote(projectPath.getPath()) : "") + " to show up") {
        @Override
        public boolean test() {
          Collection<IdeFrameImpl> frames = robot.finder().findAll(matcher);
          return !frames.isEmpty();
        }
      }, Timeout.timeout(timeoutInSeconds, TimeUnit.SECONDS));

      IdeFrameImpl ideFrame = robot.finder().find(matcher);
      return new IdeFrameFixture(robot, ideFrame, new File(ideFrame.getProject().getBasePath()));
    } catch (WaitTimedOutError timedOutError) {
      throw new ComponentLookupException("Unable to find IdeFrame in " + timeoutInSeconds + " second(s)");
    }
  }

  public static IdeFrameFixture find(@NotNull final Robot robot, @Nullable final File projectPath, @Nullable final String projectName) {
    return find(robot, projectPath, projectName, 120L);
  }

  public IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target, @NotNull File projectPath) {
    super(IdeFrameFixture.class, robot, target);
    myProjectPath = projectPath;
    final Project project = getProject();

    Disposable disposable = new NoOpDisposable();
    Disposer.register(project, disposable);

    //myGradleProjectEventListener = new GradleProjectEventListener();

    //GradleSyncState.subscribe(project, myGradleProjectEventListener);
    //PostProjectBuildTasksExecutor.subscribe(project, myGradleProjectEventListener);
  }

  @NotNull
  public File getProjectPath() {
    return myProjectPath;
  }

  @NotNull
  public List<String> getModuleNames() {
    List<String> names = new ArrayList<>();
    for (Module module : getModuleManager().getModules()) {
      names.add(module.getName());
    }
    return names;
  }

  @NotNull
  public IdeFrameFixture requireModuleCount(int expected) {
    Module[] modules = getModuleManager().getModules();
    assertThat(modules).as("Module count in project " + quote(getProject().getName())).hasSize(expected);
    return this;
  }


  @NotNull
  public Collection<String> getSourceFolderRelativePaths(@NotNull String moduleName, @NotNull final JpsModuleSourceRootType<?> sourceType) {
    final Set<String> paths = new HashSet<>();

    Module module = getModule(moduleName);
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          for (ContentEntry contentEntry : rootModel.getContentEntries()) {
            for (SourceFolder folder : contentEntry.getSourceFolders()) {
              JpsModuleSourceRootType<?> rootType = folder.getRootType();
              if (rootType.equals(sourceType)) {
                String path = urlToPath(folder.getUrl());
                String relativePath = getRelativePath(myProjectPath, new File(toSystemDependentName(path)));
                paths.add(relativePath);
              }
            }
          }
        }
        finally {
          rootModel.dispose();
        }
      }
    });

    return paths;
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    Module module = findModule(name);
    assertNotNull("Unable to find module with name " + quote(name), module);
    return module;
  }

  @Nullable
  public Module findModule(@NotNull String name) {
    for (Module module : getModuleManager().getModules()) {
      if (name.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }


  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(getProject());
  }

  @NotNull
  public EditorFixture getEditor() {
    if (myEditor == null) {
      myEditor = new EditorFixture(robot(), this);
    }

    return myEditor;
  }

  @NotNull
  public MainToolbarFixture getToolbar() {
    if (myToolbar == null) {
      myToolbar = MainToolbarFixture.Companion.createMainToolbarFixture(robot(), this);
    }

    return myToolbar;
  }

  @NotNull
  public NavigationBarFixture getNavigationBar() {
    if (myNavBar == null) {
      myNavBar = NavigationBarFixture.Companion.createNavigationBarFixture(robot(), this);
    }

    return myNavBar;
  }

  //@NotNull
  //public GradleInvocationResult invokeProjectMake() {
  //  return invokeProjectMake(null);
  //}

  //@NotNull
  //public GradleInvocationResult invokeProjectMake(@Nullable Runnable executeAfterInvokingMake) {
  //  myGradleProjectEventListener.reset();
  //
  //  final AtomicReference<GradleInvocationResult> resultRef = new AtomicReference<GradleInvocationResult>();
  //  AndroidProjectBuildNotifications.subscribe(getProject(), new AndroidProjectBuildNotifications.AndroidProjectBuildListener() {
  //    @Override
  //    public void buildComplete(@NotNull AndroidProjectBuildNotifications.BuildContext context) {
  //      if (context instanceof GradleBuildContext) {
  //        resultRef.set(((GradleBuildContext)context).getBuildResult());
  //      }
  //    }
  //  });
  //  selectProjectMakeAction();
  //
  //  if (executeAfterInvokingMake != null) {
  //    executeAfterInvokingMake.run();
  //  }
  //
  //  waitForBuildToFinish(COMPILE_JAVA);
  //
  //  GradleInvocationResult result = resultRef.get();
  //  assertNotNull(result);
  //
  //  return result;
  //}

  @NotNull
  public IdeFrameFixture invokeProjectMakeAndSimulateFailure(@NotNull final String failure) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(failure);
    };
    //ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, failTask);
    selectProjectMakeAction();
    return this;
  }


  /**
   * Finds the Run button in the IDE interface.
   *
   * @return ActionButtonFixture for the run button.
   */
  @NotNull
  public ActionButtonFixture findRunApplicationButton() {
    return findActionButtonByActionId("Run");
  }

  public void debugApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Debug").click();
  }

  public void runApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Run").click();
  }


  @NotNull
  public RunToolWindowFixture getRunToolWindow() {
    return new RunToolWindowFixture(this);
  }

  @NotNull
  public DebugToolWindowFixture getDebugToolWindow() {
    return new DebugToolWindowFixture(this);
  }

  protected void selectProjectMakeAction() {
    invokeMenuPath("Build", "Make Project");
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public void invokeMenuPath(@NotNull String... path) {
    getMenuFixture().invokeMenuPath(path);
  }

  /**
   * Invokes an action from main menu
   *
   * @param mainMenuAction is the typical AnAction with ActionPlaces.MAIN_MENU
   */
  public void invokeMainMenu(@NotNull String mainMenuActionId) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction mainMenuAction = actionManager.getAction(mainMenuActionId);
    JMenuBar jMenuBar = this.target().getRootPane().getJMenuBar();
    MouseEvent fakeMainMenuMouseEvent =
      new MouseEvent(jMenuBar, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, MouseInfo.getPointerInfo().getLocation().x,
                     MouseInfo.getPointerInfo().getLocation().y, 1, false);
    ApplicationManager.getApplication()
      .invokeLater(() -> actionManager.tryToExecute(mainMenuAction, fakeMainMenuMouseEvent, null, ActionPlaces.MAIN_MENU, true));
  }

  /**
   * Invokes an action by menu path (where each segment is a regular expression). This is particularly
   * useful when the menu items can change dynamically, such as the labels of Undo actions, Run actions,
   * etc.
   *
   * @param path the series of menu name regular expressions, e.g. {@link invokeActionByMenuPath("Build", "Make( Project)?")}
   */
  public void invokeMenuPathRegex(@NotNull String... path) {
    getMenuFixture().invokeMenuPathRegex(path);
  }

  @NotNull
  private MenuFixture getMenuFixture() {
    return new MenuFixture(robot(), target());
  }

  //@NotNull
  //public IdeFrameFixture waitForBuildToFinish(@NotNull final BuildMode buildMode) {
  //  final Project project = getProject();
  //  if (buildMode == SOURCE_GEN && !GradleProjectBuilder.getInstance(project).isSourceGenerationEnabled()) {
  //    return this;
  //  }
  //
  //  pause(new Condition("Build (" + buildMode + ") for project " + quote(project.getName()) + " to finish'") {
  //    @Override
  //    public boolean test() {
  //      if (buildMode == SOURCE_GEN) {
  //        PostProjectBuildTasksExecutor tasksExecutor = PostProjectBuildTasksExecutor.getInstance(project);
  //        if (tasksExecutor.getLastBuildTimestamp() > -1) {
  //          // This will happen when creating a new project. Source generation happens before the IDE frame is found and build listeners
  //          // are created. It is fairly safe to assume that source generation happened if we have a timestamp for a "last performed build".
  //          return true;
  //        }
  //      }
  //      return myGradleProjectEventListener.isBuildFinished(buildMode);
  //    }
  //  }, LONG_TIMEOUT);
  //
  //  waitForBackgroundTasksToFinish();
  //  robot().waitForIdle();
  //
  //  return this;
  //}

  @NotNull
  public FileFixture findExistingFileByRelativePath(@NotNull String relativePath) {
    VirtualFile file = findFileByRelativePath(relativePath, true);
    return new FileFixture(getProject(), file);
  }

  @Nullable
  @Contract("_, true -> !null")
  public VirtualFile findFileByRelativePath(@NotNull String relativePath, boolean requireExists) {
    //noinspection Contract
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    Project project = getProject();
    VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
    if (requireExists) {
      //noinspection Contract
      assertNotNull("Unable to find file with relative path " + quote(relativePath), file);
    }
    return file;
  }

  //@NotNull
  //public IdeFrameFixture requestProjectSyncAndExpectFailure() {
  //  requestProjectSync();
  //  return waitForGradleProjectSyncToFail();
  //}

  @NotNull
  public IdeFrameFixture requestProjectSyncAndSimulateFailure(@NotNull final String failure) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(failure);
    };
    //ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY, failTask);
    // When simulating the error, we don't have to wait for sync to happen. Sync never happens because the error is thrown before it (sync)
    // is started.
    return requestProjectSync();
  }

  @NotNull
  public IdeFrameFixture requestProjectSync() {
    //myGradleProjectEventListener.reset();

    // We wait until all "Run Configurations" are populated in the toolbar combo-box. Until then the "Project Sync" button is not in its
    // final position, and FEST will click the wrong button.
    pause(new Condition("Waiting for 'Run Configurations' to be populated") {
      @Override
      public boolean test() {
        RunConfigurationComboBoxFixture runConfigurationComboBox = RunConfigurationComboBoxFixture.find(IdeFrameFixture.this);
        return isNotEmpty(runConfigurationComboBox.getText());
      }
    }, GuiTestUtil.SHORT_TIMEOUT);

    waitForBackgroundTasksToFinish();
    findGradleSyncAction().waitUntilEnabledAndShowing();
    // TODO figure out why in IDEA 15 even though an action is enabled, visible and showing, clicking it (via UI testing infrastructure)
    // does not work consistently

    return this;
  }

  @NotNull
  private ActionButtonFixture findGradleSyncAction() {
    return findActionButtonByActionId("Android.SyncProject");
  }

  //@NotNull
  //public IdeFrameFixture waitForGradleProjectSyncToFail() {
  //  try {
  //    waitForGradleProjectSyncToFinish(true);
  //    fail("Expecting project sync to fail");
  //  }
  //  catch (RuntimeException expected) {
  //    // expected failure.
  //  }
  //  return waitForBackgroundTasksToFinish();
  //}

  //@NotNull
  //public IdeFrameFixture waitForGradleProjectSyncToStart() {
  //  Project project = getProject();
  //  final GradleSyncState syncState = GradleSyncState.getInstance(project);
  //  if (!syncState.isSyncInProgress()) {
  //    pause(new Condition("Syncing project " + quote(project.getName()) + " to finish") {
  //      @Override
  //      public boolean test() {
  //        return myGradleProjectEventListener.isSyncStarted();
  //      }
  //    }, SHORT_TIMEOUT);
  //  }
  //  return this;
  //}

  //@NotNull
  //public IdeFrameFixture waitForGradleProjectSyncToFinish() {
  //  waitForGradleProjectSyncToFinish(false);
  //  return this;
  //}

  //private void waitForGradleProjectSyncToFinish(final boolean expectSyncFailure) {
  //  final Project project = getProject();
  //
  //  // ensure GradleInvoker (in-process build) is always enabled.
  //  AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
  //  buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = true;
  //
  //  pause(new Condition("Syncing project " + quote(project.getName()) + " to finish") {
  //    @Override
  //    public boolean test() {
  //      GradleSyncState syncState = GradleSyncState.getInstance(project);
  //      boolean syncFinished =
  //        (myGradleProjectEventListener.isSyncFinished() || syncState.isSyncNeeded() != ThreeState.YES) && !syncState.isSyncInProgress();
  //      if (expectSyncFailure) {
  //        syncFinished = syncFinished && myGradleProjectEventListener.hasSyncError();
  //      }
  //      return syncFinished;
  //    }
  //  }, LONG_TIMEOUT);
  //
  //  findGradleSyncAction().waitUntilEnabledAndShowing();
  //
  //  if (myGradleProjectEventListener.hasSyncError()) {
  //    RuntimeException syncError = myGradleProjectEventListener.getSyncError();
  //    myGradleProjectEventListener.reset();
  //    throw syncError;
  //  }
  //
  //  if (!myGradleProjectEventListener.isSyncSkipped()) {
  //    waitForBuildToFinish(SOURCE_GEN);
  //  }
  //
  //  waitForBackgroundTasksToFinish();
  //}

  @NotNull
  public IdeFrameFixture waitForBackgroundTasksToFinish() {
    Pause.pause(new Condition("Background tasks to finish") {
                  @Override
                  public boolean test() {
                    ProgressManager progressManager = ProgressManager.getInstance();
                    return !progressManager.hasModalProgressIndicator() &&
                           !progressManager.hasProgressIndicator() &&
                           !progressManager.hasUnsafeProgressIndicator();
                  }
                }
      , GuiTestUtil.LONG_TIMEOUT);
    robot().waitForIdle();
    return this;
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  public MessagesToolWindowFixture getMessagesToolWindow() {
    return new MessagesToolWindowFixture(getProject(), robot());
  }


  @NotNull
  public EditorNotificationPanelFixture requireEditorNotification(@NotNull final String message) {
    final Ref<EditorNotificationPanel> notificationPanelRef = new Ref<EditorNotificationPanel>();

    pause(new Condition("Notification with message '" + message + "' shows up") {
      @Override
      public boolean test() {
        EditorNotificationPanel notificationPanel = findNotificationPanel(message);
        notificationPanelRef.set(notificationPanel);
        return notificationPanel != null;
      }
    });

    EditorNotificationPanel notificationPanel = notificationPanelRef.get();
    assertNotNull(notificationPanel);
    return new EditorNotificationPanelFixture(robot(), notificationPanel);
  }

  public void requireNoEditorNotification() {
    assertNull(findNotificationPanel(null));
  }

  /**
   * Locates an editor notification with the given main message (unless the message is {@code null}, in which case we assert that there are
   * no visible editor notifications. Will fail if the given notification is not found.
   */
  @Nullable
  private EditorNotificationPanel findNotificationPanel(@Nullable String message) {
    Collection<EditorNotificationPanel> panels = robot().finder().findAll(target(), new GenericTypeMatcher<EditorNotificationPanel>(
      EditorNotificationPanel.class, true) {
      @Override
      protected boolean isMatching(@NotNull EditorNotificationPanel panel) {
        return panel.isShowing();
      }
    });

    if (message == null) {
      if (!panels.isEmpty()) {
        List<String> labels = new ArrayList<>();
        for (EditorNotificationPanel panel : panels) {
          labels.addAll(getEditorNotificationLabels(panel));
        }
        fail("Found editor notifications when none were expected" + labels);
      }
      return null;
    }

    List<String> labels = new ArrayList<>();
    for (EditorNotificationPanel panel : panels) {
      List<String> found = getEditorNotificationLabels(panel);
      labels.addAll(found);
      for (String label : found) {
        if (label.contains(message)) {
          return panel;
        }
      }
    }

    return null;
  }

  /**
   * Looks up the main label for a given editor notification panel
   */
  private List<String> getEditorNotificationLabels(@NotNull EditorNotificationPanel panel) {
    final List<String> allText = new ArrayList<>();
    final Collection<JLabel> labels = robot().finder().findAll(panel, JLabelMatcher.any().andShowing());
    for (final JLabel label : labels) {
      String text = execute(new GuiQuery<String>() {
        @Override
        @Nullable
        protected String executeInEDT() throws Throwable {
          return label.getText();
        }
      });
      if (isNotEmpty(text)) {
        allText.add(text);
      }
    }
    return allText;
  }

  @NotNull
  public IdeSettingsDialogFixture openIdeSettings() {
    // Using invokeLater because we are going to show a *modal* dialog via API (instead of clicking a button, for example.) If we use
    // GuiActionRunner the test will hang until the modal dialog is closed.
    ApplicationManager.getApplication().invokeLater(() -> {
      Project project = getProject();
      ShowSettingsUtil.getInstance().showSettingsDialog(project, ShowSettingsUtilImpl.getConfigurableGroups(project, true));
    });
    return IdeSettingsDialogFixture.find(robot());
  }


  @NotNull
  public RunConfigurationsDialogFixture invokeRunConfigurationsDialog() {
    invokeMenuPath("Run", "Edit Configurations...");
    return RunConfigurationsDialogFixture.find(robot());
  }

  @NotNull
  public InspectionsFixture inspectCode() {
    invokeMenuPath("Analyze", "Inspect Code...");

    //final Ref<FileChooserDialogImpl> wrapperRef = new Ref<FileChooserDialogImpl>();
    JDialog dialog = robot().finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "Specify Inspection Scope".equals(dialog.getTitle());
      }
    });
    JButton button = robot().finder().find(dialog, JButtonMatcher.withText("OK").andShowing());
    robot().click(button);

    final InspectionTree tree = GuiTestUtil.waitUntilFound(robot(), new GenericTypeMatcher<InspectionTree>(InspectionTree.class) {
      @Override
      protected boolean isMatching(@NotNull InspectionTree component) {
        return true;
      }
    });

    return new InspectionsFixture(robot(), getProject(), tree);
  }

  @NotNull
  public ProjectViewFixture getProjectView() {
    return new ProjectViewFixture(getProject(), robot());
  }

  @NotNull
  public Project getProject() {
    Project project = target().getProject();
    assertNotNull(project);
    return project;
  }

  public void closeProject() {
    invokeMainMenu("CloseProject");
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        RecentProjectsManager.getInstance().updateLastProjectPath();
        WelcomeFrame.showIfNoProjectOpened();
      }
    });
    pause(new Condition("Waiting for 'Welcome' page to show up") {
      @Override
      public boolean test() {
        for (Frame frame : Frame.getFrames()) {
          if (frame == WelcomeFrame.getInstance() && frame.isShowing()) {
            return true;
          }
        }
        return false;
      }
    });
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), target(), title);
  }


  @NotNull
  public FindDialogFixture invokeFindInPathDialog() {
    invokeMenuPath("Edit", "Find", "Find in Path...");
    return FindDialogFixture.find(robot());
  }

  @NotNull
  public FindToolWindowFixture getFindToolWindow() {
    return new FindToolWindowFixture(this);
  }

  //@NotNull
  //public GradleBuildModelFixture parseBuildFileForModule(@NotNull String moduleName, boolean openInEditor) {
  //  Module module = getModule(moduleName);
  //  return parseBuildFile(module, openInEditor);
  //}
  //
  //@NotNull
  //public GradleBuildModelFixture parseBuildFile(@NotNull Module module, boolean openInEditor) {
  //  VirtualFile buildFile = getGradleBuildFile(module);
  //  assertNotNull(buildFile);
  //  return parseBuildFile(buildFile, openInEditor);
  //}
  //
  //@NotNull
  //public GradleBuildModelFixture parseBuildFile(@NotNull final VirtualFile buildFile, boolean openInEditor) {
  //  if (openInEditor) {
  //    getEditor().open(buildFile, Tab.DEFAULT).getCurrentFile();
  //  }
  //  final Ref<GradleBuildModel> buildModelRef = new Ref<GradleBuildModel>();
  //  new ReadAction() {
  //    @Override
  //    protected void run(@NotNull Result result) throws Throwable {
  //      buildModelRef.set(GradleBuildModel.parseBuildFile(buildFile, getProject()));
  //    }
  //  }.execute();
  //  GradleBuildModel buildModel = buildModelRef.get();
  //  assertNotNull(buildModel);
  //  return new GradleBuildModelFixture(buildModel);
  //}

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }

  public void selectApp(@NotNull String appName) {
    final ActionButtonFixture runButton = findRunApplicationButton();
    Container actionToolbarContainer = execute(new GuiQuery<Container>() {
      @Override
      protected Container executeInEDT() throws Throwable {
        return runButton.target().getParent();
      }
    });
    assertNotNull(actionToolbarContainer);

    ComboBoxActionFixture comboBoxActionFixture = ComboBoxActionFixture.findComboBox(robot(), actionToolbarContainer);
    comboBoxActionFixture.selectItem(appName);
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    robot().waitForIdle();
  }

  /////////////////////////////////////////////////////////////////
  ////     Methods to help control debugging under a test.  ///////
  /////////////////////////////////////////////////////////////////

  public void resumeProgram() {
    GuiTestUtil.invokeMenuPathOnRobotIdle(this, "Run", "Resume Program");
  }

  public void stepOver() {
    GuiTestUtil.invokeMenuPathOnRobotIdle(this, "Run", "Step Over");
  }

  public void stepInto() {
    GuiTestUtil.invokeMenuPathOnRobotIdle(this, "Run", "Step Into");
  }

  public void stepOut() {
    GuiTestUtil.invokeMenuPathOnRobotIdle(this, "Run", "Step Out");
  }

  /**
   * Toggles breakpoints at the line numbers in {@code lines} of the source file with basename {@code fileBaseName}. This will work only
   * if the fileBasename is unique in the project that's open on Android Studio.
   */
  public void toggleBreakPoints(String fileBasename, int[] lines) {
    // We open the file twice to bring the editor into focus. Idea 1.15 has this bug where opening a file doesn't automatically bring its
    // editor window into focus.
    GuiTestUtil.openFile(this, fileBasename);
    GuiTestUtil.openFile(this, fileBasename);
    for (int line : lines) {
      GuiTestUtil.navigateToLine(this, line);
      GuiTestUtil.invokeMenuPathOnRobotIdle(this, "Run", "Toggle Line Breakpoint");
    }
  }

  // Recursively prints out the debugger tree rooted at {@code node} into {@code builder} with an indent of
  // "{@code level} * {@code numIndentSpaces}" whitespaces. Each node is printed on a separate line. The indent level for every child node
  // is 1 more than their parent.
  private static void printNode(XDebuggerTreeNode node, StringBuilder builder, int level, int numIndentSpaces) {
    int numIndent = level;
    if (builder.length() > 0) {
      builder.append(System.getProperty("line.separator"));
    }
    for (int i = 0; i < level * numIndentSpaces; ++i) {
      builder.append(' ');
    }
    builder.append(node.getText().toString());
    Enumeration<XDebuggerTreeNode> children = node.children();
    while (children.hasMoreElements()) {
      printNode(children.nextElement(), builder, level + 1, numIndentSpaces);
    }
  }

  /**
   * Prints out the debugger tree rooted at {@code root}.
   */
  @NotNull
  public static String printDebuggerTree(XDebuggerTreeNode root) {
    StringBuilder builder = new StringBuilder();
    printNode(root, builder, 0, 2);
    return builder.toString();
  }

  @NotNull
  private static String[] debuggerTreeRootToChildrenTexts(XDebuggerTreeNode treeRoot) {
    List<? extends TreeNode> children = treeRoot.getChildren();
    String[] childrenTexts = new String[children.size()];
    int i = 0;
    for (TreeNode child : children) {
      childrenTexts[i] = ((XDebuggerTreeNode)child).getText().toString();
      ++i;
    }
    return childrenTexts;
  }


  /**
   * Returns the subset of {@code expectedPatterns} which do not match any of the children (just the first level children, not recursive) of
   * {@code treeRoot} .
   */
  @NotNull
  public static List<String> getUnmatchedTerminalVariableValues(String[] expectedPatterns, XDebuggerTreeNode treeRoot) {
    String[] childrenTexts = debuggerTreeRootToChildrenTexts(treeRoot);
    List<String> unmatchedPatterns = new ArrayList<>();
    for (String expectedPattern : expectedPatterns) {
      boolean matched = false;
      for (String childText : childrenTexts) {
        if (childText.matches(expectedPattern)) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        unmatchedPatterns.add(expectedPattern);
      }
    }
    return unmatchedPatterns;
  }

  /**
   * Returns the appropriate pattern to look for a variable named {@code name} with the type {@code type} and value {@code value} appearing
   * in the Variables window in Android Studio.
   */
  @NotNull
  public static String variableToSearchPattern(String name, String type, String value) {
    return String.format("%s = \\{%s\\} %s", name, type, value);
  }

  public boolean verifyVariablesAtBreakpoint(String[] expectedVariablePatterns, String debugConfigName, long tryUntilMillis) {
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(this);
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(debugConfigName);

    contentFixture.clickDebuggerTreeRoot();
    // Wait for the debugger tree to appear.
    pause(new Condition("Looking for debugger tree.") {
      @Override
      public boolean test() {
        return contentFixture.getDebuggerTreeRoot() != null;
      }
    }, Timeout.timeout(tryUntilMillis));

    // Get the debugger tree and print it.
    XDebuggerTreeNode debuggerTreeRoot = contentFixture.getDebuggerTreeRoot();
    if (debuggerTreeRoot == null) {
      return false;
    }

    List<String> unmatchedPatterns = getUnmatchedTerminalVariableValues(expectedVariablePatterns, debuggerTreeRoot);
    return unmatchedPatterns.isEmpty();
  }
}
