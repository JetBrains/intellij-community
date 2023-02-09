// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails

internal object GitLabMergeRequestsActionKeys {
  @JvmStatic
  val SELECTED = DataKey.create<GitLabMergeRequestDetails>("org.jetbrains.plugins.gitlab.mergerequest.selected")

  @JvmStatic
  val FILES_CONTROLLER = DataKey.create<GitLabMergeRequestsFilesController>("org.jetbrains.plugins.gitlab.mergerequests.files.controller")
}