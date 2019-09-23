// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.ListModel

internal class GHRepositoryList(listModel: ListModel<GHRepositoryListItem>) : JBList<GHRepositoryListItem>(listModel) {
  private val authenticationManager = GithubAuthenticationManager.getInstance()

  init {
    ScrollingUtil.installActions(this)

    selectionModel = SingleSelectionModel()

    cellRenderer = object : ColoredListCellRenderer<GHRepositoryListItem>() {
      val nameRenderer = AccountNameRenderer()

      override fun getListCellRendererComponent(list: JList<out GHRepositoryListItem>,
                                                value: GHRepositoryListItem,
                                                index: Int,
                                                selected: Boolean,
                                                hasFocus: Boolean): Component {
        val component = super.getListCellRendererComponent(list, value, index, selected, hasFocus)
        if (showAccountNameAbove(list, index)) {
          val name = with(value) {
            if (account.server.isGithubDotCom) account.name else ("${account.server.host}/${account.name}")
          }
          return nameRenderer.withName(name, component, index != 0)
        }
        return component
      }

      private fun showAccountNameAbove(list: JList<out GHRepositoryListItem>, index: Int): Boolean {
        return authenticationManager.getAccounts().size > 1
               && (index == 0 || list.model.getElementAt(index).account != list.model.getElementAt(index - 1).account)
      }

      override fun customizeCellRenderer(list: JList<out GHRepositoryListItem>,
                                         value: GHRepositoryListItem,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) = value.customizeRenderer(this, list)
    }

    val mouseAdapter = createMouseAdapter()
    addMouseListener(mouseAdapter)
    addMouseMotionListener(mouseAdapter)
  }

  private fun createMouseAdapter(): MouseAdapter {
    return object : MouseAdapter() {
      fun getRunnableAt(e: MouseEvent): Runnable? {
        val point = e.point
        val renderer = ListUtil.getDeepestRendererChildComponentAt(this@GHRepositoryList, point)
        if (renderer !is SimpleColoredComponent) return null
        val tag = renderer.getFragmentTagAt(point.x)
        return if (tag is Runnable) tag else null
      }

      override fun mouseMoved(e: MouseEvent) {
        val runnable = getRunnableAt(e)
        if (runnable != null) {
          UIUtil.setCursor(this@GHRepositoryList, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        }
        else {
          UIUtil.setCursor(this@GHRepositoryList, Cursor.getDefaultCursor())
        }
      }

      override fun mouseClicked(e: MouseEvent) {
        getRunnableAt(e)?.run()
      }
    }
  }

  private class AccountNameRenderer : CellRendererPanel() {
    private val titleLabel = SimpleColoredComponent().apply {
      background = UIUtil.getListBackground()
    }
    private val topLine = JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.listSeparatorColor(), 1, 0, 0, 0)
    private val borderLayout = BorderLayout()

    init {
      layout = borderLayout
      add(titleLabel, BorderLayout.NORTH)
      background = UIUtil.getListBackground()
    }

    fun withName(title: String, itemContent: Component, withBorder: Boolean): AccountNameRenderer {
      titleLabel.border = null
      titleLabel.clear()
      titleLabel.append(title, SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
      if (withBorder) {
        titleLabel.border = topLine
      }
      val prevContent = borderLayout.getLayoutComponent(BorderLayout.CENTER)
      if (prevContent != null) {
        remove(prevContent)
      }
      add(itemContent, BorderLayout.CENTER)
      return this
    }
  }
}