// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix.editable

import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.openapi.util.NlsSafe
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeInsight.postfix.KotlinPostfixTemplatesBundle
import org.jetbrains.kotlin.idea.imports.ImportMapper
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addIfNotNull

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

        /**
         * Returns the fully qualified names of the symbol and its expanded symbol as well
         * as any alias used for Kotlin <-> Java interop(example `java.lang.Exception` <-> `kotlin.Exception`).
         */
        @OptIn(KaContextParameterApi::class, KaIdeApi::class)
        context(_: KaSession)
        private fun KaType.getFqNamesWithJavaImportAlias(apiVersion: ApiVersion): Set<String> = buildSet {
            val ownSymbol = symbol
            // Add the FQN of the symbol itself
            val ownFqName = ownSymbol?.importableFqName
            addIfNotNull(ownFqName?.asString())

            // If the symbol is a typealias, also add the FQN of the expanded symbol
            val expandedSymbol = expandedSymbol
            addIfNotNull(expandedSymbol?.importableFqName?.asString())
            if (ownFqName == null) return@buildSet

            // In case the symbol is a Kotlin type alias of some Java class (or vice versa),
            // we want to match it both ways using the `ImportMapper` and `JavaToKotlinClassMap`

            // Java alias -> Kotlin
            val mappedFqName = ImportMapper.findCorrespondingKotlinFqName(ownFqName, apiVersion)
            addIfNotNull(mappedFqName?.asString())

            // Kotlin alias -> Java
            val mappedJavaFqName = JavaToKotlinClassMap.mapKotlinToJava(ownFqName.toUnsafe())
            addIfNotNull(mappedJavaFqName?.asFqNameString())
        }

        override fun value(expr: KtExpression): Boolean {
            return analyze(expr) {
                val classType = (expr.expressionType?.symbol as? KaClassifierSymbol)?.defaultType ?: return false
                val allSupertypesWithSelf = sequence {
                    yield(classType)
                    yieldAll(classType.allSupertypes)
                }

                val apiVersion = expr.containingFile.languageVersionSettings.apiVersion
                allSupertypesWithSelf
                    .flatMap { it.getFqNamesWithJavaImportAlias(apiVersion) }
                    .any { it == fqn }
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
