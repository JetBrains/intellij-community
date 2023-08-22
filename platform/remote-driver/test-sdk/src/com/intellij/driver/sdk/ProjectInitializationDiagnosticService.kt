package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService")
interface ProjectInitializationDiagnosticService {
  fun isProjectInitializationAndIndexingFinished(): Boolean
}