// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.intentions.AddNameToArgumentIntention
import org.jetbrains.kotlin.idea.intentions.AddNamesToCallArgumentsIntention
import org.jetbrains.kotlin.idea.intentions.AddNamesToFollowingArgumentsIntention
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import javax.swing.JComponent

class BooleanLiteralArgumentInspection(
    @JvmField var reportSingle: Boolean = false
) : AbstractKotlinInspection() {
    companion object {
        private val ignoreConstructors = listOf("kotlin.Pair", "kotlin.Triple").map { FqName(it) }
    }

    private fun KtExpression.isBooleanLiteral(): Boolean =
        this is KtConstantExpression && node.elementType == KtNodeTypes.BOOLEAN_CONSTANT

    private fun KtValueArgument.isUnnamedBooleanLiteral(): Boolean =
        !isNamed() && getArgumentExpression()?.isBooleanLiteral() == true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        valueArgumentVisitor(fun(argument: KtValueArgument) {
            if (argument.isNamed()) return
            val argumentExpression = argument.getArgumentExpression() ?: return
            if (!argumentExpression.isBooleanLiteral()) return
            val call = argument.getStrictParentOfType<KtCallExpression>() ?: return
            val valueArguments = call.valueArguments

            if (argumentExpression.safeAnalyzeNonSourceRootCode().diagnostics.forElement(argumentExpression).any { it.severity == Severity.ERROR }) return
            if (AddNameToArgumentIntention.detectNameToAdd(argument, shouldBeLastUnnamed = false) == null) return

            val resolvedCall = call.resolveToCall() ?: return
            if ((resolvedCall.resultingDescriptor as? ClassConstructorDescriptor)?.constructedClass?.fqNameOrNull() in ignoreConstructors) {
                return
            }
            if (!resolvedCall.candidateDescriptor.hasStableParameterNames()) return
            val languageVersionSettings = call.languageVersionSettings
            if (valueArguments.any {
                    !AddNameToArgumentIntention.argumentMatchedAndCouldBeNamedInCall(it, resolvedCall, languageVersionSettings)
                }
            ) return

            val highlightType = if (reportSingle) {
                GENERIC_ERROR_OR_WARNING
            } else {
                val hasNeighbourUnnamedBoolean = valueArguments.asSequence().windowed(size = 2, step = 1).any { (prev, next) ->
                    prev == argument && next.isUnnamedBooleanLiteral() ||
                            next == argument && prev.isUnnamedBooleanLiteral()
                }
                if (hasNeighbourUnnamedBoolean) GENERIC_ERROR_OR_WARNING else INFORMATION
            }
            val fix = if (argument != valueArguments.lastOrNull { !it.isNamed() }) {
                if (argument == valueArguments.firstOrNull()) {
                    AddNamesToCallArgumentsIntention()
                } else {
                    AddNamesToFollowingArgumentsIntention()
                }
            } else {
                AddNameToArgumentIntention()
            }
            holder.registerProblemWithoutOfflineInformation(
                argument, KotlinBundle.message("boolean.literal.argument.without.parameter.name"),
                isOnTheFly, highlightType, IntentionWrapper(fix)
            )
        })

    override fun createOptionsPanel(): JComponent {
        val panel = MultipleCheckboxOptionsPanel(this)
        panel.addCheckbox(KotlinBundle.message("report.also.on.call.with.single.boolean.literal.argument"), "reportSingle")
        return panel
    }
}
