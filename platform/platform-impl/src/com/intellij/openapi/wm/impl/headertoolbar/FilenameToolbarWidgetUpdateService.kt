// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
internal class FilenameToolbarWidgetProjectUpdateService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  private val fileFlow = MutableSharedFlow<VirtualFile?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      fileFlow.distinctUntilChanged().collectLatest { file ->
        ApplicationManager.getApplication().service<FilenameToolbarWidgetAppUpdateService>().update(project, file)
      }
    }
  }

  fun fileSelected(file: VirtualFile?) {
    fileFlow.tryEmit(file)
  }
}

@Service(Service.Level.APP)
internal class FilenameToolbarWidgetAppUpdateService(
  coroutineScope: CoroutineScope,
) {

  private val components = WeakList<JComponent>()

  init {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          event.manager.project.service<FilenameToolbarWidgetProjectUpdateService>().fileSelected(event.newFile)
        }
      }
    )
  }

  fun registerComponent(component: JBLabel) {
    components += component
  }

  internal suspend fun update(project: Project, virtualFile: VirtualFile?) {
    val action = components.firstNotNullOfOrNull { ClientProperty.get(it, CustomComponentAction.ACTION_KEY) } ?: return
    val presentation = action.templatePresentation.clone()
    readAction {
      updatePresentation(project, virtualFile, presentation)
    }
    withContext(Dispatchers.EDT) {
      for (component in components) {
        // Unfortunately, we can't update invisible components here, as we can't determine the project for those.
        // So invisible components are handled by the action system only, skipping fast direct updates.
        // That's only necessary for the component to initially show up when the first file is opened or the widget is enabled.
        if (ProjectUtil.getProjectForComponent(component) == project) {
          updateComponent(component, presentation)
        }
      }
    }
  }

  fun updatePresentation(project: Project, file: VirtualFile?, presentation: Presentation) {
    if (!isWidgetEnabled() || file?.isValid != true) {
      presentation.isEnabledAndVisible = false
      return
    }
    val status = FileStatusManager.getInstance(project).getStatus(file)
    var fg:Color?

    var icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project)
    if (JBColor.isBright() && isDarkToolbar()) {
      icon = IconLoader.getDarkIcon(icon, true)
      fg = EditorColorsManager.getInstance().getScheme("Dark").getColor(status.colorKey)
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
    presentation.isEnabledAndVisible = true
    presentation.putClientProperty(FILE_COLOR, fg)
    presentation.putClientProperty(FILE_FULL_PATH, if (UISettings.getInstance().fullPathsInWindowHeader) file.path else null)
    presentation.description = StringUtil.shortenTextWithEllipsis(filename, 60, 30)
    presentation.icon = icon
  }

  private fun isWidgetEnabled(): Boolean {
    val uiSettings = UISettings.getInstance()
    return uiSettings.editorTabPlacement == UISettings.TABS_NONE || uiSettings.fullPathsInWindowHeader && isIDEA331002Fixed
  }

  @Suppress("UseJBColor")
  private fun isDarkToolbar() = ColorUtil.isDark(JBColor.namedColor("MainToolbar.background", Color.WHITE))

  fun updateComponent(component: JComponent, presentation: Presentation) {
    (component as JBLabel).apply {
      isEnabled = presentation.isEnabled
      isVisible = presentation.isVisible
      @Suppress("HardCodedStringLiteral")
      val path = presentation.getClientProperty(FILE_FULL_PATH)
      isOpaque = false
      iconTextGap = JBUI.scale(4)
      icon = presentation.icon
      foreground = presentation.getClientProperty(FILE_COLOR)
      text = presentation.description
      if (path != null && isIDEA331002Fixed) {
        val htmlColor = ColorUtil.toHtmlColor(JBColor.namedColor("Component.infoForeground", foreground))
        @Suppress("HardCodedStringLiteral")
        text = "<html><body>$text <font color='$htmlColor'>[$path]</font></body></html>"
      }
    }
  }

}

private val FILE_COLOR: Key<Color> = Key.create("FILENAME_WIDGET_FILE_COLOR")
private val FILE_FULL_PATH: Key<String?> = Key.create("FILENAME_WIDGET_FILE_PATH")
private const val isIDEA331002Fixed = false //todo[mikhail.sokolov]
