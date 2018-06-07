// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.impl.FirstStart.Utils.button
import com.intellij.testGuiFramework.impl.FirstStart.Utils.dialog
import com.intellij.testGuiFramework.impl.FirstStart.Utils.radioButton
import com.intellij.testGuiFramework.impl.FirstStart.Utils.waitFrame
import com.intellij.testGuiFramework.launcher.ide.IdeType
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.AbstractComponentFixture
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JCheckBoxFixture
import org.fest.swing.fixture.JRadioButtonFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.concurrent.thread

/**
 * could be used only to initialize IDEA configuration for the first start. Please do not start it with GuiTestCase runner.
 */
abstract class FirstStart(val ideType: IdeType) {

  private val FIRST_START_ROBOT_THREAD = "First Start Robot Thread"

  private val LOG = Logger.getInstance(this.javaClass.name)

  val myRobot: Robot

  private val robotThread: Thread = thread(start = false, name = FIRST_START_ROBOT_THREAD) {
    try {
      completeFirstStart()
    }
    catch (e: Exception) {
      when (e) {
        is ComponentLookupException -> {
          takeScreenshot(e)
        }
        is WaitTimedOutError -> {
          takeScreenshot(e)
          throw exceptionWithHierarchy(e)
        }
        else -> {
          takeScreenshot(e)
          throw e
        }
      }
    }
  }

  private fun takeScreenshot(e: Throwable) {
    ScreenshotOnFailure.takeScreenshotOnFailure(e, "FirstStartFailed")
  }

  private fun exceptionWithHierarchy(e: Throwable): Throwable {
    return Exception("Hierarchy log: ${ScreenshotOnFailure.getHierarchy()}", e)
  }

  // should be initialized before IDE has been started
  private val newConfigFolder: Boolean by lazy {
    if (ApplicationManager.getApplication() != null) throw Exception(
      "Cannot get status (new or not) of config folder because IDE has been already started")
    !File(PathManager.getConfigPath()).exists() || System.getProperty("intellij.first.ide.session") == "true"
  }


  init {
    myRobot = SmartWaitRobot()
    LOG.info("Starting separated thread: '$FIRST_START_ROBOT_THREAD' to complete initial installation")
    robotThread.start()
  }

  companion object {
    var DEFAULT_TIMEOUT: Long = GuiTestCase().defaultTimeout
  }

  private fun completeFirstStart() {
      completeInstallation()
      acceptAgreement()
      acceptDataSharing()
      evaluateLicense(ideType.name, myRobot)
      customizeIde()
      waitWelcomeFrameAndClose()
  }

  private val checkIsFrameFunction: (Frame) -> Boolean
    get() {
      return { frame ->
        frame.javaClass.simpleName == "FlatWelcomeFrame"
        && frame.isShowing
        && frame.isEnabled
      }
    }

  private fun waitWelcomeFrameAndClose() {
    waitWelcomeFrame()
    LOG.info("Closing Welcome Frame")
    val welcomeFrame = Frame.getFrames().find(checkIsFrameFunction)
    myRobot.close(welcomeFrame!!)
    Pause.pause(object : Condition("Welcome Frame is gone") {
      override fun test(): Boolean {
        if (Frame.getFrames().any { checkIsFrameFunction(it) }) myRobot.close(welcomeFrame)
        return false
      }
    }, Timeout.timeout(180, TimeUnit.SECONDS))
  }

  private fun waitWelcomeFrame() {
    LOG.info("Waiting for a Welcome Frame")
    Pause.pause(object : Condition("Welcome Frame to show up") {
      override fun test() = Frame.getFrames().any { checkIsFrameFunction(it) }
    }, Timeout.timeout(180, TimeUnit.SECONDS))
  }

  private fun findPrivacyPolicyDialogOrLicenseAgreement(): JDialog {
    return GuiTestUtilKt.withPauseWhenNull(120) {
      try {
        myRobot.finder().find {
          it is JDialog && (it.title.contains("License Agreement") || it.title.contains("Privacy Policy"))
        } as JDialog
      } catch (cle: ComponentLookupException) {
        null
      }
    }
  }

  private fun acceptAgreement() {
    if (!needToShowAgreement()) return
    with(myRobot) {
      try {
        LOG.info("Waiting for License Agreement/Privacy Policy dialog")
        findPrivacyPolicyDialogOrLicenseAgreement()
        with(JDialogFixture(myRobot, findPrivacyPolicyDialogOrLicenseAgreement())) {
          click()
          while(!button("Accept").isEnabled) {
            scroll(10)
          }
          LOG.info("Accept License Agreement/Privacy Policy dialog")
          button("Accept").click()
        }
      }
      catch (e: WaitTimedOutError) {
        LOG.warn("'License Agreement/Privacy Policy dialog hasn't been shown. Check registry...")
      }
    }
  }

  private fun completeInstallation() {
    if (!needToShowCompleteInstallation()) return
    with(myRobot) {
      val title = "Complete Installation"
      LOG.info("Waiting for '$title' dialog")
      dialog(title)

      LOG.info("Click OK on 'Do not import settings'")
      radioButton("Do not import settings").select()
      button("OK").click()
    }
  }

  private fun acceptDataSharing() {
    with(myRobot) {
      LOG.info("Accepting Data Sharing")
      val title = "Data Sharing"
      try {
        dialog(title, timeoutSeconds = 5)
        button("Send Usage Statistics").click()
        LOG.info("Data sharing accepted")
      } catch (e: WaitTimedOutError) {
        LOG.info("Data sharing dialog hasn't been shown")
        return
      }
    }
  }

