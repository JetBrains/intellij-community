package com.intellij.driver.sdk.plugins.maven

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Remote("com.intellij.maven.performanceTesting.ImportMavenProjectUtil",
        plugin = "org.jetbrains.idea.maven/intellij.maven.performanceTesting",
        rdTarget = RdTarget.BACKEND)
interface ImportMavenProjectUtil {
  fun importProject(project: Project)
}

fun Driver.importMavenProject(project: Project? = null, completionTimeout: Duration = 10.minutes) {
  withContext {
    val forProject = project ?: singleProject()
    this.utility(ImportMavenProjectUtil::class).importProject(forProject)

    @OptIn(ManualWaitForIndicators::class)
    waitForIndicators(forProject, completionTimeout, waitSmartLongEnough = false)
  }
}