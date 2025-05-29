// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.Companion.getInstance
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.wm.impl.*
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
class FilenameToolbarWidgetAction : ExpandableComboAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val uiSettings = UISettings.getInstance()
    if (uiSettings.editorTabPlacement != UISettings.TABS_NONE && !(uiSettings.fullPathsInWindowHeader)) return
    val project = e.project ?: return
    val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return
    updatePresentationFromFile(project, file, e.presentation)
  }

  private fun updatePresentationFromFile(project: Project, file: VirtualFile, presentation: Presentation) {
    val status = FileStatusManager.getInstance(project).getStatus(file)
    var fg: Color?

    var icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project)
    if (JBColor.isBright() && isDarkToolbar()) {
      icon = IconLoader.getDarkIcon(icon, true)
      fg = EditorColorsManager.getInstance().getScheme("Dark")?.getColor(status.colorKey)
    }
    else {
      fg = status.color
    }

    if (fg == null) {
      @Suppress("UnregisteredNamedColor")
      fg = JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
    }

    @Suppress("HardCodedStringLiteral")
    val filename = VfsPresentationUtil.getUniquePresentableNameForUI(project, file)
    val path = FrameTitleBuilder.getInstance().getFileTitle(project, file)
    presentation.isEnabledAndVisible = true
    presentation.putClientProperty(FILE_COLOR, fg)
    presentation.putClientProperty(FILE_FULL_PATH, if (UISettings.getInstance().fullPathsInWindowHeader && path != filename) path else null)
    presentation.text = StringUtil.shortenTextWithEllipsis(filename, 60, 30)
    presentation.description = path
    presentation.icon = icon
  }

  @Suppress("UseJBColor")
  private fun isDarkToolbar() = ColorUtil.isDark(JBColor.namedColor("MainToolbar.background", Color.WHITE))

  override fun createPopup(event: AnActionEvent): JBPopup? {
    val project = event.project ?: return null
    val recentFiles = getInstance(project).fileList.asReversed()
    if (recentFiles.size > 1) {
      val files = recentFiles.subList(1, recentFiles.lastIndex + 1)
      return JBPopupFactory.getInstance().createListPopup(RecentFilesListPopupStep(project, files))
    }
    return null
  }

  override fun createToolbarComboButton(model: ToolbarComboButtonModel): ToolbarComboButton = FilenameToolbarWidget(model)

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as FilenameToolbarWidget).update(presentation)
  }

  private inner class FilenameToolbarWidget(model: ToolbarComboButtonModel) : ListenableToolbarComboButton(model) {

    init {
      isOpaque = false
      hoverBackground = JBColor.namedColor("MainToolbar.Dropdown.background", JBColor.foreground())
    }

    override fun processMouseEvent(e: MouseEvent) {
      if (e.id == MouseEvent.MOUSE_RELEASED && UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
        e.consume()
        val project = ProjectUtil.getProjectForComponent(this@FilenameToolbarWidget)
        if (project != null) {
          val files = FileEditorManager.getInstance(project).selectedFiles
          if (files.isNotEmpty()) {
            FileEditorManager.getInstance(project).closeFile(files[0])
          }
        }
        return
      }
      super.processMouseEvent(e)
    }

    fun update(presentation: Presentation) {
      isOpaque = false
      if (leftIcons.firstOrNull() != presentation.icon) leftIcons = listOf(presentation.icon!!)
      foreground = presentation.getClientProperty(FILE_COLOR)
      toolTipText = presentation.description
      val path = presentation.getClientProperty(FILE_FULL_PATH)
      text = if (path != null) "${presentation.textWithMnemonic} [$path]" else presentation.textWithMnemonic
      if (text.isNullOrEmpty()) {
        // A trick to avoid flashing the "unknown" icon on the toolbar during initialization,
        // as the action system goes out of its way to make the action visible until the first update.
        isVisible = false
      }

    }

    override fun installListeners(project: Project?, disposable: Disposable) {
      if (project == null) return
      val editorListener = object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          updateWidgetAction()
        }
      }
      project.messageBus.connect(disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorListener)
    }
  }

  class RecentFilesListPopupStep(val project: Project, files: List<VirtualFile>) : BaseListPopupStep<VirtualFile>(null, files) {
    override fun getIconFor(value: VirtualFile?): Icon? {
      if (value == null) return null
      return IconUtil.getIcon(value, Iconable.ICON_FLAG_READ_STATUS, project)
    }

    override fun getForegroundFor(value: VirtualFile?): Color? {
      if (value == null) return null
      return FileStatusManager.getInstance(project).getStatus(value).color
    }

    override fun getTextFor(value: VirtualFile?): @NlsSafe String = value?.presentableName ?: ""

    override fun onChosen(selectedValue: VirtualFile?, finalChoice: Boolean): PopupStep<*>? {
      if (selectedValue != null && finalChoice) FileEditorManager.getInstance(project).openFile(selectedValue, true)
      return null
    }
  }
}

private val FILE_COLOR: Key<Color> = Key.create("FILENAME_WIDGET_FILE_COLOR")
private val FILE_FULL_PATH: Key<String?> = Key.create("FILENAME_WIDGET_FILE_PATH")
private val LOG = logger<FilenameToolbarWidgetAction>()
