// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundArrayAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaDelegatedConstructorCall
import org.jetbrains.kotlin.analysis.api.resolution.KaPartiallyAppliedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaPartiallyAppliedSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaPartiallyAppliedVariableSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgumentName

sealed interface CallTarget {
    val caller: KtElement
    val partiallyAppliedSymbol: KaPartiallyAppliedSymbol<KaCallableSymbol, KaCallableSignature<KaCallableSymbol>>?
    val symbol: KaCallableSymbol

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

sealed interface TypedCallTarget<out S : KaCallableSymbol, out C : KaCallableSignature<S>> : CallTarget {
    override val symbol: S
}

class VariableCallTarget(
    override val caller: KtElement,
    override val partiallyAppliedSymbol: KaPartiallyAppliedVariableSymbol<KaVariableSymbol>
) : TypedCallTarget<KaVariableSymbol, KaVariableSignature<KaVariableSymbol>> {
    override val symbol: KaVariableSymbol
        get() = partiallyAppliedSymbol.symbol
}

class FunctionCallTarget(
    override val caller: KtElement,
    override val partiallyAppliedSymbol: KaPartiallyAppliedFunctionSymbol<KaFunctionSymbol>
) : TypedCallTarget<KaFunctionSymbol, KaFunctionSignature<KaFunctionSymbol>> {
    override val symbol: KaFunctionSymbol
        get() = partiallyAppliedSymbol.symbol
}

class DesugaredFunctionCallTarget(
    override val caller: KtElement,
    override val symbol: KaFunctionSymbol,
    override val partiallyAppliedSymbol: KaPartiallyAppliedVariableSymbol<KaVariableSymbol>? = null,
) : TypedCallTarget<KaFunctionSymbol, KaFunctionSignature<KaFunctionSymbol>>

interface KotlinCallTargetProcessor {
    /**
     * Processes a successfully resolved [CallTarget].
     * If false is returned from this function, no further elements will be processed.
     */
    fun KaSession.processCallTarget(target: CallTarget): Boolean

    /**
     * Processes a call that resolved as an error.
     * If false is returned from this function, no further elements will be processed.
     */
    fun KaSession.processUnresolvedCall(element: KtElement, callInfo: KaCallInfo?): Boolean
}

private fun (KaSession.(CallTarget) -> Unit).toCallTargetProcessor(): KotlinCallTargetProcessor {
    val processor = this
    return object : KotlinCallTargetProcessor {
        override fun KaSession.processCallTarget(target: CallTarget): Boolean {
            processor(target)
            return true
        }

        override fun KaSession.processUnresolvedCall(element: KtElement, callInfo: KaCallInfo?): Boolean {
            return true
        }
    }
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

    fun process(element: PsiElement, processor: KaSession.(CallTarget) -> Unit) {
        process(element, processor.toCallTargetProcessor())
    }

    fun process(element: PsiElement, processor: KotlinCallTargetProcessor): Boolean {
        return when (element) {
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
                } else {
                    true
                }
            }

            else -> true
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

    @OptIn(KaExperimentalApi::class)
    private fun handle(element: KtElement, processor: KotlinCallTargetProcessor): Boolean {
        analyze(element) {
            fun handleSpecial(element: KtElement): Boolean {
                val symbols = element.mainReference?.resolveToSymbols() ?: return true
                for (symbol in symbols) {
                    if (symbol !is KaFunctionSymbol) continue
                    with(processor) {
                        if (!processCallTarget(DesugaredFunctionCallTarget(element, symbol))) return false
                    }
                }
                return true
            }

            if (element is KtForExpression) {
                return handleSpecial(element)
            }

            if (element is KtDestructuringDeclarationEntry) {
                return handleSpecial(element)
            }

            val callInfo = element.resolveToCall()
            val call = callInfo?.successfulCallOrNull<KaCall>()

            return with(processor) {
                if (call != null) {
                    return processResolvedCall(processor, element, call)
                } else {
                    processUnresolvedCall(element, callInfo)
                }
            }
        }
    }


    fun KaSession.processResolvedCall(targetProcessor: KotlinCallTargetProcessor, element: KtElement, call: KaCall): Boolean {
        with(targetProcessor) {
            return when (call) {
                is KaDelegatedConstructorCall -> processCallTarget(FunctionCallTarget(element, call.partiallyAppliedSymbol))
                is KaSimpleFunctionCall -> processCallTarget(FunctionCallTarget(element, call.partiallyAppliedSymbol))
                is KaCompoundVariableAccessCall -> {
                    processCallTarget(VariableCallTarget(element, call.variablePartiallyAppliedSymbol))
                    processCallTarget(FunctionCallTarget(element, call.compoundOperation.operationPartiallyAppliedSymbol))
                }

                is KaSimpleVariableAccessCall -> {
                    processCallTarget(VariableCallTarget(element, call.partiallyAppliedSymbol))
                }

                is KaCompoundArrayAccessCall -> {
                    processCallTarget(FunctionCallTarget(element, call.getPartiallyAppliedSymbol))
                    processCallTarget(FunctionCallTarget(element, call.setPartiallyAppliedSymbol))
                }

                else -> true
            }
        }
    }
}

fun KotlinCallProcessor.process(elements: Collection<PsiElement>, processor: KaSession.(CallTarget) -> Unit) {
    process(elements, processor.toCallTargetProcessor())
}

fun KotlinCallProcessor.process(elements: Collection<PsiElement>, processor: KotlinCallTargetProcessor) {
    process(elements.asSequence(), processor)
}

fun KotlinCallProcessor.process(elements: Sequence<PsiElement>, processor: KotlinCallTargetProcessor) {
    for (element in elements) {
        ProgressManager.checkCanceled()
        if (!process(element, processor)) {
            return
        }
    }
}
