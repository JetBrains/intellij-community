package com.intellij.completion.ml.local.models.api

import com.intellij.psi.PsiElementVisitor

interface LocalModel {
  fun fileVisitor(): PsiElementVisitor
  fun onStarted()
  fun onFinished()
}