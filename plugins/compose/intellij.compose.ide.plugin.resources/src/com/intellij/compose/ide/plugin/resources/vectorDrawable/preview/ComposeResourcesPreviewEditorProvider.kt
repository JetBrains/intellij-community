/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.compose.ide.plugin.resources.ComposeResourcesDataProvider
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.registerCreatedFileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.JBSplitter
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

internal class ComposeResourceEditorProvider : AsyncFileEditorProvider, StructureViewFileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    // Supports XML files
    if (!file.extension.equals("xml")) return false

    // Drawable directory
    val parent = file.parent ?: return false
    return parent.name.startsWith(ResourceType.DRAWABLE.dirName, ignoreCase = true) &&
           ComposeResourcesDataProvider.findProviderForProject(project)?.isComposeResourceFolder(project, parent) == true
  }

  /** The platform calls it when it adds this editor to an open tab, for example after a Gradle sync changes the project roots. */
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val document = FileDocumentManager.getInstance().getDocument(file)
    val editorCoroutineScope = project.service<ComposeResourcePreviewScope>().coroutineScope.childScope("Compose resource preview")
    val editor = createSplitEditor(project, file, document, editorCoroutineScope)
    Disposer.register(editor) { editorCoroutineScope.cancel() }
    return editor
  }

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val safeDocument = readAction {
      FileDocumentManager.getInstance().getDocument(file)
    }

    return withContext(Dispatchers.EDT) {
      createSplitEditor(project, file, safeDocument, editorCoroutineScope).also {
        registerCreatedFileEditor(it)
      }
    }
  }

  @RequiresEdt
  private fun createSplitEditor(project: Project, file: VirtualFile, document: Document?, editorCoroutineScope: CoroutineScope): FileEditor {
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val previewEditor = ComposeResourcePreviewEditor(project, file, document, editorCoroutineScope)

    return object : TextEditorWithPreview(textEditor, previewEditor) {
      override fun createSplitter(): JBSplitter {
        return super.createSplitter().apply { setHonorComponentsMinimumSize(false) }
      }
    }.also { it.component }
  }

  override fun getEditorTypeId(): String = "compose-drawable-preview-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS

  override fun acceptRequiresReadAction(): Boolean = false

  override fun getStructureViewBuilder(project: Project, file: VirtualFile): StructureViewBuilder? =
    TextEditorProvider.getInstance().getStructureViewBuilder(project, file)
}

/** Owns the coroutines of the preview editors that [ComposeResourceEditorProvider.createEditor] creates. */
@Service(Service.Level.PROJECT)
internal class ComposeResourcePreviewScope(val coroutineScope: CoroutineScope)