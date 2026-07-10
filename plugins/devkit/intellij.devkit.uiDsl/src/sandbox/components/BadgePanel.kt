// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.actionsButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class BadgePanel : UISandboxPanel {

  override val title: String = "Badge"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.predefined.badges")) {
        row {
          for (badge in listOf(Badge.new, Badge.alpha, Badge.beta, Badge.trial, Badge.free)) {
            cell(JLabel(badge))
          }
        }
        row {
          for (badge in listOf(Badge.newDisabled, Badge.alphaDisabled, Badge.betaDisabled, Badge.trialDisabled, Badge.freeDisabled)) {
            cell(JLabel(badge))
          }
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.all.color.types")) {
        row {
          for (badge in allColorTypeBadges) {
            cell(JLabel(badge))
          }
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.badges.in.list")) {
        row {
          val renderer = listCellRenderer<Badge> {
            text(DevkitUiDslBundle.message("sandbox.item.0", index))
            icon(value)
          }

          cell(JBList(allColorTypeBadges)).applyToComponent {
            cellRenderer = renderer
          }
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.badges.in.actions")) {
        row {
          actionsButton(
            CustomAction("Simple Action"),
            CustomAction("Action with Icon", icon = AllIcons.General.Settings),
            CustomAction("Action with Badge", badge = Badge.beta),
            CustomAction("Action", comment = "Comment", badge = Badge.alpha),
            icon = AllIcons.General.Menu
          ).comment(DevkitUiDslBundle.message("sandbox.text.press.menu.icon.to.see.badge.usage.in.actions"))
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.tab.with.badges")) {
        row {
          val tabs = JBTabsImpl(project = null, disposable)

          tabs.addTab(createTabContent("Regular tab content"), "Settings")
          tabs.addTab(createTabContent("Beta feature content"), "Feature", Badge.beta)
          tabs.addTab(createTabContent("New feature content"), "New Feature", Badge.new)

          val wrapper = Wrapper(tabs)
          wrapper.border = JBUI.Borders.customLine(JBColor.border())
          wrapper.preferredSize = JBDimension(100, 100)
          cell(wrapper).align(AlignX.FILL)
        }
      }
    }
  }

  private fun JBTabsImpl.addTab(content: JComponent, text: @NlsSafe String, icon: Icon? = null) {
    val tabInfo = TabInfo(content)
      .setText(text)
      .setIcon(icon)
    addTab(tabInfo)

    if (icon != null) {
      val tabLabel = getTabLabel(tabInfo)?.labelComponent as? SimpleColoredComponent ?: return
      tabLabel.isIconOnTheRight = true
    }
  }

  private fun createTabContent(text: @NlsSafe String): JComponent {
    val content = JPanel(BorderLayout())
    content.add(JLabel(text), BorderLayout.CENTER)
    return content
  }
}

private val allColorTypeBadges = listOf(
  Badge(DevkitUiDslBundle.message("sandbox.label.blue"), Badge.ColorType.BLUE),
  Badge(DevkitUiDslBundle.message("sandbox.label.blue.secondary"), Badge.ColorType.BLUE_SECONDARY),
  Badge(DevkitUiDslBundle.message("sandbox.label.green"), Badge.ColorType.GREEN),
  Badge(DevkitUiDslBundle.message("sandbox.label.green.secondary"), Badge.ColorType.GREEN_SECONDARY),
  Badge(DevkitUiDslBundle.message("sandbox.label.purple.secondary"), Badge.ColorType.PURPLE_SECONDARY),
  Badge(DevkitUiDslBundle.message("sandbox.label.gray.secondary"), Badge.ColorType.GRAY_SECONDARY),
  Badge(DevkitUiDslBundle.message("sandbox.label.disabled")).apply { enabled = false },
)

private class CustomAction(
  private val text: String,
  private val icon: Icon? = null,
  private val comment: String? = null,
  private val badge: Icon? = null,
) :
  DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = getTextWithComment()
    e.presentation.icon = icon
    e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, badge)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun getTextWithComment(): @NlsSafe String {
    if (comment == null) {
      return text
    }

    val color = ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())
    return "<html>$text <span color=$color>$comment"
  }
}
