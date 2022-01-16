// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionListener
import java.util.function.Function
import javax.swing.*

private const val MAX_RECENT_COUNT = 100

class ProjectWidget(project: Project?): ToolbarComboWidget(), Disposable {

  private var currentFile: VirtualFile?
  private var currentProject: Project? = project
  private var editorConnection: MessageBusConnection? = null

  private val updater: Updater = Updater()

  private val projectListener = object: ProjectManagerListener {
    override fun projectOpened(project: Project) {
      currentProject = project
      editorConnection?.disconnect()
      editorConnection = project.messageBus.connect(this@ProjectWidget)
      editorConnection?.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorListener)

      updater.performUpdate()
    }
  }

  private val editorListener = object: FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      currentFile = event.newFile
      updater.performUpdate()
    }
  }

  init {
    currentFile = project?.let { FileEditorManager.getInstance(it).selectedFiles.firstOrNull() }

    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(ProjectManager.TOPIC, projectListener)
    connection.subscribe(UISettingsListener.TOPIC, updater)

    addPressListener(ActionListener {
      val myStep = MyStep(createActionsList())
      val myRenderer = ProjectWidgetRenderer(myStep::getSeparatorAbove)

      val renderer = Function<ListCellRenderer<Any>, ListCellRenderer<Any>> { base ->
        ListCellRenderer<Any> { list, value, index, isSelected, cellHasFocus ->
          if (value is ReopenProjectAction) myRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          else base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
      }

      currentProject?.let {
        JBPopupFactory.getInstance().createListPopup(it, myStep, renderer).showUnderneathOf(this)
      }
    })
  }

  override fun dispose() {}

  private fun createActionsList(): List<AnAction> {
    val res = mutableListOf<AnAction>()

    val actionManager = ActionManager.getInstance()
    res.add(actionManager.getAction("NewProject"))
    res.add(actionManager.getAction("ImportProject"))
    res.add(actionManager.getAction("ProjectFromVersionControl"))

    RecentProjectListActionProvider.getInstance().getActions().take(MAX_RECENT_COUNT).forEach { res.add(it) }

    return res
  }

  private class MyStep(val actions: List<AnAction>): ListPopupStep<AnAction> {
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

    override fun getIconFor(value: AnAction?): Icon? = value?.templatePresentation?.icon

    override fun getTextFor(value: AnAction?): String = value?.templateText ?: ""

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

  private inner class Updater : UISettingsListener {

    var uiSettings = UISettings.instance

    override fun uiSettingsChanged(settings: UISettings) {
      uiSettings = settings
      performUpdate()
    }

    fun performUpdate() {
      val sb = StringBuilder(currentProject?.name ?: IdeBundle.message("project.widget.empty"))
      if (uiSettings.editorTabPlacement == UISettings.TABS_NONE && currentFile != null) {
        sb.append(" — ").append(currentFile!!.name)
      }

      text = sb.toString()
    }
  }
}
