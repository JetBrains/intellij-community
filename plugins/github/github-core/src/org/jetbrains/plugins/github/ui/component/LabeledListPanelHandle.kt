// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.component

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.PopupConfig
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.collaboration.util.ComputedResult
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.FlowLayout
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.math.max

internal class LabeledListPanelHandle<T : Any>(cs: CoroutineScope,
                                               private val vm: LabeledListPanelViewModel<T>,
                                               private val emptyText: @NlsContexts.Label String,
                                               private val notEmptyText: @NlsContexts.Label String,
                                               private val getItemComponent: (T) -> JComponent,
                                               private val getItemPresentation: (T) -> PopupItemPresentation) {
  val label = JLabel().apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty(6, 0, 6, 5)
  }
  val panel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

  private val editButton = InlineIconButton(AllIcons.General.Inline_edit).apply {
    actionListener = ActionListener { vm.requestEdit() }
    withBackgroundHover = true
  }
  private val progressLabel = LoadingLabel().apply {
    border = JBUI.Borders.empty(6, 0)
  }
  private val errorIcon = JLabel(AllIcons.General.Error).apply {
    border = JBUI.Borders.empty(6, 0)
  }

  private val controlsPanel = HorizontalListPanel(4).apply {
    add(editButton)
    add(progressLabel)
    add(errorIcon)
  }

  val preferredLabelWidth = label.getFontMetrics(label.font)?.let {
    max(it.stringWidth(emptyText), it.stringWidth(notEmptyText))
  }

  init {
    cs.launchNow {
      vm.items.collect {
        showItems(it)
      }
    }
    cs.launchNow {
      vm.adjustmentProcessState.collect {
        updateControls(it)
      }
    }

    cs.launch {
      vm.editRequests.collect {
        val newList = showEditPopup(editButton)
        vm.adjustList(newList)
      }
    }
  }

  private fun showItems(newList: List<T>?) {
    label.text = newList?.let { if (it.isEmpty()) emptyText else notEmptyText }
    label.isVisible = newList != null

    panel.removeAll()
    panel.isVisible = newList != null
    if (newList != null) {
      if (newList.isEmpty()) {
        panel.add(controlsPanel)
      }
      else {
        for (item in newList.dropLast(1)) {
          panel.add(getListItemComponent(item))
        }
        panel.add(getListItemComponent(newList.last(), true))
      }
    }
    panel.revalidate()
    panel.repaint()
  }

  private fun updateControls(result: ComputedResult<Unit>?) {
    editButton.isVisible = vm.isEditingAllowed
    editButton.isEnabled = result == null
    progressLabel.isVisible = result != null
    val error = result?.result?.exceptionOrNull()
    errorIcon.isVisible = error != null
    val title = GithubBundle.message("pull.request.adjustment.failed")
    errorIcon.toolTipText = HtmlBuilder().append(title).br().append(error?.message.orEmpty())
      .wrapWithHtmlBody().toString()
  }

  private fun getListItemComponent(item: T, last: Boolean = false) =
    if (!last) getItemComponent(item)
    else simplePanel(getItemComponent(item)).addToRight(controlsPanel).apply {
      isOpaque = false
    }

  private suspend fun showEditPopup(parentComponent: JComponent): List<T> {
    val currentSet = vm.items.value.toSet()
    return ChooserPopupUtil.showAsyncMultipleChooserPopup(
      RelativePoint.getSouthOf(parentComponent),
      vm.getSelectableItemsBatchFlow(),
      getItemPresentation,
      currentSet::contains,
      PopupConfig(showDirection = ShowDirection.ABOVE)
    )
  }

  private fun <T> LabeledListPanelViewModel<T>.getSelectableItemsBatchFlow(): Flow<Result<List<T>>> {
    val current = items.value
    return flow {
      if (current.isNotEmpty()) {
        emit(Result.success(current))
      }
      selectableItems
        .mapNotNull { it.result?.map { items -> current + items.filter { item -> !current.contains(item) } } }
        .first().let { emit(it) }
    }
  }
}
