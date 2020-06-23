// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.references

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

sealed class ExternalReferencesResult(val references: Set<VirtualFile>) {
  class SucceedReferenceComputation(refs: Set<VirtualFile>) : ExternalReferencesResult(refs) {
    override fun contains(file: VirtualFile): ThreeState {
      return if (references.contains(file)) ThreeState.YES else ThreeState.NO
    }
  }

  class FailedReferenceComputation : ExternalReferencesResult(emptySet()) {
    override fun contains(file: VirtualFile): ThreeState = ThreeState.UNSURE
  }

  abstract fun contains(file: VirtualFile): ThreeState

  companion object {
    val NO_REFERENCES = succeed(emptySet())
    val FAILED_COMPUTATION = FailedReferenceComputation()

    fun succeed(refs: Set<PsiFile>): ExternalReferencesResult =
      SucceedReferenceComputation(refs.mapNotNull { file -> file.virtualFile }.toSet())
  }
}