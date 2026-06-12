// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix.editable

import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.openapi.util.NlsSafe
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeInsight.postfix.KotlinPostfixTemplatesBundle
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtExpression

@ApiStatus.Internal
interface KotlinPostfixTemplateExpressionCondition : PostfixTemplateExpressionCondition<KtExpression> {

    data class KotlinPostfixTemplateExpressionFqnCondition(
        @param:NlsSafe
        private val fqn: String
    ) : KotlinPostfixTemplateExpressionCondition {
        companion object {
            internal const val ID = "kotlin.fqn"
            internal const val FQN_ATTR = "fqn"
        }
        override fun getPresentableName(): String = fqn

        override fun getId(): String = ID

        override fun serializeTo(element: Element) {
            element.setAttribute(PostfixTemplateExpressionCondition.ID_ATTR, getId())
            element.setAttribute(FQN_ATTR, fqn)
        }

        @OptIn(KaIdeApi::class)
        override fun value(expr: KtExpression): Boolean {
            return analyze(expr) {
                val classType = (expr.expressionType?.symbol as? KaClassifierSymbol)?.defaultType ?: return false
                val allSupertypesWithSelf = buildList {
                    add(classType)
                    addAll(classType.allSupertypes)
                }

                allSupertypesWithSelf.any { it.symbol?.importableFqName?.asString() == fqn }
            }
        }
    }

    data object KotlinPostfixTemplateUnitExpressionCondition : KotlinPostfixTemplateExpressionCondition {
        override fun getPresentableName(): String = KotlinPostfixTemplatesBundle.message("kotlin.postfix.template.condition.unit.name")

        override fun getId(): @NonNls String = "kotlin.unit"

        override fun value(expr: KtExpression): Boolean {
            analyze(expr) {
                return expr.expressionType?.isUnitType == true
            }
        }
    }

    data object KotlinPostfixTemplateNonUnitExpressionCondition : KotlinPostfixTemplateExpressionCondition {
        override fun getPresentableName(): String =
            KotlinPostfixTemplatesBundle.message("kotlin.postfix.template.condition.non.unit.name")

        override fun getId(): @NonNls String = "kotlin.nonUnit"

        override fun value(expr: KtExpression): Boolean {
            analyze(expr) {
                return expr.expressionType?.isUnitType == false
            }
        }
    }

    data object KotlinPostfixTemplateBooleanExpressionCondition : KotlinPostfixTemplateExpressionCondition {
        override fun getPresentableName(): String =
            KotlinPostfixTemplatesBundle.message("kotlin.postfix.template.condition.boolean.name")

        override fun getId(): @NonNls String = "kotlin.boolean"

        override fun value(expr: KtExpression): Boolean {
            analyze(expr) {
                return expr.expressionType?.isBooleanType == true
            }
        }
    }

    data object KotlinPostfixTemplateNumberExpressionCondition : KotlinPostfixTemplateExpressionCondition {
        private val numberClassIds = setOf(
            StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Short,
            StandardClassIds.Byte, StandardClassIds.Float, StandardClassIds.Double,
        )

        override fun getPresentableName(): String =
            KotlinPostfixTemplatesBundle.message("kotlin.postfix.template.condition.number.name")

        override fun getId(): @NonNls String = "kotlin.number"

        override fun value(expr: KtExpression): Boolean {
            analyze(expr) {
                return (expr.expressionType as? KaClassType)?.classId in numberClassIds
            }
        }
    }

    data object KotlinPostfixTemplateNullableExpressionCondition : KotlinPostfixTemplateExpressionCondition {
        override fun getPresentableName(): String =
            KotlinPostfixTemplatesBundle.message("kotlin.postfix.template.condition.nullable.name")

        override fun getId(): @NonNls String = "kotlin.nullable"

        override fun value(expr: KtExpression): Boolean {
            analyze(expr) {
                return expr.expressionType?.isMarkedNullable == true
            }
        }
    }

    data object KotlinPostfixTemplateNotNullableExpressionCondition : KotlinPostfixTemplateExpressionCondition {
        override fun getPresentableName(): String =
            KotlinPostfixTemplatesBundle.message("kotlin.postfix.template.condition.not.nullable.name")

        override fun getId(): @NonNls String = "kotlin.notNullable"

        override fun value(expr: KtExpression): Boolean {
            analyze(expr) {
                return expr.expressionType?.isMarkedNullable == false
            }
        }
    }
}
