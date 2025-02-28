// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.RecursivePropertyAccessUtil
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RecursivePropertyAccessorInspection.Context
import org.jetbrains.kotlin.idea.codeInsight.RecursivePropertyAccessUtil.isRecursivePropertyAccess
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RecursivePropertyAccessorInspection : KotlinApplicableInspectionBase<KtSimpleNameExpression, Context>() {
    enum class Context { RECURSIVE_PROPERTY_ACCESS, RECURSIVE_SYNTHETIC_PROPERTY_ACCESS }

    private fun getProblemDescription(context: Context): @InspectionMessage String = when (context) {
        Context.RECURSIVE_PROPERTY_ACCESS -> KotlinBundle.message("recursive.property.accessor")
        Context.RECURSIVE_SYNTHETIC_PROPERTY_ACCESS -> KotlinBundle.message("recursive.synthetic.property.accessor")
    }

    private fun createQuickFix(element: KtSimpleNameExpression): KotlinModCommandQuickFix<KtSimpleNameExpression>? {
        // Skip if the property is an extension property.
        val isExtensionProperty = element.getStrictParentOfType<KtProperty>()?.receiverTypeReference != null
        if (isExtensionProperty) return null

        return object : KotlinModCommandQuickFix<KtSimpleNameExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.field.fix.text")

            override fun applyFix(
                project: Project, element: KtSimpleNameExpression, updater: ModPsiUpdater,
            ) {
                val factory = KtPsiFactory(project)
                element.replace(factory.createExpression("field"))
            }
        }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtSimpleNameExpression, context: Context, rangeInElement: TextRange?, onTheFly: Boolean
    ): ProblemDescriptor = createQuickFix(element)?.let {
        createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ getProblemDescription(context),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ it,
        )
    } ?: createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ getProblemDescription(context),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
    )

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): KtVisitor<*, *> = simpleNameExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtSimpleNameExpression): Boolean {
        return RecursivePropertyAccessUtil.isApplicablePropertyAccessPsi(element) || RecursivePropertyAccessUtil.isApplicableSyntheticPropertyAccessPsi(element)
    }

    override fun KaSession.prepareContext(element: KtSimpleNameExpression): Context? {
        if (element.isRecursivePropertyAccess(false)) return Context.RECURSIVE_PROPERTY_ACCESS
        if (RecursivePropertyAccessUtil.isRecursiveSyntheticPropertyAccess(element)) return Context.RECURSIVE_SYNTHETIC_PROPERTY_ACCESS
        return null
    }
}