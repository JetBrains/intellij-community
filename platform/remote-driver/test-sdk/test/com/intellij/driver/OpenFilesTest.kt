package com.intellij.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.spring.SpringManager
import com.intellij.driver.sdk.ui.UiComponent
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.remote.RemoteComponent
import com.intellij.driver.sdk.ui.ui
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit

class OpenFilesTest {
  @Suppress("MemberVisibilityCanBePrivate")
  fun UiRobot.newProjectDialog(action: NewProjectDialog.() -> Unit) {
    find("//div[@class='MyDialog']", NewProjectDialog::class.java).action()
  }

  class NewProjectDialog(remoteComponent: RemoteComponent) : UiComponent(remoteComponent) {
    fun fillProjectName(text: String) {
      nameTextField.click()
      keyboard {
        hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
        backspace()
        enterText(text)
      }
    }

    private val nameTextField: UiComponent
      get() = find("//div[@accessiblename='Name:' and @class='JBTextField']")

    val next: UiComponent
      get() = find("//div[@text='Next']")

    val create: UiComponent
      get() = find("//div[@text='Create']")
  }

  @Test
  fun newProject() {
    val driver = Driver.create()
    val ui = driver.ui()
    val welcomeFrame = ui.find("//div[@class='FlatWelcomeFrame']")
    val createNewProjectButton = welcomeFrame.find("//div[@visible_text='New Project']")
    createNewProjectButton.click()

    ui.newProjectDialog {
      fillProjectName("TestProject_" + System.currentTimeMillis())
      next.click()
      create.click()
    }
  }

  @Test
  fun openFiles() {
    val driver = Driver.create()

    Awaitility.await()
      .pollDelay(10, TimeUnit.SECONDS)
      .atMost(120, TimeUnit.SECONDS)
      .until { driver.isConnected }

    val productVersion = driver.getProductVersion()

    driver.withContext {
      val projectManager = driver.service(ProjectManager::class)
      val projects = projectManager.openProjects
      assertEquals(1, projects.size, "No opened projects")

      val fromUtil = driver.utility(ProjectUtilCore::class).openProjects
      assertEquals(projects.size, fromUtil.size, "Count of opened projects is the same")

      val project = projects.single()

      val projectRootManager = driver.service(ProjectRootManager::class, project)
      val contentRoots = projectRootManager.contentRoots

      val contentRoot = contentRoots.first { it.name != "main" && it.name != "test" }

      val readmeFile = contentRoot.findChild("README.md")
      assertNotNull(readmeFile, "No README.md in $contentRoot")

      driver.withReadAction {
        val psiFile = service(PsiManager::class, project).findFile(readmeFile!!)
        val springModel = service(SpringManager::class, project).getSpringModelByFile(psiFile!!)

        assertNull(springModel)
      }

      driver.withContext(OnDispatcher.EDT) {
        val fileEditorManager = service(FileEditorManager::class, project)
        fileEditorManager.openFile(readmeFile!!, true, true)
      }
    }
  }
}