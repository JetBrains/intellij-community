// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.callExpressionVisitor

/**
 * Common logic for K1 and K2 for inspection which checks that `values()` method call in enum classes
 * can be replaced with `entries` property access.
 *
 * See [KTIJ-22298](https://youtrack.jetbrains.com/issue/KTIJ-22298/Soft-deprecate-Enumvalues-for-Kotlin-callers).
 */
abstract class EnumValuesSoftDeprecateMigrationInspectionBase : AbstractKotlinInspection() {
    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        if (!holder.file.languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)) {
            PsiElementVisitor.EMPTY_VISITOR
        } else {
            callExpressionVisitor(fun(callExpression: KtCallExpression) {
                analyze(callExpression) {
                    if (!isOptInAllowed(callExpression, EXPERIMENTAL_ANNOTATION_CLASS_ID)) {
                        return
                    }
                    if (isEnumValuesMethod(callExpression)) {
                        holder.registerProblem(
                            callExpression,
                            KotlinBundle.message("inspection.enum.values.method.soft.deprecate.migration.display.name"),
                            ReplaceFix()
                        )
                    }
                }
            })
        }

    protected abstract fun KtAnalysisSession.isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean

    private fun KtAnalysisSession.isEnumValuesMethod(
        call: KtCallExpression
    ): Boolean {
        val functionCall = call.resolveCall().successfulFunctionCallOrNull() ?: return false
        val symbol = functionCall.partiallyAppliedSymbol.symbol
        // TODO: extract common logic when KTIJ-23315 merged
        return KtClassKind.ENUM_CLASS == (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind &&
                VALUES_METHOD_NAME == symbol.callableIdIfNonLocal?.callableName &&
                // Don't touch user-declared methods with the name "values"
                symbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED
    }

    private class ReplaceFix : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("replace.with.0", "Enum.entries")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            descriptor.psiElement.replace(KtPsiFactory(project).createExpression("entries"))
            // TODO: handle cases when Array type expected in next commits
        }
    }

    private companion object {
        private val EXPERIMENTAL_ANNOTATION_CLASS_ID = ClassId.fromString("kotlin/ExperimentalStdlibApi")
        private val VALUES_METHOD_NAME = Name.identifier("values")
    }
}