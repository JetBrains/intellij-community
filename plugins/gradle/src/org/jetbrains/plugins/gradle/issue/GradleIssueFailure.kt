// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable

@Experimental
@NonExtendable
interface GradleIssueFailure {

  val message: @NlsSafe String?
  val description: @NlsSafe String?

  val causes: List<GradleIssueFailure>
  val rootCause: GradleIssueFailure

  val filePosition: FilePosition?

  val className: String?

  val text: String

  @get:Internal
  val messageOrDescription: @NlsSafe String?
    get() = message ?: description

  companion object {

    @JvmStatic
    @JvmOverloads
    fun createIssueFailure(
      message: String?,
      description: String? = message,
      causes: List<GradleIssueFailure> = emptyList(),
    ): GradleIssueFailure =
      GradleIssueFailureImpl(message, description, causes)

    @JvmStatic
    fun createIssueFailure(error: Throwable): GradleIssueFailure =
      GradleThrowableIssueFailure(error)
  }
}
