// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.addArgumentNames
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.associateArgumentNamesStartingAt
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.getStableNameFor
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidKeys
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class BooleanLiteralArgumentInspection(
    @JvmField var reportSingle: Boolean = false,
) : KotlinApplicableInspectionBase.Simple<KtValueArgument, BooleanLiteralArgumentInspection.Context>() {

    data class Context(
        val problemHighlightType: ProblemHighlightType,
        @IntentionFamilyName val familyName: String,
        val argumentNames: Map<SmartPsiElementPointer<KtValueArgument>, Name>,
    )

    override fun getOptionsPane(): OptPane =
        pane(checkbox("reportSingle", KotlinBundle.message("report.also.on.call.with.single.boolean.literal.argument")))

    override fun getProblemDescription(
        element: KtValueArgument,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("boolean.literal.argument.without.parameter.name")

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtValueArgument): Context? {
        if (element.isNamed()) return null
        val argumentExpression = element.getArgumentExpression() ?: return null
        if (!argumentExpression.isBooleanLiteral()) return null
        val call = element.getStrictParentOfType<KtCallExpression>() ?: return null
        val valueArguments = call.valueArguments

        val diagnostics = argumentExpression.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        if (diagnostics.any { it.severity == KaSeverity.ERROR }) return null
        val name = getStableNameFor(element) ?: return null

        val symbol = call.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null
        if ((symbol as? KaConstructorSymbol)?.importableFqName in ignoreConstructors) return null
        if (!symbol.hasStableParameterNames) return null

        val highlightType = if (reportSingle) {
            GENERIC_ERROR_OR_WARNING
        } else {
            val hasNeighbourUnnamedBoolean = valueArguments.asSequence().windowed(size = 2, step = 1).any { (prev, next) ->
                prev == element && next.isUnnamedBooleanLiteral() || next == element && prev.isUnnamedBooleanLiteral()
            }
            if (hasNeighbourUnnamedBoolean) GENERIC_ERROR_OR_WARNING else INFORMATION
        }

        val (familyName, argumentNames) = if (element == valueArguments.lastOrNull { !it.isNamed() }) {
            KotlinBundle.message("add.0.to.argument", name) to mapOf(element.createSmartPointer() to name)
        } else if (element == valueArguments.firstOrNull()) {
            val associateArgumentNamesStartingAt = associateArgumentNamesStartingAt(call, null) ?: return null
            KotlinBundle.message("add.names.to.call.arguments") to associateArgumentNamesStartingAt
        } else {
            val associateArgumentNamesStartingAt = associateArgumentNamesStartingAt(call, element) ?: return null
            KotlinBundle.message("add.names.to.this.argument.and.following.arguments") to associateArgumentNamesStartingAt
        }

        return Context(highlightType, familyName, argumentNames)
    }

    override fun getProblemHighlightType(
        element: KtValueArgument,
        context: Context,
    ): ProblemHighlightType = context.problemHighlightType

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitArgument(argument: KtValueArgument) {
            visitTargetElement(argument, holder, isOnTheFly)
        }
    }

    override fun createQuickFix(
        element: KtValueArgument,
        context: Context,
    ): KotlinModCommandQuickFix<KtValueArgument> = object : KotlinModCommandQuickFix<KtValueArgument>() {
        override fun getFamilyName(): @IntentionFamilyName String = context.familyName

        override fun applyFix(
            project: Project,
            element: KtValueArgument,
            updater: ModPsiUpdater,
        ) {
            addArgumentNames(
                context.argumentNames.dereferenceValidKeys().mapKeys { (argument, _) ->
                    updater.getWritable(argument)
                }
            )
        }
    }
}

private fun KtExpression.isBooleanLiteral(): Boolean = this is KtConstantExpression && node.elementType == KtNodeTypes.BOOLEAN_CONSTANT

private fun KtValueArgument.isUnnamedBooleanLiteral(): Boolean = !isNamed() && getArgumentExpression()?.isBooleanLiteral() == true

private val ignoreConstructors: List<FqName> = listOf("kotlin.Pair", "kotlin.Triple").map { FqName(it) }
