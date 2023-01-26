// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.openapi.util.Key

internal object GitLabReviewDataKeys {
  @JvmStatic
  val REVIEW_TAB: Key<GitLabReviewTab> = Key.create("com.intellij.gitlab.vcs.review.tab")
}