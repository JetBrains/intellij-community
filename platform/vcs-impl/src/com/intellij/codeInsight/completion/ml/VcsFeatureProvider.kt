// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameIdentifierOwner

class VcsFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "vcs"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val project = location.project
    val psi = element.psiElement
    val psiFile = psi?.containingFile

    psiFile?.viewProvider?.virtualFile?.let { file ->
      val changeListManager = ChangeListManager.getInstance(project)
      changeListManager.getChange(file)?.let { change ->
        features["file_state"] = MLFeatureValue.categorical(change.type) // NON-NLS
        if (change.type == Change.Type.MODIFICATION && psi is PsiNameIdentifierOwner) {
          val document = PsiDocumentManager.getInstance(project).getCachedDocument(psiFile)
          val range = psi.textRange
          if (document != null && range != null && range.endOffset <= document.textLength) {
            val lineStatusTracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document)
            if (lineStatusTracker != null && lineStatusTracker.isValid()) {
              if (lineStatusTracker.isRangeModified(document.getLineNumber(range.startOffset), document.getLineNumber(range.endOffset))) {
                features["declaration_is_changed"] = MLFeatureValue.binary(true) // NON-NLS
              }
            }
          }
        }
      }
    }
    return features
  }
}