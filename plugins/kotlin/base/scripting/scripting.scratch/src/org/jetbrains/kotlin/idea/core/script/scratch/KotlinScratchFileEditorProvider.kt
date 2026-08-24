// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.createdFileEditorSink
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.core.script.scratch.ui.KotlinScratchFileEditorWithPreview

internal class KotlinScratchFileEditorProvider : AsyncFileEditorProvider, StructureViewFileEditorProvider {
    private val KTS_SCRATCH_EDITOR_PROVIDER: String = "KtsScratchFileEditorProvider"

    override fun getEditorTypeId(): String = KTS_SCRATCH_EDITOR_PROVIDER

    override fun acceptRequiresReadAction(): Boolean = false

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun getStructureViewBuilder(project: Project, file: VirtualFile): StructureViewBuilder? =
        TextEditorProvider.getInstance().getStructureViewBuilder(project, file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        TextEditorProvider.getInstance().createEditor(project, file)

    override fun accept(project: Project, file: VirtualFile): Boolean = file.isValid && file.isKotlinScratch

    override suspend fun createFileEditor(
        project: Project, file: VirtualFile, document: Document?, editorCoroutineScope: CoroutineScope
    ): FileEditor {
        val textEditorProvider = TextEditorProvider.getInstance()

        val scratchFile =
            KotlinScratchFile(project, file, editorCoroutineScope.childScope(KotlinScratchFile::class.java.simpleName))

        val mainEditor = textEditorProvider.createFileEditor(
            project = project,
            file = scratchFile.virtualFile,
            document = document,
            editorCoroutineScope = editorCoroutineScope,
        )

        val editorFactory = serviceAsync<EditorFactory>()

        // mainEditor is registered by TextEditorProvider; the wrapper below is discarded by a cancellation landing on this withContext
        val createdEditors = createdFileEditorSink()
        return withContext(Dispatchers.EDT) {
            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor, Disposable { editorFactory.releaseEditor(viewer) })
            val previewEditor = textEditorProvider.getTextEditor(viewer)
            KotlinScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor).also { createdEditors?.register(it) }
        }
    }
}

