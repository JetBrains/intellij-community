// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend.actions

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware

internal class FrontendAutoscrollToSource : FrontendProblemsViewToggleAction({ panel, event -> panel.getAutoscrollToSource(event) })
internal class FrontendOpenInPreviewTab : FrontendProblemsViewToggleAction({ panel, event -> panel.getOpenInPreviewTab(event) })
internal class FrontendShowPreview : FrontendProblemsViewToggleAction({ panel, event -> panel.getShowPreview(event) })
internal class FrontendGroupByToolId : FrontendProblemsViewToggleAction({ panel, _ -> panel.groupByToolId })
internal class FrontendSortFoldersFirst : FrontendProblemsViewToggleAction({ panel, _ -> panel.sortFoldersFirst })
internal class FrontendSortBySeverity : FrontendProblemsViewToggleAction({ panel, _ -> panel.sortBySeverity })
internal class FrontendSortByName : FrontendProblemsViewToggleAction({ panel, _ -> panel.sortByName })

internal abstract class FrontendProblemsViewToggleAction(optionSupplier: (ProblemsViewPanel, AnActionEvent) -> Option?)
  : DumbAware, ToggleOptionAction({ event ->
                                    event.getData(ProblemsViewPanel.DATA_KEY)?.let { panel ->
                                      optionSupplier(panel, event)
                                    }
                                  }) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
