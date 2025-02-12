// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.survey

import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JButton
import javax.swing.JComponent

internal enum class TrialSurveyOptions(@NlsContexts.RadioButton val text: String) {
  Unselected(""),
  Learn(IdeBundle.message("trial.survey.learn")),
  CheckForDaily(IdeBundle.message("trial.survey.check.for.daily")),
  CheckNewFeatures(IdeBundle.message("trial.survey.check.new.features")),
  WaitingLicense(IdeBundle.message("trial.survey.waiting.license")),
  NoGoal(IdeBundle.message("trial.survey.no.goal")),
  AnotherReason(IdeBundle.message("trial.survey.another.reason")),
}

@ApiStatus.Internal
fun trialSurveyPanel(createButtonsPanel: (JButton) -> JComponent, activateTrialButtonFn: (() -> Unit) -> JButton): DialogPanel {
  var chosenOption: Pair<TrialSurveyOptions, Int> = TrialSurveyOptions.Unselected to -1

  lateinit var panel: DialogPanel

  val activateTrialButton = activateTrialButtonFn {
    reportSurveyAnswer(chosenOption)
    panel.apply()
  }
  //LicenseDialogComponents.adjustStartTrialButtonText(model, activateTrialButton)
  activateTrialButton.isEnabled = false

  val options = (TrialSurveyOptions.entries - TrialSurveyOptions.Unselected - TrialSurveyOptions.AnotherReason).shuffled() +
                TrialSurveyOptions.AnotherReason
  panel = panel {
    buttonsGroup {
      for ((index, value) in options.withIndex()) {
        row {
          val option = value to index
          radioButton(value.text, option).onChanged { activateTrialButton.isEnabled = true }
        }
      }
    }.bind({ chosenOption }, { chosenOption = it })

    row {
      cell(createButtonsPanel(activateTrialButton))
    }.topGap(TopGap.MEDIUM)
  }

  panel.border = JBUI.Borders.empty(JBInsets(0, 36, 0, 36))
  return panel
}

private fun reportSurveyAnswer(chosenOption: Pair<TrialSurveyOptions, Int>) {
  val (value, order) = chosenOption
  IdeSurveyCollector.logTrialSurveyAnswered(value, order)
}
