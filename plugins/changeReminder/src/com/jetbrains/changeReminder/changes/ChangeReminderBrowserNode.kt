// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.changes

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.jetbrains.changeReminder.predict.PredictionService

class ChangeReminderBrowserNode(private val files: Collection<VirtualFile>,
                                private val predictionService: PredictionService
) : ChangesBrowserNode<Collection<VirtualFile>>(files) {
  companion object {
    private const val NODE_TITLE = "Related Files"
    private const val LOADING_ATTRIBUTE = "Git Log is loading..."
    private const val CALCULATING_ATTRIBUTE = "Calculating..."
  }

  private fun ChangesBrowserNodeRenderer.appendCustomState(state: String) {
    this.append("${if (countText.isEmpty()) spaceAndThinSpace() else ", "}$state", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  private fun getPredictionToolTipText(predictionSize: Int, changeList: LocalChangeList): String =
    "${pluralize("This", predictionSize)} $predictionSize ${pluralize("file", predictionSize)} " +
    "${if (predictionSize > 1) "are" else "is"} usually committed together with " +
    "the ${pluralize("file", changeList.changes.size)} from the ${changeList.name}"

  private fun getHelpToolTipText(): String =
    "${ApplicationNamesInfo.getInstance().fullProductName} " +
    "predicts files that are usually committed together, so that they are not forgotten by mistake."

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append(NODE_TITLE, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    appendCount(renderer)
    renderer.icon = AllIcons.General.Information
    val project = predictionService.project
    if (predictionService.isReady) {
      if (predictionService.inProgress) {
        renderer.appendCustomState(CALCULATING_ATTRIBUTE)
      }
    }
    else {
      renderer.appendCustomState(LOADING_ATTRIBUTE)
    }
    val changeListManager = ChangeListManager.getInstance(project)
    val defaultChangeList = changeListManager.defaultChangeList
    if (files.isNotEmpty()) {
      renderer.toolTipText = getPredictionToolTipText(files.size, defaultChangeList)
    }
    else {
      renderer.toolTipText = getHelpToolTipText()
    }
  }
}