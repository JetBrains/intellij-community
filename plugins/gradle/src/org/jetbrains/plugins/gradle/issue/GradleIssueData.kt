// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssueData
import com.intellij.openapi.util.io.toCanonicalPath
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import java.nio.file.Path

/**
 * @author Vladislav.Soroka
 */
@Experimental
@NonExtendable
interface GradleIssueData : BuildIssueData {

  val projectRoot: Path

  @get:Deprecated("Use projectRoot instead")
  val projectPath: String
    get() = projectRoot.toCanonicalPath()

  val failure: GradleIssueFailure

  @get:Deprecated("Use failure instead")
  val error: Throwable

  val buildEnvironment: BuildEnvironment?

  val filePosition: FilePosition?

  companion object {

    @Deprecated("Use createIssueData instead", ReplaceWith(
        "createIssueData( projectRoot = Path.of(projectPath), failure = GradleIssueFailure.createIssueFailure(error), buildEnvironment = buildEnvironment, filePosition = filePosition )",
        "org.jetbrains.plugins.gradle.issue.GradleIssueData.Companion.createIssueData",
        "java.nio.file.Path"
    ))
    operator fun invoke(
      projectPath: String,
      error: Throwable,
      buildEnvironment: BuildEnvironment? = null,
      filePosition: FilePosition? = null,
    ): GradleIssueData = createIssueData(
      projectRoot = Path.of(projectPath),
      failure = GradleIssueFailure.createIssueFailure(error),
      buildEnvironment = buildEnvironment,
      filePosition = filePosition
    )

    @JvmStatic
    fun createIssueData(
      projectRoot: Path,
      failure: GradleIssueFailure,
      buildEnvironment: BuildEnvironment?,
      filePosition: FilePosition?,
    ): GradleIssueData =
      GradleIssueDataImpl(projectRoot, failure, buildEnvironment, filePosition)
  }
}
