// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.ui.WrapLayout
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

internal abstract class LabeledListPanelHandle<T>(private val model: SingleValueModel<GHPullRequest?>,
                                                  private val securityService: GithubPullRequestsSecurityService,
                                                  private val busyStateTracker: GithubPullRequestsBusyStateTracker,
                                                  emptyText: String, notEmptyText: String)
  : Disposable {

  val label = JLabel().apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP + 2, 0, UIUtil.DEFAULT_VGAP + 2, UIUtil.DEFAULT_HGAP / 2)
  }
  val panel = NonOpaquePanel(WrapLayout(FlowLayout.LEADING, 0, 0))

  protected val editButton = InplaceButton(IconButton(null,
                                                      resizeSquareIcon(AllIcons.General.Inline_edit),
                                                      resizeSquareIcon(AllIcons.General.Inline_edit_hovered)),
                                           ActionListener { (::editList)() }).apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    isVisible = securityService.currentUserCanEditPullRequestsMetadata()
    isFocusable = true
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
          doClick()
          e.consume()
        }
      }
    })
  }

  private fun resizeSquareIcon(icon: Icon): Icon {
    val scale = 20f / icon.iconHeight
    return IconUtil.scale(icon, editButton, scale)
  }

  private var list: List<T>? by equalVetoingObservable<List<T>?>(null) { newList ->
    label.text = newList?.let { if (it.isEmpty()) emptyText else notEmptyText }
    label.isVisible = newList != null

    panel.removeAll()
    panel.isVisible = newList != null
    if (newList != null) {
      if (newList.isEmpty()) {
        panel.add(editButton)
      }
      else {
        for (item in newList.dropLast(1)) {
          panel.add(getListItemComponent(item))
        }
        panel.add(getListItemComponent(newList.last(), true))
      }
    }
  }

  init {
    fun update() {
      list = model.value?.let(::extractItems)
      updateButton()
    }

    model.addValueChangedListener(this) {
      update()
    }
    busyStateTracker.addPullRequestBusyStateListener(this) {
      updateButton()
    }
    update()
  }

  private fun updateButton() {
    editButton.isEnabled = !(model.value?.number?.let(busyStateTracker::isBusy) ?: true)
  }

  private fun getListItemComponent(item: T, last: Boolean = false) =
    if (!last) getItemComponent(item)
    else simplePanel(getItemComponent(item)).addToRight(editButton).apply {
      isOpaque = false
    }

  abstract fun extractItems(details: GHPullRequest): List<T>?

  abstract fun getItemComponent(item: T): JComponent

  abstract fun editList()

  override fun dispose() {}
}