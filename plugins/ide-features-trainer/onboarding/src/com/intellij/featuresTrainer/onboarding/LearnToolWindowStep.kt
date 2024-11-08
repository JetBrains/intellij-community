package com.intellij.featuresTrainer.onboarding

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.yield
import training.learn.LearnBundle
import training.ui.LearningUiHighlightingManager
import training.util.learningToolWindow
import java.awt.Point
import kotlin.math.min

internal class LearnToolWindowStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val toolWindow = learningToolWindow(project) ?: return null
    toolWindow.show(null)

    val learnStripeButton = UiComponentsUtil.findUiComponent(project) { button: ActionButton ->
      button.action.templateText == LearnBundle.message("toolwindow.stripe.Learn")
    }
    if (learnStripeButton != null) {
      val options = LearningUiHighlightingManager.HighlightingOptions(
        highlightBorder = true,
        highlightInside = false,
        usePulsation = false,
        isRoundedCorners = true,
        thickness = 2,
      )
      LearningUiHighlightingManager.highlightComponent(learnStripeButton, options)
      Disposer.register(disposable) {
        LearningUiHighlightingManager.clearHighlights()
      }
    }

    yield() // wait for the tool window to appear

    // Hide the tool window back when the step is finished
    Disposer.register(disposable) {
      toolWindow.hide()
    }

    val builder = GotItComponentBuilder(LearnBundle.message("newUsersOnboarding.learn.step.text"))
    builder.withHeader(LearnBundle.message("newUsersOnboarding.learn.step.header"))

    val component = toolWindow.component
    val point = Point(component.width + JBUI.scale(3), min(JBUI.scale(150), component.height / 2))
    return NewUiOnboardingStepData(builder, RelativePoint(component, point), Balloon.Position.atRight)
  }
}