// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.reflect.KClass

internal class VariableInitializerIsRedundantInspection : KotlinPsiDiagnosticBasedInspectionBase<KtElement, KaFirDiagnostic.VariableInitializerIsRedundant, TypeInfo>() {
    override val diagnosticType: KClass<KaFirDiagnostic.VariableInitializerIsRedundant>
        get() = KaFirDiagnostic.VariableInitializerIsRedundant::class

    override fun KaSession.prepareContextByDiagnostic(
        element: KtElement,
        diagnostic: KaFirDiagnostic.VariableInitializerIsRedundant,
    ): TypeInfo? {
        val property = PsiTreeUtil.getParentOfType(element, KtProperty::class.java) ?: return null
        return CallableReturnTypeUpdaterUtils.getTypeInfo(property)
    }

    override fun getProblemDescription(
        element: KtElement,
        context: TypeInfo,
    ): @InspectionMessage String = KotlinBundle.message("initializer.is.redundant")

    override fun getProblemHighlightType(element: KtElement, context: TypeInfo): ProblemHighlightType =
        ProblemHighlightType.LIKE_UNUSED_SYMBOL

    override fun createQuickFixes(
        element: KtElement,
        context: TypeInfo,
    ): Array<KotlinModCommandQuickFix<KtElement>> = arrayOf(object : KotlinModCommandQuickFix<KtElement>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.initializer")

        override fun applyFix(
            project: Project,
            element: KtElement,
            updater: ModPsiUpdater,
        ) {
            val property = PsiTreeUtil.getParentOfType(element, KtProperty::class.java) ?: return
            val initializer = property.initializer ?: return
            property.deleteChildRange(property.equalsToken ?: initializer, initializer)

            CallableReturnTypeUpdaterUtils.updateType(
                declaration = property,
                typeInfo = context,
                project = project,
                updater = updater,
            )
        }
    })

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitKtElement(element: KtElement) {
            visitTargetElement(element, holder, isOnTheFly)
        }
    }
}
