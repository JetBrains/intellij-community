// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.IconWidgetPresentation
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.Multiframe
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.ui.UIBundle
import com.intellij.util.Consumer
import com.intellij.util.io.ReadOnlyAttributeUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

internal class ToggleReadOnlyAttributePanel(private val project: Project) : Multiframe {
  private var statusBar: StatusBar? = null

  private val updateIconRequests = MutableSharedFlow<StatusBar>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    val connection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFilePropertyChangeEvent && VirtualFile.PROP_WRITABLE == event.propertyName) {
            scheduleIconUpdate()
            return
          }
        }
      }
    })
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        scheduleIconUpdate()
      }
    })
  }

  private fun scheduleIconUpdate() {
    statusBar?.let {
      check(updateIconRequests.tryEmit(it))
    }
  }

  override fun ID(): String = StatusBar.StandardWidgets.READONLY_ATTRIBUTE_PANEL

  override fun copy(): StatusBarWidget = ToggleReadOnlyAttributePanel(project)

  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  override fun getPresentation(): WidgetPresentation {
    return object : IconWidgetPresentation {
      private fun createFileFlow(): Flow<VirtualFile?> {
        return updateIconRequests
          .debounce(100.milliseconds)
          .mapLatest { statusBar ->
            statusBar.currentEditor()?.file
          }
          .distinctUntilChanged()
      }

      override fun icon(): Flow<Icon?> {
        return createFileFlow()
          .mapLatest {
            // todo current editor should be also as a flow
            val file = it?.takeIf(::isReadOnlyApplicableForFile) ?: return@mapLatest null
            if (file.isWritable) AllIcons.Ide.Readwrite else AllIcons.Ide.Readonly
          }
      }

      override fun getTooltipText(): String? {
        val virtualFile = getCurrentFile()
        val writable = if (virtualFile == null || virtualFile.isWritable) 1 else 0
        val readonly = if (writable == 1) 0 else 1
        return ActionsBundle.message("action.ToggleReadOnlyAttribute.files", readonly, writable, 1, 0)
      }

      override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer<MouseEvent> {
          val file = getCurrentFile()
          if (file == null || !isReadOnlyApplicableForFile(file)) {
            return@Consumer
          }

          FileDocumentManager.getInstance().saveAllDocuments()
          try {
            WriteAction.run<IOException> { ReadOnlyAttributeUtil.setReadOnlyAttribute(file, file.isWritable) }
            statusBar!!.updateWidget(ID())
          }
          catch (e: IOException) {
            Messages.showMessageDialog(project, e.message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon())
          }
        }
      }
    }
  }

  override fun dispose() {
    statusBar = null
  }

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
  }

  private fun getCurrentFile(): VirtualFile? = statusBar?.currentEditor?.invoke()?.file
}

private fun isReadOnlyApplicableForFile(file: VirtualFile): Boolean = !file.fileSystem.isReadOnly