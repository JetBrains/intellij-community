// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.projectView.impl.ProjectViewTransferableFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
interface ProjectViewPaneDnDHandler {
  suspend fun performInternalDnD(sourceIDs: List<Long>, targetID: Long, options: ProjectViewDnDOptions)

  suspend fun receiveExternalDrop(files: List<ProjectViewExternalFile>, targetID: Long, options: ProjectViewDnDOptions) {
  }

  suspend fun sendDropToClient(sourceIDs: List<Long>) {
  }
}

@ApiStatus.Experimental
sealed interface ProjectViewExternalFile {
  sealed interface Local : ProjectViewExternalFile {
    val path: Path
  }

  sealed interface FromClient : ProjectViewExternalFile {
    val file: ProjectViewTransferableFile
  }
}

@ApiStatus.Internal
class ProjectViewLocalExternalFile(override val path: Path) : ProjectViewExternalFile.Local {
  override fun toString(): String = "Local($path)"
}

@ApiStatus.Internal
class ProjectViewClientExternalFile(override val file: ProjectViewTransferableFile) : ProjectViewExternalFile.FromClient {
  override fun toString(): String = "FromClient($file)"
}

@ApiStatus.Experimental
interface ProjectViewDnDOptions {
  val action: DnDAction
}

@ApiStatus.Internal
data class ProjectViewDnDOptionsImpl(
  override val action: DnDAction
) : ProjectViewDnDOptions
