// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.psi.PsiCall
import com.intellij.slicer.SliceUsage
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtTypeReference

object ReceiverSliceProducer : SliceProducer {
    override fun produce(usage: UsageInfo, mode: KotlinSliceAnalysisMode, parent: SliceUsage): Collection<SliceUsage> {
        val refElement = usage.element ?: return emptyList()
        when (refElement) {
            is KtExpression -> {
                analyze(refElement) {
                    val resolvedCall = refElement.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>() ?: return emptyList()
                    when (val receiver = resolvedCall.partiallyAppliedSymbol.extensionReceiver) {
                        is KaExplicitReceiverValue -> {
                            return listOf(KotlinSliceUsage(receiver.expression, parent, mode, forcedExpressionMode = true))
                        }

                        is KaImplicitReceiverValue -> {
                            val callableSymbol = receiver.symbol as? KaCallableSymbol ?: return emptyList()
                            when (val declaration = callableSymbol.psi) {
                                is KtFunctionLiteral -> {
                                    val newMode = mode.withBehaviour(LambdaCallsBehaviour(ReceiverSliceProducer))
                                    return listOf(KotlinSliceUsage(declaration, parent, newMode, forcedExpressionMode = true))
                                }

                                is KtTypeReference -> {
                                    return listOf(KotlinSliceUsage(declaration, parent, mode, false))
                                }

                                else -> return emptyList()
                            }
                        }

                        else -> return emptyList()
                    }
                }
            }

            else -> {
                val argument = (refElement.parent as? PsiCall)?.argumentList?.expressions?.getOrNull(0) ?: return emptyList()
                return listOf(KotlinSliceUsage(argument, parent, mode, false))
            }
        }
    }

    override val testPresentation: String
        get() = "RECEIVER"

    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0
}