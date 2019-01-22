// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.FirstStart.Utils.button
import com.intellij.testGuiFramework.impl.FirstStart.Utils.dialog
import com.intellij.testGuiFramework.impl.FirstStart.Utils.radioButton
import com.intellij.testGuiFramework.impl.FirstStart.Utils.waitFrame
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.repeatUntil
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.silentWaitUntil
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
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.concurrent.thread

/**
 * could be used only to initialize IDEA configuration for the first start. Please do not start it with GuiTestCase runner.
 */
abstract class FirstStart(val ideType: IdeType) {

  private val FIRST_START_ROBOT_THREAD = "First Start Robot Thread"

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

  fun takeScreenshot(e: Throwable) {
    ScreenshotOnFailure.takeScreenshot("FirstStartFailed", e)
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
    println("Starting separated thread: '$FIRST_START_ROBOT_THREAD' to complete initial installation")
    robotThread.start()
  }


  // In case we found WelcomeFrame we don't need to make completeInstallation.
  open fun completeFirstStart() {
    findWelcomeFrame()?.close() ?: let {
      completeInstallation()
      acceptAgreement()
      acceptDataSharing()
      customizeIde()
      evaluateLicense(ideType.name, myRobot)
      findWelcomeFrame()?.close()
    }
  }

  private val checkIsWelcomeFrame: (Frame) -> Boolean = { frame ->
    frame.javaClass.simpleName == "FlatWelcomeFrame"
    && frame.isShowing
    && frame.isEnabled
  }

  fun Frame.close() = myRobot.close(this)

  fun findWelcomeFrame(seconds: Int = 5): Frame? {
    println("Waiting for a Welcome Frame")
    silentWaitUntil("Welcome Frame to show up", seconds) {
      Frame.getFrames().any { checkIsWelcomeFrame(it) }
    }
    return Frame.getFrames().firstOrNull { checkIsWelcomeFrame(it) }
  }


  private fun findPrivacyPolicyDialogOrLicenseAgreement(): JDialog {
    return GuiTestUtilKt.withPauseWhenNull(timeout = Timeouts.defaultTimeout) {
      try {
        myRobot.finder().find {
          it is JDialog && (it.title.contains("License Agreement") || it.title.contains("Privacy Policy") || it.title.contains("User Agreement"))
        } as JDialog
      }
      catch (cle: ComponentLookupException) {
        null
      }
    }
  }

  open fun acceptAgreement() {
    if (!needToShowAgreement()) return
    try {
      println("Waiting for License Agreement/Privacy Policy dialog")
      findPrivacyPolicyDialogOrLicenseAgreement()
      with(JDialogFixture(myRobot, findPrivacyPolicyDialogOrLicenseAgreement())) {
        click()
        checkboxContainingText("i confirm", true, Timeouts.noTimeout).select()
        println("Accept License Agreement/Privacy Policy dialog")
        button("Continue", Timeouts.seconds05).click()
      }
    }
    catch (e: WaitTimedOutError) {
      println("'License Agreement/Privacy Policy dialog hasn't been shown. Check registry...")
    }
  }

  open fun completeInstallation() {
    if (!needToShowCompleteInstallation()) return
    with(myRobot) {
      println("Waiting for 'import settings' dialog")
      val dialogFixture = dialog {
        it.startsWith("Import")
      }

      println("Click OK on 'Do not import settings'")
      dialogFixture.radioButton("Do not import settings").select()

      repeatUntil(
        { !dialogFixture.target().isShowing },
        { dialogFixture.button("OK").click() }
      )
    }
  }

  open fun acceptDataSharing() {
    with(myRobot) {
      println("Accepting Data Sharing")
      val title = "Data Sharing"
      try {
        dialog(title, timeout = Timeouts.seconds05)
        button("Send Usage Statistics").click()
        println("Data sharing accepted")
      }
      catch (e: WaitTimedOutError) {
        println("Data sharing dialog hasn't been shown")
        return
      }
    }
  }

  open fun customizeIde(ideName: String = ideType.name) {
    if (!needToShowCustomizeWizard()) return
    with(myRobot) {
      val title = "Customize $ideName"
      println("Waiting for '$title' dialog")
      dialog(title)
      val buttonText = "Skip Remaining and Set Defaults"
      println("Click '$buttonText'")
      button(buttonText).click()
    }
  }

  open fun evaluateLicense(ideName: String, robot: Robot) {
    with(robot) {
      val licenseActivationFrameTitle = "$ideName License Activation"
      println("Waiting for '$licenseActivationFrameTitle' dialog")
      try {
        waitFrame(licenseActivationFrameTitle) { it == licenseActivationFrameTitle }
        radioButton("Evaluate for free").select()
        val evaluateButton = button("Evaluate")
        GuiTestUtilKt.waitUntil("activate button will be enabled") { evaluateButton.isEnabled }
        println("Click '${evaluateButton.text()}'")
        evaluateButton.click()

        dialog(timeout = Timeouts.seconds10) { it.startsWith("License Agreement for") }
        button("Accept").click()
      }
      catch (waitTimedOutError: WaitTimedOutError) {
        println("No License Activation dialog has been found")
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
    fun Robot.dialog(title: String? = null, timeout: Timeout = Timeouts.defaultTimeout): JDialogFixture {
      val jDialog = waitUntilFound(this, null, JDialog::class.java, timeout) { dialog ->
        if (title != null) dialog.title == title else true
      }
      return JDialogFixture(this, jDialog)
    }

    fun Robot.dialog(timeout: Timeout = Timeouts.defaultTimeout, titleMatcher: (String) -> Boolean): JDialogFixture {
      val jDialog = waitUntilFound(this, null, JDialog::class.java, timeout) { dialog ->
        titleMatcher(dialog.title)
      }
      return JDialogFixture(this, jDialog)
    }


    fun Robot.radioButton(text: String, timeout: Timeout = Timeouts.defaultTimeout): JRadioButtonFixture {
      val jRadioButton = waitUntilFound(this, null, JRadioButton::class.java, timeout) { radioButton ->
        radioButton.text == text && radioButton.isShowing && radioButton.isEnabled
      }
      return JRadioButtonFixture(this, jRadioButton)
    }

    fun Robot.button(text: String, timeout: Timeout = Timeouts.defaultTimeout): JButtonFixture {
      val jButton = waitUntilFound(this, null, JButton::class.java, timeout) { button ->
        button.isShowing && button.text == text
      }
      return JButtonFixture(this, jButton)
    }

    fun Robot.checkbox(text: String, timeout: Timeout = Timeouts.defaultTimeout): JCheckBoxFixture {
      val jCheckBox = waitUntilFound(this, null, JCheckBox::class.java, timeout) { checkBox ->
        checkBox.text == text && checkBox.isShowing && checkBox.isEnabled
      }
      return JCheckBoxFixture(this, jCheckBox)
    }

    fun Robot.waitFrame(title: String, timeout: Timeout = Timeouts.seconds30, titleMatching: (String?) -> Boolean) {
      GuiTestUtilKt.waitUntil("frame with title '$title' will appear",
                              timeout) { this.hierarchy().roots().any { it is JFrame && titleMatching(it.title) } }
    }

    fun <ComponentType : Component> waitUntilFound(myRobot: Robot,
                                                   container: Container?,
                                                   componentClass: Class<ComponentType>,
                                                   timeout: Timeout,
                                                   matcher: (ComponentType) -> Boolean): ComponentType {
      return waitUntilFound(myRobot, container, object : GenericTypeMatcher<ComponentType>(componentClass) {
        override fun isMatching(cmp: ComponentType): Boolean = matcher(cmp)
      }, timeout)
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
