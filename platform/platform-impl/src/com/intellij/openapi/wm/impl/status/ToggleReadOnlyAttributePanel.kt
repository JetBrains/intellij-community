// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.IconWidgetPresentation
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.ui.UIBundle
import com.intellij.util.io.ReadOnlyAttributeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

internal class ToggleReadOnlyAttributePanel(private val dataContext: WidgetPresentationDataContext,
                                            scope: CoroutineScope) : IconWidgetPresentation {
  private val updateIconRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    val disposable = Disposer.newDisposable()
    val connection = dataContext.project.messageBus.connect(disposable)
    connection.subscribe(VirtualFileManager.VFS_CHANGES_BG, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFilePropertyChangeEvent && VirtualFile.PROP_WRITABLE == event.propertyName) {
            check(updateIconRequests.tryEmit(Unit))
            return
          }
        }
      }
    })
    scope.coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }
  }

  private fun createFileFlow(): Flow<VirtualFile?> {
    return merge(updateIconRequests.mapLatest { dataContext.currentFileEditor.value }, dataContext.currentFileEditor)
      .debounce(100.milliseconds)
      .mapLatest {
        it?.file
      }
  }

  override fun icon(): Flow<Icon?> {
    return createFileFlow()
      .mapLatest {
        // todo current editor should be also as a flow
        val file = it?.takeIf(::isReadOnlyApplicableForFile) ?: return@mapLatest null
        if (file.isWritable) AllIcons.Ide.Readwrite else AllIcons.Ide.Readonly
      }
  }

  override suspend fun getTooltipText(): String? {
    val virtualFile = getCurrentFile()
    val writable = if (virtualFile == null || virtualFile.isWritable) 1 else 0
    val readonly = if (writable == 1) 0 else 1
    return ActionsBundle.message("action.ToggleReadOnlyAttribute.files", readonly, writable, 1, 0)
  }

  override fun getClickConsumer(): (MouseEvent) -> Unit {
    return h@ {
      val file = getCurrentFile()
      if (file == null || !isReadOnlyApplicableForFile(file)) {
        return@h
      }

      FileDocumentManager.getInstance().saveAllDocuments()
      try {
        WriteAction.run<IOException> { ReadOnlyAttributeUtil.setReadOnlyAttribute(file, file.isWritable) }
      }
      catch (e: IOException) {
        Messages.showMessageDialog(dataContext.project, e.message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon())
      }
    }
  }

  private fun getCurrentFile(): VirtualFile? = dataContext.currentFileEditor.value?.file
}

private fun isReadOnlyApplicableForFile(file: VirtualFile): Boolean = !file.fileSystem.isReadOnly