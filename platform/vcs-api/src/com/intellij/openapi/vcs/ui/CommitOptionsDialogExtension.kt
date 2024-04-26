// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CommitOptionsDialogExtension {
  companion object {
    val EP_NAME: ExtensionPointName<CommitOptionsDialogExtension> =
      ExtensionPointName.create("com.intellij.openapi.vcs.ui.commitOptionsDialogExtension")
  }

  fun getOptions(project: Project): Collection<RefreshableOnComponent>
}