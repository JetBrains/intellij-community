// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

sealed interface CallTarget {
    val caller: KtElement
    val call: KaCall
    val partiallyAppliedSymbol: KaPartiallyAppliedSymbol<KaCallableSymbol, KaCallableSignature<KaCallableSymbol>>
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
    override val partiallyAppliedSymbol: KaPartiallyAppliedSymbol<S, C>
    override val symbol: S
}

class VariableCallTarget(
    override val caller: KtElement,
    override val call: KaCall,
    override val partiallyAppliedSymbol: KaPartiallyAppliedVariableSymbol<KaVariableSymbol>
) : TypedCallTarget<KaVariableSymbol, KaVariableSignature<KaVariableSymbol>> {
    override val symbol: KaVariableSymbol
        get() = partiallyAppliedSymbol.symbol
}

class FunctionCallTarget(
    override val caller: KtElement,
    override val call: KaCall,
    override val partiallyAppliedSymbol: KaPartiallyAppliedFunctionSymbol<KaFunctionSymbol>
) : TypedCallTarget<KaFunctionSymbol, KaFunctionSignature<KaFunctionSymbol>> {
    override val symbol: KaFunctionSymbol
        get() = partiallyAppliedSymbol.symbol
}

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

    // TODO: KTIJ-34670 drop this implementation
    @OptIn(KaImplementationDetail::class)
    private object ReadAccess : KaSimpleVariableAccess.Read

    // TODO: KTIJ-34670 drop this implementation
    @OptIn(KaImplementationDetail::class)
    private class SpecialPartiallyAppliedSymbol<out S : KaCallableSymbol, out C : KaCallableSignature<S>>(
        private val backingSignature: C
    ) : KaPartiallyAppliedSymbol<S, C> {
        override val signature: C get() = withValidityAssertion { backingSignature }
        override val dispatchReceiver: KaReceiverValue? get() = withValidityAssertion { null }
        override val extensionReceiver: KaReceiverValue? get() = withValidityAssertion { null }

        @KaExperimentalApi
        override val contextArguments: List<KaReceiverValue> get() = withValidityAssertion { emptyList() }
        override val token: KaLifetimeToken get() = backingSignature.token
    }

    @OptIn(KaExperimentalApi::class)
    private fun handle(element: KtElement, processor: KotlinCallTargetProcessor): Boolean {
        analyze(element) {
            fun handleSpecial(element: KtElement, filter: (KaSymbol) -> Boolean): Boolean {
                val symbols = element.mainReference?.resolveToSymbols() ?: return true
                for (symbol in symbols) {
                    if (!filter(symbol)) {
                        continue
                    }

                    @OptIn(KaImplementationDetail::class)
                    val shouldContinue = with(processor) {
                        when (symbol) {
                            is KaFunctionSymbol -> {
                                val signature = symbol.asSignature()
                                val partiallyAppliedSymbol = SpecialPartiallyAppliedSymbol(signature)

                                // TODO: KTIJ-34670 drop this implementation
                                val call = object : KaSimpleFunctionCall {
                                    override val isImplicitInvoke: Boolean get() = withValidityAssertion { false }
                                    override val argumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>> get() = withValidityAssertion { emptyMap() }
                                    override val partiallyAppliedSymbol: KaPartiallyAppliedSymbol<KaFunctionSymbol, KaFunctionSignature<KaFunctionSymbol>> get() = withValidityAssertion { partiallyAppliedSymbol }
                                    override val typeArgumentsMapping: Map<KaTypeParameterSymbol, KaType> get() = withValidityAssertion { emptyMap() }
                                    override val token: KaLifetimeToken get() = partiallyAppliedSymbol.token
                                }

                                processCallTarget(FunctionCallTarget(element, call, partiallyAppliedSymbol))
                            }

                            is KaVariableSymbol -> {
                                val signature = symbol.asSignature()
                                val partiallyAppliedSymbol = SpecialPartiallyAppliedSymbol(signature)

                                // TODO: KTIJ-34670 drop this implementation
                                val call = object : KaSimpleVariableAccessCall {
                                    override val simpleAccess: KaSimpleVariableAccess.Read get() = withValidityAssertion { ReadAccess }
                                    override val partiallyAppliedSymbol: KaPartiallyAppliedSymbol<KaVariableSymbol, KaVariableSignature<KaVariableSymbol>> get() = withValidityAssertion { partiallyAppliedSymbol }
                                    override val typeArgumentsMapping: Map<KaTypeParameterSymbol, KaType> get() = withValidityAssertion { emptyMap() }
                                    override val token: KaLifetimeToken get() = partiallyAppliedSymbol.token
                                }

                                processCallTarget(VariableCallTarget(element, call, partiallyAppliedSymbol))
                            }

                            else -> true
                        }
                    }
                    if (!shouldContinue) {
                        return false
                    }
                }
                return true
            }

            if (element is KtForExpression) {
                return handleSpecial(element) { it is KaFunctionSymbol }
            }

            if (element is KtDestructuringDeclarationEntry) {
                return handleSpecial(element) { !(it is KaLocalVariableSymbol && it.psi == element) }
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
                is KaDelegatedConstructorCall -> processCallTarget(FunctionCallTarget(element, call, call.partiallyAppliedSymbol))
                is KaSimpleFunctionCall -> processCallTarget(FunctionCallTarget(element, call, call.partiallyAppliedSymbol))
                is KaCompoundVariableAccessCall -> {
                    processCallTarget(VariableCallTarget(element, call, call.variablePartiallyAppliedSymbol))
                    processCallTarget(FunctionCallTarget(element, call, call.compoundOperation.operationPartiallyAppliedSymbol))
                }

                is KaSimpleVariableAccessCall -> {
                    processCallTarget(VariableCallTarget(element, call, call.partiallyAppliedSymbol))
                }

                is KaCompoundArrayAccessCall -> {
                    processCallTarget(FunctionCallTarget(element, call, call.getPartiallyAppliedSymbol))
                    processCallTarget(FunctionCallTarget(element, call, call.setPartiallyAppliedSymbol))
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
