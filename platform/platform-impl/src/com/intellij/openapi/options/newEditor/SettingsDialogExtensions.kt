// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.getActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext

private const val PANEL_MAX_WIDTH = 1000
private const val SEARCH_MAX_WIDTH = 400
private const val PANEL_NARROW_WIDTH = 850


internal fun SettingsDialog.createEditorToolbar(actions: List<Action>): DialogPanel? {
  val actionGroup = getActionGroup("Back", "Forward")
  val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SETTINGS_HISTORY, actionGroup!!, true)
  val settingsEditor = editor as? SettingsEditor ?: return null
  settingsEditor.search.preferredSize = JBUI.size(SEARCH_MAX_WIDTH, settingsEditor.search.preferredSize.height)
  settingsEditor.search.maximumSize = JBUI.size(SEARCH_MAX_WIDTH, settingsEditor.search.maximumSize.height)
  val showSidebar = AtomicProperty(ShowSidebar.DEFAULT)

  val editorToolbar = panel {
    row {
      val action = object : DumbAwareAction({ ActionsBundle.message("action.SettingsEditor.ToggleSidebar.text") },
                                            AllIcons.General.Menu) {
        override fun actionPerformed(e: AnActionEvent) {
          showSidebar.set(if (settingsEditor.isSidebarVisible) ShowSidebar.HIDE else ShowSidebar.SHOW)
          repaint()
        }
      }
      val sidebarActionButton: Cell<ActionButton> = actionButton(action).visible(true)
      sidebarActionButton.customize(UnscaledGaps(left = 8, right = 8))

      rootPane.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          if (e == null)
            return
          if (rootPane.width < PANEL_NARROW_WIDTH) {
            settingsEditor.isSidebarVisible = when (showSidebar.get()) {
              ShowSidebar.SHOW -> true
              ShowSidebar.HIDE -> false
              ShowSidebar.DEFAULT -> false
            }
          }
          else {
            settingsEditor.isSidebarVisible = when (showSidebar.get()) {
              ShowSidebar.SHOW -> true
              ShowSidebar.HIDE -> false
              ShowSidebar.DEFAULT -> true
            }
          }
        }
      })
      showSidebar.afterChange {
        when (showSidebar.get()) {
          ShowSidebar.SHOW -> {
            sidebarActionButton.component.background = null
            settingsEditor.isSidebarVisible = true
          }
          ShowSidebar.HIDE -> {
            sidebarActionButton.component.background = JBUI.CurrentTheme.ActionButton.pressedBackground()
            settingsEditor.isSidebarVisible = false
          }
          ShowSidebar.DEFAULT -> {}
        }
      }
      cell(toolbar.component)
      cell(settingsEditor.search).resizableColumn()
      for (i in 0..actions.size - 1) {
        val button = DialogWrapper.createJButtonForAction(actions[i], rootPane)
        val gaps = if (i == actions.size - 1) UnscaledGaps(right = 16) else UnscaledGaps(right = 8)
        cell(button).align(AlignX.RIGHT).customize(gaps).applyToComponent {
        }
      }
    }
  }
  editorToolbar.maximumSize = JBUI.size(PANEL_MAX_WIDTH, editorToolbar.maximumSize.height)
  editorToolbar.preferredSize = JBUI.size(PANEL_MAX_WIDTH, editorToolbar.preferredSize.height)
  editorToolbar.minimumSize = JBUI.size(10, editorToolbar.minimumSize.height)
  editorToolbar.border = JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBColor.border()),
                                               JBUI.Borders.customLineRight(JBColor.border()))
  toolbar.targetComponent = editorToolbar

  return panel {
    row {
      cell(editorToolbar).resizableColumn()
    }
  }

}

private enum class ShowSidebar {
  SHOW,
  HIDE,
  DEFAULT;
}

internal fun SettingsEditor.paneWithCorner(panel: JPanel, helpButton: JButton): JComponent {
  val layeredPane: JLayeredPane = object : JBLayeredPane() {
    override fun doLayout() {
      val r = bounds
      for (component in components) {
        if (component === panel) {
          component.setBounds(0, 0, r.width, r.height)
        }
        else if (component === helpButton) {
          val d = component.preferredSize
          component.setBounds(r.width - d.width-9, r.height - d.height-9, d.width, d.height)
        }
        else {
          error("can't layout unexpected component: $component")
        }
      }
    }

    override fun getPreferredSize(): Dimension {
      return panel.preferredSize
    }
  }
  layeredPane.setLayer(panel, JLayeredPane.DEFAULT_LAYER)
  layeredPane.add(panel)
  layeredPane.setLayer(helpButton, JLayeredPane.PALETTE_LAYER)
  layeredPane.add(helpButton)
  return layeredPane
}


internal fun SettingsEditor.createWrapperPanel(splitter: OnePixelSplitter) : DialogPanel {
  splitter.border = JBUI.Borders.customLineRight(JBColor.border())
  splitter.preferredSize = JBUI.size(PANEL_MAX_WIDTH, splitter.preferredSize.height)
  splitter.maximumSize = JBUI.size(PANEL_MAX_WIDTH, splitter.maximumSize.height)
  val panel = panel {
    row {
      cell(splitter).resizableColumn().align(AlignY.FILL)
    }.resizableRow()
  }
  return panel
}

internal class ResetConfigurableHandler(
  project: Project,
  private val context: OptionsEditorContext,
  coroutineScope: CoroutineScope,
  disposable: Disposable,
) {
  private val jobs = ConcurrentHashMap<String, Job>()
  private val properties = PropertiesComponent.getInstance(project)
  private val myCoroutineScope: CoroutineScope = coroutineScope.childScope("ResetConfigurableHandler", EmptyCoroutineContext, true)

  init {
    Disposer.register(disposable, Disposable {
      myCoroutineScope.cancel()
    })
  }

  fun scheduleConfigurableReset(configurable: Configurable) {
    val configurableId = ConfigurableVisitor.getId(configurable)
    if (configurable.isModified()) {
      myCoroutineScope.launch {
        val job = this.coroutineContext[Job] ?: return@launch
        jobs.put(configurableId, job)?.cancel()
        delay(System.getProperty("settings.editor.reset.delay.seconds", "60").toLong() * 1000)
        jobs.remove(configurableId, job)
        if (properties.getValue(SettingsEditor.SELECTED_CONFIGURABLE) != configurableId) {
          context.fireReset(configurable)
          configurable.reset()
        }
      }
    }
  }

}