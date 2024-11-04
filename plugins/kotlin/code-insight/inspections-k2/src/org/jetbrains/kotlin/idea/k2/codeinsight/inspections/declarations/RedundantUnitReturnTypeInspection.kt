// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class RedundantUnitReturnTypeInspection :
    KotlinApplicableInspectionBase.Simple<KtNamedFunction, TypeInfo>(),
    CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitNamedFunction(function: KtNamedFunction) {
            visitTargetElement(function, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(element: KtNamedFunction, context: TypeInfo): String =
        KotlinBundle.message("inspection.redundant.unit.return.type.display.name")

    override fun getApplicableRanges(element: KtNamedFunction): List<TextRange> =
        ApplicabilityRange.single(element) { it.typeReference?.typeElement }

    override fun isApplicableByPsi(element: KtNamedFunction): Boolean {
        return element.hasBlockBody() && element.typeReference != null
    }

    context(KaSession)
    override fun prepareContext(element: KtNamedFunction): TypeInfo? {
        val returnType = element.symbol.returnType.fullyExpandedType

        if (!returnType.isMarkedNullable && returnType.isUnitType) {
            return TypeInfo(TypeInfo.UNIT)
        }

        return null
    }

    override fun createQuickFix(
        element: KtNamedFunction,
        context: TypeInfo,
    ) = object : KotlinModCommandQuickFix<KtNamedFunction>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.redundant.unit.return.type.action.name")

        override fun applyFix(
            project: Project,
            element: KtNamedFunction,
            updater: ModPsiUpdater,
        ) {
            updateType(element, context, project, updater)
        }
    }
}