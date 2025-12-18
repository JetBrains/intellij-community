// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ChangeListClassifierProvider {
  fun providesCommitMessage(project: Project, changeList: LocalChangeList): Boolean?
  companion object {
    internal val EXTENSION_POINT_NAME: ExtensionPointName<ChangeListClassifierProvider> =
      ExtensionPointName("com.intellij.vcs.changeListClassifierProvider")

    fun providesCommitMessage(project: Project, changeList: LocalChangeList): Boolean =
      EXTENSION_POINT_NAME.extensionList.firstNotNullOfOrNull { it.providesCommitMessage(project, changeList) } ?: true
  }
}