// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.diagnostic.MessagePool
import com.intellij.ide.GeneralSettings
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.testGuiFramework.fixtures.ActionLinkFixture
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.WelcomeFrameFixture
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.computeOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.runOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.testGuiFramework.util.Key
import com.intellij.ui.Splash
import com.intellij.util.concurrency.AppExecutorUtil
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Pause
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.xpath.XPath
import org.junit.Assert
import org.junit.Assume
import org.junit.AssumptionViolatedException
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import java.awt.Dialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class GuiTestRule : TestRule {

  var CREATE_NEW_PROJECT_ACTION_NAME: String = "Create New Project"

  private val myRobotTestRule: RobotTestRule by lazy {
    val robotTestRule = RobotTestRule()
    GuiTestUtil.initializeRobot(robotTestRule.getRobot())
    return@lazy robotTestRule
  }
  private val myFatalErrorsFlusher = FatalErrorsFlusher()
  private var myProjectPath: File? = null
    set
  private var myTestName: String = "undefined"
  private var myTestShortName: String = "undefined"
  private var currentTestDateStart: Date = Date()

  private val myRuleChain = RuleChain.emptyRuleChain()
    .around(myRobotTestRule)
    .around(myFatalErrorsFlusher)
    .around(IdeHandling())
    .around(ScreenshotOnFailure())

  private val timeoutRule = Timeout(20, TimeUnit.MINUTES)

  override fun apply(base: Statement?, description: Description?): Statement {
    myTestName = "${description!!.className}#${description.methodName}"
    myTestShortName = "${description.testClass.simpleName}#${description.methodName}"
    //do not apply timeout rule if it is already applied to a test class
    return if (description.testClass.fields.any { it.type == Timeout::class.java })
      myRuleChain.apply(base, description)
    else
      myRuleChain.around(timeoutRule).apply(base, description)
  }

  fun robot(): Robot = myRobotTestRule.getRobot()

  inner class IdeHandling : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
      return object : Statement() {
        @Throws(Throwable::class)
        override fun evaluate() {
          Assume.assumeTrue("IDE error list is empty", GuiTestUtilKt.fatalErrorsFromIde().isEmpty())
          assumeOnlyWelcomeFrameShowing()
          setUp()
          val errors = ArrayList<Throwable>()
          try {
            base.evaluate()
          }
          catch (e: MultipleFailureException) {
            errors.addAll(e.failures)
          }
          catch (e: Throwable) {
            errors.add(e)
          }
          finally {
            try {
              errors.addAll(tearDown())  // shouldn't throw, but called inside a try-finally for defense in depth
            }
            finally {
              //noinspection ThrowFromFinallyBlock; assertEmpty is intended to throw here
              MultipleFailureException.assertEmpty(errors)
            }
          }
        }
      }
    }

    fun setUp() {
      GuiTestUtil.setUpDefaultProjectCreationLocationPath()
      GeneralSettings.getInstance().isShowTipsOnStartup = false
      currentTestDateStart = Date()
    }

    fun tearDown(): List<Throwable> {
      val errors = mutableListOf<Throwable>()
      errors.addAll(thrownFromRunning(Runnable { GuiTestUtilKt.waitForBackgroundTasks(robot()) }))
      errors.addAll(checkForModalDialogs())
      errors.addAll(thrownFromRunning(Runnable { this.tearDownProject() }))
      errors.addAll(thrownFromRunning(Runnable { this.returnToTheFirstStepOfWelcomeFrame() }))
      errors.addAll(GuiTestUtilKt.fatalErrorsFromIde(currentTestDateStart)) //do not add fatal errors from previous tests
      return errors.toList()
    }

    private fun tearDownProject() {
      if (myProjectPath != null) {
        val ideFrameFixture = IdeFrameFixture.find(robot(), myProjectPath, null)
        if (ideFrameFixture.target().isShowing) {
          ideFrameFixture.closeProject()
        }
        FileUtilRt.delete(myProjectPath!!)
      }
      else {
        try {
          val ideFrameFixture = IdeFrameFixture.find(robot(), null, null, 2)
          if (ideFrameFixture.target().isShowing)
            ideFrameFixture.closeProject()
        }
        catch (e: ComponentLookupException) {
          // do nothing because ideFixture is already closed
        }
      }
    }

    private fun returnToTheFirstStepOfWelcomeFrame() {
      val welcomeFrameFixture = WelcomeFrameFixture.find(robot())
      val tenSec = org.fest.swing.timing.Timeout.timeout(10, TimeUnit.SECONDS)

      fun isFirstStep(): Boolean {
        return try {
          val actionLinkFixture = ActionLinkFixture.findActionLinkByName(CREATE_NEW_PROJECT_ACTION_NAME, robot(),
                                                                         welcomeFrameFixture.target(), tenSec)
          actionLinkFixture.target().isShowing
        }
        catch (componentLookupException: ComponentLookupException) {
          false
        }
      }
      for (i in 0..3) {
        if (!isFirstStep()) GuiTestUtil.invokeActionViaShortcut(Key.ESCAPE.name)
      }
    }


    private fun thrownFromRunning(r: Runnable): List<Throwable> {
      return try {
        r.run()
        emptyList()
      }
      catch (e: Throwable) {
        ScreenshotOnFailure.takeScreenshot("$myTestName.thrownFromRunning")
        listOf(e)
      }

    }

    private fun checkForModalDialogs(): List<AssertionError> {
      val errors = ArrayList<AssertionError>()
      // We close all modal dialogs left over, because they block the AWT thread and could trigger a deadlock in the next test.
      val closedModalDialogSet = hashSetOf<Dialog>()
      try {
        waitUntil("all modal dialogs will be closed", timeoutInSeconds = 10) {
          val modalDialog: Dialog = getActiveModalDialog() ?: return@waitUntil true
          if (closedModalDialogSet.contains(modalDialog)) {
            //wait a second to let a dialog be closed
            Pause.pause(1L, TimeUnit.SECONDS)
          }
          else {
            closedModalDialogSet.add(modalDialog)
            ScreenshotOnFailure.takeScreenshot("$myTestName.checkForModalDialogFail")
            robot().close(modalDialog)
            errors.add(AssertionError("Modal dialog showing: ${modalDialog.javaClass.name} with title '${modalDialog.title}'"))
          }
          return@waitUntil false
        }
      }
      catch (timeoutError: WaitTimedOutError) {
        errors.add(AssertionError("Modal dialogs closing exceeded timeout: ${timeoutError.message}"))
      }
      return errors
    }

    // Note: this works with a cooperating window manager that returns focus properly. It does not work on bare Xvfb.
    private fun getActiveModalDialog(): Dialog? {
      val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
      if (activeWindow is Dialog) {
        if (activeWindow.modalityType == Dialog.ModalityType.APPLICATION_MODAL) {
          return activeWindow
        }
      }
      return null
    }


    private fun assumeOnlyWelcomeFrameShowing() {
      try {
        WelcomeFrameFixture.find(robot())

      }
      catch (e: WaitTimedOutError) {
        throw AssumptionViolatedException("didn't find welcome frame", e)
      }
      GuiTestUtilKt.waitUntil("Splash is gone") { !GuiTestUtilKt.windowsShowing().any { it is Splash } }
      Assume.assumeTrue("Only welcome frame is showing", GuiTestUtilKt.windowsShowing().size == 1)
    }
  }

  inner class FatalErrorsFlusher : ExternalResource() {
    override fun after() {
      try {
        val executorService = AppExecutorUtil.getAppExecutorService()
        //wait 10 second for the termination of all
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) executorService.shutdownNow()
        MessagePool.getInstance().clearErrors()
      }
      catch (e: Exception) {
        //TODO: log it
      }
    }
  }

  fun findWelcomeFrame(): WelcomeFrameFixture {
    return WelcomeFrameFixture.find(robot())
  }

  fun findNewProjectWizard(): NewProjectWizardFixture {
    return NewProjectWizardFixture.find(robot())
  }

  fun findIdeFrame(projectName: String, projectPath: File): IdeFrameFixture {
    return IdeFrameFixture.find(robot(), projectPath, projectName)
  }

  fun closeAllProjects() {
    waitUntil("close all projects", 120) {
      val openProjects = ProjectManager.getInstance().openProjects
      runOnEdt {
        TransactionGuard.submitTransaction(ApplicationManager.getApplication(), Runnable {
          for (project in openProjects) {
            Assert.assertTrue("Failed to close project ${project.name}", ProjectUtil.closeAndDispose(project))
          }
        })
      }
      ProjectManager.getInstance().openProjects.isEmpty()
    }


    val welcomeFrameShown = computeOnEdt {
      val openProjects = ProjectManager.getInstance().openProjects
      if (openProjects.isEmpty()) {
        WelcomeFrame.showNow()
        val windowManager = WindowManager.getInstance() as WindowManagerImpl
        windowManager.disposeRootFrame()
        true
      }
      else {
        false
      }
    } ?: false

    if (welcomeFrameShown) {
      waitUntil("Welcome frame to show up", 120) {
        Frame.getFrames().any { it === WelcomeFrame.getInstance() && it.isShowing }
      }
    }
  }

  fun importSimpleProject(): IdeFrameFixture {
    return importProjectAndWaitForProjectSyncToFinish("SimpleProject")
  }

  fun importMultiModule(): IdeFrameFixture {
    return importProjectAndWaitForProjectSyncToFinish("MultiModule")
  }

  fun importProjectAndWaitForProjectSyncToFinish(projectDirName: String): IdeFrameFixture {
    return importProjectAndWaitForProjectSyncToFinish(projectDirName, null)
  }

  fun importProjectAndWaitForProjectSyncToFinish(projectDirName: String, gradleVersion: String?): IdeFrameFixture {
    val projectPath = setUpProject(projectDirName)
    val toSelect = VfsUtil.findFileByIoFile(projectPath, false)
    Assert.assertNotNull(toSelect)
    doImportProject(toSelect!!)
    //TODO: add wait to open project
    return findIdeFrame(projectPath)
  }

  fun importProject(projectDirName: String): File {
    val projectPath = setUpProject(projectDirName)
    val toSelect = VfsUtil.findFileByIoFile(projectPath, false)
    Assert.assertNotNull(toSelect)
    doImportProject(toSelect!!)
    return projectPath
  }

  fun importProject(projectFile: File): File {
    val projectPath = setUpProject(projectFile)
    val toSelect = VfsUtil.findFileByIoFile(projectPath, false)
    Assert.assertNotNull(toSelect)
    doImportProject(toSelect!!)
    return projectPath
  }

  private fun doImportProject(projectDir: VirtualFile) {
    runOnEdt {
      TransactionGuard.submitTransaction(ApplicationManager.getApplication(),
                                         Runnable { ProjectUtil.openOrImport(projectDir.path, null, false) })
    }
  }


  private fun setUpProject(projectDirName: String): File {
    val projectPath = copyProjectBeforeOpening(projectDirName)
    Assert.assertNotNull(projectPath)
    return projectPath
  }

  private fun setUpProject(projectDirFile: File): File {
    val projectPath = copyProjectBeforeOpening(projectDirFile)
    Assert.assertNotNull(projectPath)
    return projectPath
  }


  fun copyProjectBeforeOpening(projectDirName: String): File {
    val masterProjectPath = getMasterProjectDirPath(projectDirName)

    val projectPath = getTestProjectDirPath(projectDirName)
    if (projectPath.isDirectory) {
      FileUtilRt.delete(projectPath)
      println(String.format("Deleted project path '%1\$s'", projectPath.path))
    }
    FileUtil.copyDir(masterProjectPath, projectPath)
    println("Copied project '$projectDirName' to path '${projectPath.path}'")
    return projectPath
  }

  fun copyProjectBeforeOpening(projectDirFile: File): File {

    val projectPath = getTestProjectDirPath(projectDirFile.name)
    if (projectPath.isDirectory) {
      FileUtilRt.delete(projectPath)
      println(String.format("Deleted project path '%1\$s'", projectPath.path))
    }
    FileUtil.copyDir(projectDirFile, projectPath)
    println("Copied project '${projectDirFile.name}' to path '${projectPath.path}'")
    return projectPath
  }


  fun getMasterProjectDirPath(projectDirName: String): File {
    return File(GuiTestUtil.testProjectsRootDirPath, projectDirName)
  }

  fun getTestProjectDirPath(projectDirName: String): File {
    return File(GuiTestUtil.projectCreationDirPath, projectDirName)
  }

  fun cleanUpProjectForImport(projectPath: File) {
    val dotIdeaFolderPath = File(projectPath, Project.DIRECTORY_STORE_FOLDER)
    if (dotIdeaFolderPath.isDirectory) {
      val modulesXmlFilePath = File(dotIdeaFolderPath, "modules.xml")
      if (modulesXmlFilePath.isFile) {
        val saxBuilder = SAXBuilder()
        try {
          val document = saxBuilder.build(modulesXmlFilePath)
          val xpath = XPath.newInstance("//*[@fileurl]")

          val modules = xpath.selectNodes(document)
          val urlPrefixSize = "file://\$PROJECT_DIR$/".length
          for (module in modules) {
            val fileUrl = (module as Element).getAttributeValue("" + "fileurl")
            if (!StringUtil.isEmpty(fileUrl)) {
              val relativePath = FileUtil.toSystemDependentName(fileUrl!!.substring(urlPrefixSize))
              val imlFilePath = File(projectPath, relativePath)
              if (imlFilePath.isFile) FileUtilRt.delete(imlFilePath)

              // It is likely that each module has a "build" folder. Delete it as well.
              val buildFilePath = File(imlFilePath.parentFile, "build")
              if (buildFilePath.isDirectory) FileUtil.delete(buildFilePath)
            }
          }
        }
        catch (ignored: Throwable) {
          // if something goes wrong, just ignore. Most likely it won't affect project import in any way.
        }

      }
      FileUtil.delete(dotIdeaFolderPath)
    }
  }

  fun findIdeFrame(projectPath: File): IdeFrameFixture {
    return IdeFrameFixture.find(robot(), projectPath, null)
  }


  fun findIdeFrame(): IdeFrameFixture {
    return IdeFrameFixture.find(robot(), null, null)
  }

  fun getTestName(): String {
    return myTestName
  }

}