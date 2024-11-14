// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.project.Project
import com.intellij.vcs.ShelveTitlePatch
import com.intellij.vcs.ShelveTitleProvider
import kotlinx.coroutines.launch
import java.util.function.Consumer

object ShelveChangesNameSuggester {
  fun suggestBetterName(project: Project, patch: ShelveTitlePatch, rename: Consumer<String>) {
    ShelveChangesManager.getInstance(project).coroutineScope.launch {
      rename.accept(ShelveTitleProvider.EP_NAME.getIterable().firstNotNullOf { it?.suggestTitle(project, patch) })
    }
  }
}