  private fun customizeIde(ideName: String = ideType.name) {
    if (!needToShowCustomizeWizard()) return
    with(myRobot) {
      val title = "Customize $ideName"
      LOG.info("Waiting for '$title' dialog")
      dialog(title)
      val buttonText = "Skip Remaining and Set Defaults"
      LOG.info("Click '$buttonText'")
      button(buttonText).click()
    }
  }

  private fun evaluateLicense(ideName: String, robot: Robot) {
    with(robot) {
      val licenseActivationFrameTitle = "$ideName License Activation"
      LOG.info("Waiting for '$licenseActivationFrameTitle' dialog")
      try {
        waitFrame(licenseActivationFrameTitle) { it == licenseActivationFrameTitle }
        radioButton("Evaluate for free").select()
        val evaluateButton = button("Evaluate")
        GuiTestUtilKt.waitUntil("activate button will be enabled") { evaluateButton.isEnabled }
        LOG.info("Click '${evaluateButton.text()}'")
        evaluateButton.click()

        dialog(10) { it.startsWith("License Agreement for") }
        button("Accept").click()
      }
      catch (waitTimedOutError: WaitTimedOutError) {
        LOG.info("No License Activation dialog has been found")
      }
    }
  }

  private fun needToShowAgreement(): Boolean {
    val agreement = EndUserAgreement.getLatestDocument()
    return !agreement.isAccepted
  }

  private fun needToShowCompleteInstallation(): Boolean {
    return newConfigFolder
  }

  private fun needToShowCustomizeWizard(): Boolean {
    return (newConfigFolder && !ConfigImportHelper.isConfigImported())
  }

  object Utils {
    fun Robot.dialog(title: String? = null, timeoutSeconds: Long = DEFAULT_TIMEOUT): JDialogFixture {
      val jDialog = waitUntilFound(this, null, JDialog::class.java, timeoutSeconds) { dialog ->
        if (title != null) dialog.title == title else true
      }
      return JDialogFixture(this, jDialog)
    }

    fun Robot.dialog(timeoutSeconds: Long = DEFAULT_TIMEOUT, titleMatcher: (String) -> Boolean): JDialogFixture {
      val jDialog = waitUntilFound(this, null, JDialog::class.java, timeoutSeconds) { dialog ->
        titleMatcher(dialog.title)
      }
      return JDialogFixture(this, jDialog)
    }


    fun Robot.radioButton(text: String, timeoutSeconds: Long = DEFAULT_TIMEOUT): JRadioButtonFixture {
      val jRadioButton = waitUntilFound(this, null, JRadioButton::class.java, timeoutSeconds) { radioButton ->
        radioButton.text == text && radioButton.isShowing && radioButton.isEnabled
      }
      return JRadioButtonFixture(this, jRadioButton)
    }

    fun Robot.button(text: String, timeoutSeconds: Long = DEFAULT_TIMEOUT): JButtonFixture {
      val jButton = waitUntilFound(this, null, JButton::class.java, timeoutSeconds) { button ->
        button.isShowing && button.text == text
      }
      return JButtonFixture(this, jButton)
    }

    fun Robot.checkbox(text: String, timeoutSeconds: Long = DEFAULT_TIMEOUT): JCheckBoxFixture {
      val jCheckBox = waitUntilFound(this, null, JCheckBox::class.java, timeoutSeconds) { checkBox ->
        checkBox.text == text && checkBox.isShowing && checkBox.isEnabled
      }
      return JCheckBoxFixture(this, jCheckBox)
    }

    fun Robot.waitFrame(title: String, timeoutInSeconds: Int = 10, titleMatching: (String) -> Boolean) {
      GuiTestUtilKt.waitUntil("frame with title '$title' will appear",
                              timeoutInSeconds) { this.hierarchy().roots().any { it is JFrame && titleMatching(it.title) } }
    }

    fun <ComponentType : Component> waitUntilFound(myRobot: Robot,
                                                   container: Container?,
                                                   componentClass: Class<ComponentType>,
                                                   timeoutSeconds: Long,
                                                   matcher: (ComponentType) -> Boolean): ComponentType {
      return waitUntilFound(myRobot, container, object : GenericTypeMatcher<ComponentType>(componentClass) {
        override fun isMatching(cmp: ComponentType): Boolean = matcher(cmp)
      }, Timeout.timeout(timeoutSeconds, TimeUnit.SECONDS))
    }

    fun <T : Component> waitUntilFound(robot: Robot,
                                       root: Container?,
                                       matcher: GenericTypeMatcher<T>,
                                       timeout: Timeout): T {
      val reference = AtomicReference<T>()
      Pause.pause(object : Condition("Find component using " + matcher.toString()) {
        override fun test(): Boolean {
          val finder = robot.finder()
          val allFound = if (root != null) finder.findAll(root, matcher) else finder.findAll(matcher)
          if (allFound.size == 1) {
            reference.set(allFound.first())
          }
          else if (allFound.size > 1) {
            // Only allow a single component to be found, otherwise you can get some really confusing
            // test failures; the matcher should pick a specific enough instance
            throw Exception("Found more than one " + matcher.supportedType().simpleName + " which matches the criteria: " + allFound)
          }
          return allFound.size == 1
        }
      }, timeout)

      return reference.get()
    }

    fun exists(fixture: () -> AbstractComponentFixture<*, *, *>): Boolean {
      return GuiTestCase().exists(fixture)
    }
  }

}
