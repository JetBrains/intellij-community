// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class RedundantUnitReturnTypeInspection :
  AbstractKotlinApplicableInspectionWithContext<KtNamedFunction, TypeInfo>(),
  CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                visitTargetElement(function, holder, isOnTheFly)
            }
        }
    }
    override fun getProblemDescription(element: KtNamedFunction, context: TypeInfo): String =
        KotlinBundle.message("inspection.redundant.unit.return.type.display.name")

    override fun getActionFamilyName(): String = KotlinBundle.message("inspection.redundant.unit.return.type.action.name")

    override fun getApplicabilityRange() = ApplicabilityRanges.CALLABLE_RETURN_TYPE

    override fun isApplicableByPsi(element: KtNamedFunction): Boolean {
        return element.hasBlockBody() && element.typeReference != null
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtNamedFunction): TypeInfo? = when {
        element.getFunctionLikeSymbol().returnType.isUnit -> TypeInfo(TypeInfo.UNIT)
        else -> null
    }

    override fun apply(element: KtNamedFunction, context: TypeInfo, project: Project, updater: ModPsiUpdater) {
        updateType(element, context, project, updater = updater)
    }
}