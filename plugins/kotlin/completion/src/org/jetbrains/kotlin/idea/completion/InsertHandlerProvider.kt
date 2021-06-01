// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
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
import java.util.*

class InsertHandlerProvider(
    private val callType: CallType<*>,
    expectedInfosCalculator: () -> Collection<ExpectedInfo>
) {
    private val expectedInfos by lazy(LazyThreadSafetyMode.NONE) { expectedInfosCalculator() }

    fun insertHandler(descriptor: DeclarationDescriptor): InsertHandler<LookupElement> {
        return when (descriptor) {
            is FunctionDescriptor -> {
                when (callType) {
                    CallType.DEFAULT, CallType.DOT, CallType.SAFE, CallType.SUPER_MEMBERS -> {
                        val needTypeArguments = needTypeArguments(descriptor)
                        val parameters = descriptor.valueParameters
                        when (parameters.size) {
                            0 -> KotlinFunctionInsertHandler.Normal(callType, needTypeArguments, inputValueArguments = false)

                            1 -> {
                                if (callType != CallType.SUPER_MEMBERS) { // for super call we don't suggest to generate "super.foo { ... }" (seems to be non-typical use)
                                    val parameter = parameters.single()
                                    val parameterType = parameter.type
                                    if (parameterType.isBuiltinFunctionalType) {
                                        if (getValueParametersCountFromFunctionType(parameterType) <= 1 && !parameter.hasDefaultValue()) {
                                            // otherwise additional item with lambda template is to be added
                                            return KotlinFunctionInsertHandler.Normal(
                                                callType, needTypeArguments, inputValueArguments = false,
                                                lambdaInfo = GenerateLambdaInfo(parameterType, false)
                                            )
                                        }
                                    }
                                }

                                KotlinFunctionInsertHandler.Normal(callType, needTypeArguments, inputValueArguments = true)
                            }

                            else -> KotlinFunctionInsertHandler.Normal(callType, needTypeArguments, inputValueArguments = true)
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

    private fun needTypeArguments(function: FunctionDescriptor): Boolean {
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