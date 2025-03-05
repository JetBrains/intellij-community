// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Allows modifying the commit options panel by adding new options outside the pre-defined groups.
 */
@ApiStatus.Experimental
interface CommitOptionsDialogExtension {
  companion object {
    val EP_NAME: ExtensionPointName<CommitOptionsDialogExtension> =
      ExtensionPointName.create("com.intellij.openapi.vcs.ui.commitOptionsDialogExtension")
  }

  /**
   * To add a new group, consider creating [RefreshableOnComponent] using
   * [com.intellij.ui.dsl.builder.BuilderKt.panel] and [com.intellij.ui.dsl.builder.Panel.group]
   */
  fun getOptions(project: Project): Collection<RefreshableOnComponent>
}