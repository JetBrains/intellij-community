// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.shouldUseFallbackSwitcher
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.ui.components.JBList
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import java.awt.event.*
import java.util.function.Consumer
import javax.swing.AbstractAction
import javax.swing.JList

private fun forward(event: AnActionEvent) = true != event.inputEvent?.isShiftDown


internal class ShowSwitcherForwardAction : BaseSwitcherAction(true)
internal class ShowSwitcherBackwardAction : BaseSwitcherAction(false)

@ApiStatus.Internal
abstract class BaseSwitcherAction(val forward: Boolean?) : DumbAwareAction() {
  private fun isControlTab(event: KeyEvent?) = event?.run { isControlDown && keyCode == KeyEvent.VK_TAB } ?: false
  private fun isControlTabDisabled(event: AnActionEvent) = ScreenReader.isActive() && isControlTab(event.inputEvent as? KeyEvent)

  override fun update(event: AnActionEvent) {
    if (shouldUseFallbackSwitcher()) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    event.presentation.isEnabled = event.project != null && !isControlTabDisabled(event)
    event.presentation.isVisible = forward == null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val switcher = Switcher.SWITCHER_KEY.get(project)
    if (switcher != null && (!switcher.recent || forward != null)) {
      switcher.go(forward ?: forward(event))
    }
    else {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("switcher")
      createAndShowNewSwitcher(null, event, message("window.title.switcher"), project)
    }
  }
}


internal class ShowRecentFilesAction : LightEditCompatible, BaseRecentFilesAction(false)
internal class ShowRecentlyEditedFilesAction : BaseRecentFilesAction(true)
internal abstract class BaseRecentFilesAction(private val onlyEditedFiles: Boolean) : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    if (shouldUseFallbackSwitcher()) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    event.presentation.isEnabledAndVisible = event.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val existingPanel = Switcher.SWITCHER_KEY.get(project)
    if (existingPanel != null) {
      existingPanel.cbShowOnlyEditedFiles?.apply { isSelected = !isSelected }
    }
    else {
      createAndShowNewSwitcher(onlyEditedFiles, null, message("title.popup.recent.files"), project)
    }
  }
}


internal class SwitcherIterateThroughItemsAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    if (shouldUseFallbackSwitcher()) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    event.presentation.isEnabledAndVisible = Switcher.SWITCHER_KEY.get(event.project) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    Switcher.SWITCHER_KEY.get(event.project)?.go(forward(event))
  }
}


internal class SwitcherToggleOnlyEditedFilesAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  private fun getCheckBox(event: AnActionEvent) =
    Switcher.SWITCHER_KEY.get(event.project)?.cbShowOnlyEditedFiles

  override fun update(event: AnActionEvent) {
    if (shouldUseFallbackSwitcher()) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    event.presentation.isEnabledAndVisible = getCheckBox(event) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(event: AnActionEvent): Boolean = getCheckBox(event)?.isSelected ?: false
  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    getCheckBox(event)?.isSelected = selected
  }
}


internal class SwitcherNextProblemAction : SwitcherProblemAction(true)
internal class SwitcherPreviousProblemAction : SwitcherProblemAction(false)
internal abstract class SwitcherProblemAction(val forward: Boolean) : DumbAwareAction() {
  private fun getFileList(event: AnActionEvent): JBList<SwitcherVirtualFile>? {
    return Switcher.SWITCHER_KEY.get(event.project)?.let { if (it.pinned) it.files else null }
  }

  private fun getErrorIndex(list: JList<SwitcherVirtualFile>): Int? {
    val model = list.model ?: return null
    val size = model.size
    if (size <= 0) return null
    val range = 0 until size
    val start = when (forward) {
      true -> list.leadSelectionIndex.let { if (range.first <= it && it < range.last) it + 1 else range.first }
      else -> list.leadSelectionIndex.let { if (range.first < it && it <= range.last) it - 1 else range.last }
    }
    for (i in range) {
      val index = when (forward) {
        true -> (start + i).let { if (it > range.last) it - size else it }
        else -> (start - i).let { if (it < range.first) it + size else it }
      }
      if (model.getElementAt(index)?.hasProblems == true) return index
    }
    return null
  }

  override fun update(event: AnActionEvent) {
    if (shouldUseFallbackSwitcher()) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    event.presentation.isEnabledAndVisible = getFileList(event) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val list = getFileList(event) ?: return
    val index = getErrorIndex(list) ?: return
    list.selectedIndex = index
    list.ensureIndexIsVisible(index)
  }
}


internal class SwitcherListFocusAction(val fromList: JList<*>, val toList: JList<*>, vararg listActionIds: String)
  : FocusListener, AbstractAction() {

  override fun actionPerformed(event: ActionEvent) {
    if (toList.isShowing) toList.requestFocusInWindow()
  }

  override fun focusLost(event: FocusEvent): Unit = Unit
  override fun focusGained(event: FocusEvent) {
    val size = toList.model.size
    if (size > 0) {
      val fromIndex = fromList.selectedIndex
      when {
        fromIndex >= 0 -> toIndex = fromIndex.coerceAtMost(size - 1)
        toIndex < 0 -> toIndex = 0
      }
    }
  }

  private var toIndex: Int
    get() = toList.selectedIndex
    set(index) {
      fromList.clearSelection()
      toList.selectedIndex = index
      toList.ensureIndexIsVisible(index)
    }

  init {
    listActionIds.forEach { fromList.actionMap.put(it, this) }
    toList.addFocusListener(this)
    toList.addListSelectionListener {
      if (!fromList.isSelectionEmpty && !toList.isSelectionEmpty) {
        fromList.selectionModel.clearSelection()
      }
    }
  }
}


internal class SwitcherKeyReleaseListener(private val launchParameters: SwitcherLaunchEventParameters?, private val consumer: Consumer<InputEvent>) : KeyAdapter() {
  private val initialModifiers =
    if (launchParameters?.isEnabled != true) {
      null
    }
    else {
      StringBuilder().apply {
        if (launchParameters.wasAltDown) append("alt ")
        if (launchParameters.wasAltGraphDown) append("altGraph ")
        if (launchParameters.wasControlDown) append("control ")
        if (launchParameters.wasMetaDown) append("meta ")
      }.toString()
    }

  fun getShortcuts(vararg keys: String): CustomShortcutSet {
    val modifiers = initialModifiers ?: return CustomShortcutSet.fromString(*keys)
    val list = mutableListOf<String>()
    keys.mapTo(list) { modifiers + it }
    keys.mapTo(list) { modifiers + "shift " + it }
    return CustomShortcutSet.fromStrings(list)
  }

  override fun keyReleased(keyEvent: KeyEvent) {
    when (keyEvent.keyCode) {
      KeyEvent.VK_ALT -> if (launchParameters?.wasAltDown == true) consumer.accept(keyEvent)
      KeyEvent.VK_ALT_GRAPH -> if (launchParameters?.wasAltGraphDown == true) consumer.accept(keyEvent)
      KeyEvent.VK_CONTROL -> if (launchParameters?.wasControlDown == true) consumer.accept(keyEvent)
      KeyEvent.VK_META -> if (launchParameters?.wasMetaDown == true) consumer.accept(keyEvent)
    }
  }
}
