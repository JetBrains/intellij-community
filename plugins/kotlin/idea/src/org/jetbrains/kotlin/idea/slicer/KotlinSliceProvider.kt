// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.codeInsight.Nullability
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.slicer.*
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.slicer.HackedSliceNullnessAnalyzerBase
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPlainWithEscapes
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.isNullabilityFlexible

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
                val types = when (element) {
                    is KtCallableDeclaration -> listOfNotNull((element.resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType)
                    is KtDeclaration -> emptyList()
                    is KtExpression -> listOfNotNull(element.analyze(BodyResolveMode.PARTIAL).getType(element))
                    else -> emptyList()
                }
                return when {
                    types.isEmpty() -> return Nullability.UNKNOWN
                    types.all { KotlinBuiltIns.isNullableNothing(it) } -> Nullability.NULLABLE
                    types.any { it.isError || TypeUtils.isNullableType(it) || it.isNullabilityFlexible() } -> Nullability.UNKNOWN
                    else -> Nullability.NOT_NULL
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
        val element = atCaret.parentsWithSelf
            .filterIsInstance<KtElement>()
            .firstOrNull(::isSliceElement)
            ?.deparenthesize() ?: return null

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
