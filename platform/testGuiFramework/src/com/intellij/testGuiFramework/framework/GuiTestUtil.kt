// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import com.intellij.diagnostic.MessagePool
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil.toCanonicalPath
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.RadioButtonFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.impl.GuiRobot
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.getComponentText
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.isTextComponent
import com.intellij.testGuiFramework.impl.toFestTimeout
import com.intellij.testGuiFramework.matcher.ClassNameMatcher
import com.intellij.testGuiFramework.util.Clipboard
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Shortcut
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.util.JdkBundle
import com.intellij.util.PathUtil
import com.intellij.util.Producer
import com.intellij.util.containers.ContainerUtil.getFirstItem
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.EdtInvocationManager
import org.fest.assertions.Assertions.assertThat
import org.fest.swing.core.BasicRobot
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiQuery
import org.fest.swing.edt.GuiTask
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.finder.WindowFinder.findDialog
import org.fest.swing.finder.WindowFinder.findFrame
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.fixture.JTreeFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import org.fest.swing.timing.Timeout.timeout
import org.fest.util.Strings.isNullOrEmpty
import org.fest.util.Strings.quote
import org.junit.Assert.assertNotNull
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.Nonnull
import javax.swing.*
import javax.swing.text.JTextComponent

object GuiTestUtil {
  /**
   * default timeout to find target component for fixture. Using seconds as time unit.
   */
  const val defaultTimeout = 120L

  val THIRTY_SEC_TIMEOUT = timeout(30, SECONDS)

  val MINUTE_TIMEOUT = timeout(1, MINUTES)
  val SHORT_TIMEOUT = timeout(2, MINUTES)
  val LONG_TIMEOUT = timeout(5, MINUTES)
  val TEN_MIN_TIMEOUT = timeout(10, MINUTES)
  val FIFTEEN_MIN_TIMEOUT = timeout(15, MINUTES)
  val GUI_TESTS_RUNNING_IN_SUITE_PROPERTY = "gui.tests.running.in.suite"

  private val LOG = Logger.getInstance("#com.intellij.tests.gui.framework.GuiTestUtil")

  /**
   * Environment variable pointing to the JDK to be used for tests
   */

  val JDK_HOME_FOR_TESTS = "JDK_HOME_FOR_TESTS"
  val TEST_DATA_DIR = "GUI_TEST_DATA_DIR"
  val FIRST_START = "GUI_FIRST_START"
  private val SYSTEM_EVENT_QUEUE = Toolkit.getDefaultToolkit().systemEventQueue
  val projectCreationDirPath = createTempProjectCreationDir()

  val gradleHomePath: File?
    get() = getFilePathProperty("supported.gradle.home.path", "the path of a local Gradle 2.2.1 distribution", true)

  val unsupportedGradleHome: File?
    get() = getGradleHomeFromSystemProperty("unsupported.gradle.home.path", "2.1")

  val testProjectsRootDirPath: File
    get() {

      val testDataDirEnvVar = getSystemPropertyOrEnvironmentVariable(TEST_DATA_DIR)
      if (testDataDirEnvVar != null) return File(testDataDirEnvVar)

      var testDataPath = PathManagerEx.getCommunityHomePath() + "/platform/testGuiFramework/testData"
      assertNotNull(testDataPath)
      assertThat(testDataPath).isNotEmpty
      testDataPath = toCanonicalPath(toSystemDependentName(testDataPath))

      return File(testDataPath, "guiTests")
    }

  val bundledJdkLocation: String
    get() {
      var jdkBundle = JdkBundle.createBundled()
      if (jdkBundle == null) jdkBundle = JdkBundle.createBoot()
      val homeSubPath = if (SystemInfo.isMac) "/Contents/Home" else ""
      return jdkBundle.location.absolutePath + homeSubPath
    }

  fun failIfIdeHasFatalErrors() {
    val messagePool = MessagePool.getInstance()
    val fatalErrors = messagePool.getFatalErrors(true, true)
    val fatalErrorCount = fatalErrors.size
    for (i in 0 until fatalErrorCount) {
      LOG.error("** Fatal Error " + (i + 1) + " of " + fatalErrorCount)
      val error = fatalErrors[i]
      LOG.error("* Message: ")
      LOG.error(error.message)

      val additionalInfo = error.additionalInfo
      if (isNotEmpty(additionalInfo)) {
        LOG.error("* Additional Info: ")
        LOG.error(additionalInfo)
      }

      val throwableText = error.throwableText
      if (isNotEmpty(throwableText)) {
        LOG.error("* Throwable: ")
        LOG.error(throwableText)
      }
    }
    if (fatalErrorCount > 0) {
      throw AssertionError(fatalErrorCount.toString() + " fatal errors found. Stopping test execution.")
    }
  }

