// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.openapi.project.Project

interface SuggestedRefactoringProvider {
  /**
   * Resets state of accumulated signature changes used for suggesting refactoring.
   */
  fun reset()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SuggestedRefactoringProvider = project.getService(SuggestedRefactoringProvider::class.java)
  }
}