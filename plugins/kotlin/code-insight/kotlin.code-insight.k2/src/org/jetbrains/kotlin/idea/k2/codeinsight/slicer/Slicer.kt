// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.slicer.JavaSliceUsage
import com.intellij.slicer.SliceUsage
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.base.psi.hasInlineModifier
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinPropertyFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.processAllExactUsages
import org.jetbrains.kotlin.idea.base.searching.usages.processAllUsages
import org.jetbrains.kotlin.idea.codeInsight.slicer.AbstractKotlinSliceUsage
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpected
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectedDeclarationIfAny
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchOverriders
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addIfNotNull

abstract class Slicer(
    protected val element: KtElement,
    protected val processor: Processor<in SliceUsage>,
    protected val parentUsage: AbstractKotlinSliceUsage
) {
    abstract fun processChildren(forcedExpressionMode: Boolean)

    protected val analysisScope: SearchScope = parentUsage.scope.toSearchScope()
    protected val mode: KotlinSliceAnalysisMode = parentUsage.mode
    protected val project = element.project

    protected fun PsiElement.passToProcessor(mode: KotlinSliceAnalysisMode = this@Slicer.mode) {
        processor.process(KotlinSliceUsage(this, parentUsage, mode, forcedExpressionMode = false))
    }

    protected fun PsiElement.passToProcessorAsValue(mode: KotlinSliceAnalysisMode = this@Slicer.mode) {
        processor.process(KotlinSliceUsage(this, parentUsage, mode, forcedExpressionMode = true))
    }

    protected fun PsiElement.passToProcessorInCallMode(
        callElement: KtElement,
        mode: KotlinSliceAnalysisMode = this@Slicer.mode,
        withOverriders: Boolean = false
    ) {
        val newMode = when (this) {
            is KtNamedFunction -> this.callMode(callElement, mode)

            is KtParameter -> ownerFunction.callMode(callElement, mode)

            is KtTypeReference -> {
                val declaration = parent
                require(declaration is KtCallableDeclaration)
                require(this == declaration.receiverTypeReference)
                declaration.callMode(callElement, mode)
            }

            else -> mode
        }

        if (withOverriders) {
            passDeclarationToProcessorWithOverriders(newMode)
        } else {
            passToProcessor(newMode)
        }
    }

    protected fun PsiElement.passDeclarationToProcessorWithOverriders(mode: KotlinSliceAnalysisMode = this@Slicer.mode) {
        passToProcessor(mode)

        HierarchySearchRequest(this, analysisScope)
            .searchOverriders()
            .forEach { it.namedUnwrappedElement?.passToProcessor(mode) }

        if (this is KtCallableDeclaration && isExpectDeclaration()) {
            this.actualsForExpected().forEach {
                    it.passToProcessor(mode)
                }
        }
    }

    protected open fun processCalls(
        callable: KtCallableDeclaration,
        includeOverriders: Boolean,
        sliceProducer: SliceProducer,
    ) {
        if (callable is KtFunctionLiteral || callable is KtFunction && callable.name == null) {
            callable.passToProcessorAsValue(mode.withBehaviour(LambdaCallsBehaviour(sliceProducer)))
            return
        }

        val options = when (callable) {
            is KtFunction -> {
                KotlinFunctionFindUsagesOptions(project).apply {
                    isSearchForTextOccurrences = false
                    isSkipImportStatements = true
                    searchScope = analysisScope
                }
            }

            is KtProperty -> {
                KotlinPropertyFindUsagesOptions(project).apply {
                    isSearchForTextOccurrences = false
                    isSkipImportStatements = true
                    searchScope = analysisScope
                }
            }

            else -> return
        }

        analyze(callable) {
            val callableSymbol = callable.symbol as? KaCallableSymbol ?: return
            val superDeclarations = if (includeOverriders) {
               KotlinSearchUsagesSupport.getInstance(element.project).findSuperMethodsNoWrapping(element, true).takeUnless { it.isEmpty() } ?: listOf(callable)
            } else {
                mutableListOf<PsiElement>().apply {
                    add(callable)
                    addAll(callableSymbol.allOverriddenSymbols.mapNotNull { it.psi })
                    if (callableSymbol.isActual) {
                        addIfNotNull(callable.expectedDeclarationIfAny())
                    }
                }
            }

            for (superDeclaration in superDeclarations) {
                when (superDeclaration) {
                    is KtDeclaration -> {
                        val usageProcessor: (UsageInfo) -> Unit = processor@{ usageInfo ->
                            val element = usageInfo.element ?: return@processor
                            if (element.parentOfType<PsiComment>() != null) return@processor
                            val sliceUsage = KotlinSliceUsage(element, parentUsage, mode, false)
                            sliceProducer.produceAndProcess(sliceUsage, mode, parentUsage, processor)
                        }
                        if (includeOverriders) {
                            superDeclaration.processAllUsages(options, usageProcessor)
                        } else {
                            superDeclaration.processAllExactUsages(options, usageProcessor)
                        }
                    }

                    is PsiMethod -> {
                        val sliceUsage = JavaSliceUsage.createRootUsage(superDeclaration, parentUsage.params)
                        sliceProducer.produceAndProcess(sliceUsage, mode, parentUsage, processor)
                    }

                    else -> {
                        val sliceUsage = KotlinSliceUsage(superDeclaration, parentUsage, mode, false)
                        sliceProducer.produceAndProcess(sliceUsage, mode, parentUsage, processor)
                    }
                }
            }
        }
    }

    protected enum class AccessKind {
        READ_ONLY, WRITE_ONLY, WRITE_WITH_OPTIONAL_READ, READ_OR_WRITE
    }

    protected fun processVariableAccesses(
        declaration: KtCallableDeclaration,
        scope: SearchScope,
        kind: AccessKind,
        usageProcessor: (UsageInfo) -> Unit
    ) {
        val options = KotlinPropertyFindUsagesOptions(project).apply {
            isReadAccess = kind == AccessKind.READ_ONLY || kind == AccessKind.READ_OR_WRITE
            isWriteAccess =
                kind == AccessKind.WRITE_ONLY || kind == AccessKind.WRITE_WITH_OPTIONAL_READ || kind == AccessKind.READ_OR_WRITE
            isReadWriteAccess = kind == AccessKind.WRITE_WITH_OPTIONAL_READ || kind == AccessKind.READ_OR_WRITE
            isSearchForTextOccurrences = false
            isSkipImportStatements = true
            searchScope = scope
        }

        val allDeclarations = mutableListOf(declaration)
        analyze(declaration) {
            val descriptor = declaration.symbol
            if (descriptor is KaCallableSymbol) {
                if (descriptor.isActual) {
                    allDeclarations.addIfNotNull(declaration.expectedDeclarationIfAny() as? KtCallableDeclaration)
                } else {
                    descriptor.allOverriddenSymbols.mapNotNullTo(allDeclarations) { it.psi as? KtCallableDeclaration }
                }
            }
        }

        allDeclarations.forEach {
            it.processAllExactUsages(options) { usageInfo ->
                if (!shouldIgnoreVariableUsage(usageInfo)) {
                    usageProcessor.invoke(usageInfo)
                }
            }
        }
    }

    // ignore parameter usages in function contract
    private fun shouldIgnoreVariableUsage(usage: UsageInfo): Boolean {
        val element = usage.element ?: return true
        return element.parents.any { el ->
            el is KtCallExpression &&
                    (el.calleeExpression as? KtSimpleNameExpression)?.getReferencedName() == "contract" &&
                    analyze(el) { el.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()?.asString() == "kotlin.contracts.contract" }
        }
    }

    protected fun canProcessParameter(parameter: KtParameter) = !parameter.isVarArg

    protected fun processExtensionReceiverUsages(
        declaration: KtCallableDeclaration,
        body: KtExpression?,
        mode: KotlinSliceAnalysisMode,
    ) {
        if (body == null) return
        //TODO: overriders
        analyze(declaration) {
            val callableSymbol = declaration.symbol as? KaCallableSymbol ?: return
            val extensionReceiver = callableSymbol.receiverParameter ?: return

            //explicit this
            body.forEachDescendantOfType<KtThisExpression> { thisExpression ->
                val receiverDescriptor = thisExpression.instanceReference.mainReference.resolveToSymbol()
                if (receiverDescriptor == extensionReceiver) {
                    thisExpression.passToProcessor(mode)
                }
            }

            //implicit this
            body.forEachDescendantOfType<KtSimpleNameExpression> { simpleNameExpression ->
                val symbol = simpleNameExpression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol ?: return@forEachDescendantOfType
                if ((symbol.extensionReceiver as? KaImplicitReceiverValue)?.symbol == extensionReceiver) {
                    symbol.symbol.receiverParameter?.psi?.passToProcessor(mode)
                }
            }
        }
    }

    companion object {
        protected fun KtDeclaration?.callMode(callElement: KtElement, defaultMode: KotlinSliceAnalysisMode): KotlinSliceAnalysisMode {
            return if (this is KtNamedFunction && hasInlineModifier())
                defaultMode.withInlineFunctionCall(callElement, this)
            else
                defaultMode
        }
    }
}