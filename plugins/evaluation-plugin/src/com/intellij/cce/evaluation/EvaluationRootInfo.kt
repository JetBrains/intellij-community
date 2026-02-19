// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.psi.PsiElement

data class EvaluationRootInfo(val useDefault: Boolean, val offset: Int? = null, val parentPsi: PsiElement? = null)