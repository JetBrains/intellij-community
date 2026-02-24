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
package com.intellij.compose.ide.plugin.resources.previewDrawables

import com.intellij.compose.ide.plugin.resources.COMPOSE_RESOURCES_DIR
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ComposeResourceEditorProvider : AsyncFileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!file.extension.equals("xml")) return false
    if (file.name.startsWith("strings")) return false

    val parent = file.parent ?: return false
    if (!parent.name.startsWith(ResourceType.DRAWABLE.dirName, ignoreCase = true)) return false

    val composeResourcesDir = parent.parent ?: return false
    val result = composeResourcesDir.name == COMPOSE_RESOURCES_DIR

    return result
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    thisLogger().warn("ComposeResourceEditorProvider.createEditor should not be called")
    return TextEditorProvider.getInstance().createEditor(project, file)
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
      val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
      val previewEditor = ComposeResourcePreviewEditor(file, safeDocument, editorCoroutineScope)

      object : TextEditorWithPreview(textEditor, previewEditor) {
        override fun createSplitter(): JBSplitter {
          return super.createSplitter().apply { setHonorComponentsMinimumSize(false) }
        }
      }
    }
  }

  override fun getEditorTypeId(): String = "compose-drawable-preview-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}
