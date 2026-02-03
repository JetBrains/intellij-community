package com.intellij.grazie.cloud.license

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class LicenseStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    GrazieLoginManager.getInstance()
  }
}