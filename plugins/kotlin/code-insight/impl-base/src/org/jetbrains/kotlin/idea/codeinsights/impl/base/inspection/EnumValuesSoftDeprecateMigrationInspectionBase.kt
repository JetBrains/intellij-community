// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesMethod
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.migration.MigrationInfo
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*

/**
 * Common logic for K1 and K2 for inspection which checks that `values()` method call in enum classes
 * can be replaced with `entries` property access.
 *
 * See [KTIJ-22298](https://youtrack.jetbrains.com/issue/KTIJ-22298/Soft-deprecate-Enumvalues-for-Kotlin-callers).
 */
abstract class EnumValuesSoftDeprecateMigrationInspectionBase : AbstractKotlinInspection(), MigrationFix {

    final override fun isApplicable(migrationInfo: MigrationInfo): Boolean {
        val sinceVersion = LanguageFeature.EnumEntries.sinceVersion ?: return false
        return migrationInfo.oldLanguageVersion < sinceVersion && migrationInfo.newLanguageVersion >= sinceVersion
    }

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        if (!holder.file.isEnumValuesSoftDeprecateEnabled()) {
            PsiElementVisitor.EMPTY_VISITOR
        } else {
            callExpressionVisitor(fun(callExpression: KtCallExpression) {
                analyze(callExpression) {
                    if (!isOptInAllowed(callExpression, EXPERIMENTAL_ANNOTATION_CLASS_ID)) {
                        return
                    }
                    val resolvedCall = callExpression.resolveCall().successfulFunctionCallOrNull() ?: return
                    val resolvedCallSymbol = resolvedCall.partiallyAppliedSymbol.symbol
                    if (isEnumValuesMethod(resolvedCallSymbol)) {
                        val quickFix = createQuickFix(callExpression, resolvedCallSymbol) ?: return
                        holder.registerProblem(
                            callExpression,
                            KotlinBundle.message("inspection.enum.values.method.soft.deprecate.migration.display.name"),
                            quickFix
                        )
                    }
                }
            })
        }

    protected abstract fun KtAnalysisSession.isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean

    private fun KtAnalysisSession.createQuickFix(callExpression: KtCallExpression, symbol: KtFunctionLikeSymbol): LocalQuickFix? {
        val enumClassSymbol = symbol.getContainingSymbol() as? KtClassOrObjectSymbol
        val enumClassQualifiedName = enumClassSymbol?.classIdIfNonLocal?.asFqNameString() ?: return null
        return createQuickFix(isCastToArrayNeeded(callExpression), enumClassQualifiedName)
    }

    protected open fun createQuickFix(castToArrayNeeded: Boolean, enumClassQualifiedName: String): LocalQuickFix {
        return ReplaceFix(castToArrayNeeded, enumClassQualifiedName)
    }

    private fun KtAnalysisSession.isCastToArrayNeeded(callExpression: KtCallExpression): Boolean {
        val qualifiedOrSimpleCall = callExpression.qualifiedOrSimpleValuesCall()
        val parent = qualifiedOrSimpleCall.parent
        // Special handling for most popular use cases where `entries` can be used without cast to Array
        when {
            // values()[index]
            parent is KtArrayAccessExpression && parent.parent !is KtBinaryExpression -> return false

            // for (v in values())
            parent is KtContainerNode && parent.parent is KtForExpression -> return false

            // values().someMethod()
            parent is KtDotQualifiedExpression -> {
                val callableIdString = getCallableMethodIdString(parent)
                return callableIdString !in METHOD_IDS_SUITABLE_FOR_LIST
            }
        }
        return true
    }

    private fun KtAnalysisSession.getCallableMethodIdString(expression: KtDotQualifiedExpression): String? {
        val resolvedCall = expression.selectorExpression?.resolveCall()?.successfulCallOrNull<KtCallableMemberCall<*, *>>()
        return resolvedCall?.partiallyAppliedSymbol?.symbol?.callableIdIfNonLocal?.toString()
    }

    protected open class ReplaceFix(private val castToArrayNeeded: Boolean,
                                    private val enumClassQualifiedName: String) : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("replace.with.0", "Enum.entries")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val qualifiedOrSimpleCall = descriptor.psiElement.qualifiedOrSimpleValuesCall()
            val entriesCallStr = if (castToArrayNeeded) "entries.toTypedArray()" else "entries"
            val replaced = qualifiedOrSimpleCall.replace(KtPsiFactory(project).createExpression("$enumClassQualifiedName.$entriesCallStr"))
            if (replaced is KtElement) {
                shortenReferences(replaced)
            }
        }

        protected open fun shortenReferences(element: KtElement) {
            ShortenReferencesFacility.getInstance().shorten(element)
        }
    }

    private companion object {
        private val EXPERIMENTAL_ANNOTATION_CLASS_ID = ClassId.fromString("kotlin/ExperimentalStdlibApi")

        // We hardcode here most popular methods used with values() because it's easier than
        // programmatically search method overload suitable for use with List.
        // These are not all the collections methods, but only methods which chosen based on usages found in intellij and kotlin repositories.
        private val METHOD_IDS_SUITABLE_FOR_LIST = setOf(
            "kotlin/Array.size",
            "kotlin/Array.get",
            "kotlin/Array.iterator",
            "kotlin/collections/any",
            "kotlin/collections/asIterable",
            "kotlin/collections/asSequence",
            "kotlin/collections/associate",
            "kotlin/collections/associateBy",
            "kotlin/collections/associateWith",
            "kotlin/collections/filter",
            "kotlin/collections/filterNot",
            "kotlin/collections/find",
            "kotlin/collections/first",
            "kotlin/collections/firstNotNullOfOrNull",
            "kotlin/collections/firstOrNull",
            "kotlin/collections/flatMap",
            "kotlin/collections/forEach",
            "kotlin/collections/forEachIndexed",
            "kotlin/collections/getOrNull",
            "kotlin/collections/groupBy",
            "kotlin/collections/indexOf",
            "kotlin/collections/joinToString",
            "kotlin/collections/last",
            "kotlin/collections/map",
            "kotlin/collections/mapNotNull",
            "kotlin/collections/mapTo",
            "kotlin/collections/maxByOrNull",
            "kotlin/collections/reversed",
            "kotlin/collections/single",
            "kotlin/collections/singleOrNull",
            "kotlin/collections/sortedBy",
            "kotlin/collections/toList",
            "kotlin/collections/toMutableList",
            "kotlin/collections/toSet",
            "kotlin/collections/withIndex",
        )

        private fun PsiElement.qualifiedOrSimpleValuesCall() =
            if (parent is KtDotQualifiedExpression) parent // EnumClass.values()
            else this                                      // values()
    }
}