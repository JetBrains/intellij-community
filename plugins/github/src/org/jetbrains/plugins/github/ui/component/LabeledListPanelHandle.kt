// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.component

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRMetadataModel
import java.awt.FlowLayout
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.math.max
import kotlin.properties.Delegates

internal abstract class LabeledListPanelHandle<T>(protected val model: GHPRMetadataModel,
                                                  @NlsContexts.Label emptyText: String, @NlsContexts.Label notEmptyText: String) {

  private var isBusy by Delegates.observable(false) { _, _, _ ->
    updateControls()
  }
  private var adjustmentError by Delegates.observable<Throwable?>(null) { _, _, _ ->
    updateControls()
  }

  val label = JLabel().apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty(6, 0, 6, 5)
  }
  val panel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

  private val editButton = InlineIconButton(AllIcons.General.Inline_edit,
                                            AllIcons.General.Inline_edit_hovered).apply {
    border = JBUI.Borders.empty(6, 0)
    actionListener = ActionListener { editList() }
  }
  private val progressLabel = JLabel(AnimatedIcon.Default()).apply {
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

  private var list: List<T>? by equalVetoingObservable<List<T>?>(null) { newList ->
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
    panel.validate()
    panel.repaint()
  }

  val preferredLabelWidth = label.getFontMetrics(label.font)?.let {
    max(it.stringWidth(emptyText), it.stringWidth(notEmptyText))
  }

  init {
    model.addAndInvokeChangesListener(::updateList)
    updateControls()
  }

  private fun updateList() {
    list = getItems()
  }

  private fun updateControls() {
    editButton.isVisible = !isBusy && model.isEditingAllowed
    progressLabel.isVisible = isBusy
    errorIcon.isVisible = adjustmentError != null
    val title = GithubBundle.message("pull.request.adjustment.failed")
    val errorMessage = adjustmentError?.message.orEmpty()
    //language=html
    errorIcon.toolTipText = "<html><body>$title<br/>$errorMessage</body></html>"
  }

  private fun getListItemComponent(item: T, last: Boolean = false) =
    if (!last) getItemComponent(item)
    else simplePanel(getItemComponent(item)).addToRight(controlsPanel).apply {
      isOpaque = false
    }

  abstract fun getItems(): List<T>?

  abstract fun getItemComponent(item: T): JComponent

  private fun editList() {
    showEditPopup(editButton)
      ?.thenComposeAsync(Function<CollectionDelta<T>, CompletableFuture<Unit>> { delta ->
        if (delta == null || delta.isEmpty) {
          CompletableFuture.completedFuture(Unit)
        }
        else {
          adjustmentError = null
          isBusy = true
          adjust(EmptyProgressIndicator(), delta)
        }
      }, CompletableFutureUtil.getEDTExecutor())
      ?.handleOnEdt { _, error ->
        adjustmentError = error
        isBusy = false
      }
  }

  @RequiresEdt
  abstract fun showEditPopup(parentComponent: JComponent): CompletableFuture<CollectionDelta<T>>?

  @RequiresEdt
  abstract fun adjust(indicator: ProgressIndicator, delta: CollectionDelta<T>): CompletableFuture<Unit>
}
