// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

sealed interface CallTarget {
    val caller: KtElement
    val call: KtCall
    val partiallyAppliedSymbol: KtPartiallyAppliedSymbol<KtCallableSymbol, KtCallableSignature<KtCallableSymbol>>
    val symbol: KtCallableSymbol

    val anchor: PsiElement
        get() = when (val element = caller) {
            is KtUnaryExpression -> element.operationReference
            is KtBinaryExpression -> element.operationReference
            else -> element
        }

    val anchorLeaf: PsiElement
        get() = when (val element = anchor) {
            is LeafPsiElement -> element
            else -> generateSequence(element) { it.firstChild }.last()
        }
}

sealed interface TypedCallTarget<out S : KtCallableSymbol, out C : KtCallableSignature<S>> : CallTarget {
    override val partiallyAppliedSymbol: KtPartiallyAppliedSymbol<S, C>
    override val symbol: S
}

class VariableCallTarget(
    override val caller: KtElement,
    override val call: KtCall,
    override val partiallyAppliedSymbol: KtPartiallyAppliedVariableSymbol<KtVariableLikeSymbol>
) : TypedCallTarget<KtVariableLikeSymbol, KtVariableLikeSignature<KtVariableLikeSymbol>> {
    override val symbol: KtVariableLikeSymbol
        get() = partiallyAppliedSymbol.symbol
}

class FunctionCallTarget(
    override val caller: KtElement,
    override val call: KtCall,
    override val partiallyAppliedSymbol: KtPartiallyAppliedFunctionSymbol<KtFunctionLikeSymbol>
) : TypedCallTarget<KtFunctionLikeSymbol, KtFunctionLikeSignature<KtFunctionLikeSymbol>> {
    override val symbol: KtFunctionLikeSymbol
        get() = partiallyAppliedSymbol.symbol
}

object KotlinCallProcessor {
    private val NAME_REFERENCE_IGNORED_PARENTS = arrayOf(
        KtUserType::class.java,
        KtImportDirective::class.java,
        KtPackageDirective::class.java,
        KtValueArgumentName::class.java,
        PsiComment::class.java,
        KDoc::class.java
    )

    fun process(element: PsiElement, processor: KtAnalysisSession.(CallTarget) -> Unit) {
        val containingFile = element.containingFile
        if (containingFile is KtCodeFragment) {
            return
        }

        when (element) {
            is KtArrayAccessExpression -> handle(element, processor)
            is KtCallExpression -> handle(element, processor)
            is KtUnaryExpression -> handle(element, processor)
            is KtBinaryExpression -> handle(element, processor)
            is KtForExpression -> handle(element, processor)
            is KtDestructuringDeclaration -> handle(element, processor)
            is KtDestructuringDeclarationEntry -> handle(element, processor)
            is KtNameReferenceExpression -> {
                if (shouldHandleNameReference(element)) {
                    handle(element, processor)
                }
            }
        }
    }

    private fun shouldHandleNameReference(element: KtNameReferenceExpression): Boolean {
        val qualified = qualifyNameExpression(element) ?: return false

        val isDuplicatingCall = when (val parent = qualified.parent) {
            is KtCallableReferenceExpression -> qualified == parent.callableReference
            is KtCallExpression -> qualified == parent.calleeExpression
            is KtUnaryExpression -> qualified == parent.baseExpression
            is KtBinaryExpression -> parent.operationToken in KtTokens.ALL_ASSIGNMENTS && qualified == parent.left
            else -> false
        }

        return !isDuplicatingCall && PsiTreeUtil.getParentOfType(qualified, *NAME_REFERENCE_IGNORED_PARENTS) == null
    }

    private fun qualifyNameExpression(element: KtNameReferenceExpression): KtExpression? {
        var current: KtExpression = element

        while (true) {
            val parent = current.parent
            if (parent !is KtQualifiedExpression || parent.selectorExpression != current) {
                break
            }

            current = KtPsiUtil.deparenthesize(parent) ?: return null
        }

        return current
    }

    private fun handle(element: KtElement, processor: KtAnalysisSession.(CallTarget) -> Unit) {
        analyze(element) {
            fun handleSpecial(element: KtElement, filter: (KtSymbol) -> Boolean) {
                val symbols = element.mainReference?.resolveToSymbols() ?: return
                for (symbol in symbols) {
                    if (!filter(symbol)) {
                        continue
                    }

                    if (symbol is KtFunctionLikeSymbol) {
                        val signature = symbol.asSignature()
                        val partiallyAppliedSymbol = KtPartiallyAppliedFunctionSymbol(signature, null, null)
                        val call = KtSimpleFunctionCall(partiallyAppliedSymbol, linkedMapOf(), mapOf(), _isImplicitInvoke = false)
                        processor(FunctionCallTarget(element, call, partiallyAppliedSymbol))
                    } else if (symbol is KtVariableLikeSymbol) {
                        val signature = symbol.asSignature()
                        val partiallyAppliedSymbol = KtPartiallyAppliedVariableSymbol(signature, null, null)
                        val call = KtSimpleVariableAccessCall(partiallyAppliedSymbol, linkedMapOf(), KtSimpleVariableAccess.Read)
                        processor(VariableCallTarget(element, call, partiallyAppliedSymbol))
                    }
                }
            }

            if (element is KtForExpression) {
                handleSpecial(element) { it is KtFunctionLikeSymbol }
                return
            }

            if (element is KtDestructuringDeclarationEntry) {
                handleSpecial(element) { !(it is KtLocalVariableSymbol && it.psi == element) }
                return
            }

            val call = element.resolveCall()?.successfulCallOrNull<KtCall>()

            if (call != null) {
                when (call) {
                    is KtDelegatedConstructorCall -> processor(FunctionCallTarget(element, call, call.partiallyAppliedSymbol))
                    is KtSimpleFunctionCall -> processor(FunctionCallTarget(element, call, call.partiallyAppliedSymbol))
                    is KtCompoundVariableAccessCall -> {
                        processor(VariableCallTarget(element, call, call.partiallyAppliedSymbol))
                        processor(FunctionCallTarget(element, call, call.compoundAccess.operationPartiallyAppliedSymbol))
                    }
                    is KtSimpleVariableAccessCall -> {
                        processor(VariableCallTarget(element, call, call.partiallyAppliedSymbol))
                    }
                    is KtCompoundArrayAccessCall -> {
                        processor(FunctionCallTarget(element, call, call.getPartiallyAppliedSymbol))
                        processor(FunctionCallTarget(element, call, call.setPartiallyAppliedSymbol))
                    }
                    else -> {}
                }
            }
        }
    }
}

fun KotlinCallProcessor.process(elements: Collection<PsiElement>, processor: KtAnalysisSession.(CallTarget) -> Unit) {
    for (element in elements) {
        ProgressManager.checkCanceled()
        process(element, processor)
    }
}