  // Called by MethodInvoker via reflection
  fun doesIdeHaveFatalErrors(): Boolean {
    val messagePool = MessagePool.getInstance()
    val fatalErrors = messagePool.getFatalErrors(true, true)
    return !fatalErrors.isEmpty()
  }

  // Called by IdeTestApplication via reflection.
  fun setUpDefaultGeneralSettings() {

  }

  fun getGradleHomeFromSystemProperty(propertyName: String, gradleVersion: String): File? {
    val description = "the path of a Gradle $gradleVersion distribution"
    return getFilePathProperty(propertyName, description, true)
  }


  fun getFilePathProperty(propertyName: String,
                          description: String,
                          isDirectory: Boolean): File? {
    val pathValue = System.getProperty(propertyName)
    if (!isNullOrEmpty(pathValue)) {
      val path = File(pathValue)
      if (isDirectory && path.isDirectory || !isDirectory && path.isFile) {
        return path
      }
    }
    LOG.warn("Please specify " + description + ", using system property " + quote(propertyName))
    return null
  }

  fun setUpDefaultProjectCreationLocationPath() {
    RecentProjectsManager.getInstance().lastProjectCreationLocation = PathUtil.toSystemIndependentName(projectCreationDirPath.path)
  }

  // Called by IdeTestApplication via reflection.
  fun waitForIdeToStart() {
    val firstStart = getSystemPropertyOrEnvironmentVariable(FIRST_START)
    val isFirstStart = firstStart != null && firstStart.toLowerCase() == "true"
    GuiActionRunner.executeInEDT(false)
    var robot: Robot? = null
    try {
      robot = BasicRobot.robotWithCurrentAwtHierarchy()

      //[ACCEPT IntelliJ IDEA Privacy Policy Agreement]
      acceptAgreementIfNeeded(robot)

      val listener = MyProjectManagerListener()
      val connection = Ref<MessageBusConnection>()

      findFrame(object : GenericTypeMatcher<Frame>(Frame::class.java) {
        override fun isMatching(frame: Frame): Boolean {
          if (frame is IdeFrame) {
            if (frame is IdeFrameImpl) {
              listener.myActive = true
              connection.set(ApplicationManager.getApplication().messageBus.connect())
              connection.get().subscribe(ProjectManager.TOPIC, listener)
            }
            return true
          }
          return false
        }
      }).withTimeout(LONG_TIMEOUT.duration()).using(robot)

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
        Pause.pause(object : Condition("Project to be opened") {
          override fun test(): Boolean {
            val notified = listener.myNotified
            if (notified) {
              val progressManager = ProgressManager.getInstance()
              val isIdle = !progressManager.hasModalProgressIndicator() &&
                           !progressManager.hasProgressIndicator() &&
                           !progressManager.hasUnsafeProgressIndicator()
              if (isIdle) {
                val busConnection = connection.get()
                if (busConnection != null) {
                  connection.set(null)
                  busConnection.disconnect()
                }
              }
              return isIdle
            }
            return false
          }
        }, LONG_TIMEOUT)
      }
    }
    finally {
      GuiActionRunner.executeInEDT(true)
      if (robot != null) {
        robot.cleanUpWithoutDisposingWindows()
      }
    }
  }

  private fun acceptAgreementIfNeeded(robot: Robot) {
    val policyAgreement = "Privacy Policy Agreement"

    val doc = EndUserAgreement.getLatestDocument()
    val showPrivacyPolicyAgreement = !doc.isAccepted
    if (!showPrivacyPolicyAgreement) {
      LOG.info("$policyAgreement dialog should be skipped on this system.")
      return
    }

    try {
      val privacyDialogFixture = findDialog(object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
        override fun isMatching(dialog: JDialog): Boolean {
          return if (dialog.title == null) false else dialog.title.contains(policyAgreement) && dialog.isShowing
        }
      }).withTimeout(LONG_TIMEOUT.duration()).using(robot)
      val buttonText = "Accept"
      val acceptButton = privacyDialogFixture.button(object : GenericTypeMatcher<JButton>(JButton::class.java) {
        override fun isMatching(button: JButton): Boolean {
          return if (button.text == null) false else button.text == buttonText
        }
      }).target()
      //we clicking this button to avoid NPE org.fest.util.Preconditions.checkNotNull(Preconditions.java:71)
      execute(object : GuiTask() {
        @Throws(Throwable::class)
        override fun executeInEDT() {
          EdtInvocationManager.getInstance().invokeLater { acceptButton.doClick() }
        }
      })
    }
    catch (we: WaitTimedOutError) {
      LOG.warn("Timed out waiting for \"$policyAgreement\" JDialog. Continue...")
    }

  }

  private fun completeInstallation(robot: Robot) {
    val dialogName = ApplicationBundle.message("title.complete.installation")
    try {
      val completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot)
      completeInstallationDialog.button("OK").click()
    }
    catch (we: WaitTimedOutError) {
      LOG.warn("Timed out waiting for \"$dialogName\" JDialog. Continue...")
    }

  }

  private fun evaluateIdea(robot: Robot) {
    val dialogName = ApplicationNamesInfo.getInstance().fullProductName + " License Activation"
    try {
      val completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot)
      completeInstallationDialog.button("Evaluate for free for 30 days").click()
    }
    catch (we: WaitTimedOutError) {
      LOG.error("Timed out waiting for \"$dialogName\" JDialog. Continue...")
    }

  }

  private fun acceptLicenseAgreement(robot: Robot) {
    val dialogName = "License Agreement for" + ApplicationInfoImpl.getShadowInstance().fullApplicationName
    try {
      val completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot)

      completeInstallationDialog.button("Evaluate for free for 30 days").click()
    }
    catch (we: WaitTimedOutError) {
      LOG.error("Timed out waiting for \"$dialogName\" JDialog. Continue...")
    }

  }

  private fun customizeIdea(robot: Robot) {
    val dialogName = "Customize " + ApplicationNamesInfo.getInstance().fullProductName
    try {
      val completeInstallationDialog = findDialog(dialogName)
        .withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(robot)

      completeInstallationDialog.button("Skip All and Set Defaults").click()
    }
    catch (we: WaitTimedOutError) {
      LOG.error("Timed out waiting for \"$dialogName\" JDialog. Continue...")
    }

  }

  fun createTempProjectCreationDir(): File {
    try {
      // The temporary location might contain symlinks, such as /var@ -> /private/var on MacOS.
      // EditorFixture seems to require a canonical path when opening the file.
      val tempDir = FileUtilRt.createTempDirectory("guiTest", null)
      return tempDir.canonicalFile
    }
    catch (ex: IOException) {
      // For now, keep the original behavior and point inside the source tree.
      ex.printStackTrace()
      return File(testProjectsRootDirPath, "newProjects")
    }

  }

  fun deleteFile(file: VirtualFile?) {
    // File deletion must happen on UI thread under write lock
    if (file != null) {
      execute(object : GuiTask() {
        @Throws(Throwable::class)
        override fun executeInEDT() {
          ApplicationManager.getApplication().runWriteAction(object : Runnable {
            override fun run() {
              try {
                file.delete(this)
              }
              catch (e: IOException) {
                // ignored
              }

            }
          })
        }
      })
    }
  }

  /**
   * Returns the root container containing the given component
   */
  fun getRootContainer(component: Component): Container? {
    return execute(object : GuiQuery<Container>() {
      @Throws(Throwable::class)
      override fun executeInEDT(): Container? {
        return SwingUtilities.getRoot(component) as Container
      }
    })
  }

  fun findAndClickOkButton(container: ContainerFixture<out Container>) {
    findAndClickButton(container, "OK")
  }

  fun findAndClickCancelButton(container: ContainerFixture<out Container>) {
    findAndClickButton(container, "Cancel")
  }

  fun findAndClickButton(container: ContainerFixture<out Container>, text: String) {
    val robot = container.robot()
    val button = findButton(container, text, robot)
    robot.click(button)
  }

  fun findAndClickButtonWhenEnabled(container: ContainerFixture<out Container>, text: String) {
    val robot = container.robot()
    val button = findButton(container, text, robot)
    Pause.pause(object : Condition("Wait for button $text to be enabled.") {
      override fun test(): Boolean {
        return button.isEnabled && button.isVisible && button.isShowing
      }
    }, SHORT_TIMEOUT)
    robot.click(button)
  }

  fun invokeMenuPathOnRobotIdle(projectFrame: IdeFrameFixture, vararg path: String) {
    projectFrame.robot().waitForIdle()
    projectFrame.invokeMenuPath(*path)
  }

  /**
   * Opens the file with basename `fileBasename`
   */
  fun openFile(projectFrame: IdeFrameFixture, fileBasename: String) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "File...")
    projectFrame.robot().waitForIdle()
    typeText("multifunction-jni.c", projectFrame.robot(), 30)
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
  }

  /**
   * Navigates to line number `lineNum` of the currently active editor window.
   */
  fun navigateToLine(projectFrame: IdeFrameFixture, lineNum: Int) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "Line...")
    projectFrame.robot().enterText(Integer.toString(lineNum))
    projectFrame.robot().waitForIdle()
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
  }

  fun typeText(text: CharSequence, robot: Robot, delayAfterEachCharacterMillis: Long) {
    robot.waitForIdle()
    for (i in 0 until text.length) {
      robot.type(text[i])
      Pause.pause(delayAfterEachCharacterMillis, TimeUnit.MILLISECONDS)
    }
    Pause.pause(300, TimeUnit.MILLISECONDS)
  }

  fun findBoundedLabel(container: Container, textField: JTextField, robot: Robot): JLabel? {
    //in Case of parent component is TextFieldWithBrowseButton
    if (textField.parent is TextFieldWithBrowseButton) {
      return robot.finder().find(container, object : GenericTypeMatcher<JLabel>(JLabel::class.java) {
        override fun isMatching(label: JLabel): Boolean {
          return label.labelFor != null && label.labelFor == textField.parent
        }
      })
    }
    else {
      val labels = robot.finder().findAll(container, object : GenericTypeMatcher<JLabel>(JLabel::class.java) {
        override fun isMatching(label: JLabel): Boolean {
          return label.labelFor != null && label.labelFor == textField
        }
      })
      return if (labels != null && !labels.isEmpty()) {
        labels.iterator().next()
      }
      else {
        null
      }
    }
  }

  fun findButton(container: ContainerFixture<out Container>, text: String, robot: Robot): JButton {
    val matcher = object : GenericTypeMatcher<JButton>(JButton::class.java) {
      override fun isMatching(button: JButton): Boolean {
        val buttonText = button.text
        return if (buttonText != null) {
          buttonText.trim { it <= ' ' } == text && button.isShowing
        }
        else false
      }
    }

    Pause.pause(object : Condition("Finding for a button with text \"$text\"") {
      override fun test(): Boolean {
        val buttons = robot.finder().findAll(matcher)
        return !buttons.isEmpty()
      }
    }, SHORT_TIMEOUT)

    return robot.finder().find(container.target(), matcher)
  }


  /** Returns a full path to the GUI data directory in the user's AOSP source tree, if known, or null  */
  //@Nullable
  //public static File getTestDataDir() {
  //  File aosp = getAospSourceDir();
  //  return aosp != null ? new File(aosp, RELATIVE_DATA_PATH) : null;
  //}


  /**
   * Waits for a first component which passes the given matcher to become visible
   */
  fun <T : Component> waitUntilFound(robot: Robot, matcher: GenericTypeMatcher<T>): T {
    return waitUntilFound(robot, null, matcher)
  }

  fun skip(testName: String) {
    LOG.info("Skipping test '$testName'")
  }

  /**
   * Waits for a first component which passes the given matcher under the given root to become visible.
   */
  fun <T : Component> waitUntilFound(robot: Robot,
                                     root: Container?,
                                     matcher: GenericTypeMatcher<T>): T {
    return waitUntilFound(robot, root, matcher, SHORT_TIMEOUT)
  }

  /**
   * Waits for a first component which passes the given matcher under the given root to become visible.
   */
  fun <T : Component> waitUntilFound(robot: Robot,
                                     root: Container?,
                                     matcher: GenericTypeMatcher<T>,
                                     timeout: Timeout): T {
    val reference = AtomicReference<T>()
    Pause.pause(object : Condition("Find component using " + matcher.toString()) {
      override fun test(): Boolean {
        val finder = robot.finder()
        val allFound = if (root != null) finder.findAll(root, matcher) else finder.findAll(matcher)
        val found = allFound.size == 1
        if (found) {
          reference.set(getFirstItem(allFound))
        }
        else if (allFound.size > 1) {
          // Only allow a single component to be found, otherwise you can get some really confusing
          // test failures; the matcher should pick a specific enough instance
          throw ComponentLookupException(
            "Found more than one " + matcher.supportedType().simpleName + " which matches the criteria: " + allFound)
        }
        return found
      }
    }, timeout)

    return reference.get()
  }


  /**
   * Waits until no components match the given criteria under the given root
   */
  fun <T : Component> waitUntilGone(root: Container?,
                                    matcher: GenericTypeMatcher<T>) {
    Pause.pause(object : Condition("Find component using " + matcher.toString()) {
      override fun test(): Boolean {
        val allFound = if (root == null) GuiRobot.robot.finder().findAll(matcher) else GuiRobot.robot.finder().findAll(root, matcher)
        return allFound.isEmpty()
      }
    }, SHORT_TIMEOUT)
  }

  /**
   * Waits until no components match the given criteria under the given root
   */
  fun <T : Component> waitUntilGone(root: Container?,
                                    timeoutInSeconds: Int,
                                    matcher: GenericTypeMatcher<T>) {
    Pause.pause(object : Condition("Find component using " + matcher.toString()) {
      override fun test(): Boolean {
        val allFound = if (root == null) GuiRobot.robot.finder().findAll(matcher) else GuiRobot.robot.finder().findAll(root, matcher)
        return allFound.isEmpty()
      }
    }, timeout(timeoutInSeconds.toLong(), SECONDS))
  }


  fun getSystemPropertyOrEnvironmentVariable(name: String): String? {
    val s = System.getProperty(name)
    return s ?: System.getenv(name)
  }

  private class MyProjectManagerListener : ProjectManagerListener {
    internal var myActive: Boolean = false
    internal var myNotified: Boolean = false

    override fun projectOpened(project: Project) {
      myNotified = true
    }
  }

  fun adduction(s: String): String {
    val ESCAPE_SYMBOL = '\u001B'
    val ESCAPE_SYMBOL_STRING = "" + ESCAPE_SYMBOL
    return if (s.contains(ESCAPE_SYMBOL_STRING)) {
      StringUtil.replace(s, ESCAPE_SYMBOL_STRING, "")
    }
    else {
      s
    }
  }

  fun findTextField(labelText: String): JTextComponentFixture {
    return JTextComponentFixture(GuiRobot.robot, GuiRobot.robot.finder().findByLabel(labelText, JTextComponent::class.java))
  }

  fun findJTreeFixture(container: Container): JTreeFixture {
    val actionTree = GuiRobot.robot.finder().findByType(container, JTree::class.java)
    return JTreeFixture(GuiRobot.robot, actionTree)
  }

  fun findJTreeFixtureByClassName(container: Container, className: String): JTreeFixture {
    val actionTree = GuiRobot.robot.finder().find(container, ClassNameMatcher.forClass(className, JTree::class.java, true))
    return JTreeFixture(GuiRobot.robot, actionTree)
  }

  fun findRadioButton(container: Container?, text: String, timeout: Timeout): RadioButtonFixture {
    val radioButton = waitUntilFound(GuiRobot.robot, container, object : GenericTypeMatcher<JRadioButton>(JRadioButton::class.java) {
      override fun isMatching(@Nonnull button: JRadioButton): Boolean {
        return button.text != null && button.text == text && button.isShowing
      }
    }, timeout)
    return RadioButtonFixture(GuiRobot.robot, radioButton)
  }

  fun findRadioButton(container: Container, text: String): RadioButtonFixture {
    val radioButton = waitUntilFound(GuiRobot.robot, container, object : GenericTypeMatcher<JRadioButton>(JRadioButton::class.java) {
      override fun isMatching(@Nonnull button: JRadioButton): Boolean {
        return button.text != null && button.text == text
      }
    }, SHORT_TIMEOUT)
    return RadioButtonFixture(GuiRobot.robot, radioButton)
  }

  fun findComboBox(container: Container, labelText: String): JComboBoxFixture {
    val label = GuiRobot.robot.finder().find(container) {
      it is JLabel && it.text != null && it.text == labelText
    } as? JLabel ?: throw ComponentLookupException("Unable to find label with text \" + labelText+\"")

    val boundedCmp = label.labelFor as? Container ?: throw ComponentLookupException(
      "Unable to find bounded component for label \" + labelText+\"")
    val cb = GuiRobot.robot.finder().findByType(boundedCmp, JComboBox::class.java)
    // findByType returns non null, so no need to use elvis:
    // ?: throw ComponentLookupException("Unable to find JComboBox near label \" + labelText+\"")
    return JComboBoxFixture(GuiRobot.robot, cb)
  }

  /**
   * @param shortcut should follow [KeyStrokeAdapter.getKeyStroke] instructions and be generated by [KeyStrokeAdapter.toString] preferably
   */
  fun invokeActionViaShortcut(shortcut: String) {

    val keyStroke = KeyStrokeAdapter.getKeyStroke(shortcut)
    LOG.info("Invoking action via shortcut \"$shortcut\"")
    GuiRobot.robot.pressAndReleaseKey(keyStroke.keyCode, *intArrayOf(keyStroke.modifiers))
  }

  fun invokeAction(actionId: String) {
    val keyboardShortcut = ActionManager.getInstance().getKeyboardShortcut(actionId)!!

    val keyStroke = keyboardShortcut.firstKeyStroke
    LOG.info("Invoking action \"" + actionId + "\" via shortcut " + keyboardShortcut.toString())
    GuiRobot.robot.pressAndReleaseKey(keyStroke.keyCode, *intArrayOf(keyStroke.modifiers))
  }

  fun pause(conditionString: String, producer: Producer<Boolean>, timeout: Timeout) {
    Pause.pause(object : Condition(conditionString) {
      override fun test(): Boolean {
        val produce = producer.produce()
        assertNotNull(produce)
        return produce!!
      }
    }, timeout)
  }

  fun getListCellRendererComponent(list: JList<*>, value: Any, index: Int): Component {
    return (list as JList<Any>).cellRenderer.getListCellRendererComponent(list, value, index, true, true)
  }

  fun textfield(textLabel: String?, container: Container, timeout: Long): JTextComponentFixture {
    if (textLabel.isNullOrEmpty()) {
      val jTextField = com.intellij.testGuiFramework.impl.waitUntilFound(container, JTextField::class.java,
                                                                         timeout) { jTextField -> jTextField.isShowing }
      return JTextComponentFixture(GuiRobot.robot, jTextField)
    }
    //wait until label has appeared
    com.intellij.testGuiFramework.impl.waitUntilFound(container, Component::class.java, timeout) {
      it.isShowing && it.isVisible && it.isTextComponent() && it.getComponentText() == textLabel
    }
    val jTextComponent = GuiTestUtilKt.findBoundedComponentByText(GuiRobot.robot, container, textLabel!!, JTextComponent::class.java)
    return JTextComponentFixture(GuiRobot.robot, jTextComponent)
  }

  fun jTreePath(container: Container, timeout: Long, vararg pathStrings: String): ExtendedTreeFixture {
    val myTree: JTree?
    val pathList = pathStrings.toList()
    try {
      myTree = if (pathList.isEmpty()) {
        waitUntilFound(GuiRobot.robot, container, GuiTestUtilKt.typeMatcher(JTree::class.java) { true }, timeout.toFestTimeout())
      }
      else {
        waitUntilFound(GuiRobot.robot, container,
                       GuiTestUtilKt.typeMatcher(JTree::class.java) { ExtendedTreeFixture(GuiRobot.robot, it).hasPath(pathList) },
                       timeout.toFestTimeout())
      }
    }
    catch (e: WaitTimedOutError){
      throw ComponentLookupException("""JTree "${if (pathStrings.isNotEmpty()) "by path $pathStrings" else ""}"""")
    }
    return ExtendedTreeFixture(GuiRobot.robot, myTree)
  }

  //*********COMMON FUNCTIONS WITHOUT CONTEXT
  /**
   * Type text by symbol with a constant delay. Generate system key events, so entered text will aply to a focused component.
   */
  fun typeText(text: String) = GuiTestUtil.typeText(text, GuiRobot.robot, 10)

  /**
   * @param keyStroke should follow {@link KeyStrokeAdapter#getKeyStroke(String)} instructions and be generated by {@link KeyStrokeAdapter#toString(KeyStroke)} preferably
   *
   * examples: shortcut("meta comma"), shortcut("ctrl alt s"), shortcut("alt f11")
   * modifiers order: shift | ctrl | control | meta | alt | altGr | altGraph
   */
  fun shortcut(keyStroke: String) = GuiTestUtil.invokeActionViaShortcut(keyStroke)

  fun shortcut(shortcut: Shortcut) = shortcut(shortcut.getKeystroke())

  fun shortcut(winShortcut: Shortcut, macShortcut: Shortcut) {
    if (com.intellij.testGuiFramework.launcher.system.SystemInfo.isMac()) shortcut(macShortcut)
    else shortcut(winShortcut)
  }

  fun shortcut(key: Key) = shortcut(key.name)

  /**
   * copies a given string to a system clipboard
   */
  fun copyToClipboard(string: String) = Clipboard.copyToClipboard(string)


}
