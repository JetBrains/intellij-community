package com.intellij.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.spring.SpringManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpenFilesTest {

  @Test
  fun uiTest() {
    val driver = Driver.create()
    val robot = driver.service(RobotService::class)

    val component = robot.find("//div[@text='2023.3 EAP']")
    println(component)
    component.click()
  }

  @Test
  fun openFiles() {
    val driver = Driver.create()

    assertTrue(driver.isConnected)

    val productVersion = driver.getProductVersion()

    val projectManager = driver.service(ProjectManager::class)
    val projects = projectManager.openProjects
    assertEquals(1, projects.size, "No opened projects")

    val fromUtil = driver.utility(ProjectUtilCore::class).openProjects
    assertEquals(projects.size, fromUtil.size, "Count of opened projects is the same")

    val project = projects.single()

    val projectRootManager = driver.service(ProjectRootManager::class, project)
    val contentRoots = projectRootManager.contentRoots

    val contentRoot = contentRoots.first { it.name != "main" && it.name != "test" }

    driver.withContext {
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