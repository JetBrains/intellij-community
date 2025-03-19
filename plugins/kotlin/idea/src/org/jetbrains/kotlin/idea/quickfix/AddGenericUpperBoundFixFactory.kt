// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintsUtil
import org.jetbrains.kotlin.resolve.calls.inference.InferenceErrorData
import org.jetbrains.kotlin.resolve.calls.inference.constraintPosition.ConstraintPositionKind
import org.jetbrains.kotlin.resolve.calls.inference.filterConstraintsOut
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance

internal object AddGenericUpperBoundFixFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        return when (diagnostic.factory) {
            Errors.UPPER_BOUND_VIOLATED -> {
                val upperBoundViolated = Errors.UPPER_BOUND_VIOLATED.cast(diagnostic)
                listOfNotNull(createAction(upperBoundViolated.b, upperBoundViolated.a))
            }

            Errors.TYPE_INFERENCE_UPPER_BOUND_VIOLATED -> {
                val inferenceData = Errors.TYPE_INFERENCE_UPPER_BOUND_VIOLATED.cast(diagnostic).a
                createActionsByInferenceData(inferenceData)
            }

            ErrorsJvm.UPPER_BOUND_VIOLATED_BASED_ON_JAVA_ANNOTATIONS -> {
                val upperBoundViolated = ErrorsJvm.UPPER_BOUND_VIOLATED_BASED_ON_JAVA_ANNOTATIONS.cast(diagnostic)
                listOfNotNull(createAction(upperBoundViolated.b, upperBoundViolated.a))
            }

            else -> emptyList()
        }
    }

    private fun createActionsByInferenceData(inferenceData: InferenceErrorData): List<IntentionAction> {
        val successfulConstraintSystem = inferenceData.constraintSystem.filterConstraintsOut(ConstraintPositionKind.TYPE_BOUND_POSITION)

        if (!successfulConstraintSystem.status.isSuccessful()) return emptyList()

        val resultingSubstitutor = successfulConstraintSystem.resultingSubstitutor

        return inferenceData.descriptor.typeParameters.mapNotNull factory@{ typeParameterDescriptor ->

            if (ConstraintsUtil.checkUpperBoundIsSatisfied(
                    successfulConstraintSystem, typeParameterDescriptor, inferenceData.call,
                    /* substituteOtherTypeParametersInBound */ true
                )
            ) return@factory null

            val upperBound = typeParameterDescriptor.upperBounds.singleOrNull() ?: return@factory null
            val argument = resultingSubstitutor.substitute(typeParameterDescriptor.defaultType, Variance.INVARIANT)
                ?: return@factory null

            createAction(argument, upperBound)
        }
    }

    private fun createAction(argument: KotlinType, upperBound: KotlinType): IntentionAction? {
        if (!upperBound.constructor.isDenotable) return null

        val typeParameterDescriptor = (argument.constructor.declarationDescriptor as? TypeParameterDescriptor) ?: return null
        val typeParameterDeclaration =
            (DescriptorToSourceUtils.getSourceFromDescriptor(typeParameterDescriptor) as? KtTypeParameter) ?: return null

        if (typeParameterDeclaration.name == null || typeParameterDeclaration.extendsBound != null) return null

        return AddGenericUpperBoundFix(
            element = typeParameterDeclaration,
            fqName = IdeDescriptorRenderers.SOURCE_CODE.renderType(upperBound),
            shortName = IdeDescriptorRenderers.SOURCE_CODE_TYPES_WITH_SHORT_NAMES.renderType(upperBound),
        ).asIntention()
    }
}
