package com.intellij.driver.sdk.plugins.gradle

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Remote("org.jetbrains.plugins.gradle.performanceTesting.ImportGradleProjectUtil", plugin = "org.jetbrains.plugins.gradle")
interface ImportGradleProjectUtil {
  fun importProject(project: Project)
}

fun Driver.importGradleProject(project: Project? = null, completionTimeout: Duration = 10.minutes) {
  withContext {
    val forProject = project ?: singleProject()
    this.utility(ImportGradleProjectUtil::class).importProject(forProject)

    waitForIndicators(forProject, completionTimeout)
  }
}