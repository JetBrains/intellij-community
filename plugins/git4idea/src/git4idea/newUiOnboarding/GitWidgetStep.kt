// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.newUiOnboarding

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.GotItComponentBuilder
import com.intellij.util.ui.JBUI
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.ui.toolbar.GitToolbarWidgetAction
import git4idea.ui.toolbar.GitToolbarWidgetAction.GitWidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Point
import java.net.URL

class GitWidgetStep : NewUiOnboardingStep {
  private val ideHelpTopic = "version-control-integration.html"

  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val widget = NewUiOnboardingUtil.findUiComponent(project) { widget: ToolbarComboWidget ->
      ClientProperty.get(widget, CustomComponentAction.ACTION_KEY) is GitToolbarWidgetAction
    } ?: return null

    val popup = NewUiOnboardingUtil.showToolbarWidgetPopup(widget, disposable) ?: return null

    val context = DataManager.getInstance().getDataContext(widget)
    val state = withContext(Dispatchers.Default) {
      readAction {
        val gitRepository = GitBranchUtil.guessWidgetRepository(project, context)
        GitToolbarWidgetAction.getWidgetState(project, gitRepository)
      }
    }

    val text = if (state is GitWidgetState.Repo) {
      GitBundle.message("newUiOnboarding.git.widget.step.text.with.repo")
    }
    else GitBundle.message("newUiOnboarding.git.widget.step.text.no.repo")

    val ideHelpLink = NewUiOnboardingUtil.getHelpLink(ideHelpTopic)
    val builder = GotItComponentBuilder(text)
      .withHeader(GitBundle.message("newUiOnboarding.git.widget.step.header"))
      .withBrowserLink(NewUiOnboardingBundle.message("gotIt.learn.more"), URL(ideHelpLink))

    val popupPoint = Point(popup.content.width + JBUI.scale(4), JBUI.scale(32))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, popup.content, popupPoint) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atRight)
  }
}