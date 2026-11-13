// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Takes over an open of a project before the platform builds the project.
 *
 * `ProjectManagerEx.openProjectAsync` asks each interceptor in order, after it has checked that the project is not open
 * yet and before it attaches, asks for trust, or allocates a frame. The first answer ends the open. A `null` answer lets
 * the platform continue with the next interceptor, or with the open.
 *
 * An interceptor that answers owns the open: it confirms the trust, picks the frame and opens what it wants. It gets the
 * options as the caller passed them. When the options carry a project, the platform closes that project after the answer,
 * because the caller created it for the open that does not happen.
 *
 * A `ProjectOpenProcessor` runs only inside `ProjectUtil.openOrImportAsync`. An interceptor also sees an open that a
 * caller sends to the project manager directly, such as a recent projects row or the New Project wizard.
 *
 * Register via the `com.intellij.projectOpenInterceptor` extension point.
 */
@ApiStatus.Internal
interface ProjectOpenInterceptor {
  /**
   * Answers the open of the project at [projectIdentityFile] with [options], or gives `null` to let the platform continue.
   * [Answer.project] is the project the caller gets, or `null` when the open ends with no project.
   */
  suspend fun intercept(projectIdentityFile: Path, options: OpenProjectTask): Answer?

  class Answer(@JvmField val project: Project?)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ProjectOpenInterceptor> = ExtensionPointName("com.intellij.projectOpenInterceptor")
  }
}
