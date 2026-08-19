// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.scratch.ui.findScratchFileEditorWithPreview

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class KotlinScratchFileAutoRunner(private val project: Project, private val scope: CoroutineScope) : DocumentListener {
    private val flow = MutableSharedFlow<KotlinScratchFile>()

    init {
        scope.launch {
            flow.debounce(AUTO_RUN_DELAY_MS).collect {
                it.executor.execute()
            }
        }
    }

    fun submitRun(file: KotlinScratchFile) {
        scope.launch {
            flow.emit(file)
        }
    }

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

        if (project.isDisposed) return
        val scratchFile = getScratchFile(file, project) ?: return
        if (!scratchFile.options.isInteractiveMode) return

        scope.launch {
            flow.emit(scratchFile)
        }
    }

    private fun getScratchFile(file: VirtualFile, project: Project): KotlinScratchFile? {
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor
        return editor?.findScratchFileEditorWithPreview()?.kotlinScratchFile
    }

    companion object {
        const val AUTO_RUN_DELAY_MS: Long = 2_000
    }
}