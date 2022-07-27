// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.*
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.InputEvent
import java.util.function.Function
import javax.swing.*
import kotlin.properties.Delegates

private const val MAX_RECENT_COUNT = 100

internal class ProjectWidgetFactory : MainToolbarProjectWidgetFactory {
  override fun createWidget(project: Project): JComponent {
    val widget = ProjectWidget(project)
    ProjectWidgetUpdater(project, widget).subscribe()
    return widget
  }

  override fun getPosition(): Position = Position.Center
}

private class ProjectWidgetUpdater(private val proj: Project,
                                   private val widget: ProjectWidget) : FileEditorManagerListener, UISettingsListener, ProjectManagerListener {
  private var file: VirtualFile? by Delegates.observable(null) { _, _, _ -> updateText() }
  private var settings: UISettings by Delegates.observable(UISettings.getInstance()) { _, _, _ -> updateText() }

  init {
    file = FileEditorManager.getInstance(proj).selectedFiles.firstOrNull()
  }

  private fun updateText() {
    val currentFile = file
    val showFileName = settings.editorTabPlacement == UISettings.TABS_NONE && currentFile != null
    val maxLength = if (showFileName) 12 else 24

    @NlsSafe val fullName = StringBuilder(proj.name)
    @NlsSafe val cutName = StringBuilder(cutProject(proj.name, maxLength))
    if (showFileName) {
      fullName.append(" — ").append(currentFile!!.name)
      cutName.append(" — ").append(cutFile(currentFile.name, maxLength))
    }

    widget.text = cutName.toString()
    widget.toolTipText = if (cutName.toString() == fullName.toString()) null else fullName.toString()
  }

  private fun cutFile(value: String, maxLength: Int): String {
    if (value.length <= maxLength) return value

    val extension = value.substringAfterLast(".", "")
    val name = value.substringBeforeLast(".")
    if (name.length + extension.length <= maxLength) return value

    return name.substring(0, maxLength - extension.length) + "..." + extension
  }

  private fun cutProject(value: String, maxLength: Int): String {
    return if (value.length <= maxLength) value else value.substring(0, maxLength) + "..."
  }

  fun subscribe() {
    ApplicationManager.getApplication().messageBus.connect(widget).subscribe(UISettingsListener.TOPIC, this)
    proj.messageBus.connect(widget).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    SwingUtilities.invokeLater { settings = uiSettings }
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    SwingUtilities.invokeLater { file = event.newFile }
  }
}

private class ProjectWidget(private val project: Project): ToolbarComboWidget(), Disposable {

  override fun doExpand(e: InputEvent) {
    val dataContext = DataManager.getInstance().getDataContext(this)
    val anActionEvent = AnActionEvent.createFromInputEvent(e, ActionPlaces.PROJECT_WIDGET_POPUP, null, dataContext)
    val step = createStep(createActionGroup(anActionEvent))

    val widgetRenderer = ProjectWidgetRenderer(step::getSeparatorAbove)

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

    val popup = JBPopupFactory.getInstance().createListPopup(project, step, renderer)
    popup.setRequestFocus(false)
    popup.showUnderneathOf(this)
  }

  private fun createActionGroup(initEvent: AnActionEvent): ActionGroup {
    val res = DefaultActionGroup()

    val group = ActionManager.getInstance().getAction("ProjectWidget.Actions") as ActionGroup
    res.addAll(group.getChildren(initEvent).asList())
    res.addSeparator(IdeBundle.message("project.widget.recent.projects"))
    RecentProjectListActionProvider.getInstance().getActions().take(MAX_RECENT_COUNT).forEach { res.add(it) }

    return res
  }

  private fun createStep(actionGroup: ActionGroup): ListPopupStep<Any> {
    val context = DataManager.getInstance().getDataContext(this)
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, this, false, 0, false)
  }

  override fun removeNotify() {
    super.removeNotify()
    Disposer.dispose(this)
  }

  override fun dispose() {}

  private class ProjectWidgetRenderer(val separatorSupplier: (PopupFactoryImpl.ActionItem) -> ListSeparator?): ListCellRenderer<PopupFactoryImpl.ActionItem> {
    override fun getListCellRendererComponent(list: JList<out PopupFactoryImpl.ActionItem>?,
                                              value: PopupFactoryImpl.ActionItem?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      return createRecentProjectPane(value as PopupFactoryImpl.ActionItem, isSelected, separatorSupplier.invoke(value))
    }

    private fun createRecentProjectPane(value: PopupFactoryImpl.ActionItem, isSelected: Boolean, separator: ListSeparator?): JComponent {
      val action = value.action as ReopenProjectAction
      val projectPath = action.projectPath
      lateinit var nameLbl: JLabel
      lateinit var pathLbl: JLabel

      val content = panel {
        customizeSpacingConfiguration(EmptySpacingConfiguration()) {
          row {
            icon(RecentProjectsManagerBase.getInstanceEx().getProjectIcon(projectPath, true))
              .verticalAlign(VerticalAlign.TOP)
              .customize(JBGaps(right = 8))

            panel {
              row {
                nameLbl = label(action.projectNameToDisplay ?: "")
                  .customize(JBGaps(bottom = 4))
                  .applyToComponent {
                    foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
                  }.component
              }
              row {
                pathLbl = label(FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(projectPath), false))
                  .applyToComponent {
                    font = JBFont.small()
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
      PopupUtil.configSelectablePanel(result)
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
      res.add(createSeparator(separator), BorderLayout.NORTH)
      res.add(result, BorderLayout.CENTER)
      return res
    }

    private fun createSeparator(separator: ListSeparator): JComponent {
      val res = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
      res.caption = separator.text

      val panel = JPanel(BorderLayout())
      panel.border = JBUI.Borders.empty()
      panel.isOpaque = true
      panel.background = JBUI.CurrentTheme.Popup.BACKGROUND
      panel.add(res)

      return panel
    }
  }
}
