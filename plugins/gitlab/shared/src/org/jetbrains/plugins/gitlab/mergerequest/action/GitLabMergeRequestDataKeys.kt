// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails

/**
 * The data keys of the merge request actions that the frontend and the backend share.
 */
@ApiStatus.Internal
object GitLabMergeRequestDataKeys {
  /**
   * The merge request that is selected in the merge request list.
   */
  @JvmStatic
  val SELECTED: DataKey<GitLabMergeRequestDetails> = DataKey.create("org.jetbrains.plugins.gitlab.mergerequest.selected")
}
