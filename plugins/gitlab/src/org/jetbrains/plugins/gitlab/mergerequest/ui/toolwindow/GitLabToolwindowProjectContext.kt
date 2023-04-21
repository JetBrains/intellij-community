// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectContext
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection

class GitLabToolwindowProjectContext(
  val connection: GitLabProjectConnection
) : ReviewToolwindowProjectContext {
  override val projectName: @Nls String = connection.repo.repository.projectPath.name
}