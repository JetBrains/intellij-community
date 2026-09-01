// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallResolutionAttempt
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundArrayAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaDelegatedConstructorCall
import org.jetbrains.kotlin.analysis.api.resolution.KaForLoopCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleOrMultiCall
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.successful
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
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
import org.jetbrains.kotlin.resolution.KtResolvableCall

sealed interface CallTarget {
    val caller: KtElement
    val call: KaCall?
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
    override val call: KaVariableAccessCall
) : TypedCallTarget<KaVariableSymbol, KaVariableSignature<KaVariableSymbol>> {
    override val symbol: KaVariableSymbol
        get() = call.symbol
}

class FunctionCallTarget(
    override val caller: KtElement,
    override val call: KaFunctionCall<*>
) : TypedCallTarget<KaFunctionSymbol, KaFunctionSignature<KaFunctionSymbol>> {
    override val symbol: KaFunctionSymbol
        get() = call.symbol
}

interface KotlinCallTargetProcessor {
    /**
     * Processes a successfully resolved [CallTarget].
     * If false is returned from this function, no further elements will be processed.
     */
    context(session: KaSession)
    fun processCallTarget(target: CallTarget): Boolean

    /**
     * Processes a call that resolved as an error.
     * If false is returned from this function, no further elements will be processed.
     */
    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    fun processUnresolvedCall(element: KtElement, callInfo: KaCallResolutionAttempt?): Boolean
}

@OptIn(KaExperimentalApi::class)
private fun (context(KaSession) (CallTarget) -> Unit).toCallTargetProcessor(): KotlinCallTargetProcessor {
    val processor = this
    return object : KotlinCallTargetProcessor {
        context(session: KaSession)
        override fun processCallTarget(target: CallTarget): Boolean {
            processor(target)
            return true
        }

        context(session: KaSession)
        override fun processUnresolvedCall(element: KtElement, callInfo: KaCallResolutionAttempt?): Boolean {
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

    fun process(element: PsiElement, processor: context(KaSession) (CallTarget) -> Unit) {
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
            val resolutionAttempt: KaCallResolutionAttempt? = (element as? KtResolvableCall)?.tryResolveCall()
            val call: KaSimpleOrMultiCall? = resolutionAttempt?.successful

            return with(processor) {
                if (call != null) {
                    processResolvedCall(processor, element, call)
                } else {
                    processUnresolvedCall(element, resolutionAttempt)
                }
            }
        }
    }


    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    fun processResolvedCall(targetProcessor: KotlinCallTargetProcessor, element: KtElement, call: KaSimpleOrMultiCall): Boolean {
        with(targetProcessor) {
            return when (call) {
                is KaDelegatedConstructorCall -> processCallTarget(FunctionCallTarget(element, call))
                is KaFunctionCall<*> -> processCallTarget(FunctionCallTarget(element, call))
                is KaCompoundVariableAccessCall -> {
                    processCallTarget(VariableCallTarget(element, call.variableCall))
                    processCallTarget(FunctionCallTarget(element, call.operationCall))
                }

                is KaVariableAccessCall -> {
                    processCallTarget(VariableCallTarget(element, call))
                }

                is KaCompoundArrayAccessCall -> {
                    processCallTarget(FunctionCallTarget(element, call.getterCall))
                    processCallTarget(FunctionCallTarget(element, call.setterCall))
                }

                is KaForLoopCall -> {
                    processCallTarget(FunctionCallTarget(element, call.nextCall))
                    processCallTarget(FunctionCallTarget(element, call.iteratorCall))
                    processCallTarget(FunctionCallTarget(element, call.hasNextCall))
                }

                else -> true
            }
        }
    }
}

fun KotlinCallProcessor.process(elements: Collection<PsiElement>, processor: context(KaSession) (CallTarget) -> Unit) {
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
