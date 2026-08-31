// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.actions

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class EditorChoice {
  ALL_SELECTED,
  LAST_FOCUSED_ONLY,
}

@ApiStatus.Internal
interface SelectInSplitProjectView {
  companion object {
    fun getInstance(project: Project): SelectInSplitProjectView = project.service()
  }

  fun isSelectOpenedFileEnabled(): Boolean
  fun selectOpenedFile(editorChoice: EditorChoice, invokedManually: Boolean)
  fun selectIn(context: SelectInContext, target: SelectInTarget, requestFocus: Boolean)
}
