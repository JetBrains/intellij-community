// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.java

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

/**
 * This inspection detects usages of `componentN()` calls on data classes from **Java** code.
 * If such a call is detected, a quick-fix is offered to replace it with a named getter.
 */
internal class UseNamedGetterInspection : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor = object : JavaElementVisitor() {
        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            val reference = expression.methodExpression.referenceName ?: return
            val referenceElement = expression.methodExpression.referenceNameElement ?: return

            // Match regex first before doing any PSI operations or resolving because it is inexpensive.
            val matchResult = COMPONENT_REGEX.matchEntire(reference) ?: return
            val componentNumber = matchResult.groups[1]?.value?.toIntOrNull() ?: return

            val resolvedMethod = expression.resolveMethod() as? KtLightMethod ?: return
            val containingClass = resolvedMethod.containingClass.kotlinOrigin ?: return
            if (!containingClass.isData()) return

            if (resolvedMethod.kotlinOrigin !is KtNamedFunction && resolvedMethod.kotlinOrigin !is KtParameter) return
            if (!PsiUtil.isAccessible(resolvedMethod, referenceElement, null)) return

            val parameter = containingClass.primaryConstructor?.valueParameters?.getOrNull(componentNumber - 1) ?: return
            val parameterGetter = LightClassUtil.getLightClassPropertyMethods(parameter).getter ?: return

            val problemDescriptor = holder.manager.createProblemDescriptor(
                /* psiElement = */ expression.methodExpression,
                /* rangeInElement = */ referenceElement.textRangeInParent,
                /* descriptionTemplate = */ KotlinBundle.message("inspection.use.named.getter.problem.descriptor", parameterGetter.name, reference),
                /* highlightType = */ ProblemHighlightType.WARNING,
                /* onTheFly = */ isOnTheFly,
                /* ...fixes = */ UseNamedGetterQuickFix(parameterGetter.name)
            )
            holder.registerProblem(problemDescriptor)
        }
    }
}

private class UseNamedGetterQuickFix(
    private val getterName: String
) : PsiUpdateModCommandQuickFix() {
    override fun applyFix(
        project: Project,
        element: PsiElement,
        updater: ModPsiUpdater
    ) {
        val referenceExpression = element as? PsiReferenceExpression ?: return
        referenceExpression.referenceNameElement?.replace(
            PsiElementFactory.getInstance(project).createIdentifier(getterName)
        )
    }

    override fun getName(): String {
        return KotlinBundle.message("inspection.use.named.getter.fix.name", getterName)
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("inspection.use.named.getter.fix.family.name")
    }
}

private val COMPONENT_REGEX = Regex("component(\\d+)")
