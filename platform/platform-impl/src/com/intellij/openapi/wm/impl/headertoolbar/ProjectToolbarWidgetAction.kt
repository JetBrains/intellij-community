// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.ClientProperty
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
import com.intellij.util.IconUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import kotlinx.coroutines.awaitCancellation
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsListener
import java.awt.event.HierarchyEvent
import java.beans.PropertyChangeListener
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
    return super.createCustomComponent(presentation, place).apply {
      maximumWidth = JBUI.scale(500)
      val widget = this as ToolbarComboButton
      launchOnShow("ProjectWidget") {
        val positionListeners = WidgetPositionListeners(widget, presentation)
        try {
          positionListeners.updatePosition() // this could be in WidgetPositionListeners.init, but it's safer inside the try
          awaitCancellation()
        }
        finally {
          positionListeners.dispose()
        }
      }
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)

    val widget = component as? ToolbarComboButton ?: return
    widget.isOpaque = false
    widget.positionListeners?.setProjectFromPresentation(presentation)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val projectName = project?.name ?: ""
    e.presentation.setText(projectName, false)
    e.presentation.description = FileUtil.getLocationRelativeToUserHome(project?.guessProjectDir()?.path) ?: projectName
    e.presentation.putClientProperty(projectKey, project)
    val customizer = ProjectWindowCustomizerService.getInstance()
    if (project != null && customizer.isAvailable()) {
      e.presentation.icon = customizer.getProjectIcon(project)
    }
  }

  private fun createPopup(it: Project, step: ListPopupStep<Any>): ListPopup {
    val widgetRenderer = ProjectWidgetRenderer()
    val renderer = Function<ListCellRenderer<Any>, ListCellRenderer<out Any>> { base ->
      ListCellRenderer<PopupFactoryImpl.ActionItem> { list, value, index, isSelected, cellHasFocus ->
        val action = (value as PopupFactoryImpl.ActionItem).action
        if (action is ProjectToolbarWidgetPresentable) {
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

private val widgetPositionListenersKey = Key.create<WidgetPositionListeners>("project-widget-position-listeners")
private val ToolbarComboButton.positionListeners: WidgetPositionListeners?
  get() = getClientProperty(widgetPositionListenersKey) as WidgetPositionListeners?

private class WidgetPositionListeners(private val widget: ToolbarComboButton, presentation: Presentation) {

  private val componentListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      updatePosition() // resize shouldn't affect the position, but in theory it might move the project icon
    }

    override fun componentMoved(e: ComponentEvent?) {
      updatePosition()
    }
  }

  private val hierarchyBoundsListener = object : HierarchyBoundsListener {
    override fun ancestorMoved(e: HierarchyEvent?) {
      updatePosition()
    }

    override fun ancestorResized(e: HierarchyEvent?) {
      updatePosition()
    }
  }

  private val propertyChangeListener = PropertyChangeListener { e ->
    if (e.propertyName == "leftIcons") {
      updatePosition()
    }
  }

  private var project: Project? = null

  init {
    setProjectFromPresentation(presentation)
    ClientProperty.put(widget, widgetPositionListenersKey, this)
    widget.addComponentListener(componentListener)
    widget.addHierarchyBoundsListener(hierarchyBoundsListener)
    widget.addPropertyChangeListener(propertyChangeListener)
  }

  fun dispose() {
    widget.removePropertyChangeListener(propertyChangeListener)
    widget.removeHierarchyBoundsListener(hierarchyBoundsListener)
    widget.removeComponentListener(componentListener)
    ClientProperty.remove(widget, widgetPositionListenersKey)
    project?.service<ProjectWidgetGradientLocationService>()?.setProjectWidgetIconCenterRelativeToRootPane(null)
    project = null
  }

  fun setProjectFromPresentation(presentation: Presentation) {
    val oldProject = project
    project = presentation.getClientProperty(projectKey)
    if (oldProject == null && project != null) {
      updatePosition() // now that the project is finally known, we can tell the service the position
    }
  }

  fun updatePosition() {
    val projectIconWidth = widget.leftIcons.firstOrNull()?.iconWidth?.toFloat() ?: 0f
    val offset = widget.let {
      SwingUtilities.convertPoint(it.parent, it.x, it.y, widget.rootPane).x.toFloat() + it.margin.left.toFloat() + projectIconWidth / 2
    }
    project?.service<ProjectWidgetGradientLocationService>()?.setProjectWidgetIconCenterRelativeToRootPane(offset)
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
    val action = value.action as ProjectToolbarWidgetPresentable
    lateinit var nameLbl: JLabel
    var providerPathLbl: JLabel? = null
    var projectPathLbl: JLabel? = null

    val content = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          icon(IconUtil.downscaleIconToSize(action.projectIcon, userScaledProjectIconSize(), userScaledProjectIconSize()))
            .align(AlignY.TOP)
            .customize(UnscaledGaps(right = 8))

          panel {
            val textGaps = UnscaledGaps(bottom = 4, top = 4)
            row {
              nameLbl = label(action.projectNameToDisplay)
                .customize(textGaps.copy(top = 0))
                .applyToComponent {
                  foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
                }.component
            }
            val providerPath = action.providerPathToDisplay
            if (providerPath != null) {
              row {
                icon(action.providerIcon ?: AllIcons.Nodes.Console)
                  .align(AlignY.CENTER)
                  .customize(customGaps = UnscaledGaps(right = 5))
                providerPathLbl = label(providerPath)
                  .customize(textGaps)
                  .align(AlignY.CENTER)
                  .applyToComponent {
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component
              }
            }
            val projectPathToDisplay = action.projectPathToDisplay
            if (projectPathToDisplay != null) {
              row {
                projectPathLbl = label(projectPathToDisplay)
                  .customize(textGaps)
                  .applyToComponent {
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component
              }
            }
            action.branchName?.let {
              row {
                label(it)
                  .customize(textGaps)
                  .applyToComponent {
                    icon = AllIcons.Vcs.Branch
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component
              }
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

    AccessibleContextUtil.setCombinedName(result, nameLbl, " - ", providerPathLbl, " - ", projectPathLbl)
    AccessibleContextUtil.setCombinedDescription(result, nameLbl, " - ", providerPathLbl, " - ", projectPathLbl)

    if (separator == null) {
      return result
    }

    val res = NonOpaquePanel(BorderLayout())
    res.border = JBUI.Borders.empty()
    res.add(createSeparator(separator, hideLine), BorderLayout.NORTH)
    res.add(result, BorderLayout.CENTER)

    AccessibleContextUtil.setName(res, result)
    AccessibleContextUtil.setDescription(res, result)

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


interface ProjectToolbarWidgetPresentable {
  val projectNameToDisplay: @NlsSafe String
  val providerPathToDisplay: @NlsSafe String? get() = null
  val projectPathToDisplay: @NlsSafe String?
  val branchName: @NlsSafe String?
  val projectIcon: Icon
  val providerIcon: Icon?
  val activationTimestamp: Long?
}