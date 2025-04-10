// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.util.function.Supplier

class GHPRReloadListAction
  : RefreshAction(GithubBundle.messagePointer("pull.request.refresh.list.action"),
                  Supplier { null },
                  AllIcons.Actions.Refresh) {

  override fun update(e: AnActionEvent) {
    val controller = e.getData(GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER)
    e.presentation.isEnabled = controller != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val controller = e.getData(GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER) ?: return
    controller.reloadList()
  }
}