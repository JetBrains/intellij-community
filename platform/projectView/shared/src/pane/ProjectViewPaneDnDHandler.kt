// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.dnd.DnDAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewPaneDnDHandler {
  suspend fun performInternalDnD(sourceIDs: List<Long>, targetID: Long, options: ProjectViewDnDOptions)
}

@ApiStatus.Experimental
interface ProjectViewDnDOptions {
  val action: DnDAction
}

@ApiStatus.Internal
data class ProjectViewDnDOptionsImpl(
  override val action: DnDAction
) : ProjectViewDnDOptions
