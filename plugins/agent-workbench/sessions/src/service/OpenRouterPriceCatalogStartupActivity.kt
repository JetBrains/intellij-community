// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.sessions.core.cost.OpenRouterPriceCatalogService
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class OpenRouterPriceCatalogStartupActivity : ProjectActivity {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun execute(project: Project) {
    serviceAsync<OpenRouterPriceCatalogService>().refreshAtStartup()
  }
}
