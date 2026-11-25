// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInContext
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * A Select In target that selects in the pane that declares it.
 *
 * A target with a [group] shows as a child of that group.
 */
@ApiStatus.Internal
@Serializable
data class SelectInTargetDescriptor(
  val presentableName: @NlsSafe String,
  val weight: Float,
  val group: SelectInTargetGroupImpl?,
)

@ApiStatus.Internal
@Serializable
data class SelectInTargetGroupImpl(
  val id: @NonNls String,
  val presentableName: @NlsSafe String,
  val weight: Float,
) : SelectInTargetGroup

/**
 * Checks the Select In contexts against the backend panes of this process, see [ProjectViewPaneServiceBase.canSelect].
 *
 * Only the backend module registers this service.
 * So the frontend gets it only in the monolith mode, where the backend runs in the same process.
 */
@ApiStatus.Internal
interface BackendProjectViewSelectInChecker {
  companion object {
    /** Returns `null` if the backend does not run in this process. */
    fun getInstanceIfLocal(project: Project): BackendProjectViewSelectInChecker? = project.serviceOrNull()
  }

  @RequiresReadLock
  fun canSelect(paneId: ProjectViewPaneId, context: SelectInContext): Boolean
}

@ApiStatus.Internal
fun SelectInRequestDTO.toSelectInRequest(project: Project): SelectInRequest? {
  val context = context ?: restoreSerializedContext(contextDTO, project) ?: return null
  return SelectByContextImpl(context)
}

private fun restoreSerializedContext(contextDescriptor: SelectInContextDTO, project: Project): SelectInContext? {
  val file = contextDescriptor.fileId.virtualFile() ?: return null
  return FileSelectInContext(project, file)
}

internal data class SelectByContextImpl(
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
  val paneId: ProjectViewPaneId,
  val contextDTO: SelectInContextDTO,
  @Transient val context: SelectInContext? = null,
)

@ApiStatus.Internal
@Serializable
data class SelectInContextDTO(
  val fileId: VirtualFileId,
)

@ApiStatus.Internal
fun SelectInContext.serialize(): SelectInContextDTO {
  val fileId = virtualFile.rpcId()
  return SelectInContextDTO(fileId)
}

internal class SelectInProjectViewRequestBuilderImpl(nodePath: ProjectViewNodePath) : SelectInProjectViewRequestBuilder {
  private val nodePath = nodePath as ProjectViewNodePathImpl
  override var requestFocus: Boolean = false
  
  fun buildSelectNodeEvent(): ProjectViewSelectNodeEvent {
    return ProjectViewSelectNodeEvent(
      nodePath = nodePath,
      requestFocus = requestFocus,
    )
  }
}
