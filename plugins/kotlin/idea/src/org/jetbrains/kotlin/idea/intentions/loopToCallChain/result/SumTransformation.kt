// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.loopToCallChain.result

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.*
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.sequence.MapTransformation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

abstract class SumTransformationBase(
    loop: KtForExpression,
    initialization: VariableInitialization
) : AssignToVariableResultTransformation(loop, initialization) {

    override fun generateCode(chainedCallGenerator: ChainedCallGenerator): KtExpression {
        val call = generateCall(chainedCallGenerator)

        return if (initialization.initializer.isZeroConstant()) {
            call
        } else {
            KtPsiFactory(call.project).createExpressionByPattern(
                "$0 + $1", initialization.initializer, call,
                reformat = chainedCallGenerator.reformat
            )
        }
    }

    protected abstract fun generateCall(chainedCallGenerator: ChainedCallGenerator): KtExpression

    /**
     * Matches:
     *     val variable = <initial>
     *     for (...) {
     *         ...
     *         variable += <expression>
     *     }
     */
    object Matcher : TransformationMatcher {
        override val indexVariableAllowed: Boolean
            get() = true

        override fun match(state: MatchingState): TransformationMatch.Result? {
            val statement = state.statements.singleOrNull() as? KtBinaryExpression ?: return null
            if (statement.operationToken != KtTokens.PLUSEQ) return null

            val variableInitialization =
                statement.left.findVariableInitializationBeforeLoop(state.outerLoop, checkNoOtherUsagesInLoop = true)
                    ?: return null

            val value = statement.right ?: return null

            val valueType = value.typeWithSmartCast()?.toSupportedType() ?: return null
            val sumType = variableInitialization.variable.resolveToDescriptorIfAny()?.type?.toSupportedType() ?: return null

            val conversionFunctionName = when (sumType) {
                SupportedType.INT -> {
                    val needConversion = when (valueType) {
                        SupportedType.INT -> false

                        SupportedType.BYTE, SupportedType.SHORT -> {
                            // we don't need conversion to Int to use "sum" function but need it for "sumBy"
                            !value.isVariableReference(state.inputVariable) && state.indexVariable == null
                        }

                        else -> true
                    }
                    if (needConversion) "toInt" else null
                }

                SupportedType.LONG -> if (valueType != SupportedType.LONG) "toLong" else null

                SupportedType.FLOAT -> if (valueType != SupportedType.FLOAT) "toFloat" else null

                SupportedType.DOUBLE -> if (valueType != SupportedType.DOUBLE) "toDouble" else null

                SupportedType.BYTE, SupportedType.SHORT -> return null // cannot use sum or sumBy to get Byte or Short result
            }

            val byExpression = if (conversionFunctionName != null)
                KtPsiFactory(value.project).createExpressionByPattern("$0.$conversionFunctionName()", value, reformat = state.reformat)
            else
                value

            if (byExpression.isVariableReference(state.inputVariable)) {
                val transformation = SumTransformation(state.outerLoop, variableInitialization)
                return TransformationMatch.Result(transformation)
            }

            if (state.indexVariable != null) {
                val mapTransformation =
                    MapTransformation(state.outerLoop, state.inputVariable, state.indexVariable, byExpression, mapNotNull = false)
                val sumTransformation = SumTransformation(state.outerLoop, variableInitialization)
                return TransformationMatch.Result(sumTransformation, mapTransformation)
            }

            val apiVersion = statement.languageVersionSettings.apiVersion
            val sumByFunctionName = when (sumType) {
                SupportedType.INT -> if (apiVersion < ApiVersion.KOTLIN_1_4) "sumBy" else "sumOf"

                SupportedType.DOUBLE -> if (apiVersion < ApiVersion.KOTLIN_1_4) "sumByDouble" else "sumOf"

                else -> {
                    val mapTransformation = MapTransformation(state.outerLoop, state.inputVariable, null, byExpression, mapNotNull = false)
                    val sumTransformation = SumTransformation(state.outerLoop, variableInitialization)
                    return TransformationMatch.Result(sumTransformation, mapTransformation)
                }
            }

            val transformation =
                SumByTransformation(state.outerLoop, variableInitialization, state.inputVariable, byExpression, sumByFunctionName)
            return TransformationMatch.Result(transformation)
        }

        private enum class SupportedType {
            INT, LONG, SHORT, BYTE, DOUBLE, FLOAT
        }

        private fun KotlinType.toSupportedType(): SupportedType? {
            return when {
                KotlinBuiltIns.isInt(this) -> SupportedType.INT
                KotlinBuiltIns.isLong(this) -> SupportedType.LONG
                KotlinBuiltIns.isShort(this) -> SupportedType.SHORT
                KotlinBuiltIns.isByte(this) -> SupportedType.BYTE
                KotlinBuiltIns.isDouble(this) -> SupportedType.DOUBLE
                KotlinBuiltIns.isFloat(this) -> SupportedType.FLOAT
                else -> null
            }
        }

        private fun KtExpression.typeWithSmartCast(): KotlinType? {
            val bindingContext = analyze(BodyResolveMode.PARTIAL)
            return bindingContext[BindingContext.SMARTCAST, this]?.defaultType
                ?: bindingContext.getType(this)
        }
    }
}

class SumTransformation(loop: KtForExpression, initialization: VariableInitialization) : SumTransformationBase(loop, initialization) {
    override val presentation: String
        get() = "sum()"

    override fun generateCall(chainedCallGenerator: ChainedCallGenerator): KtExpression {
        return chainedCallGenerator.generate("sum()")
    }
}

class SumByTransformation(
    loop: KtForExpression,
    initialization: VariableInitialization,
    private val inputVariable: KtCallableDeclaration,
    private val byExpression: KtExpression,
    private val functionName: String
) : SumTransformationBase(loop, initialization) {

    override val presentation: String
        get() = "$functionName{}"

    override fun generateCall(chainedCallGenerator: ChainedCallGenerator): KtExpression {
        val lambda = generateLambda(inputVariable, byExpression, chainedCallGenerator.reformat)
        return chainedCallGenerator.generate("$functionName $0:'{}'", lambda)
    }
}