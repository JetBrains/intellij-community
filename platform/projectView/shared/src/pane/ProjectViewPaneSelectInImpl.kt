// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInContext
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.actions.EditorChoice
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
data class SelectInTargetDescriptor(
  val id: @NonNls String,
  val presentableName: @NlsSafe String,
  val weight: Float,
)

@ApiStatus.Internal
fun SelectInRequestDTO.toSelectInRequest(project: Project): SelectInRequest? {
  val context = context ?: restoreSerializedContext(contextDTO, project) ?: return null
  return SelectByContextImpl(targetId, context)
}

private fun restoreSerializedContext(contextDescriptor: SelectInContextDTO, project: Project): SelectInContext? {
  val file = contextDescriptor.fileId.virtualFile() ?: return null
  return FileSelectInContext(project, file)
}

private data class SelectByContextImpl(
  override val targetId: @NonNls String,
  override val context: SelectInContext,
) : SelectByContext

internal data class SelectByEditorImpl(
  val editorChoice: EditorChoice,
  override val isInvokedManually: Boolean,
) : SelectByEditor {
  override val considerOnlyLastFocusedEditor: Boolean
    get() = when (editorChoice) {
      EditorChoice.ALL_SELECTED -> false
      EditorChoice.LAST_FOCUSED_ONLY -> true
    }
}

@ApiStatus.Internal
@Serializable
data class SelectInRequestDTO(
  val targetId: @NonNls String,
  val contextDTO: SelectInContextDTO,
  @Transient val context: SelectInContext? = null,
)

@ApiStatus.Internal
@Serializable
data class SelectInContextDTO(
  val fileId: VirtualFileId,
)
