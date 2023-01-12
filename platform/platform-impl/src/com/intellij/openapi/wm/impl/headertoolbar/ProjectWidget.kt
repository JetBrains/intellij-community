// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.InputEvent
import java.util.function.Function
import javax.swing.*

private const val MAX_RECENT_COUNT = 100

internal val projectKey = Key.create<Project>("project-widget-project")

internal class ProjectWidget(private val presentation: Presentation) : ToolbarComboWidget() {

  private val project: Project?
    get() = presentation.getClientProperty(projectKey)

  init {
    presentation.addPropertyChangeListener { updateWidget() }
    rightIcons = listOf(AllIcons.General.ChevronDown)
  }

  override fun updateWidget() {
    text = presentation.text
    toolTipText = presentation.description
  }

  override fun doExpand(e: InputEvent?) {
    val dataContext = DataManager.getInstance().getDataContext(this)
    val anActionEvent = AnActionEvent.createFromInputEvent(e, ActionPlaces.PROJECT_WIDGET_POPUP, null, dataContext)
    val step = createStep(createActionGroup(anActionEvent))

    val widgetRenderer = ProjectWidgetRenderer()

    val renderer = Function<ListCellRenderer<Any>, ListCellRenderer<out Any>> { base ->
      ListCellRenderer<PopupFactoryImpl.ActionItem> { list, value, index, isSelected, cellHasFocus ->
        val action = (value as PopupFactoryImpl.ActionItem).action
        if (action is ReopenProjectAction) {
          widgetRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
        else {
          base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
      }
    }

    project?.let { createPopup(it, step, renderer) }?.showUnderneathOf(this)
  }

  private fun createPopup(it: Project, step: ListPopupStep<Any>, renderer: Function<ListCellRenderer<Any>, ListCellRenderer<out Any>>): ListPopup {
    val res = JBPopupFactory.getInstance().createListPopup(it, step, renderer)
    PopupImplUtil.setPopupToggleButton(res, this)
    res.setRequestFocus(false)
    return res
  }

  private fun createActionGroup(initEvent: AnActionEvent): ActionGroup {
    val res = DefaultActionGroup()

    val group = ActionManager.getInstance().getAction("ProjectWidget.Actions") as ActionGroup
    res.addAll(group.getChildren(initEvent).asList())
    val openProjects = ProjectUtilCore.getOpenProjects()
    val actionsMap: Map<Boolean, List<AnAction>> = RecentProjectListActionProvider.getInstance().getActions().take(MAX_RECENT_COUNT).groupBy(createSelector(openProjects))

    actionsMap[true]?.let {
      res.addSeparator(IdeBundle.message("project.widget.open.projects"))
      res.addAll(it)
    }

    actionsMap[false]?.let {
      res.addSeparator(IdeBundle.message("project.widget.recent.projects"))
      res.addAll(it)
    }

    return res
  }

  private fun createSelector(openProjects: Array<Project>): (AnAction) -> Boolean {
    val paths = openProjects.map { it.basePath }
    return { action -> (action as? ReopenProjectAction)?.projectPath in paths }
  }


  private fun createStep(actionGroup: ActionGroup): ListPopupStep<Any> {
    val context = DataManager.getInstance().getDataContext(this)
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, this, false, 0, false)
  }

  private class ProjectWidgetRenderer : ListCellRenderer<PopupFactoryImpl.ActionItem> {
    override fun getListCellRendererComponent(list: JList<out PopupFactoryImpl.ActionItem>?,
                                              value: PopupFactoryImpl.ActionItem?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      return createRecentProjectPane(value as PopupFactoryImpl.ActionItem, isSelected, getSeparator(list, value), index == 0)
    }

    private fun getSeparator(list: JList<out PopupFactoryImpl.ActionItem>?, value: PopupFactoryImpl.ActionItem?): ListSeparator? {
      val model = list?.model as? ListPopupModel<*> ?: return null
      val hasSeparator = model.isSeparatorAboveOf(value)
      if (!hasSeparator) return null
      return ListSeparator(model.getCaptionAboveOf(value))
    }

    private fun createRecentProjectPane(value: PopupFactoryImpl.ActionItem, isSelected: Boolean, separator: ListSeparator?, hideLine: Boolean): JComponent {
      val action = value.action as ReopenProjectAction
      val projectPath = action.projectPath
      lateinit var nameLbl: JLabel
      lateinit var pathLbl: JLabel

      val content = panel {
        customizeSpacingConfiguration(EmptySpacingConfiguration()) {
          row {
            icon(RecentProjectsManagerBase.getInstanceEx().getProjectIcon(projectPath, true))
              .align(AlignY.TOP)
              .customize(JBGaps(right = 8))

            panel {
              row {
                nameLbl = label(action.projectNameToDisplay ?: "")
                  .customize(JBGaps(bottom = 4))
                  .applyToComponent {
                    foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
                  }.component
              }
              row {
                pathLbl = label(FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(projectPath), false))
                  .applyToComponent {
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component
              }
            }
          }
        }
      }.apply {
        border = JBUI.Borders.empty(8, 0)
        isOpaque = false
      }

      val result = SelectablePanel.wrap(content, JBUI.CurrentTheme.Popup.BACKGROUND)
      PopupUtil.configListRendererFlexibleHeight(result)
      if (isSelected) {
        result.selectionColor = ListPluginComponent.SELECTION_COLOR
      }

      AccessibleContextUtil.setCombinedName(result, nameLbl, " - ", pathLbl)
      AccessibleContextUtil.setCombinedDescription(result, nameLbl, " - ", pathLbl)

      if (separator == null) {
        return result
      }

      val res = NonOpaquePanel(BorderLayout())
      res.border = JBUI.Borders.empty()
      res.add(createSeparator(separator, hideLine), BorderLayout.NORTH)
      res.add(result, BorderLayout.CENTER)
      return res
    }

    private fun createSeparator(separator: ListSeparator, hideLine: Boolean): JComponent {
      val res = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
      res.caption = separator.text
      res.setHideLine(hideLine)

      val panel = JPanel(BorderLayout())
      panel.border = JBUI.Borders.empty()
      panel.isOpaque = true
      panel.background = JBUI.CurrentTheme.Popup.BACKGROUND
      panel.add(res)

      return panel
    }
  }
}
