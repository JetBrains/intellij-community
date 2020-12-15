package com.intellij.completion.ml.local.models

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElementVisitor

interface LocalModel {
  fun calculateContextFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue>
  fun calculateElementFeatures(element: LookupElement, contextFeatures: ContextFeatures): Map<String, MLFeatureValue>
  fun visitor(): PsiElementVisitor
  fun onStarted()
  fun onFinished()
}