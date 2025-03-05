// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.slicer

import com.intellij.psi.PsiElement
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceUsage

abstract class AbstractKotlinSliceUsage : SliceUsage {
    val mode: KotlinSliceAnalysisMode
    val forcedExpressionMode: Boolean

    constructor(element: PsiElement, parent: SliceUsage, mode: KotlinSliceAnalysisMode, forcedExpressionMode: Boolean) : super(element, parent) {
        this.mode = mode
        this.forcedExpressionMode = forcedExpressionMode
    }

    constructor(element: PsiElement, params: SliceAnalysisParams) : super(element, params) {
        this.mode = KotlinSliceAnalysisMode.Default
        this.forcedExpressionMode = false
    }

    open val isDereference: Boolean
        get() = false
}