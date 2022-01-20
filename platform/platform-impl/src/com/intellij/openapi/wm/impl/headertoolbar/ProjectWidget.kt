// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
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
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.IconManager
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.InputEvent
import java.util.concurrent.Executor
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

private class ProjectWidgetUpdater(val proj: Project, val widget: ProjectWidget) : FileEditorManagerListener, UISettingsListener, ProjectManagerListener {
  private var file: VirtualFile? by Delegates.observable(null) { _, _, _ -> updateText() }
  private var settings: UISettings by Delegates.observable(UISettings.instance) { _, _, _ -> updateText() }

  private val swingExecutor: Executor = Executor { run -> SwingUtilities.invokeLater(run) }

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

  private fun cutProject(value: String, maxLength: Int): String =
    if (value.length <= maxLength) value else value.substring(0, maxLength) + "..."

  fun subscribe() {
    ApplicationManager.getApplication().messageBus.connect(widget).subscribe(UISettingsListener.TOPIC, this)
    proj.messageBus.connect(widget).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    swingExecutor.execute { settings = uiSettings }
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    swingExecutor.execute { file = event.newFile }
  }
}

private class ProjectWidget(private val project: Project): ToolbarComboWidget(), Disposable {
  override fun doExpand(e: InputEvent) {
    val step = MyStep(createActionsList())
    val widgetRenderer = ProjectWidgetRenderer(step::getSeparatorAbove)

    val renderer = Function<ListCellRenderer<Any>, ListCellRenderer<Any>> { base ->
      ListCellRenderer<Any> { list, value, index, isSelected, cellHasFocus ->
        if (value is ReopenProjectAction) {
          widgetRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
        else {
          base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
      }
    }

    JBPopupFactory.getInstance().createListPopup(project, step, renderer).showUnderneathOf(this)
  }

  override fun removeNotify() {
    super.removeNotify()
    Disposer.dispose(this)
  }

  override fun dispose() {}

  private fun createActionsList(): Map<AnAction, Presentation?> {
    val actionManager = ActionManager.getInstance()
    val res = mutableMapOf<AnAction, Presentation?>(
      actionManager.createActionPair("NewProject", IdeBundle.message("project.widget.new"), "expui/general/add.svg"),
      actionManager.createActionPair("ImportProject", IdeBundle.message("project.widget.open"), "expui/toolwindow/project.svg"),
      actionManager.createActionPair("ProjectFromVersionControl", IdeBundle.message("project.widget.from.vcs"), "expui/vcs/vcs.svg")
    )

    RecentProjectListActionProvider.getInstance().getActions().take(MAX_RECENT_COUNT).forEach { res[it] = null }

    return res
  }

  private fun ActionManager.createActionPair(actionID: String, name: String, iconPath: String): Pair<AnAction, Presentation> {
    val action = getAction(actionID)
    val presentation = action.templatePresentation.clone()
    presentation.text = name
    presentation.icon = IconManager.getInstance().getIcon(iconPath, AllIcons::class.java)
    return Pair(action, presentation)
  }

  private class MyStep(private val actionsMap: Map<AnAction, Presentation?>): ListPopupStep<AnAction> {
    private val actions: List<AnAction> = actionsMap.keys.toList()
    private val presentationMapper: (AnAction?) -> Presentation? = { action -> action?.let { actionsMap[it] } }

    override fun getTitle(): String? = null

    override fun onChosen(selectedValue: AnAction?, finalChoice: Boolean): PopupStep<*>? {
      selectedValue?.actionPerformed(AnActionEvent.createFromDataContext("", selectedValue.templatePresentation, DataContext.EMPTY_CONTEXT))
      return PopupStep.FINAL_CHOICE
    }

    override fun hasSubstep(selectedValue: AnAction?): Boolean = false

    override fun canceled() {}

    override fun isMnemonicsNavigationEnabled(): Boolean = false

    override fun getMnemonicNavigationFilter(): MnemonicNavigationFilter<AnAction>? = null

    override fun isSpeedSearchEnabled(): Boolean = false

    override fun getSpeedSearchFilter(): SpeedSearchFilter<AnAction>? = null

    override fun isAutoSelectionEnabled(): Boolean = false

    override fun getFinalRunnable(): Runnable? = null

    override fun getValues(): MutableList<AnAction> = actions.toMutableList()

    override fun isSelectable(value: AnAction?): Boolean = value !is SeparatorAction

    override fun getIconFor(value: AnAction?): Icon? = presentationMapper(value)?.let { it.icon }

    override fun getTextFor(value: AnAction?): String = presentationMapper(value)?.let { it.text } ?: ""

    override fun getSeparatorAbove(value: AnAction?): ListSeparator? {
      val index = actions.indexOf(value)
      if (index == 0) return null

      val prev = actions[index - 1]
      return if ((prev !is ReopenProjectAction) && (value is ReopenProjectAction))
        ListSeparator(IdeBundle.message("project.widget.recent.projects"))
        else null
    }

    override fun getDefaultOptionIndex(): Int = 0
  }

  private class ProjectWidgetRenderer(val separatorSupplier: (AnAction) -> ListSeparator?): ListCellRenderer<Any> {

    private val recentProjectsManager = RecentProjectsManagerBase.instanceEx

    override fun getListCellRendererComponent(list: JList<out Any>?,
                                              value: Any?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      return createRecentProjectPane(value as ReopenProjectAction, isSelected, separatorSupplier.invoke(value))
    }

    private fun createRecentProjectPane(action: ReopenProjectAction, isSelected: Boolean, separator: ListSeparator?): JComponent {
      val projectPath = action.projectPath
      val nameLbl = JLabel(action.projectNameToDisplay ?: "")
      val pathLbl = JLabel(projectPath)
      val iconLbl = JLabel(recentProjectsManager.getProjectIcon(projectPath, true))

      pathLbl.font = JBFont.small()
      pathLbl.foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelInfoForeground()
      nameLbl.foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getListForeground()

      val inner = NonOpaquePanel()
      inner.add(nameLbl, BorderLayout.NORTH)
      inner.add(pathLbl, BorderLayout.SOUTH)
      inner.border = JBUI.Borders.emptyLeft(11)

      val outer = NonOpaquePanel(BorderLayout())
      outer.add(iconLbl, BorderLayout.WEST)
      outer.add(inner, BorderLayout.CENTER)
      outer.border = JBUI.Borders.empty(5, 13)

      AccessibleContextUtil.setCombinedName(outer, nameLbl, " - ", pathLbl)
      AccessibleContextUtil.setCombinedDescription(outer, nameLbl, " - ", pathLbl)

      var res = outer
      if (separator != null) {
        res = NonOpaquePanel(BorderLayout())
        res.border = JBUI.Borders.empty()
        res.add(createSeparator(separator), BorderLayout.NORTH)
        res.add(outer, BorderLayout.CENTER)
      }

      return res
    }

    private fun createSeparator(separator: ListSeparator): JComponent {
      val res = GroupHeaderSeparator(JBUI.insets(0, 13, 5, 0))
      res.caption = separator.text

      val panel = JPanel(BorderLayout())
      panel.border = JBUI.Borders.empty()
      panel.isOpaque = true
      panel.background = UIUtil.getListBackground()
      panel.add(res)

      return panel
    }
  }
}
