// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.changes

import com.intellij.icons.AllIcons
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.jetbrains.changeReminder.ChangeReminderBundle
import com.jetbrains.changeReminder.predict.PredictionData
import com.jetbrains.changeReminder.predict.PredictionService
import org.jetbrains.annotations.Nls

internal class ChangeReminderBrowserNode(
  private val predictionData: PredictionData,
  private val predictionService: PredictionService
) : ChangesBrowserNode<PredictionData>(predictionData) {
  private fun ChangesBrowserNodeRenderer.appendCustomState(state: @Nls String) {
    this.append("${if (countText.isEmpty()) spaceAndThinSpace() else ", "}$state", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append(ChangeReminderBundle.message("changes.browser.node.title"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    renderer.icon = AllIcons.Nodes.Related
    appendCount(renderer)
    val project = predictionService.project
    if (predictionService.isReadyToDisplay) {
      if (predictionService.inProgress) {
        renderer.appendCustomState(ChangeReminderBundle.message("changes.browser.node.attribute.prediction.is.calculating"))
      }
    }
    else {
      renderer.appendCustomState(ChangeReminderBundle.message("changes.browser.node.attribute.git.log.is.loading"))
    }
    val changeListManager = ChangeListManager.getInstance(project)
    val defaultChangeList = changeListManager.defaultChangeList
    val predictionSize = predictionData.predictionToDisplay.size
    renderer.toolTipText = ChangeReminderBundle.message(
      "changes.browser.node.prediction.tooltip.text",
      predictionSize,
      defaultChangeList.changes.size,
      defaultChangeList.name
    )
  }

  override fun getTextPresentation(): String = ChangeReminderBundle.message("changes.browser.node.title")
}