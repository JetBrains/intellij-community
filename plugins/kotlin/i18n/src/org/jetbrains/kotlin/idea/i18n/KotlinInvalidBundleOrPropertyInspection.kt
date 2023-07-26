// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.i18n

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.i18n.JavaI18nUtil
import com.intellij.java.i18n.JavaI18nBundle
import com.intellij.lang.properties.ResourceBundleReference
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class KotlinInvalidBundleOrPropertyInspection : AbstractKotlinInspection() {
    override fun getDisplayName(): String = JavaI18nBundle.message("inspection.unresolved.property.key.reference.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            private fun processResourceBundleReference(ref: ResourceBundleReference, template: KtStringTemplateExpression) {
                if (ref.multiResolve(true).isEmpty()) {
                    holder.registerProblem(
                        template,
                        JavaI18nBundle.message("inspection.invalid.resource.bundle.reference", ref.canonicalText),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        TextRange(0, template.textLength)
                    )
                }
            }

            private fun processPropertyReference(ref: PropertyReference, template: KtStringTemplateExpression) {
                if (ref.isSoft) return // don't highlight soft references, they are inserted in every string literal

                val property = ref.multiResolve(true).firstOrNull()?.element as? Property
                if (property == null) {
                    holder.registerProblem(
                        template,
                        JavaI18nBundle.message("inspection.unresolved.property.key.reference.message", ref.canonicalText),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        TextRange(0, template.textLength),
                        *ref.quickFixes
                    )
                    return
                }

                val argument = template.parents.firstIsInstanceOrNull<KtValueArgument>() ?: return
                if (argument.getArgumentExpression() != KtPsiUtil.deparenthesize(template)) return

                val callExpression = argument.getStrictParentOfType<KtCallExpression>() ?: return
                val keyArgumentIndex = callExpression.valueArguments.indexOf(argument)
                if (keyArgumentIndex < 0) return

                analyze(callExpression) {
                    val callable = callExpression.resolveCall()?.singleFunctionCallOrNull()?.symbol ?: return
                    if (callable.valueParameters.size != keyArgumentIndex + 2) return
                    if (!callable.valueParameters.last().isVararg) return
                }

                val argsSize = callExpression.valueArguments.size
                val messageArgument = if (argsSize > keyArgumentIndex + 1) callExpression.valueArguments[keyArgumentIndex + 1] else null

                if (messageArgument?.isSpread == true) return

                val expectedArgumentCount = JavaI18nUtil.getPropertyValuePlaceholdersCount(property.value ?: "")
                val actualArgumentCount = argsSize - keyArgumentIndex - 1
                if (actualArgumentCount < expectedArgumentCount) {
                    val description = JavaI18nBundle.message(
                        "property.has.more.parameters.than.passed",
                        ref.canonicalText, expectedArgumentCount, actualArgumentCount
                    )
                    holder.registerProblem(template, description, ProblemHighlightType.GENERIC_ERROR)
                }
            }

            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                for (ref in expression.references) {
                    when (ref) {
                        is ResourceBundleReference -> processResourceBundleReference(ref, expression)
                        is PropertyReference -> processPropertyReference(ref, expression)
                    }
                }
            }
        }
    }
}