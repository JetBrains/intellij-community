// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

/**
 * A cleanup of a ModPlan refactoring after its edits, such as the removal of the imports that are unused: the counterpart of
 * [com.intellij.refactoring.RefactoringHelper]. It runs in the command of the refactoring, so the preview, the files of the
 * execution and the undo include it.
 */
interface ModPlanRefactoringListener {
  /**
   * @param copy the writable copy of a file that the edits changed, after all of them; it is not a physical file, so nothing may ask the
   *             user or show UI
   */
  fun onRefactoringDone(copy: PsiFile)

  companion object {
    private val EP_NAME: ExtensionPointName<ModPlanRefactoringListener> = ExtensionPointName("com.intellij.refactoring.modPlanListener")

    /** Runs every registered listener on the writable copy of a file that the edits changed, see [onRefactoringDone]. */
    fun runAfterEdits(copy: PsiFile) {
      EP_NAME.forEachExtensionSafe { it.onRefactoringDone(copy) }
    }
  }
}
