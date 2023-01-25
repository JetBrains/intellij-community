// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabFilesController

object GitLabMergeRequestsActionKeys {
  @JvmStatic
  val SELECTED = DataKey.create<GitLabMergeRequestShortRestDTO>("org.jetbrains.plugins.gitlab.mergerequest.selected")

  @JvmStatic
  val FILES_CONTROLLER = DataKey.create<GitLabFilesController>("org.jetbrains.plugins.gitlab.mergerequest.files.controller")
}