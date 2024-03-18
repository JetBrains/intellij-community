// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.isArrayOfFunction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ReplaceArrayOfWithLiteralInspection : AbstractKotlinApplicableInspection<KtCallExpression>() {
    override fun apply(element: KtCallExpression, project: Project, updater: ModPsiUpdater) {
        val valueArgument = element.getParentOfType<KtValueArgument>(false)
        valueArgument?.getSpreadElement()?.delete()

        val arguments = element.valueArguments
        val arrayLiteral = KtPsiFactory(project).buildExpression {
            appendFixedText("[")
            for ((index, argument) in arguments.withIndex()) {
                appendExpression(argument.getArgumentExpression())
                if (index != arguments.size - 1) {
                    appendFixedText(", ")
                }
            }
            appendFixedText("]")
        } as KtCollectionLiteralExpression

        element.replace(arrayLiteral)
    }

    override fun getProblemDescription(element: KtCallExpression): String {
        val calleeExpression = element.calleeExpression as KtNameReferenceExpression
        return KotlinBundle.message("0.call.should.be.replaced.with.array.literal", calleeExpression.getReferencedName())
    }

    override fun getActionFamilyName(): String = KotlinBundle.message("replace.with.array.literal.fix.family.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.calleeExpression !is KtNameReferenceExpression) return false

        when (val parent = element.parent) {
            is KtValueArgument -> {
                if (parent.parent?.parent !is KtAnnotationEntry) return false
                if (parent.getSpreadElement() != null && !parent.isNamed()) return false
            }

            is KtParameter -> {
                val constructor = parent.parent?.parent as? KtPrimaryConstructor ?: return false
                val containingClass = constructor.getContainingClassOrObject()
                if (!containingClass.isAnnotation()) return false
            }

            else -> return false
        }

        return true
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtCallExpression): Boolean = element.isArrayOfFunction()
}
