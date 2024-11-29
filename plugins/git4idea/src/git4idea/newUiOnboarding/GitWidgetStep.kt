// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.newUiOnboarding

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.GotItComponentBuilder
import com.intellij.util.ui.JBUI
import git4idea.i18n.GitBundle
import git4idea.ui.toolbar.GitToolbarWidgetAction
import git4idea.ui.toolbar.GitToolbarWidgetAction.GitWidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Point
import java.net.URL

open class GitWidgetStep : NewUiOnboardingStep {
  protected open val generalVcsHelpTopic: String? = "version-control-integration.html"
  protected open val enableVcsHelpTopic: String? = "enabling-version-control.html"

  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val button = UiComponentsUtil.findUiComponent(project) { button: ToolbarComboButton ->
      ClientProperty.get(button, CustomComponentAction.ACTION_KEY) is GitToolbarWidgetAction
    } ?: return null

    val action = ClientProperty.get(button, CustomComponentAction.ACTION_KEY) as GitToolbarWidgetAction
    val popup = NewUiOnboardingUtil.showToolbarComboButtonPopup(button, action, disposable) ?: return null

    val dataContext = DataManager.getInstance().getDataContext(button)
    val state = withContext(Dispatchers.Default) {
      readAction {
        GitToolbarWidgetAction.getWidgetState(project, dataContext)
      }
    }

    val text = if (state is GitWidgetState.Repo) {
      GitBundle.message("newUiOnboarding.git.widget.step.text.with.repo")
    }
    else GitBundle.message("newUiOnboarding.git.widget.step.text.no.repo")

    val builder = GotItComponentBuilder(text)
      .withHeader(GitBundle.message("newUiOnboarding.git.widget.step.header"))

    val helpTopic = if (state is GitWidgetState.Repo) generalVcsHelpTopic else enableVcsHelpTopic
    if (helpTopic != null) {
      val ideHelpLink = NewUiOnboardingUtil.getHelpLink(helpTopic)
      builder.withBrowserLink(NewUiOnboardingBundle.message("gotIt.learn.more"), URL(ideHelpLink))
    }

    val popupPoint = Point(popup.content.width + JBUI.scale(4), JBUI.scale(32))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, popup.content, popupPoint) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atRight)
  }
}