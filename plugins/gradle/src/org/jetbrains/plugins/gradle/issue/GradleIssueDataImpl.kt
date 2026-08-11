// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import org.gradle.tooling.model.build.BuildEnvironment
import java.nio.file.Path

internal class GradleIssueDataImpl(
  override val projectRoot: Path,
  override val failure: GradleIssueFailure,
  override val buildEnvironment: BuildEnvironment? = null,
  filePosition: FilePosition? = null,
) : GradleIssueData {

  override val filePosition: FilePosition? = filePosition
    get() = field ?: failure.filePosition

  @Suppress("OVERRIDE_DEPRECATION")
  override val error: Throwable = failure.toThrowable()
}

private fun GradleIssueFailure.toThrowable(): Throwable {
  if (this is GradleThrowableIssueFailure) {
    return throwable
  }
  val error = RuntimeException(messageOrDescription)
  val cause = causes.firstOrNull()
  if (cause != null) {
    error.initCause(cause.toThrowable())
  }
  return error
}
