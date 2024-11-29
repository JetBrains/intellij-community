// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.slicer.SliceUsage
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.slicer.AbstractKotlinSliceUsage
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement

data class LambdaCallsBehaviour(private val sliceProducer: SliceProducer) : KotlinSliceAnalysisMode.Behaviour {
    override fun processUsages(element: KtElement, parent: AbstractKotlinSliceUsage, uniqueProcessor: Processor<in SliceUsage>) {
        val processor = object : Processor<SliceUsage> {
            override fun process(sliceUsage: SliceUsage): Boolean {
                if (sliceUsage is KotlinSliceUsage && sliceUsage.mode.currentBehaviour === this@LambdaCallsBehaviour) {
                    val sliceElement = sliceUsage.element ?: return true
                    if (sliceElement is KtElement) {
                        analyze(sliceElement) {
                            val targetElement =
                                (sliceElement.parent as? KtCallExpression)?.takeIf { it.calleeExpression == sliceElement } ?: sliceElement
                            val resolvedCall = targetElement.resolveToCall()?.singleFunctionCallOrNull() as? KaSimpleFunctionCall
                            if (resolvedCall != null &&
                                (resolvedCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol)?.isBuiltinFunctionInvoke == true) {
                                val originalMode = sliceUsage.mode.dropBehaviour()
                                val newSliceUsage = KotlinSliceUsage(targetElement, parent, originalMode, true)
                                return sliceProducer.produceAndProcess(newSliceUsage, originalMode, parent, uniqueProcessor)
                            }
                        }
                    }
                }
                return uniqueProcessor.process(sliceUsage)
            }
        }
        OutflowSlicer(element, processor, parent).processChildren(parent.forcedExpressionMode)
    }

    override val slicePresentationPrefix: String
        get() = KotlinBundle.message("slicer.text.tracking.lambda.calls")

    override val testPresentationPrefix: String
        get() = buildString {
            append("[LAMBDA CALLS")
            sliceProducer.testPresentation?.let {
                append(" ")
                append(it)
            }
            append("] ")
        }
}
