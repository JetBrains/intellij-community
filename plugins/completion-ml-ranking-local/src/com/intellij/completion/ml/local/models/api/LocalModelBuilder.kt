package com.intellij.completion.ml.local.models.api

import com.intellij.psi.PsiElementVisitor

interface LocalModelBuilder {
  fun visitor(): PsiElementVisitor
  fun onStarted()
  fun onFinished()
}