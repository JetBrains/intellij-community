// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.slicer.SliceUsage
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.slicer.AbstractKotlinSliceUsage
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.psi.KtElement

data class LambdaParameterInflowBehaviour(val parameterIndex: Int) : KotlinSliceAnalysisMode.Behaviour {
    override fun processUsages(
        element: KtElement,
        parent: AbstractKotlinSliceUsage,
        uniqueProcessor: Processor<in SliceUsage>
    ) {
        InflowSlicer(element, uniqueProcessor, parent).processChildren(parent.forcedExpressionMode)
    }

    override val slicePresentationPrefix: String
        get() = KotlinBundle.message("slicer.text.tracking.lambda.argument")

    override val testPresentationPrefix: String
        get() = "[LAMBDA PARAMETER #$parameterIndex] "
}