// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.util.maximumWidth
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Component
import java.util.function.Function
import java.util.function.Predicate
import javax.swing.*

private const val MAX_RECENT_COUNT = 100
private val projectKey = Key.create<Project>("project-widget-project")

internal class DefaultOpenProjectSelectionPredicateSupplier : OpenProjectSelectionPredicateSupplier {
  override fun getPredicate(): Predicate<AnAction> {
    val openProjects = ProjectUtilCore.getOpenProjects()
    val paths = openProjects.map { it.basePath }
    return Predicate { action -> (action as? ReopenProjectAction)?.projectPath in paths }
  }
}

class ProjectToolbarWidgetAction : ExpandableComboAction(), DumbAware {
  override fun createPopup(event: AnActionEvent): JBPopup? {
    val widget = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? ToolbarComboButton?
    val step = createStep(createActionGroup(event), event.dataContext, widget)
    return event.project?.let { createPopup(it = it, step = step) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply { maximumWidth = 500 }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)

    val widget = component as? ToolbarComboButton ?: return

    widget.text = presentation.text
    widget.toolTipText = presentation.description
    widget.leftIcons = emptyList()
    widget.isOpaque = false

    val customizer = ProjectWindowCustomizerService.getInstance()
    val project = presentation.getClientProperty(projectKey)

    if (project != null && customizer.isAvailable()) {
      widget.leftIcons = listOf(customizer.getProjectIcon(project))
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val projectName = project?.name ?: ""
    e.presentation.setText(projectName, false)
    e.presentation.description = FileUtil.getLocationRelativeToUserHome(project?.guessProjectDir()?.path) ?: projectName
    e.presentation.putClientProperty(projectKey, project)
  }

  private fun createPopup(it: Project, step: ListPopupStep<Any>): ListPopup {
    val widgetRenderer = ProjectWidgetRenderer()
    val renderer = Function<ListCellRenderer<Any>, ListCellRenderer<out Any>> { base ->
      ListCellRenderer<PopupFactoryImpl.ActionItem> { list, value, index, isSelected, cellHasFocus ->
        val action = (value as PopupFactoryImpl.ActionItem).action
        if (action is ReopenProjectAction) {
          widgetRenderer.getListCellRendererComponent(list = list,
                                                      value = value,
                                                      index = index,
                                                      isSelected = isSelected,
                                                      cellHasFocus = cellHasFocus)
        }
        else {
          base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
      }
    }

    val result = JBPopupFactory.getInstance().createListPopup(it, step, renderer)
    result.setRequestFocus(false)
    return result
  }

  private fun createActionGroup(initEvent: AnActionEvent): ActionGroup {
    val result = DefaultActionGroup()

    val group = ActionManager.getInstance().getAction("ProjectWidget.Actions") as ActionGroup
    result.addAll(group.getChildren(initEvent).asList())
    val openProjectsPredicate = OpenProjectSelectionPredicateSupplier.getInstance().getPredicate()
    val actionsMap = RecentProjectListActionProvider.getInstance().getActions()
      .asSequence()
      .take(MAX_RECENT_COUNT)
      .groupBy { openProjectsPredicate.test(it) }

    actionsMap[true]?.let {
      result.addSeparator(IdeUICustomization.getInstance().projectMessage("project.widget.open.projects"))
      result.addAll(it)
    }

    actionsMap[false]?.let {
      result.addSeparator(IdeUICustomization.getInstance().projectMessage("project.widget.recent.projects"))
      result.addAll(it)
    }

    return result
  }

  private fun createStep(actionGroup: ActionGroup, context: DataContext, widget: JComponent?): ListPopupStep<Any> {
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, widget, false, 0, false)
  }
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
    if (!hasSeparator) {
      return null
    }
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
          icon(RecentProjectsManagerBase.getInstanceEx().getProjectIcon(projectPath, true, 20))
            .align(AlignY.TOP)
            .customize(UnscaledGaps(right = 8))

          panel {
            row {
              nameLbl = label(action.projectNameToDisplay ?: "")
                .customize(UnscaledGaps(bottom = 4))
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
