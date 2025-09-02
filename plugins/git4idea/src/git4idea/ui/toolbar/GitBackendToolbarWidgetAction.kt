// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.widget.GitToolbarWidgetActionBase

internal class GitBackendToolbarWidgetAction : GitToolbarWidgetActionBase() {
  override fun doUpdate(e: AnActionEvent, project: Project) {
    if (Registry.isRdBranchWidgetEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.doUpdate(e, project)
  }
}
