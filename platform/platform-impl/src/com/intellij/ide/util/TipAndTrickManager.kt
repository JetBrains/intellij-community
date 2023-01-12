// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TipAndTrickManager {
  /**
   * Shows the dialog with the tips sorted in descending order of usefulness.
   * Should be run from background thread, because sorting of the tips can take some time.
   */
  @RequiresBackgroundThread
  fun showTipDialog(project: Project)

  /**
   * Show the dialog with one tip without "Next tip" and "Previous tip" buttons
   */
  fun showTipDialog(project: Project, tip: TipAndTrickBean)

  fun closeTipDialog()

  fun canShowDialogAutomaticallyNow(project: Project): Boolean

  companion object {
    val DISABLE_TIPS_FOR_PROJECT = Key.create<Boolean>("DISABLE_TIPS_FOR_PROJECT")

    @JvmStatic
    fun getInstance(): TipAndTrickManager = service()
  }
}