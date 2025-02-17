// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isSetterParameter
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveSetterParameterTypeInspection :
    KotlinApplicableInspectionBase.Simple<KtParameter, Unit>() {
    override fun getProblemDescription(
        element: KtParameter,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("redundant.setter.parameter.type")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {

        override fun visitParameter(parameter: KtParameter) {
            visitTargetElement(parameter, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtParameter) = Unit

    override fun isApplicableByPsi(element: KtParameter): Boolean {
        if (!element.isSetterParameter) return false
        val typeReference = element.typeReference ?: return false
        return typeReference.endOffset > typeReference.startOffset
    }

    override fun createQuickFix(
        element: KtParameter,
        context: Unit
    ): KotlinModCommandQuickFix<KtParameter> = object : KotlinModCommandQuickFix<KtParameter>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.explicit.type.specification")

        override fun applyFix(
            project: Project,
            element: KtParameter,
            updater: ModPsiUpdater,
        ) {
            element.typeReference = null
        }
    }
}