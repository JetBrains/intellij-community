// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.codeInsight.*
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.DeprecationCollectingInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isOptInRequired
import org.jetbrains.kotlin.idea.statistics.DeprecatedFeaturesInspectionData
import org.jetbrains.kotlin.idea.statistics.KotlinLanguageFeaturesFUSCollector
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*

/**
 * Common logic for K1 and K2 for inspection which checks that `values()` method call in enum classes
 * can be replaced with `entries` property access.
 *
 * See [KTIJ-22298](https://youtrack.jetbrains.com/issue/KTIJ-22298/Soft-deprecate-Enumvalues-for-Kotlin-callers).
 */
abstract class EnumValuesSoftDeprecateInspectionBase : DeprecationCollectingInspection<DeprecatedFeaturesInspectionData>(
    collector = KotlinLanguageFeaturesFUSCollector.enumEntriesCollector,
    defaultDeprecationData = DeprecatedFeaturesInspectionData()
) {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
        if (!holder.file.isEnumValuesSoftDeprecateEnabled()) {
            PsiElementVisitor.EMPTY_VISITOR
        } else {
            callExpressionVisitor(fun(callExpression: KtCallExpression) {
                if (callExpression.text != "values()") {
                    return
                }
                analyze(callExpression) {
                    val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return
                    val resolvedCallSymbol = resolvedCall.partiallyAppliedSymbol.symbol
                    val enumClassSymbol = (resolvedCallSymbol.containingDeclaration as? KaClassSymbol) ?: return

                    if (!isSoftDeprecatedEnumValuesMethod(resolvedCallSymbol, enumClassSymbol)) {
                        return
                    }
                    val enumEntriesPropertySymbol = getEntriesPropertyOfEnumClass(enumClassSymbol) ?: return
                    val enumEntriesClassSymbol = enumEntriesPropertySymbol.returnType.expandedSymbol ?: return

                    val optInRequired = enumEntriesClassSymbol.isOptInRequired(
                        EXPERIMENTAL_ANNOTATION_CLASS_ID,
                        callExpression.languageVersionSettings.apiVersion
                    )
                    if (optInRequired && !isOptInAllowed(callExpression, EXPERIMENTAL_ANNOTATION_CLASS_ID)) {
                        return
                    }
                    val quickFix = createQuickFix(callExpression, resolvedCallSymbol) ?: return
                    session.updateDeprecationData { it.withDeprecatedFeature() }
                    holder.registerProblem(
                        callExpression,
                        KotlinBundle.message("inspection.enum.values.method.soft.deprecate.migration.display.name"),
                        quickFix
                    )
                }
            })
        }

    context(_: KaSession)
    protected abstract fun isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean

    context(_: KaSession)
    private fun createQuickFix(callExpression: KtCallExpression, symbol: KaFunctionSymbol): LocalQuickFix? {
        val enumClassSymbol = symbol.containingDeclaration as? KaClassSymbol
        val enumClassQualifiedName = enumClassSymbol?.classId?.asFqNameString() ?: return null
        return createQuickFix(getReplaceFixType(callExpression), enumClassQualifiedName)
    }

    protected open fun createQuickFix(fixType: ReplaceFixType, enumClassQualifiedName: String): LocalQuickFix {
        return ReplaceFix(fixType, enumClassQualifiedName)
    }

    context(_: KaSession)
    private fun getReplaceFixType(callExpression: KtCallExpression): ReplaceFixType {
        val qualifiedOrSimpleCall = callExpression.qualifiedOrSimpleValuesCall()
        val parent = qualifiedOrSimpleCall.parent
        // Special handling for most popular use cases where `entries` can be used without cast to Array
        when {
            parent is KtBlockExpression && parent.parent is KtNamedFunction -> return ReplaceFixType.WITHOUT_CAST

            // values()[index]
            parent is KtArrayAccessExpression && parent.parent !is KtBinaryExpression -> return ReplaceFixType.WITHOUT_CAST

            // for (v in values())
            parent is KtContainerNode && parent.parent is KtForExpression -> return ReplaceFixType.WITHOUT_CAST

            // values().someMethod()
            parent is KtDotQualifiedExpression -> {
                val callableIdString = getCallableMethodIdString(parent.selectorExpression)
                if (callableIdString in LIST_CONVERSION_METHOD_IDS) {
                    return ReplaceFixType.REMOVE_SUBSEQUENT_TO_LIST_CALL
                }
                if (callableIdString in METHOD_IDS_SUITABLE_FOR_LIST) {
                    return ReplaceFixType.WITHOUT_CAST
                }
            }

            // listOf(values())
            parent is KtValueArgument && parent.isSpread && parent.parent is KtValueArgumentList -> {
                val argumentList = parent.parent as KtValueArgumentList
                if (argumentList.arguments.size == 1) {
                    val callableIdString = getCallableMethodIdString(argumentList.parent as? KtCallExpression)
                    if (callableIdString == "kotlin/collections/listOf") {
                        return ReplaceFixType.REMOVE_WRAPPED_LIST_OF_CALL
                    }
                }
            }
        }
        return ReplaceFixType.WITH_CAST
    }

    context(_: KaSession)
    private fun getCallableMethodIdString(expression: KtElement?): String? {
        val resolvedCall = expression?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
        return resolvedCall?.partiallyAppliedSymbol?.symbol?.callableId?.toString()
    }

    protected enum class ReplaceFixType {
        WITH_CAST,
        WITHOUT_CAST,
        REMOVE_SUBSEQUENT_TO_LIST_CALL,
        REMOVE_WRAPPED_LIST_OF_CALL,
    }

    protected open class ReplaceFix(private val fixType: ReplaceFixType,
                                    private val enumClassQualifiedName: String) : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("replace.with.0", "Enum.entries")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val qualifiedOrSimpleCall = descriptor.psiElement.qualifiedOrSimpleValuesCall()
            val entriesCallStr = when(fixType) {
                ReplaceFixType.WITH_CAST -> "entries.toTypedArray()"
                else -> "entries"
            }
            KotlinLanguageFeaturesFUSCollector.enumEntriesCollector.logQuickFixApplied(qualifiedOrSimpleCall.containingFile)
            var replaced = qualifiedOrSimpleCall.replace(KtPsiFactory(project).createExpression("$enumClassQualifiedName.$entriesCallStr"))
            replaced = applyRemovalsIfNeeded(replaced)

            if (replaced is KtElement) {
                shortenReferences(replaced)
            }
        }

        private fun applyRemovalsIfNeeded(element: PsiElement): PsiElement {
            return when (fixType) {
                ReplaceFixType.REMOVE_SUBSEQUENT_TO_LIST_CALL -> element.parent.replace(element)
                ReplaceFixType.REMOVE_WRAPPED_LIST_OF_CALL -> element.findParentOfType<KtCallExpression>()!!.replace(element)
                else -> element
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
            "kotlin/Array.get",
            "kotlin/Array.iterator",
            "kotlin/Array.size",
            "kotlin/collections/any",
            "kotlin/collections/asIterable",
            "kotlin/collections/asSequence",
            "kotlin/collections/associate",
            "kotlin/collections/associateBy",
            "kotlin/collections/associateWith",
            "kotlin/collections/drop",
            "kotlin/collections/dropLast",
            "kotlin/collections/dropLastWhile",
            "kotlin/collections/dropWhile",
            "kotlin/collections/filter",
            "kotlin/collections/filterNot",
            "kotlin/collections/filterTo",
            "kotlin/collections/find",
            "kotlin/collections/first",
            "kotlin/collections/firstNotNullOfOrNull",
            "kotlin/collections/firstOrNull",
            "kotlin/collections/flatMap",
            "kotlin/collections/fold",
            "kotlin/collections/foldIndexed",
            "kotlin/collections/foldRight",
            "kotlin/collections/foldRightIndexed",
            "kotlin/collections/forEach",
            "kotlin/collections/forEachIndexed",
            "kotlin/collections/getOrNull",
            "kotlin/collections/groupBy",
            "kotlin/collections/indexOf",
            "kotlin/collections/joinTo",
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
        private val LIST_CONVERSION_METHOD_IDS = setOf(
            "kotlin/collections/asList",
            "kotlin/collections/toList",
        )

        private fun PsiElement.qualifiedOrSimpleValuesCall() =
            if ((parent as? KtDotQualifiedExpression)?.selectorExpression === this) parent // EnumClass.values()
            else this // values()
    }
}