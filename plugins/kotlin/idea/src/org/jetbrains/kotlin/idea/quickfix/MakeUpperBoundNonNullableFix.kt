// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.isOverridable
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getTypeParameters
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.NotNullTypeParameter
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * A quick fix that make a type parameter non-nullable by either adding `Any` as an upper bound or by changing an existing
 * explicitly nullable upper bound with its non-nullable subtype.
 *
 * Since Kotlin 1.6.20, the compiler generates warnings if Kotlin code uses nullable generic types when calling Java code
 * with `@NotNull` annotations (these warnings are going to become errors in future releases, see
 * https://youtrack.jetbrains.com/issue/KT-36770 for details). Adding a non-nullable upper bound to a type parameter
 * changes the function or classifier interface, but this can fix a lot of warnings and errors, so one may view it
 * as a "default" fix for this kind of errors.
 *
 * @param typeParameter the type parameter to update
 * @param kind the required modification of the upper bound
 */
open class MakeUpperBoundNonNullableFix(
    typeParameter: KtTypeParameter,
    private val kind: Kind
) : KotlinQuickFixAction<KtTypeParameter>(typeParameter) {

    /**
     * The set of actions that can be performed
     */
    sealed class Kind {
        abstract val renderedUpperBound: String

        @IntentionName
        abstract fun getText(parameter: KtTypeParameter): String

        /**
         * Add `Any` as an upper bound
         */
        object AddAnyAsUpperBound : Kind() {
            override val renderedUpperBound = StandardNames.FqNames.any.render()

            override fun getText(parameter: KtTypeParameter): String = KotlinBundle.message(
                "fix.make.upperbound.not.nullable.any.text",
                parameter.name ?: ""
            )
        }

        /**
         * Replace an existing upper bound with another upper bound
         * @param replacement the type of the new upper bound
         */
        class ReplaceExistingUpperBound(val replacement: KotlinType) : Kind() {
            override val renderedUpperBound = IdeDescriptorRenderers.SOURCE_CODE.renderType(replacement)

            override fun getText(parameter: KtTypeParameter): String = KotlinBundle.message(
                "fix.make.upperbound.not.nullable.remove.nullability.text",
                parameter.name ?: "",
                renderedUpperBound
            )
        }
    }

    override fun getText(): String = element?.let { kind.getText(it) } ?: ""
    override fun getFamilyName() = KotlinBundle.message("fix.make.upperbound.not.nullable.family")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = this.element ?: return false
        return element.name != null &&
                when (kind) {
                    Kind.AddAnyAsUpperBound -> element.extendsBound == null
                    is Kind.ReplaceExistingUpperBound -> element.extendsBound != null
                }
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val typeParameter = element ?: return
        val typeReference = KtPsiFactory(project).createType(kind.renderedUpperBound)
        val insertedTypeReference = typeParameter.setExtendsBound(typeReference) ?: return
        ShortenReferences.DEFAULT.process(insertedTypeReference)
    }

    companion object : KotlinIntentionActionsFactory() {
        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            return when (diagnostic.factory) {
                ErrorsJvm.NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER -> {
                    val info = ErrorsJvm.NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER.cast(diagnostic)
                    listOfNotNull(createActionForTypeParameter(info.a))
                }

                ErrorsJvm.WRONG_TYPE_PARAMETER_NULLABILITY_FOR_JAVA_OVERRIDE -> {
                    val info = ErrorsJvm.WRONG_TYPE_PARAMETER_NULLABILITY_FOR_JAVA_OVERRIDE.cast(diagnostic)
                    listOfNotNull(createActionForTypeParameter(info.a))
                }

                Errors.TYPE_MISMATCH -> {
                    val info = Errors.TYPE_MISMATCH.cast(diagnostic)
                    createActionsForTypeMismatch(actual = info.b, expected = info.a)
                }

                Errors.TYPE_MISMATCH_WARNING -> {
                    val info = Errors.TYPE_MISMATCH_WARNING.cast(diagnostic)
                    createActionsForTypeMismatch(actual = info.b, expected = info.a)
                }

                ErrorsJvm.WRONG_NULLABILITY_FOR_JAVA_OVERRIDE -> {
                    val info = ErrorsJvm.WRONG_NULLABILITY_FOR_JAVA_OVERRIDE.cast(diagnostic)
                    createActionsForOverride(actual = info.a, expected = info.b)
                }

                Errors.NOTHING_TO_OVERRIDE -> {
                    val info = Errors.NOTHING_TO_OVERRIDE.cast(diagnostic)
                    createActionsForOverrideNothing(actual = info.a)
                }

                else -> emptyList()
            }
        }

        private fun extractPotentiallyFixableTypesForExpectedType(actual: KotlinType, expected: KotlinType): Set<KotlinType> {
            fun KotlinType.isNonNullable() = !this.isNullable() || this is NotNullTypeParameter

            val result = mutableSetOf<KotlinType>()
            if (actual.isTypeParameter() && actual.isNullable() && expected.isNonNullable()) {
                result.add(actual)
            }

            for ((actualProjection, expectedProjection) in actual.arguments.zip(expected.arguments)) {
                result.addAll(extractPotentiallyFixableTypesForExpectedType(actualProjection.type, expectedProjection.type))
            }

            return result
        }

        private fun createActionsForOverride(actual: CallableMemberDescriptor, expected: CallableMemberDescriptor): List<IntentionAction> {
            val result = mutableListOf<IntentionAction>()
            for ((actualParameter, expectedParameter) in actual.valueParameters.zip(expected.valueParameters)) {
                val actualType = actualParameter?.type ?: continue
                val expectedType = expectedParameter?.type ?: continue
                extractPotentiallyFixableTypesForExpectedType(actualType, expectedType).forEach {
                    result.addAll(createActionsForType(it))
                }
            }
            return result
        }

        // Generate actions for NOTHING_TO_OVERRIDE error. The function searches for compatible override candidates
        // in parent classes and tries to find an upper bound that makes the nullability of the actual function
        // arguments match the candidate's arguments.

        private fun createActionsForOverrideNothing(actual: CallableMemberDescriptor): List<IntentionAction> {
            val functionName = actual.name
            val containingClass = actual.containingDeclaration.safeAs<ClassDescriptor>() ?: return emptyList()
            return containingClass.defaultType.supertypes()
                .asSequence()
                .flatMap { supertype -> supertype.memberScope.getContributedFunctions(functionName, NoLookupLocation.FROM_IDE) }
                .filter { it.kind.isReal && it.isOverridable }
                .flatMap { candidate -> createActionsForOverride(actual, candidate) }
                .toList()
        }

        private fun createActionsForTypeMismatch(actual: KotlinType, expected: KotlinType): List<IntentionAction> {
            val result = mutableListOf<IntentionAction>()
            val fixableTypes = extractPotentiallyFixableTypesForExpectedType(actual, expected)
            for (type in fixableTypes) {
                result.addAll(createActionsForType(type, highPriority = true))
            }
            return result
        }

        private fun createActionsForType(type: KotlinType, highPriority: Boolean = false): List<IntentionAction> {
            val result = mutableListOf<IntentionAction>()
            for (typeParameterDescriptor in type.getTypeParameters()) {
                result.addIfNotNull(createActionForTypeParameter(typeParameterDescriptor, highPriority))
            }
            return result
        }

        private fun createActionForTypeParameter(
            typeParameterDescriptor: TypeParameterDescriptor,
            highPriority: Boolean = false,
        ): IntentionAction? {
            fun makeAction(typeParameter: KtTypeParameter, kind: Kind): IntentionAction {
                return if (highPriority)
                    HighPriorityMakeUpperBoundNonNullableFix(typeParameter, kind)
                else
                    MakeUpperBoundNonNullableFix(typeParameter, kind)
            }

            val psiElement = typeParameterDescriptor.psiElement?.safeAs<KtTypeParameter>() ?: return null
            val existingUpperBound = psiElement.extendsBound
            if (existingUpperBound != null) {
                val context = existingUpperBound.analyze(BodyResolveMode.PARTIAL)
                val upperBoundType = context[BindingContext.TYPE, existingUpperBound] ?: return null
                if (upperBoundType.isMarkedNullable) {
                    return makeAction(psiElement, Kind.ReplaceExistingUpperBound(upperBoundType.makeNotNullable()))
                }
            } else {
                return makeAction(psiElement, Kind.AddAnyAsUpperBound)
            }

            return null
        }

    }
}

/**
 * A higher priority version of the parent fix for handling type mismatch errors.

 * The type mismatch error (`T & Any` expected, `T` found) may usually be resolved
 * by either replacing the `T` type with a definitely non-nullable type `T & Any`, or
 * by adding a non-nullable upper bound constraint to the type parameter `T`.
 * The latter fix is more general and does not depend on the language version settings
 * of the module, so it should be proposed first.
 */
class HighPriorityMakeUpperBoundNonNullableFix(
    typeParameter: KtTypeParameter,
    kind: Kind
) : MakeUpperBoundNonNullableFix(typeParameter, kind),
    HighPriorityAction
