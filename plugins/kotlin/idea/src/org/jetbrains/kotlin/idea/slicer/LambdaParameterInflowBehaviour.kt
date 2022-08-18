// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.slicer.SliceUsage
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtElement

data class LambdaParameterInflowBehaviour(val parameterIndex: Int) : KotlinSliceAnalysisMode.Behaviour {
    override fun processUsages(
        element: KtElement,
        parent: KotlinSliceUsage,
        uniqueProcessor: Processor<in SliceUsage>
    ) {
        InflowSlicer(element, uniqueProcessor, parent).processChildren(parent.forcedExpressionMode)
    }

    override val slicePresentationPrefix: String
        get() = KotlinBundle.message("slicer.text.tracking.lambda.argument")

    override val testPresentationPrefix: String
        get() = "[LAMBDA PARAMETER #$parameterIndex] "
}