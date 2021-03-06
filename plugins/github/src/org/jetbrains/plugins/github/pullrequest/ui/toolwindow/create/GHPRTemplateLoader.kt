// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.plugins.github.util.submitIOTask
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

object GHPRTemplateLoader {

  private val LOG = logger<GHPRTemplateLoader>()

  private val paths = listOf(
    ".github/pull_request_template.md",
    "pull_request_template.md",
    "docs/pull_request_template.md"
  )

  fun readTemplate(project: Project): CompletableFuture<String?> {
    return ProgressManager.getInstance().submitIOTask(EmptyProgressIndicator()) {
      doLoad(project)
    }
  }

  @RequiresBackgroundThread
  private fun doLoad(project: Project): String? {
    val basePath = project.basePath ?: return null
    try {
      val files = paths.map { Paths.get(basePath, it) }
      val fileContent = files.find(Files::exists)?.let(Files::readString)
      if (fileContent != null) return fileContent
    }
    catch (e: Exception) {
      LOG.warn("Failed to read PR template", e)
    }
    return null
  }
}