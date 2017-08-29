/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.framework;

import com.intellij.diagnostic.AbstractMessage;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.PrivacyPolicy;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SwitchBootJdkAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture;
import com.intellij.testGuiFramework.fixtures.RadioButtonFixture;
import com.intellij.testGuiFramework.matcher.ClassNameMatcher;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.util.JdkBundle;
import com.intellij.util.PathUtil;
import com.intellij.util.Producer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import org.fest.swing.core.*;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.*;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.swing.finder.WindowFinder.findFrame;
import static org.fest.swing.timing.Timeout.timeout;
import static org.fest.util.Strings.isNullOrEmpty;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public final class GuiTestUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tests.gui.framework.GuiTestUtil");

  public static final Timeout THIRTY_SEC_TIMEOUT = timeout(30, SECONDS);
  public static final Timeout MINUTE_TIMEOUT = timeout(1, MINUTES);
  public static final Timeout SHORT_TIMEOUT = timeout(2, MINUTES);
  public static final Timeout LONG_TIMEOUT = timeout(5, MINUTES);

  public static final String GUI_TESTS_RUNNING_IN_SUITE_PROPERTY = "gui.tests.running.in.suite";

  /**
   * Environment variable pointing to the JDK to be used for tests
   */

  public static final String JDK_HOME_FOR_TESTS = "JDK_HOME_FOR_TESTS";
  public static final String TEST_DATA_DIR = "GUI_TEST_DATA_DIR";
  public static final String FIRST_START = "GUI_FIRST_START";
  private static final EventQueue SYSTEM_EVENT_QUEUE = Toolkit.getDefaultToolkit().getSystemEventQueue();
  private static final File TMP_PROJECT_ROOT = createTempProjectCreationDir();

  public static void failIfIdeHasFatalErrors() {
    final MessagePool messagePool = MessagePool.getInstance();
    List<AbstractMessage> fatalErrors = messagePool.getFatalErrors(true, true);
    int fatalErrorCount = fatalErrors.size();
    for (int i = 0; i < fatalErrorCount; i++) {
      LOG.error("** Fatal Error " + (i + 1) + " of " + fatalErrorCount);
      AbstractMessage error = fatalErrors.get(i);
      LOG.error("* Message: ");
      LOG.error(error.getMessage());

      String additionalInfo = error.getAdditionalInfo();
      if (isNotEmpty(additionalInfo)) {
        LOG.error("* Additional Info: ");
        LOG.error(additionalInfo);
      }

      String throwableText = error.getThrowableText();
      if (isNotEmpty(throwableText)) {
        LOG.error("* Throwable: ");
        LOG.error(throwableText);
      }
    }
    if (fatalErrorCount > 0) {
      throw new AssertionError(fatalErrorCount + " fatal errors found. Stopping test execution.");
    }
  }

  // Called by MethodInvoker via reflection
  @SuppressWarnings("unused")
  public static boolean doesIdeHaveFatalErrors() {
    final MessagePool messagePool = MessagePool.getInstance();
    List<AbstractMessage> fatalErrors = messagePool.getFatalErrors(true, true);
    return !fatalErrors.isEmpty();
  }

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("unused")
  public static void setUpDefaultGeneralSettings() {

  }

  @Nullable
  public static File getGradleHomePath() {
    return getFilePathProperty("supported.gradle.home.path", "the path of a local Gradle 2.2.1 distribution", true);
  }

  @Nullable
  public static File getUnsupportedGradleHome() {
    return getGradleHomeFromSystemProperty("unsupported.gradle.home.path", "2.1");
  }

  @Nullable
  public static File getGradleHomeFromSystemProperty(@NotNull String propertyName, @NotNull String gradleVersion) {
    String description = "the path of a Gradle " + gradleVersion + " distribution";
    return getFilePathProperty(propertyName, description, true);
  }


  @Nullable
  public static File getFilePathProperty(@NotNull String propertyName,
                                         @NotNull String description,
                                         boolean isDirectory) {
    String pathValue = System.getProperty(propertyName);
    if (!isNullOrEmpty(pathValue)) {
      File path = new File(pathValue);
      if (isDirectory && path.isDirectory() || !isDirectory && path.isFile()) {
        return path;
      }
    }
    LOG.warn("Please specify " + description + ", using system property " + quote(propertyName));
    return null;
  }

  public static void setUpDefaultProjectCreationLocationPath() {
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(PathUtil.toSystemIndependentName(getProjectCreationDirPath().getPath()));
  }

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("UnusedDeclaration")
  public static void waitForIdeToStart() {
    String firstStart = getSystemPropertyOrEnvironmentVariable(FIRST_START);
    boolean isFirstStart = firstStart != null && firstStart.toLowerCase().equals("true");
    GuiActionRunner.executeInEDT(false);
    Robot robot = null;
    try {
      robot = BasicRobot.robotWithCurrentAwtHierarchy();

      //[ACCEPT IntelliJ IDEA Privacy Policy Agreement]
      acceptAgreementIfNeeded(robot);

      final MyProjectManagerListener listener = new MyProjectManagerListener();
      final Ref<MessageBusConnection> connection = new Ref<>();

      findFrame(new GenericTypeMatcher<Frame>(Frame.class) {
        @Override
        protected boolean isMatching(@NotNull Frame frame) {
          if (frame instanceof IdeFrame) {
            if (frame instanceof IdeFrameImpl) {
              listener.myActive = true;
              connection.set(ApplicationManager.getApplication().getMessageBus().connect());
              connection.get().subscribe(ProjectManager.TOPIC, listener);
            }
            return true;
          }
          return false;
        }
      }).withTimeout(LONG_TIMEOUT.duration()).using(robot);

      //TODO: clarify why we are skipping event here?
      // We know the IDE event queue was pushed in front of the AWT queue. Some JDKs will leave a dummy event in the AWT queue, which
      // we attempt to clear here. All other events, including those posted by the Robot, will go through the IDE event queue.
      //try {
      //  if (SYSTEM_EVENT_QUEUE.peekEvent() != null) {
      //    SYSTEM_EVENT_QUEUE.getNextEvent();
      //  }
      //}
      //catch (InterruptedException ex) {
      //  // Ignored.
      //}

      if (listener.myActive) {
        Pause.pause(new Condition("Project to be opened") {
          @Override
          public boolean test() {
            boolean notified = listener.myNotified;
            if (notified) {
              ProgressManager progressManager = ProgressManager.getInstance();
              boolean isIdle = !progressManager.hasModalProgressIndicator() &&
                               !progressManager.hasProgressIndicator() &&
                               !progressManager.hasUnsafeProgressIndicator();
              if (isIdle) {
                MessageBusConnection busConnection = connection.get();
                if (busConnection != null) {
                  connection.set(null);
                  busConnection.disconnect();
                }
              }
              return isIdle;
            }
            return false;
          }
        }, LONG_TIMEOUT);
      }
    }
    finally {
      GuiActionRunner.executeInEDT(true);
      if (robot != null) {
        robot.cleanUpWithoutDisposingWindows();
      }
    }
  }

  private static void acceptAgreementIfNeeded(Robot robot) {
    final String policyAgreement = "Privacy Policy Agreement";

    Pair<PrivacyPolicy.Version, String> policy = PrivacyPolicy.getContent();
    boolean showPrivacyPolicyAgreement = !PrivacyPolicy.isVersionAccepted(policy.getFirst());
    if (!showPrivacyPolicyAgreement) {
      LOG.info(policyAgreement + " dialog should be skipped on this system.");
      return;
    }

    try {
      final DialogFixture
        privacyDialogFixture = findDialog(new GenericTypeMatcher<JDialog>(JDialog.class) {
        @Override
        protected boolean isMatching(@NotNull JDialog dialog) {
          if (dialog.getTitle() == null) return false;
          return dialog.getTitle().contains(policyAgreement) && dialog.isShowing();
        }
      }).withTimeout(LONG_TIMEOUT.duration()).using(robot);
      String buttonText = "Accept";
      JButton acceptButton = privacyDialogFixture.button(new GenericTypeMatcher<JButton>(JButton.class) {
        @Override
        protected boolean isMatching(@NotNull JButton button) {
          if (button.getText() == null) return false;
          return button.getText().equals(buttonText);
        }
      }).target();
      //we clicking this button to avoid NPE org.fest.util.Preconditions.checkNotNull(Preconditions.java:71)
      execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          EdtInvocationManager.getInstance().invokeLater(() -> acceptButton.doClick());
        }
      });
    }
    catch (WaitTimedOutError we) {
      LOG.warn("Timed out waiting for \"" + policyAgreement + "\" JDialog. Continue...");
    }
  }

  private static void completeInstallation(Robot robot) {
    final String dialogName = ApplicationBundle.message("title.complete.installation");
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);
      completeInstallationDialog.button("OK").click();
    }
    catch (WaitTimedOutError we) {
      LOG.warn("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  private static void evaluateIdea(Robot robot) {
    final String dialogName = ApplicationNamesInfo.getInstance().getFullProductName() + " License Activation";
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);
      completeInstallationDialog.button("Evaluate for free for 30 days").click();
    }
    catch (WaitTimedOutError we) {
      LOG.error("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  private static void acceptLicenseAgreement(Robot robot) {
    final String dialogName = "License Agreement for" + ApplicationInfoImpl.getShadowInstance().getFullApplicationName();
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);

      completeInstallationDialog.button("Evaluate for free for 30 days").click();
    }
    catch (WaitTimedOutError we) {
      LOG.error("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  private static void customizeIdea(Robot robot) {
    final String dialogName = "Customize " + ApplicationNamesInfo.getInstance().getFullProductName();
    try {
      final DialogFixture
        completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot);

      completeInstallationDialog.button("Skip All and Set Defaults").click();
    }
    catch (WaitTimedOutError we) {
      LOG.error("Timed out waiting for \"" + dialogName + "\" JDialog. Continue...");
    }
  }

  @NotNull
  public static File getProjectCreationDirPath() {
    return TMP_PROJECT_ROOT;
  }

  @NotNull
  public static File createTempProjectCreationDir() {
    try {
      // The temporary location might contain symlinks, such as /var@ -> /private/var on MacOS.
      // EditorFixture seems to require a canonical path when opening the file.
      File tempDir = FileUtilRt.createTempDirectory("guiTest", null);
      return tempDir.getCanonicalFile();
    }
    catch (IOException ex) {
      // For now, keep the original behavior and point inside the source tree.
      ex.printStackTrace();
      return new File(getTestProjectsRootDirPath(), "newProjects");
    }
  }

  @NotNull
  public static File getTestProjectsRootDirPath() {

    String testDataDirEnvVar = getSystemPropertyOrEnvironmentVariable(TEST_DATA_DIR);
    if (testDataDirEnvVar != null) return new File(testDataDirEnvVar);

    String testDataPath = PathManagerEx.getCommunityHomePath() + "/platform/testGuiFramework/testData";
    assertNotNull(testDataPath);
    assertThat(testDataPath).isNotEmpty();
    testDataPath = toCanonicalPath(toSystemDependentName(testDataPath));

    return new File(testDataPath, "guiTests");
  }


  private GuiTestUtil() {
  }

  public static void deleteFile(@Nullable final VirtualFile file) {
    // File deletion must happen on UI thread under write lock
    if (file != null) {
      execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                file.delete(this);
              }
              catch (IOException e) {
                // ignored
              }
            }
          });
        }
      });
    }
  }

  /**
   * Waits until an IDE popup is shown (and returns it
   */
  public static JBList waitForPopup(@NotNull Robot robot) {
    return waitUntilFound(robot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });
  }

  /**
   * Clicks an IntelliJ/Studio popup menu item with the given label prefix
   *
   * @param labelPrefix the target menu item label prefix
   * @param component   a component in the same window that the popup menu is associated with
   * @param robot       the robot to drive it with
   */
  public static void clickPopupMenuItem(@NotNull String labelPrefix, @NotNull Component component, @NotNull Robot robot) {
    clickPopupMenuItemMatching(new PrefixMatcher(labelPrefix), component, robot);
  }

  public static void clickPopupMenuItem(@NotNull String label, boolean searchByPrefix, @NotNull Component component, @NotNull Robot robot) {
    if (searchByPrefix) {
      clickPopupMenuItemMatching(new PrefixMatcher(label), component, robot);
    }
    else {
      clickPopupMenuItemMatching(new EqualsMatcher(label), component, robot);
    }
  }

  public static void clickPopupMenuItem(@NotNull String label, boolean searchByPrefix, @NotNull Component component, @NotNull Robot robot, @NotNull Timeout timeout) {
    if (searchByPrefix) {
      clickPopupMenuItemMatching(new PrefixMatcher(label), component, robot, timeout);
    }
    else {
      clickPopupMenuItemMatching(new EqualsMatcher(label), component, robot, timeout);
    }
  }

  public static void clickPopupMenuItemMatching(@NotNull Matcher<String> labelMatcher, @NotNull Component component, @NotNull Robot robot) {
    clickPopupMenuItemMatching(labelMatcher, component, robot, SHORT_TIMEOUT);
  }

  public static void clickPopupMenuItemMatching(@NotNull Matcher<String> labelMatcher, @NotNull Component component, @NotNull Robot robot, @NotNull Timeout timeout) {
    // IntelliJ doesn't seem to use a normal JPopupMenu, so this won't work:
    //    JPopupMenu menu = myRobot.findActivePopupMenu();
    // Instead, it uses a JList (technically a JBList), which is placed somewhere
    // under the root pane.

    Container root = getRootContainer(component);

    // First find the JBList which holds the popup. There could be other JBLists in the hierarchy,
    // so limit it to one that is actually used as a popup, as identified by its model being a ListPopupModel:
    assertNotNull(root);

    JBList list = waitUntilFound(robot, null,  new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    }, timeout);


    // We can't use the normal JListFixture method to click by label since the ListModel items are
    // ActionItems whose toString does not reflect the text, so search through the model items instead:
    ListPopupModel model = (ListPopupModel)list.getModel();
    List<String> items = new ArrayList<>();
    for (int i = 0; i < model.getSize(); i++) {
      Object elementAt = model.getElementAt(i);
      if (elementAt instanceof PopupFactoryImpl.ActionItem) {
        PopupFactoryImpl.ActionItem
          item = (PopupFactoryImpl.ActionItem)elementAt;
        String s = item.getText();
        if (labelMatcher.matches(s)) {
          new JListFixture(robot, list).clickItem(i);
          return;
        }
        items.add(s);
      }
      else { // For example package private class IntentionActionWithTextCaching used in quickfix popups
        String s = elementAt.toString();
        if (labelMatcher.matches(s)) {
          new JListFixture(robot, list).clickItem(i);
          return;
        }
        items.add(s);
      }
    }

    if (items.isEmpty()) {
      fail("Could not find any menu items in popup");
    }
    fail("Did not find menu item '" + labelMatcher + "' among " + StringUtil.join(items, ", "));
  }

  /**
   * Returns the root container containing the given component
   */
  @Nullable
  public static Container getRootContainer(@NotNull final Component component) {
    return execute(new GuiQuery<Container>() {
      @Override
      @Nullable
      protected Container executeInEDT() throws Throwable {
        return (Container)SwingUtilities.getRoot(component);
      }
    });
  }

  public static void findAndClickOkButton(@NotNull ContainerFixture<? extends Container> container) {
    findAndClickButton(container, "OK");
  }

  public static void findAndClickCancelButton(@NotNull ContainerFixture<? extends Container> container) {
    findAndClickButton(container, "Cancel");
  }

  public static void findAndClickButton(@NotNull ContainerFixture<? extends Container> container, @NotNull final String text) {
    Robot robot = container.robot();
    JButton button = findButton(container, text, robot);
    robot.click(button);
  }

  public static void findAndClickButtonWhenEnabled(@NotNull ContainerFixture<? extends Container> container, @NotNull final String text) {
    Robot robot = container.robot();
    final JButton button = findButton(container, text, robot);
    Pause.pause(new Condition("Wait for button " + text + " to be enabled.") {
      @Override
      public boolean test() {
        return button.isEnabled() && button.isVisible() && button.isShowing();
      }
    }, SHORT_TIMEOUT);
    robot.click(button);
  }

  public static void invokeMenuPathOnRobotIdle(IdeFrameFixture projectFrame, String... path) {
    projectFrame.robot().waitForIdle();
    projectFrame.invokeMenuPath(path);
  }

  /**
   * Opens the file with basename {@code fileBasename}
   */
  public static void openFile(IdeFrameFixture projectFrame, String fileBasename) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "File...");
    projectFrame.robot().waitForIdle();
    typeText("multifunction-jni.c", projectFrame.robot(), 30);
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  /**
   * Navigates to line number {@code lineNum} of the currently active editor window.
   */
  public static void navigateToLine(IdeFrameFixture projectFrame, int lineNum) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "Line...");
    projectFrame.robot().enterText(Integer.toString(lineNum));
    projectFrame.robot().waitForIdle();
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  public static void typeText(CharSequence text, Robot robot, long delayAfterEachCharacterMillis) {
    robot.waitForIdle();
    for (int i = 0; i < text.length(); ++i) {
      robot.type(text.charAt(i));
      Pause.pause(delayAfterEachCharacterMillis, TimeUnit.MILLISECONDS);
    }
  }

  @Nullable
  public static JLabel findBoundedLabel(@NotNull Container container, @NotNull JTextField textField, @NotNull Robot robot) {
    //in Case of parent component is TextFieldWithBrowseButton
    if(textField.getParent() instanceof TextFieldWithBrowseButton) {
      return robot.finder().find(container, new GenericTypeMatcher<JLabel>(JLabel.class) {
        @Override
        protected boolean isMatching(@NotNull JLabel label) {
          return (label.getLabelFor() != null && label.getLabelFor().equals(textField.getParent()));
        }
      });
    } else {
      Collection<JLabel> labels = robot.finder().findAll(container, new GenericTypeMatcher<JLabel>(JLabel.class) {
        @Override
        protected boolean isMatching(@NotNull JLabel label) {
          return (label.getLabelFor() != null && label.getLabelFor().equals(textField));
        }
      });
      if (labels != null && !labels.isEmpty()) {
        return labels.iterator().next();
      }
      else {
        return null;
      }
    }
  }

  @NotNull
  public static JButton findButton(@NotNull ContainerFixture<? extends Container> container, @NotNull final String text, Robot robot) {
    GenericTypeMatcher<JButton> matcher = new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        String buttonText = button.getText();
        if (buttonText != null) {
          return buttonText.trim().equals(text) && button.isShowing();
        }
        return false;
      }
    };

    Pause.pause(new Condition("Finding for a button with text \"" + text + "\"") {
      @Override
      public boolean test() {
        Collection<JButton> buttons = robot.finder().findAll(matcher);
        return !buttons.isEmpty();
      }
    }, SHORT_TIMEOUT);

    return robot.finder().find(container.target(), matcher);
  }


  /** Returns a full path to the GUI data directory in the user's AOSP source tree, if known, or null */
  //@Nullable
  //public static File getTestDataDir() {
  //  File aosp = getAospSourceDir();
  //  return aosp != null ? new File(aosp, RELATIVE_DATA_PATH) : null;
  //}


  /**
   * Waits for a first component which passes the given matcher to become visible
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull final Robot robot, @NotNull final GenericTypeMatcher<T> matcher) {
    return waitUntilFound(robot, null, matcher);
  }

  public static void skip(@NotNull String testName) {
    LOG.info("Skipping test '" + testName + "'");
  }

  /**
   * Waits for a first component which passes the given matcher under the given root to become visible.
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull final Robot robot,
                                                       @Nullable final Container root,
                                                       @NotNull final GenericTypeMatcher<T> matcher) {
    return waitUntilFound(robot, root, matcher, SHORT_TIMEOUT);
  }

  /**
   * Waits for a first component which passes the given matcher under the given root to become visible.
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull final Robot robot,
                                                       @Nullable final Container root,
                                                       @NotNull final GenericTypeMatcher<T> matcher,
                                                       @NotNull Timeout timeout) {
    final AtomicReference<T> reference = new AtomicReference<T>();
    Pause.pause(new Condition("Find component using " + matcher.toString()) {
      @Override
      public boolean test() {
        ComponentFinder finder = robot.finder();
        Collection<T> allFound = root != null ? finder.findAll(root, matcher) : finder.findAll(matcher);
        boolean found = allFound.size() == 1;
        if (found) {
          reference.set(getFirstItem(allFound));
        }
        else if (allFound.size() > 1) {
          // Only allow a single component to be found, otherwise you can get some really confusing
          // test failures; the matcher should pick a specific enough instance
          throw new ComponentLookupException("Found more than one " + matcher.supportedType().getSimpleName() + " which matches the criteria: " + allFound);
        }
        return found;
      }
    }, timeout);

    return reference.get();
  }

  /**
   * Waits until no components match the given criteria under the given root
   */
  public static <T extends Component> void waitUntilGone(@NotNull final Robot robot,
                                                         @Nullable final Container root,
                                                         @NotNull final GenericTypeMatcher<T> matcher) {
    Pause.pause(new Condition("Find component using " + matcher.toString()) {
      @Override
      public boolean test() {
        Collection<T> allFound = (root == null) ? robot.finder().findAll(matcher) : robot.finder().findAll(root, matcher);
        return allFound.isEmpty();
      }
    }, SHORT_TIMEOUT);
  }

  /**
   * Waits until no components match the given criteria under the given root
   */
  public static <T extends Component> void waitUntilGone(@NotNull final Robot robot,
                                                         @Nullable final Container root, int timeoutInSeconds,
                                                         @NotNull final GenericTypeMatcher<T> matcher) {
    Pause.pause(new Condition("Find component using " + matcher.toString()) {
      @Override
      public boolean test() {
        Collection<T> allFound = (root == null) ? robot.finder().findAll(matcher) : robot.finder().findAll(root, matcher);
        return allFound.isEmpty();
      }
    }, timeout(timeoutInSeconds, SECONDS));
  }


  @Nullable
  public static String getSystemPropertyOrEnvironmentVariable(@NotNull String name) {
    String s = System.getProperty(name);
    return s == null ? System.getenv(name) : s;
  }

  private static class MyProjectManagerListener implements ProjectManagerListener {
    boolean myActive;
    boolean myNotified;

    @Override
    public void projectOpened(Project project) {
      myNotified = true;
    }
  }

  private static class PrefixMatcher extends BaseMatcher<String> {

    private final String prefix;

    public PrefixMatcher(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public boolean matches(Object item) {
      return item instanceof String && ((String)item).startsWith(prefix);
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("with prefix '" + prefix + "'");
    }
  }

  private static class EqualsMatcher extends BaseMatcher<String> {

    private final String wanted;

    public EqualsMatcher(String wanted) {
      this.wanted = wanted;
    }

    @Override
    public boolean matches(Object item) {
      return item instanceof String && ((String)item).equals(wanted);
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("equals to '" + wanted + "'");
    }
  }


  public static String adduction(String s) {
    char ESCAPE_SYMBOL = '\u001B';
    String ESCAPE_SYMBOL_STRING = "" + ESCAPE_SYMBOL;
    if (s.contains(ESCAPE_SYMBOL_STRING)) {
      return StringUtil.replace(s, ESCAPE_SYMBOL_STRING, "");
    }
    else {
      return s;
    }
  }

  public static String getBundledJdLocation() {

    ArrayList<JdkBundle> bundleList = SwitchBootJdkAction.findJdkPaths().toArrayList();
    //we believe that Idea has at least one bundled jdk
    JdkBundle jdkBundle = bundleList.get(0);
    String homeSubPath = SystemInfo.isMac ? "/Contents/Home" : "";
    return jdkBundle.getLocation().getAbsolutePath() + homeSubPath;
  }

  @NotNull
  public static JTextComponentFixture findTextField(@NotNull Robot robot, @NotNull final String labelText) {
    return new JTextComponentFixture(robot, robot.finder().findByLabel(labelText, JTextComponent.class));
  }

  @NotNull
  public static JTreeFixture findJTreeFixture(@NotNull Robot robot, @NotNull Container container) {
    JTree actionTree = robot.finder().findByType(container, JTree.class);
    return new JTreeFixture(robot, actionTree);
  }

  @NotNull
  public static JTreeFixture findJTreeFixtureByClassName(@NotNull Robot robot, @NotNull Container container, @NotNull String className) {
    JTree actionTree = robot.finder().find(container, ClassNameMatcher.forClass(className, JTree.class, true));
    return new JTreeFixture(robot, actionTree);
  }

  public static RadioButtonFixture findRadioButton(@NotNull Robot robot, @Nullable Container container, @NotNull String text, @NotNull Timeout timeout){
    JRadioButton radioButton = waitUntilFound(robot, container, new GenericTypeMatcher<JRadioButton>(JRadioButton.class) {
      @Override
      protected boolean isMatching(@Nonnull JRadioButton button) {
        return (button.getText() != null && button.getText().equals(text));
      }
    }, timeout);
    return new RadioButtonFixture(robot, radioButton);
  }

  public static RadioButtonFixture findRadioButton(@NotNull Robot robot, @NotNull Container container, @NotNull String text){
    JRadioButton radioButton = waitUntilFound(robot, container, new GenericTypeMatcher<JRadioButton>(JRadioButton.class) {
      @Override
      protected boolean isMatching(@Nonnull JRadioButton button) {
        return (button.getText() != null && button.getText().equals(text));
      }
    }, SHORT_TIMEOUT);
    return new RadioButtonFixture(robot, radioButton);
  }

  public static JComboBoxFixture findComboBox(@NotNull Robot robot, @NotNull Container container, @NotNull String labelText) {
    JLabel label = (JLabel)robot.finder().find(container, new ComponentMatcher() {
      @Override
      public boolean matches(Component p0) {
        return (p0 instanceof JLabel && ((JLabel)p0).getText() != null && ((JLabel)p0).getText().equals(labelText));
      }
    });

    if (label == null) throw new ComponentLookupException("Unable to find label with text \" + labelText+\"");
    Container boundedCmp = (Container)label.getLabelFor();
    if (boundedCmp == null) throw new ComponentLookupException("Unable to find bounded component for label \" + labelText+\"");
    JComboBox cb = robot.finder().findByType(boundedCmp, JComboBox.class);
    if (cb == null) throw new ComponentLookupException("Unable to find JComboBox near label \" + labelText+\"");
    return new JComboBoxFixture(robot, cb);
  }

  /**
   * @param shortcut should follow {@link KeyStrokeAdapter#getKeyStroke(String)} instructions and be generated by {@link KeyStrokeAdapter#toString(KeyStroke)} preferably
   */
  public static void invokeActionViaShortcut(@NotNull Robot robot, @NotNull String shortcut) {

    KeyStroke keyStroke = KeyStrokeAdapter.getKeyStroke(shortcut);
    LOG.info("Invoking action via shortcut \"" + shortcut + "\"");
    robot.pressAndReleaseKey(keyStroke.getKeyCode(), new int[]{keyStroke.getModifiers()});
  }

  public static void invokeAction(@NotNull Robot robot, @NotNull String actionId) {
    KeyboardShortcut keyboardShortcut = ActionManager.getInstance().getKeyboardShortcut(actionId);

    assert keyboardShortcut != null;
    KeyStroke keyStroke = keyboardShortcut.getFirstKeyStroke();
    LOG.info("Invoking action \"" + actionId + "\" via shortcut " + keyboardShortcut.toString());
    robot.pressAndReleaseKey(keyStroke.getKeyCode(), new int[]{keyStroke.getModifiers()});
  }

  public static void pause(String conditionString, Producer<Boolean> producer, Timeout timeout){
    Pause.pause(new Condition(conditionString) {
      @Override
      public boolean test() {
        Boolean produce = producer.produce();
        assertNotNull(produce);
        return produce;
      }
    }, timeout);
  }

  public static Component getListCellRendererComponent(JList list, Object value, int index) {
    return list.getCellRenderer().getListCellRendererComponent(list, value, index, true, true);
  }
}
