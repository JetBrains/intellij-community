package com.intellij.ae.database.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class IdServiceTempPreloader : ProjectActivity {
  override suspend fun execute(project: Project) {
    IdService.getInstanceAsync().id
  }
}