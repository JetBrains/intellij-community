// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.codeInsight.Nullability
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.slicer.*
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.codeInsight.slicer.HackedSliceNullnessAnalyzerBase
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPlainWithEscapes
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

private val LEAF_ELEMENT_EQUALITY = object : SliceLeafEquality() {
    override fun substituteElement(element: PsiElement) = (element as? KtReference)?.resolve() ?: element
}

class KotlinSliceProvider : SliceLanguageSupportProvider, SliceUsageTransformer {
    class KotlinGroupByNullnessAction(treeBuilder: SliceTreeBuilder) : GroupByNullnessActionBase(treeBuilder) {
        override fun isAvailable() = true
    }

    val leafAnalyzer by lazy { SliceLeafAnalyzer(LEAF_ELEMENT_EQUALITY, this) }

    val nullnessAnalyzer: HackedSliceNullnessAnalyzerBase by lazy {
        object : HackedSliceNullnessAnalyzerBase(LEAF_ELEMENT_EQUALITY, this) {
            override fun checkNullability(element: PsiElement?): Nullability {
                if (element !is KtElement) return Nullability.UNKNOWN
                analyze(element) {
                    val types = when (element) {
                        is KtCallableDeclaration -> listOfNotNull(element.returnType)
                        is KtDeclaration -> emptyList()
                        is KtExpression -> listOfNotNull(element.expressionType)
                        else -> emptyList()
                    }
                    return when {
                        types.isEmpty() -> return Nullability.UNKNOWN
                        types.all { it.isNothingType && it.isMarkedNullable } -> Nullability.NULLABLE
                        types.any { it is KaErrorType || it.canBeNull ||
                                (it as? KaFlexibleType)?.let { flexibleType -> flexibleType.upperBound.isMarkedNullable != flexibleType.lowerBound.isMarkedNullable } == true } -> Nullability.UNKNOWN
                        else -> Nullability.NOT_NULL
                    }
                }
            }
        }
    }

    override fun createRootUsage(element: PsiElement, params: SliceAnalysisParams) = KotlinSliceUsage(element, params)

    override fun transform(usage: SliceUsage): Collection<SliceUsage>? {
        if (usage is KotlinSliceUsage) return null
        return listOf(KotlinSliceUsage(usage.element ?: return null, usage.parent, KotlinSliceAnalysisMode.Default, false))
    }

    override fun getExpressionAtCaret(atCaret: PsiElement, dataFlowToThis: Boolean): KtElement? {
        val ktElement = atCaret.parentsWithSelf
            .filterIsInstance<KtElement>()
            .firstOrNull(::isSliceElement) ?: return null

        val element = (ktElement as? KtExpression)?.safeDeparenthesize() ?: ktElement

        if (dataFlowToThis) {
            if (element is KtConstantExpression) return null
            if (element is KtStringTemplateExpression && element.isPlainWithEscapes()) return null
            if (element is KtClassLiteralExpression) return null
            if (element is KtCallableReferenceExpression) return null
        }

        return element
    }

    private fun isSliceElement(element: KtElement): Boolean {
        return when {
            element is KtProperty -> true
            element is KtParameter -> true
            element is KtDeclarationWithBody -> true
            element is KtClass && !element.hasExplicitPrimaryConstructor() -> true
            element is KtExpression && element !is KtDeclaration && element.parentOfType<KtTypeReference>() == null -> true
            element is KtTypeReference && element == (element.parent as? KtCallableDeclaration)?.receiverTypeReference -> true
            else -> false
        }
    }

    override fun getElementForDescription(element: PsiElement): PsiElement {
        return (element as? KtSimpleNameExpression)?.mainReference?.resolve() ?: element
    }

    override fun getRenderer() = KotlinSliceUsageCellRenderer

    override fun startAnalyzeLeafValues(structure: AbstractTreeStructure, finalRunnable: Runnable) {
        leafAnalyzer.startAnalyzeValues(structure, finalRunnable)
    }

    override fun startAnalyzeNullness(structure: AbstractTreeStructure, finalRunnable: Runnable) {
        nullnessAnalyzer.startAnalyzeNullness(structure, finalRunnable)
    }

    override fun registerExtraPanelActions(group: DefaultActionGroup, builder: SliceTreeBuilder) {
        if (builder.dataFlowToThis) {
            group.add(GroupByLeavesAction(builder))
            group.add(KotlinGroupByNullnessAction(builder))
        }
    }
}
