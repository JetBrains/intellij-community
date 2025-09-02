// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.ui

import com.intellij.compilation.charts.CompilationChartsBundle
import com.intellij.compilation.charts.impl.CompilationChartsViewModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


class ActionPanel(private val project: Project, private val vm: CompilationChartsViewModel,
                  diagrams: CompilationChartsDiagramsComponent, private val component: JComponent) : BorderLayoutPanel() {
  private val searchField: JBTextField = object : ExtendableTextField() {
    val reset = Runnable {
      text = ""
      updateLabel(vm.filter(""))
    }

    init {
      setExtensions(object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean) = AllIcons.Actions.Search
        override fun isIconBeforeText() = true
        override fun getIconGap() = scale(6)
      }, object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean) = IconButton(
          CompilationChartsBundle.message("charts.reset"),
          AllIcons.Actions.Close,
          AllIcons.Actions.CloseHovered
        )

        override fun isIconBeforeText() = false
        override fun getIconGap() = scale(6)
        override fun getActionOnClick() = reset
      })

      document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = updateFilter()
        override fun removeUpdate(e: DocumentEvent) = updateFilter()
        override fun changedUpdate(e: DocumentEvent) = updateFilter()

        private fun updateFilter() {
          updateLabel(vm.filter(text))
        }
      })

      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ESCAPE) reset.run()
        }
      })
      border = JBUI.Borders.empty(1)
      BoxLayout(this, BoxLayout.LINE_AXIS)
      background = Colors.Background.DEFAULT
    }
  }

  private val countLabel = JBLabel("").apply {
    border = JBUI.Borders.emptyLeft(5)
    fontColor = UIUtil.FontColor.BRIGHTER
  }

  init {
    border = JBUI.Borders.customLine(Colors.LINE)
    layout = BorderLayout()

    // module name
    addToLeft(JPanel().apply {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      border = JBUI.Borders.empty(2, 10, 2, 2)
      add(JBLabel(CompilationChartsBundle.message("charts.module")))
      add(searchField)
      add(countLabel)
    })

    // legend
    val actionManager = ActionManager.getInstance()

    val actionGroup = DefaultActionGroup(
      CompilationChartsStatsActionHolder(diagrams, vm),
      Separator(),
      actionManager.getAction("CompilationChartsZoomResetAction"),
      actionManager.getAction("CompilationChartsZoomOutAction"),
      actionManager.getAction("CompilationChartsZoomInAction"),
      actionManager.getAction("CompilationChartsScrollToEndAction"),
    )

    val toolbar = actionManager.createActionToolbar(Settings.Toolbar.ID, actionGroup, true).apply {
      targetComponent = this@ActionPanel
      component.border = JBUI.Borders.empty()
    }
    addToRight(toolbar.component)

    DumbAwareAction.create {
      val focusManager = IdeFocusManager.getInstance(project)
      if (focusManager.getFocusedDescendantFor(this@ActionPanel.component) != null) {
        focusManager.requestFocus(searchField, true)
      }
    }.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_FIND).shortcutSet, this@ActionPanel.component)
  }

  fun updateLabel(count: Int) {
    if (count == -1) {
      countLabel.text = ""
    }
    else {
      countLabel.text = CompilationChartsBundle.message("charts.search.results", count)
    }
  }

  internal class ScrollToEndAction : CompilationChartsActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
      e.getData(COMPILATION_CHARTS_VIEW_KEY)?.scrollToEnd()
    }
  }

  internal abstract class CompilationChartsActionBase : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.getData(COMPILATION_CHARTS_VIEW_KEY) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  internal abstract class ZoomActionBase(private val zoomEvent: ZoomEvent) : CompilationChartsActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
      e.getData(COMPILATION_CHARTS_VIEW_KEY)?.zoom(zoomEvent)
    }
  }

  internal class ZoomInAction : ZoomActionBase(ZoomEvent.IN)

  internal class ZoomOutAction : ZoomActionBase(ZoomEvent.OUT)

  internal class ZoomResetAction : ZoomActionBase(ZoomEvent.RESET)

  private class CompilationChartsStatsActionHolder(private val diagrams: CompilationChartsDiagramsComponent,
                                                   private val vm: CompilationChartsViewModel) : DumbAwareAction(), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      border = JBUI.Borders.empty(2)
      add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
        val block = JBLabel().apply {
          preferredSize = Dimension(10, 10)
          isOpaque = true
          background = Colors.Production.ENABLED
          border = BorderFactory.createLineBorder(Colors.Production.BORDER, 1)
        }
        add(block)
        add(JBLabel(CompilationChartsBundle.message("charts.production.type")))

        addMouseListener(object : MouseAdapter() {
          override fun mouseEntered(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(Colors.Production.SELECTED, 1)
          }

          override fun mouseExited(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(Colors.Production.BORDER, 1)
          }

          override fun mouseClicked(e: MouseEvent) {
            if (vm.changeProduction())
              block.background = Colors.Production.ENABLED
            else
              block.background = Colors.Production.DISABLED
          }
        })

      })

      add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
        val block = JBLabel().apply {
          preferredSize = Dimension(10, 10)
          isOpaque = true
          background = Colors.Test.ENABLED
          border = BorderFactory.createLineBorder(Colors.Test.BORDER)
        }
        add(block)
        add(JBLabel(CompilationChartsBundle.message("charts.test.type")))
        addMouseListener(object : MouseAdapter() {
          override fun mouseEntered(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(Colors.Test.SELECTED, 1)
          }

          override fun mouseExited(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(Colors.Test.BORDER, 1)
          }

          override fun mouseClicked(e: MouseEvent) {
            if (vm.changeTest())
              block.background = Colors.Test.ENABLED
            else
              block.background = Colors.Test.DISABLED

          }
        })
      })

      add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
        val declaration = vm.changeStatistic()
        val block = JBLabel().apply {
          preferredSize = Dimension(10, 2)
          isOpaque = true
          background = declaration.color().background()
        }
        val label = JBLabel(declaration.title())
        add(block)
        add(label)

        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            val declaration = vm.changeStatistic()
            label.text = declaration.title()
            block.background = declaration.color().background()
            diagrams.smartDraw(true, true)
          }
        })
      })
    }

    override fun actionPerformed(e: AnActionEvent) = Unit
  }
}