// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.newUiOnboarding

import com.intellij.ide.DataManager
import com.intellij.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.ui.ClientProperty
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.actions.GitInit
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.ui.branch.tree.GitBranchesTreeModel
import git4idea.ui.toolbar.GitToolbarWidgetAction
import git4idea.ui.toolbar.GitToolbarWidgetAction.GitWidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Point
import java.net.URL

class GitWidgetStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project): NewUiOnboardingStepData? {
    val widget = NewUiOnboardingUtil.findUiComponent(project) { widget: ToolbarComboWidget ->
      ClientProperty.get(widget, CustomComponentAction.ACTION_KEY) is GitToolbarWidgetAction
    } ?: return null

    widget.doExpand(e = null)

    val context = DataManager.getInstance().getDataContext(widget)
    val state = withContext(Dispatchers.Default) {
      readAction {
        val gitRepository = GitBranchUtil.guessWidgetRepository(project, context)
        GitToolbarWidgetAction.getWidgetState(project, gitRepository)
      }
    }

    val popupContent = if (state is GitWidgetState.Repo) {
      NewUiOnboardingUtil.findUiComponent(project) { tree: Tree ->
        tree.model is GitBranchesTreeModel
      }
    }
    else {
      NewUiOnboardingUtil.findUiComponent(project) { list: JBList<*> ->
        val model = list.model
        (0 until model.size).any {
          (model.getElementAt(it) as? AnActionHolder)?.action is GitInit
        }
      }
    }

    val popupRootPane = popupContent?.let { UIUtil.getRootPane(it) } ?: return null

    val text = if (state is GitWidgetState.Repo) {
      GitBundle.message("newUiOnboarding.git.widget.step.text.with.repo")
    }
    else GitBundle.message("newUiOnboarding.git.widget.step.text.no.repo")
    val ideHelpUrl = URL("https://www.jetbrains.com/help/idea/version-control-integration.html")

    val builder = GotItComponentBuilder(text)
      .withHeader(GitBundle.message("newUiOnboarding.git.widget.step.header"))
      .withBrowserLink(NewUiOnboardingBundle.message("gotIt.learn.more"), ideHelpUrl)

    val popupPoint = Point(popupRootPane.width + JBUI.scale(4), JBUI.scale(32))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, popupRootPane, popupPoint) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atRight)
  }
}