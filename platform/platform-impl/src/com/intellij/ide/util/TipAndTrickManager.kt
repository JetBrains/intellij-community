// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TipAndTrickManager {
  /**
   * Shows the dialog with the tips sorted in descending order of usefulness.
   * Should be run from background thread, because sorting of the tips can take some time.
   *
   * If a provided project is null, tip applicability will not be taken into account during sorting.
   * Also Features Trainer lessons promoter will not be shown.
   */
  suspend fun showTipDialog(project: Project?)

  /**
   * Show the dialog with one tip without "Next tip" and "Previous tip" buttons
   */
  suspend fun showTipDialog(project: Project, tip: TipAndTrickBean)

  fun closeTipDialog()

  fun canShowDialogAutomaticallyNow(project: Project): Boolean

  companion object {
    @JvmField
    val DISABLE_TIPS_FOR_PROJECT: Key<Boolean> = Key.create("DISABLE_TIPS_FOR_PROJECT")

    fun getInstance(): TipAndTrickManager {
      return service()
    }
  }
}