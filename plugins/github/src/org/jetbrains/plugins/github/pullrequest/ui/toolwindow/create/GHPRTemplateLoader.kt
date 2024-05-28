// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

internal object GHPRTemplateLoader {

  private val LOG = logger<GHPRTemplateLoader>()

  private val paths = listOf(
    ".github/pull_request_template.md",
    "pull_request_template.md",
    "docs/pull_request_template.md"
  )

  suspend fun loadTemplate(project: Project): String? =
    withContext(Dispatchers.IO) {
      doLoad(project)
    }

  @RequiresBackgroundThread
  private fun doLoad(project: Project): String? {
    val basePath = project.basePath ?: return null
    try {
      val files = paths.map { Paths.get(basePath, it) }
      val fileContent = files.find(Files::exists)?.let(Files::readString)
      if (fileContent != null) return fileContent.filter { it != '\r' }
    }
    catch (e: Exception) {
      LOG.warn("Failed to read PR template", e)
    }
    return null
  }
}