// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.project.Project
import com.intellij.vcs.ShelveNamePatch
import com.intellij.vcs.ShelveNameProvider
import kotlinx.coroutines.launch
import java.util.function.Consumer

object ShelveChangesNameSuggester {
  fun suggestBetterName(project: Project, patch: ShelveNamePatch, rename: Consumer<String>) {
    ShelveChangesManager.getInstance(project).coroutineScope.launch {
      ShelveNameProvider.generateShelveName(project, patch)?.let { rename.accept(it) }
    }
  }
}