package com.intellij.filePrediction

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

internal sealed class ExternalReferencesResult(val references: Set<VirtualFile>) {
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