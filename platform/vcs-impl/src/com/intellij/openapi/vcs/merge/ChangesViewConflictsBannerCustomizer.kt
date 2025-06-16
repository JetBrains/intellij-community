// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
interface ChangesViewConflictsBannerCustomizer {
  companion object {
    @JvmField
    val EP_NAME: ProjectExtensionPointName<ChangesViewConflictsBannerCustomizer> =
      ProjectExtensionPointName<ChangesViewConflictsBannerCustomizer>("com.intellij.vcs.changes.changesViewConflictsBannerCustomizer")
  }

  @get:Nls
  val name: String
  val icon: Icon?
  fun createAction(changesView: ChangesListView): Runnable
  fun isAvailable(changesView: ChangesListView): Boolean = true
}
