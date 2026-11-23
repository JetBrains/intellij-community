// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * The review settings of the backend, which [GitLabMergeRequestsPreferences] stores.
 */
internal class BackendGitLabReviewSettings(project: Project)
  : GitLabReviewSettings by project.service<GitLabMergeRequestsPreferences>()
