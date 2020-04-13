// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.actions.FormatChangedTextUtil
import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.*

class VcsFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "vcs"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val psi = element.psiElement
    val psiFile = psi?.containingFile
    val file = psiFile?.viewProvider?.virtualFile
    if (file != null) {
      val changeListManager = ChangeListManager.getInstance(location.project)
      val change = changeListManager.getChange(file)
      if (change != null)
        features["file_state"] = MLFeatureValue.categorical(change.type)

      if (change != null && change.type == Change.Type.MODIFICATION && psi is PsiNameIdentifierOwner) {
        val changedRanges = FormatChangedTextUtil.getInstance().getChangedTextRanges(location.project, psiFile)
        if (changedRanges.any { psi.textRange.intersects(it) })
          features["declaration_is_changed"] = MLFeatureValue.binary(true)
      }
    }
    return features
  }
}