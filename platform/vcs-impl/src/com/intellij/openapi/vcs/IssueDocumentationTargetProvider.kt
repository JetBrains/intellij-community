// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.model.Symbol
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a way to create a [Symbol] for an issue link.
 * Symbol may provide some additional functionality, like syntax highlighting,
 * or documentation.
 */
@ApiStatus.Experimental
interface IssueDocumentationTargetProvider {

  fun getIssueDocumentationTarget(project: Project, issueId: String, issueUrl: String): DocumentationTarget?

  companion object {
    @ApiStatus.Experimental
    internal val EP_NAME = ExtensionPointName<IssueDocumentationTargetProvider>("com.intellij.vcs.issueDocumentationTargetProvider")

    @ApiStatus.Internal
    fun getIssueDocumentationTarget(project: Project, issueId: String, issueUrl: String): DocumentationTarget? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getIssueDocumentationTarget(project, issueId, issueUrl) }

  }

}