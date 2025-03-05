package com.intellij.driver.sdk.plugins.qodana

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.minutes

@Remote("org.jetbrains.qodana.inspectionKts.tests.ProfileCheckerKt", plugin = "org.intellij.qodana")
interface InspectionProfileChecker {
  fun isInspectionPresentInProfile(project: Project, inspectionId: String): Boolean
}

fun Driver.awaitInspectionAppearInProfile(inspectionId: String, project: Project? = null) {
  return withContext {
    val forProject = project ?: singleProject()
    waitFor("Wait for $inspectionId appear in inspection profile", timeout = 1.minutes) {
      this.utility(InspectionProfileChecker::class).isInspectionPresentInProfile(forProject, inspectionId)
    }
  }
}

