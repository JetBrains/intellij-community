// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.editor.GutterMarkPreprocessor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.ui.ClickListener
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

internal fun showClientIdGutterIconRenderer(project: Project): Boolean {
  return LineStatusClientIdRenderer.getInstance(project) != null
}

internal fun createClientIdGutterIconRenderer(project: Project, clientIds: List<ClientId>): GutterIconRenderer? {
  if (clientIds.isEmpty()) return null
  val renderer = LineStatusClientIdRenderer.getInstance(project) ?: return null
  return ClientIdGutterIconRenderer(clientIds, renderer)
}

internal fun createClientIdGutterPopupPanel(project: Project, clientIds: List<ClientId>): JComponent? {
  if (clientIds.isEmpty()) return null
  val renderer = LineStatusClientIdRenderer.getInstance(project) ?: return null

  val icon = renderer.getIcon(clientIds)
  val tooltipText = renderer.getTooltipText(clientIds)
  val action = renderer.getClickAction(clientIds)

  val label = JLabel(icon)
  label.toolTipText = tooltipText
  if (action != null) {
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        val dataContext = DataManager.getInstance().getDataContext(label)
        val actionEvent = AnActionEvent.createFromAnAction(action, event, ActionPlaces.EDITOR_GUTTER, dataContext)
        val result = ActionUtil.performAction(action, actionEvent)
        return !result.isIgnored
      }
    }.installOn(label)
  }
  return label
}

internal class ClientIdGutterIconRenderer(val clientIds: List<ClientId>,
                                          val renderer: LineStatusClientIdRenderer) : GutterIconRenderer() {
  override fun getIcon(): Icon {
    return renderer.getIcon(clientIds)
  }

  override fun getTooltipText(): String? {
    return renderer.getTooltipText(clientIds)
  }

  override fun getClickAction(): AnAction? {
    return renderer.getClickAction(clientIds)
  }

  override fun equals(other: Any?): Boolean {
    return other is ClientIdGutterIconRenderer && other.clientIds == clientIds
  }

  override fun hashCode(): Int {
    return Objects.hash(clientIds)
  }
}

internal class ClientIdGutterIconMerge : GutterMarkPreprocessor {
  override fun processMarkers(marks: List<GutterMark>): List<GutterMark> {
    val clientIcons = marks.filterIsInstance<ClientIdGutterIconRenderer>()
    if (clientIcons.size < 2) return marks

    var mergedClientIds = emptyList<ClientId>()
    for (icon in clientIcons) {
      mergedClientIds = mergeSortedClientIds(mergedClientIds, icon.clientIds)
    }

    val renderer = clientIcons.first().renderer

    val result = mutableListOf<GutterMark>()
    result += ClientIdGutterIconRenderer(mergedClientIds, renderer)
    result += marks.filter { it !is ClientIdGutterIconRenderer }
    return result
  }
}


internal abstract class ClientIdsDocumentTrackerHandler(val project: Project) : DocumentTracker.Handler {
  /**
   * Sorted by [ClientId.value]
   */
  protected abstract var DocumentTracker.Block.clientIds: List<ClientId>

  override fun onRangesChanged(before: List<DocumentTracker.Block>, after: DocumentTracker.Block) {
    var result = emptyList<ClientId>()
    for (block in before) {
      result = mergeSortedClientIds(result, block.clientIds)
    }

    val session = project.currentSession
    if (session.isGuest) {
      result = mergeSortedClientIds(result, listOf(session.clientId))
    }
    after.clientIds = result
  }

  override fun onRangeRefreshed(before: DocumentTracker.Block, after: List<DocumentTracker.Block>) {
    val clientIds = before.clientIds
    if (clientIds.isEmpty()) return

    for (block in after) {
      block.clientIds = clientIds
    }
  }

  override fun mergeRanges(block1: DocumentTracker.Block, block2: DocumentTracker.Block, merged: DocumentTracker.Block): Boolean {
    merged.clientIds = mergeSortedClientIds(block1.clientIds, block2.clientIds)
    return true
  }

  override fun onRangeShifted(before: DocumentTracker.Block, after: DocumentTracker.Block) {
    after.clientIds = before.clientIds
  }
}

/**
 * Merge two immutable sorted lists
 */
@VisibleForTesting
internal fun mergeSortedClientIds(clientIds1: List<ClientId>, clientIds2: List<ClientId>): List<ClientId> {
  if (clientIds1.isEmpty()) return clientIds2
  if (clientIds2.isEmpty()) return clientIds1

  val len1 = clientIds1.size
  val len2 = clientIds2.size
  if (len2 > len1) {
    return mergeSortedClientIds(clientIds2, clientIds1)
  }

  var index1 = 0
  var index2 = 0
  var newResult: MutableList<ClientId>? = null // != null if clientIds2 contains elements missing in clientIds1

  while (index1 < len1 || index2 < len2) {
    when {
      index1 == len1 -> {
        if (newResult == null) newResult = clientIds1.toMutableList()
        newResult.addAll(clientIds2.subList(index2, len2))
        break
      }
      index2 == len2 -> {
        if (newResult != null) {
          newResult.addAll(clientIds1.subList(index1, len1))
        }
        break
      }
      else -> {
        val value1 = clientIds1[index1]
        val value2 = clientIds2[index2]
        val delta = value1.value.compareTo(value2.value)
        if (delta == 0) {
          if (newResult != null) {
            newResult += value1
          }
          index1++
          index2++
        }
        else if (delta < 0) {
          if (newResult != null) {
            newResult += value1
          }
          index1++
        }
        else {
          if (newResult != null) {
            newResult += value2
          }
          else {
            newResult = clientIds1.subList(0, index1).toMutableList()
            newResult += value2
          }
          index2++
        }
      }
    }
  }

  return when {
    newResult == null -> clientIds1
    newResult.size == 1 -> listOf(newResult.single())
    else -> ArrayList(newResult) // trim array
  }
}
