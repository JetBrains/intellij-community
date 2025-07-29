// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
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
import com.intellij.openapi.wm.impl.ToolbarComboButtonModel
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.util.maximumWidth
import com.intellij.util.IconUtil
import com.intellij.util.application
import com.intellij.util.ui.*
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsListener
import java.awt.event.HierarchyEvent
import java.beans.PropertyChangeListener
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.*

private const val MAX_RECENT_COUNT = 100
private val projectKey = Key.create<Project>("project-widget-project")

internal class DefaultOpenProjectSelectionPredicateSupplier : OpenProjectSelectionPredicateSupplier {
  override fun getPredicate(): Predicate<AnAction> {
    val openProjects = ProjectUtilCore.getOpenProjects()
    val paths: List<@SystemIndependent @NonNls String?> = openProjects.map { it.basePath }
    return Predicate { action ->
      when (action) {
        is ReopenProjectAction -> action.projectPath in paths
        is ProjectToolbarWidgetPresentable -> action.status?.isOpened == true
        else -> false
      }
    }
  }
}

@ApiStatus.Internal
open class ProjectToolbarWidgetAction : ExpandableComboAction(), DumbAware {

  override fun createPopup(event: AnActionEvent): JBPopup? {
    val step = createStep(createActionGroup(event), event.dataContext)
    return event.project?.let { createPopup(it = it, step = step) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createToolbarComboButton(model: ToolbarComboButtonModel): ToolbarComboButton {
    return super.createToolbarComboButton(model).apply {
      accessibleNamePrefix = IdeUICustomization.getInstance().projectMessage("project.widget.accessible.name.prefix")
    }
  }

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
    val icons = buildList {
      UpdatesInfoProviderManager.getInstance().getUpdateIcons().let { updateIcons ->
        for (icon in updateIcons) {
          if (isNotEmpty()) addGap()
          add(icon)
        }
      }

      val customizer = ProjectWindowCustomizerService.getInstance()
      if (project != null && customizer.isAvailable()) {
        if (isNotEmpty()) addGap()
        add(customizer.getProjectIcon(project))
      }
    }
    e.presentation.icon = when (icons.size) {
      0 -> null
      1 -> icons.single()
      else -> IconManager.getInstance().createRowIcon(*icons.toTypedArray())
    }
  }

  private fun createPopup(it: Project, step: ListPopupStep<PopupFactoryImpl.ActionItem>): ListPopup {
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

    if (result is ListPopupImpl) {
      ClientProperty.put(result.list, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)

      application.messageBus.connect(result).subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
        override fun change() {
          updateChildGroupAvailability(result)

          result.list.repaint()
        }
      })
    }

