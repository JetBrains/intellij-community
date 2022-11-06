// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.PsiOnlyKotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.namedFunctionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

private val detectorConfiguration = KotlinMainFunctionDetector.Configuration(checkResultType = false)

class MainFunctionReturnUnitInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return namedFunctionVisitor { processFunction(it, holder) }
    }

    private fun processFunction(function: KtNamedFunction, holder: ProblemsHolder) {
        if (!PsiOnlyKotlinMainFunctionDetector.isMain(function, detectorConfiguration)) return
        if (!function.hasDeclaredReturnType() && function.hasBlockBody()) return
        if (!KotlinMainFunctionDetector.getInstance().isMain(function, detectorConfiguration)) return

        analyze(function) {
            if (!function.getFunctionLikeSymbol().returnType.isUnit) {
                holder.registerProblem(
                    function.typeReference ?: function,
                    KotlinBundle.message("0.should.return.unit", "'main()'"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    ChangeMainFunctionReturnTypeToUnitFix(function.typeReference != null)
                )
            }
        }
    }

    private class ChangeMainFunctionReturnTypeToUnitFix(private val hasExplicitReturnType: Boolean) : LocalQuickFix {
        override fun getFamilyName() = name

        override fun getName(): String {
            return if (hasExplicitReturnType)
                KotlinBundle.message("change.main.function.return.type.to.unit.fix.text2")
            else
                KotlinBundle.message("change.main.function.return.type.to.unit.fix.text")
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val function = descriptor.psiElement.getNonStrictParentOfType<KtNamedFunction>() ?: return
            if (function.hasBlockBody()) {
                function.typeReference = null
            } else {
                val newTypeReference = KtPsiFactory(project).createType(StandardClassIds.Unit.asFqNameString())
                function.typeReference = newTypeReference
                ShortenReferencesFacility.getInstance().shorten(newTypeReference)
            }
        }
    }
}