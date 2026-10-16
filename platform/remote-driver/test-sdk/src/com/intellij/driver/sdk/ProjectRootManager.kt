package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ui.components.common.waitForIdeFrameReady
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.openapi.roots.ProjectRootManager", rdTarget = RdTarget.BACKEND)
interface ProjectRootManager {
  fun getContentRoots(): Array<VirtualFile>

  fun getProjectSdk(): Sdk?

  fun setProjectSdk(sdk: Sdk?)
}

@Remote("com.intellij.openapi.roots.ProjectRootManager",
        rdTarget = RdTarget.FRONTEND,
        plugin = "com.jetbrains.performancePlugin/intellij.performanceTesting.frontend.split")
interface FrontendProjectRootManager {
  fun getContentRoots(): Array<VirtualFile>
}

fun Driver.findFile(relativePath: String, project: Project? = null): VirtualFile? {
  return withReadAction {
    val notNullProject = project ?: singleProject()
    if (isRemDevMode) {
      service<FrontendProjectRootManager>(notNullProject).getContentRoots()
        .firstNotNullOfOrNull { it.findFileByRelativePath(relativePath) }
    } else {
      // On Frontend the file will not be found unless it was opened previously
      val roots = service<ProjectRootManager>(notNullProject).getContentRoots().toList() +
                         // project with no modules and no configurators run
                         listOfNotNull(utility<ProjectUtil>().guessProjectDir(notNullProject))
      roots.firstNotNullOfOrNull { it.findFileByRelativePath(relativePath) }
    }
  }
}

@Remote("com.intellij.openapi.project.ProjectUtil")
private interface ProjectUtil {
  fun guessProjectDir(project: Project): VirtualFile?
}

@Remote("com.intellij.openapi.projectRoots.Sdk")
interface Sdk {
  fun getName(): String

  fun getVersionString(): String?

  fun getHomePath(): String?
}

@Remote(value = "com.jetbrains.performancePlugin.commands.ReloadFilesCommand",
        plugin = "com.jetbrains.performancePlugin")
interface ReloadFromDiskCommand {
  fun synchronizeFiles(filePaths: List<String>)
}

@Remote(value = "com.jetbrains.performancePlugin.commands.SetupProjectSdkUtil",
        plugin = "com.jetbrains.performancePlugin",
        rdTarget = RdTarget.BACKEND)
interface SetupProjectSdkUtil {
  fun setupOrDetectSdk(project: Project, name: String, type: String, home: String)

  fun setupOrDetectSdk(name: String, type: String, home: String): Sdk

  fun isApplicationLoaded(): Boolean
}

fun Driver.setupOrDetectSdk(project: Project, name: String, type: String, home: String) {
  waitForIdeFrameReady()
  utility<SetupProjectSdkUtil>().setupOrDetectSdk(project, name, type, home)
}

fun Driver.setupOrDetectSdk(name: String, type: String, home: String) {
  waitForIdeFrameReady()
  waitFor("Application is loaded", timeout = 15.seconds) { utility<SetupProjectSdkUtil>().isApplicationLoaded() }
  utility<SetupProjectSdkUtil>().setupOrDetectSdk(name, type, home)
}
