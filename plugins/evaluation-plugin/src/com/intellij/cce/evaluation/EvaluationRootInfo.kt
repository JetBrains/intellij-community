package com.intellij.cce.evaluation

import com.intellij.psi.PsiElement

data class EvaluationRootInfo(val useDefault: Boolean, val offset: Int? = null, val parentPsi: PsiElement? = null)