    return result
  }

  private fun updateChildGroupAvailability(listPopup: ListPopupImpl) {
    val popupStep = listPopup.listStep as? ActionPopupStep ?: return
    popupStep.updateStepItems(listPopup.list)
  }

  private fun createActionGroup(initEvent: AnActionEvent): ActionGroup {
    val result = DefaultActionGroup()

    UpdatesInfoProviderManager.getInstance()
      .getUpdateActions()
      .takeIf { it.isNotEmpty() }
      ?.let {
        result.addAll(it)
        result.addSeparator()
      }

    val group = ActionManager.getInstance().getAction("ProjectWidget.Actions") as ActionGroup
    result.addAll(group.getChildren(initEvent).asList())
    val openProjectsPredicate = OpenProjectSelectionPredicateSupplier.getInstance().getPredicate()
    val actionsMap = RecentProjectListActionProvider.getInstance().getActions(initEvent.project)
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

  private fun createStep(actionGroup: ActionGroup, context: DataContext): ListPopupStep<PopupFactoryImpl.ActionItem> {
    val presentationFactory = PresentationFactory()
    val asyncDataContext: DataContext = Utils.createAsyncDataContext(context)
    val options = ActionPopupOptions.showDisabled()
      .withSpeedSearchFilter(ProjectWidgetSpeedsearchFilter())
    return ActionPopupStep.createActionsStep(null, actionGroup, asyncDataContext, ActionPlaces.PROJECT_WIDGET_POPUP, presentationFactory,
                                             Supplier { asyncDataContext }, options)
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

private class ProjectWidgetSpeedsearchFilter : SpeedSearchFilter<PopupFactoryImpl.ActionItem> {
  override fun getIndexedString(value: PopupFactoryImpl.ActionItem): String {
    val action = value.action as? ProjectToolbarWidgetPresentable ?: return value.text
    return action.projectNameToDisplay + " " + action.projectPathToDisplay.orEmpty() + " " + action.providerPathToDisplay.orEmpty()
  }
}

private class ProjectWidgetRenderer : ListCellRenderer<PopupFactoryImpl.ActionItem> {
  override fun getListCellRendererComponent(
    list: JList<out PopupFactoryImpl.ActionItem>?,
    value: PopupFactoryImpl.ActionItem?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
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
          val rowGaps = UnscaledGaps(bottom = 2, top = 2)

          icon(IconUtil.downscaleIconToSize(action.projectIcon, userScaledProjectIconSize(), userScaledProjectIconSize()))
            .align(AlignY.TOP)
            .customize(rowGaps.copy(right = 8))

          panel {
            row {
              nameLbl = label(action.projectNameToDisplay)
                .customize(rowGaps)
                .applyToComponent {
                  foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
                }.component

              val projectStatus = action.status
              if (projectStatus?.statusText != null) {
                label(projectStatus.statusText)
                  .customize(rowGaps.copy(left = 4, right = 8))
                  .applyToComponent {
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }
              }

              val hasSubmenuArrow = value.isEnabled && action is ActionGroup && !value.isSubstepSuppressed
              if (projectStatus?.progressText != null || hasSubmenuArrow) {
                // UI DSL is broken for AlignX.RIGHT
                val rightPanel = JPanel()
                rightPanel.layout = BoxLayout(rightPanel, BoxLayout.X_AXIS)
                rightPanel.isOpaque = false

                if (projectStatus?.progressText != null) {
                  val progressLabel = JBLabel(projectStatus.progressText).apply {
                    icon = AnimatedIcon.Default.INSTANCE
                    font = JBFont.smallOrNewUiMedium()
                  }
                  rightPanel.add(progressLabel)
                }

                if (hasSubmenuArrow) {
                  val arrowLabel = JBLabel().apply {
                    icon = if (isSelected) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow
                    border = JBUI.Borders.emptyLeft(6)
                  }
                  rightPanel.add(arrowLabel)
                }

                cell(rightPanel)
                  .align(AlignY.CENTER)
                  .align(AlignX.RIGHT)
              }
            }
            val providerPath = action.providerPathToDisplay
            if (providerPath != null) {
              row {
                providerPathLbl = label(providerPath)
                  .customize(rowGaps)
                  .applyToComponent {
                    icon = action.providerIcon ?: AllIcons.Welcome.RecentProjects.RemoteProject
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component
              }
            }
            val projectPathToDisplay = action.projectPathToDisplay
            if (projectPathToDisplay != null) {
              row {
                projectPathLbl = label(projectPathToDisplay)
                  .customize(rowGaps)
                  .applyToComponent {
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component
              }
            }
            action.branchName?.let {
              row {
                label(it)
                  .customize(rowGaps)
                  .applyToComponent {
                    icon = IconUtil.colorize(AllIcons.Vcs.Branch, UIUtil.getLabelInfoForeground(), keepGray = false, keepBrightness = false)
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component
              }
            }
          }
        }
      }
    }.apply {
      border = JBUI.Borders.empty(6, 0)
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


@JvmDefaultWithCompatibility
interface ProjectToolbarWidgetPresentable {
  val projectNameToDisplay: @NlsSafe String
  val providerPathToDisplay: @NlsSafe String? get() = null
  val projectPathToDisplay: @NlsSafe String?
  val branchName: @NlsSafe String?
  val projectIcon: Icon
  val providerIcon: Icon?
  val activationTimestamp: Long?

  @get:ApiStatus.Internal
  val status: ProjectStatus? get() = null

  /**
   * Combined info to be used, when only a single-line-label is applicable.
   */
  val nameToDisplayAsText: @NlsSafe String get() = projectNameToDisplay
}

@ApiStatus.Internal
class ProjectStatus(
  val isOpened: Boolean,
  val statusText: @Nls String?,
  val progressText: @Nls String?,
)

private fun MutableList<in Icon>.addGap() {
  add(EmptyIcon.create(BETWEEN_ICONS_GAP, 1))
}

private const val BETWEEN_ICONS_GAP = 9
