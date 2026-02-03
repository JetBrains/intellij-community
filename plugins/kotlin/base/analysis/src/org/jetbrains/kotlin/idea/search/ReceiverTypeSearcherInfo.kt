// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDeclaration

data class ReceiverTypeSearcherInfo(
    val klass: PsiElement?,
    val containsTypeOrDerivedInside: ((KtDeclaration) -> Boolean)
)