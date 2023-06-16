// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Nls

/**
 * Migrate to [withModalProgress] or [runWithModalProgressBlocking]:
 * ```
 * launch {
 *   withModalProgress(project, title) {
 *     ...
 *   }
 * }
 * ```
 */
@Obsolete
inline fun runModalTask(@Nls(capitalization = Nls.Capitalization.Title) title: String,
                        project: Project? = null,
                        cancellable: Boolean = true,
                        crossinline task: (indicator: ProgressIndicator) -> Unit) {
  ProgressManager.getInstance().run(object : Task.Modal(project, title, cancellable) {
    override fun run(indicator: ProgressIndicator) {
      task(indicator)
    }
  })
}

/**
 * Migrate to [withBackgroundProgress]:
 * ```
 * launch {
 *   withBackgroundProgress(project, title, cancellable) {
 *     ...
 *   }
 * }
 * ```
 */
@Obsolete
inline fun runBackgroundableTask(@NlsContexts.ProgressTitle title: String,
                                 project: Project? = null,
                                 cancellable: Boolean = true,
                                 crossinline task: (indicator: ProgressIndicator) -> Unit) {
  ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {
    override fun run(indicator: ProgressIndicator) {
      task(indicator)
    }
  })
}