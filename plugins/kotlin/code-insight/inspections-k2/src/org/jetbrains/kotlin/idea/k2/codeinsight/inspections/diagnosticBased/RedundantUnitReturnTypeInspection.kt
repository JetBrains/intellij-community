// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinKtDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import kotlin.reflect.KClass

internal class RedundantUnitReturnTypeInspection :
    KotlinKtDiagnosticBasedInspectionBase<KtElement, KaFirDiagnostic.RedundantReturnUnitType, CallableReturnTypeUpdaterUtils.TypeInfo>(),
    CleanupLocalInspectionTool {

    override val diagnosticType: KClass<KaFirDiagnostic.RedundantReturnUnitType>
        get() = KaFirDiagnostic.RedundantReturnUnitType::class

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitTypeReference(typeReference: KtTypeReference) {
            visitTargetElement(typeReference, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(element: KtElement, context: CallableReturnTypeUpdaterUtils.TypeInfo): String =
        KotlinBundle.message("inspection.redundant.unit.return.type.display.name")

    override fun KaSession.prepareContextByDiagnostic(
        element: KtElement,
        diagnostic: KaFirDiagnostic.RedundantReturnUnitType,
    ): CallableReturnTypeUpdaterUtils.TypeInfo? {
        val typeReference = element as? KtTypeReference ?: return null
        val returnType = typeReference.type.fullyExpandedType

        if (!returnType.isMarkedNullable && returnType.isUnitType) {
            return CallableReturnTypeUpdaterUtils.TypeInfo(CallableReturnTypeUpdaterUtils.TypeInfo.Companion.UNIT)
        }

        return null
    }

    override fun createQuickFix(
        element: KtElement,
        context: CallableReturnTypeUpdaterUtils.TypeInfo,
    ): KotlinModCommandQuickFix<KtElement> = object : KotlinModCommandQuickFix<KtElement>() {

        override fun getFamilyName(): String = KotlinBundle.message("inspection.redundant.unit.return.type.action.name")

        override fun applyFix(
            project: Project,
            element: KtElement,
            updater: ModPsiUpdater,
        ) {
            val function = element.getParentOfType<KtNamedFunction>(strict = true) ?: return
            CallableReturnTypeUpdaterUtils.updateType(function, context, project, updater)
        }
    }
}