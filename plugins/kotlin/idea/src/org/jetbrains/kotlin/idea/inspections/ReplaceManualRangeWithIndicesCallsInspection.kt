// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.collections.isMap
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.idea.intentions.receiverTypeIfSelectorIsSizeOrLength
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Tests:
 * [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.ReplaceManualRangeWithIndicesCalls]
 */
class ReplaceManualRangeWithIndicesCallsInspection : AbstractRangeInspection() {
    override fun visitRange(range: KtExpression, context: Lazy<BindingContext>, type: RangeKtExpressionType, holder: ProblemsHolder) {
        val (left, right) = range.getArguments() ?: return
        if (left == null) return
        if (right == null) return

        if (left.toIntConstant() != 0) return
        val sizeOrLengthCall = right.sizeOrLengthCall(type) ?: return
        val collection = sizeOrLengthCall.safeAs<KtQualifiedExpression>()?.receiverExpression
        if (collection != null && collection !is KtSimpleNameExpression && collection !is KtThisExpression) return

        range.parents.match(KtContainerNode::class, last = KtForExpression::class)
            ?.let { it.loopParameter?.originalElement ?: return }
            ?.let { paramElement ->
                val usageElement = ReferencesSearch.search(paramElement).singleOrNull()?.element
                val arrayAccess =
                    usageElement?.parents?.match(KtContainerNode::class, last = KtArrayAccessExpression::class)
                if (arrayAccess != null &&
                    arrayAccess.indexExpressions.singleOrNull() == usageElement &&
                    (arrayAccess.arrayExpression as? KtSimpleNameExpression)?.mainReference?.resolve() == collection?.mainReference?.resolve()
                ) {
                    val arrayAccessParent = arrayAccess.parent
                    if (arrayAccessParent !is KtBinaryExpression ||
                        arrayAccessParent.left != arrayAccess ||
                        arrayAccessParent.operationToken !in KtTokens.ALL_ASSIGNMENTS
                    ) {
                        holder.registerProblem(
                            range,
                            KotlinBundle.message("for.loop.over.indices.could.be.replaced.with.loop.over.elements"),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            ReplaceIndexLoopWithCollectionLoopQuickFix(type)
                        )
                        return
                    }
                }
            }
        holder.registerProblem(
            range,
            KotlinBundle.message("range.could.be.replaced.with.indices.call"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            ReplaceManualRangeWithIndicesCallQuickFix()
        )
    }
}

class ReplaceManualRangeWithIndicesCallQuickFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.manual.range.with.indices.call.quick.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as KtExpression
        val receiver = when (val secondArg = element.getArguments()?.second) {
            is KtBinaryExpression -> (secondArg.left as? KtDotQualifiedExpression)?.receiverExpression
            is KtDotQualifiedExpression -> secondArg.receiverExpression
            else -> null
        }
        val psiFactory = KtPsiFactory(project)
        val newExpression = if (receiver != null && receiver !is KtThisExpression) {
            psiFactory.createExpressionByPattern("$0.indices", receiver)
        } else {
            psiFactory.createExpression("indices")
        }
        element.replace(newExpression)
    }
}

/**
 * Affected tests: [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.ReplaceManualRangeWithIndicesCalls]
 */
class ReplaceIndexLoopWithCollectionLoopQuickFix(private val type: RangeKtExpressionType) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.index.loop.with.collection.loop.quick.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement.getStrictParentOfType<KtForExpression>() ?: return
        val loopParameter = element.loopParameter ?: return
        val loopRange = element.loopRange ?: return
        val collectionParent = when (loopRange) {
            is KtDotQualifiedExpression -> (loopRange.parent as? KtCallExpression)?.valueArguments?.firstOrNull()?.getArgumentExpression()
            is KtBinaryExpression -> loopRange.right
            else -> null
        } ?: return
        val sizeOrLengthCall = collectionParent.sizeOrLengthCall(type) ?: return
        val collection = (sizeOrLengthCall as? KtDotQualifiedExpression)?.receiverExpression
        val paramElement = loopParameter.originalElement ?: return
        val usageElement = ReferencesSearch.search(paramElement).singleOrNull()?.element ?: return
        val arrayAccessElement =
            usageElement.parents.match(KtContainerNode::class, last = KtArrayAccessExpression::class) ?: return
        val factory = KtPsiFactory(project)
        val newParameter = factory.createLoopParameter("element")
        val newReferenceExpression = factory.createExpression("element")
        arrayAccessElement.replace(newReferenceExpression)
        loopParameter.replace(newParameter)
        loopRange.replace(collection ?: factory.createThisExpression())
    }
}

private fun KtExpression.toIntConstant(): Int? {
    return (this as? KtConstantExpression)?.text?.toIntOrNull()
}

private fun KtExpression.sizeOrLengthCall(type: RangeKtExpressionType): KtExpression? {
    val expression = when (type) {
        UNTIL, RANGE_UNTIL -> this
        RANGE_TO -> (this as? KtBinaryExpression)
            ?.takeIf { operationToken == KtTokens.MINUS && right?.toIntConstant() == 1 }
            ?.left
        DOWN_TO -> return null
    }
    val receiverType = expression.receiverTypeIfSelectorIsSizeOrLength() ?: return null
    if (receiverType.isMap()) return null
    return expression
}
