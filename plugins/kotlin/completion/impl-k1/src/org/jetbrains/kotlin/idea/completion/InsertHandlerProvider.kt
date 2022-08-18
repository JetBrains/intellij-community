// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.completion.handlers.*
import org.jetbrains.kotlin.idea.core.ExpectedInfo
import org.jetbrains.kotlin.idea.core.fuzzyType
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.fuzzyReturnType
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.util.getValueParametersCountFromFunctionType
import org.jetbrains.kotlin.types.KotlinType

class InsertHandlerProvider(
    private val callType: CallType<*>,
    private val editor: Editor,
    expectedInfosCalculator: () -> Collection<ExpectedInfo>,
) {
    private val expectedInfos by lazy(LazyThreadSafetyMode.NONE) { expectedInfosCalculator() }

    companion object {
        fun isKotlinLambda(descriptor: DeclarationDescriptor, callType: CallType<*>): Boolean {
            if (descriptor is FunctionDescriptor) {
                if (listOf(CallType.DEFAULT, CallType.DOT, CallType.SAFE).contains(callType)) {
                    val parameters = descriptor.valueParameters
                    if (parameters.size == 1) {
                        val parameter = parameters.single()
                        val parameterType = parameter.type

                        if (parameterType.isBuiltinFunctionalType && getValueParametersCountFromFunctionType(parameterType) <= 1 && !parameter.hasDefaultValue()) {
                            return true
                        }
                    }
                }
            }

            return false
        }
    }

    fun insertHandler(descriptor: DeclarationDescriptor, argumentsOnly: Boolean = false): InsertHandler<LookupElement> {
        return when (descriptor) {
            is FunctionDescriptor -> {
                when (callType) {
                    CallType.DEFAULT, CallType.DOT, CallType.SAFE, CallType.SUPER_MEMBERS -> {
                        if (!EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically) {
                            return KotlinFunctionInsertHandler.OnlyName(callType)
                        }
                        val needTypeArguments = needTypeArguments(descriptor)
                        val parameters = descriptor.valueParameters
                        val functionName = descriptor.name
                        when (parameters.size) {
                            0 -> {
                                createNormalFunctionInsertHandler(editor, callType, functionName, needTypeArguments,
                                    inputValueArguments = false, argumentsOnly = argumentsOnly)
                            }

                            1 -> {
                                if (callType != CallType.SUPER_MEMBERS) { // for super call we don't suggest to generate "super.foo { ... }" (seems to be non-typical use)
                                    val parameter = parameters.single()
                                    val parameterType = parameter.type
                                    if (parameterType.isBuiltinFunctionalType) {
                                        if (getValueParametersCountFromFunctionType(parameterType) <= 1 && !parameter.hasDefaultValue()) {
                                            // otherwise additional item with lambda template is to be added
                                            return KotlinFunctionInsertHandler.Normal(
                                                callType, needTypeArguments, inputValueArguments = false,
                                                lambdaInfo = GenerateLambdaInfo(parameterType, false), argumentsOnly = argumentsOnly
                                            )
                                        }
                                    }
                                }

                                createNormalFunctionInsertHandler(editor, callType, functionName, inputTypeArguments = needTypeArguments, inputValueArguments = true, argumentsOnly = argumentsOnly)
                            }

                            else -> createNormalFunctionInsertHandler(editor, callType, functionName, needTypeArguments, inputValueArguments = true, argumentsOnly = argumentsOnly)
                        }
                    }

                    CallType.INFIX -> KotlinFunctionInsertHandler.Infix

                    else -> KotlinFunctionInsertHandler.OnlyName(callType)
                }

            }

            is PropertyDescriptor -> KotlinPropertyInsertHandler(callType)

            is ClassifierDescriptor -> KotlinClassifierInsertHandler

            else -> BaseDeclarationInsertHandler()
        }
    }

    fun needTypeArguments(function: FunctionDescriptor): Boolean {
        if (function.typeParameters.isEmpty()) return false

        val originalFunction = function.original
        val typeParameters = originalFunction.typeParameters

        val potentiallyInferred = HashSet<TypeParameterDescriptor>()

        fun addPotentiallyInferred(type: KotlinType) {
            val descriptor = type.constructor.declarationDescriptor as? TypeParameterDescriptor
            if (descriptor != null && descriptor in typeParameters && descriptor !in potentiallyInferred) {
                potentiallyInferred.add(descriptor)
                // Add possible inferred by type-arguments of upper-bound of parameter
                // e.g. <T, C: Iterable<T>>, so T inferred from C
                descriptor.upperBounds.filter { it.arguments.isNotEmpty() }.forEach(::addPotentiallyInferred)
            }

            if (type.isBuiltinFunctionalType && getValueParametersCountFromFunctionType(type) <= 1) {
                // do not rely on inference from input of function type with one or no arguments - use only return type of functional type
                addPotentiallyInferred(type.getReturnTypeFromFunctionType())
                return
            }

            for (argument in type.arguments) {
                if (!argument.isStarProjection) { // otherwise we can fall into infinite recursion
                    addPotentiallyInferred(argument.type)
                }
            }
        }

        originalFunction.extensionReceiverParameter?.type?.let(::addPotentiallyInferred)
        originalFunction.valueParameters.forEach { addPotentiallyInferred(it.type) }

        fun allTypeParametersPotentiallyInferred() = originalFunction.typeParameters.all { it in potentiallyInferred }

        if (allTypeParametersPotentiallyInferred()) return false

        val returnType = originalFunction.returnType
        // check that there is an expected type and return value from the function can potentially match it
        if (returnType != null) {
            addPotentiallyInferred(returnType)

            if (allTypeParametersPotentiallyInferred() && expectedInfos.any {
                    it.fuzzyType?.checkIsSuperTypeOf(originalFunction.fuzzyReturnType()!!) != null
                }
            ) {
                return false
            }
        }

        return true
    }
}