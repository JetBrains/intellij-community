package com.intellij.driver.sdk.plugins.gradle

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Remote("com.intellij.gradle.java.performance.ImportGradleProjectUtil",
        plugin = "org.jetbrains.plugins.gradle/intellij.gradle.java.performance",
        rdTarget = RdTarget.BACKEND)
interface ImportGradleProjectUtil {
  fun importProject(project: Project)
}

fun Driver.importGradleProject(project: Project? = null, completionTimeout: Duration = 10.minutes) {
  withContext {
    val forProject = project ?: singleProject()
    this.utility(ImportGradleProjectUtil::class).importProject(forProject)

    @OptIn(ManualWaitForIndicators::class)
    waitForIndicators(forProject, completionTimeout, waitSmartLongEnough = false)
  }
}