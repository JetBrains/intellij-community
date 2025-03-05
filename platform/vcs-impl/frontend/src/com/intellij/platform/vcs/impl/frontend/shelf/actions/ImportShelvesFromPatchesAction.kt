// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfService
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ImportShelvesFromPatchesAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ShelfService.getInstance(project).importPatches()
  }
}