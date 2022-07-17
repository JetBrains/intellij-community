// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.collections.isMap
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.idea.intentions.receiverTypeIfSelectorIsSizeOrLength
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class ReplaceManualRangeWithIndicesCallsInspection : AbstractKotlinInspection() {
    companion object {
        private val rangeFunctionNames = setOf("until", "rangeTo", "..")

        private val rangeFunctionFqNames = listOf(
            "Char",
            "Byte", "Short", "Int", "Long",
            "UByte", "UShort", "UInt", "ULong"
        ).map { FqName("kotlin.$it.rangeTo") } + FqName("kotlin.ranges.until")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitBinaryExpression(binaryExpression: KtBinaryExpression) {
            val left = binaryExpression.left ?: return
            val right = binaryExpression.right ?: return
            val operator = binaryExpression.operationReference
            visitRange(holder, binaryExpression, left, right, operator)
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            val call = expression.callExpression ?: return
            val left = expression.receiverExpression
            val right = call.valueArguments.singleOrNull()?.getArgumentExpression() ?: return
            val operator = call.calleeExpression ?: return
            visitRange(holder, expression, left, right, operator)
        }
    }

    private fun visitRange(holder: ProblemsHolder, range: KtExpression, left: KtExpression, right: KtExpression, operator: KtExpression) {
        if (operator.text !in rangeFunctionNames) return
        val functionFqName = range.resolveToCall()?.resultingDescriptor?.fqNameOrNull() ?: return
        if (functionFqName !in rangeFunctionFqNames) return
        val rangeFunction = functionFqName.shortName().asString()

        if (left.toIntConstant() != 0) return
        val sizeOrLengthCall = right.sizeOrLengthCall(rangeFunction) ?: return
        val collection = sizeOrLengthCall.safeAs<KtQualifiedExpression>()?.receiverExpression
        if (collection != null && collection !is KtSimpleNameExpression) return

        val parent = range.parent.parent
        if (parent is KtForExpression) {
            val paramElement = parent.loopParameter?.originalElement ?: return
            val usageElement = ReferencesSearch.search(paramElement).singleOrNull()?.element
            val arrayAccess = usageElement?.parent?.parent as? KtArrayAccessExpression
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
                        ReplaceIndexLoopWithCollectionLoopQuickFix(rangeFunction)
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
        val newExpression = if (receiver != null) {
            psiFactory.createExpressionByPattern("$0.indices", receiver)
        } else {
            psiFactory.createExpression("indices")
        }
        element.replace(newExpression)
    }
}

class ReplaceIndexLoopWithCollectionLoopQuickFix(private val rangeFunction: String) : LocalQuickFix {
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
        val sizeOrLengthCall = collectionParent.sizeOrLengthCall(rangeFunction) ?: return
        val collection = (sizeOrLengthCall as? KtDotQualifiedExpression)?.receiverExpression
        val paramElement = loopParameter.originalElement ?: return
        val usageElement = ReferencesSearch.search(paramElement).singleOrNull()?.element ?: return
        val arrayAccessElement = usageElement.parent.parent as? KtArrayAccessExpression ?: return
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

private fun KtExpression.sizeOrLengthCall(rangeFunction: String): KtExpression? {
    val expression = when(rangeFunction) {
        "until" -> this
        "rangeTo" -> (this as? KtBinaryExpression)
            ?.takeIf { operationToken == KtTokens.MINUS && right?.toIntConstant() == 1}
            ?.left
        else -> null
    } ?: return null
    val receiverType = expression.receiverTypeIfSelectorIsSizeOrLength() ?: return null
    if (receiverType.isMap(DefaultBuiltIns.Instance)) return null
    return expression
}
