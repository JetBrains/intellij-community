package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.openapi.roots.ProjectRootManager", rdTarget = RdTarget.BACKEND)
interface ProjectRootManager {
  fun getContentRoots(): Array<VirtualFile>

  fun getProjectSdk(): Sdk?

  fun setProjectSdk(sdk: Sdk?)
}

fun Driver.findFile(relativePath: String, project: Project? = null): VirtualFile? {
  return withReadAction {
    service<ProjectRootManager>(project ?: singleProject()).getContentRoots()
      .firstNotNullOfOrNull { it.findFileByRelativePath(relativePath) }
  }
}

@Remote("com.intellij.openapi.projectRoots.Sdk")
interface Sdk {
  fun getName(): String

  fun getVersionString(): String?

  fun getHomePath(): String?
}

@Remote(value = "com.jetbrains.performancePlugin.commands.SetupProjectSdkUtil",
        plugin = "com.jetbrains.performancePlugin",
        rdTarget = RdTarget.BACKEND)
interface SetupProjectSdkUtil {
  fun setupOrDetectSdk(project: Project, name: String, type: String, home: String)

  fun setupOrDetectSdk(name: String, type: String, home: String): Sdk
}

fun Driver.setupOrDetectSdk(project: Project, name: String, type: String, home: String) {
  utility<SetupProjectSdkUtil>().setupOrDetectSdk(project, name, type, home)
}

fun Driver.setupOrDetectSdk(name: String, type: String, home: String) {
  utility<SetupProjectSdkUtil>().setupOrDetectSdk(name, type, home)
}