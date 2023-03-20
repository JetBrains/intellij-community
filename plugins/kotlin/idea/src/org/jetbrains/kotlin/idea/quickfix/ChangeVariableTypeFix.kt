// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import java.util.*

open class ChangeVariableTypeFix(element: KtCallableDeclaration, type: KotlinType) : KotlinQuickFixAction<KtCallableDeclaration>(element) {
    private val typeContainsError = ErrorUtils.containsErrorType(type)
    private val typePresentation = IdeDescriptorRenderers.SOURCE_CODE_TYPES_WITH_SHORT_NAMES.renderType(type)
    private val typeSourceCode = IdeDescriptorRenderers.SOURCE_CODE_TYPES.renderType(type)

    open fun variablePresentation(): String? {
        val element = element!!
        val name = element.name
        return if (name != null) {
            val container = element.unsafeResolveToDescriptor().containingDeclaration as? ClassDescriptor
            val containerName = container?.name?.takeUnless { it.isSpecial }?.asString()
            if (containerName != null) "'$containerName.$name'" else "'$name'"
        } else {
            null
        }
    }

    override fun getText(): String {
        if (element == null) return ""

        val variablePresentation = variablePresentation()
        return if (variablePresentation != null) {
            KotlinBundle.message("change.type.of.0.to.1", variablePresentation, typePresentation)
        } else {
            KotlinBundle.message("change.type.to.0", typePresentation)
        }
    }

    class OnType(element: KtCallableDeclaration, type: KotlinType) : ChangeVariableTypeFix(element, type), HighPriorityAction {
        override fun variablePresentation() = null
    }

    class ForOverridden(element: KtVariableDeclaration, type: KotlinType) : ChangeVariableTypeFix(element, type) {
        override fun variablePresentation(): String? {
            val presentation = super.variablePresentation() ?: return null
            return KotlinBundle.message("base.property.0", presentation)
        }
    }

    override fun getFamilyName() = KotlinBundle.message("fix.change.return.type.family")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile) = !typeContainsError

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val psiFactory = KtPsiFactory(project)

        assert(element.nameIdentifier != null) { "ChangeVariableTypeFix applied to variable without name" }

        val replacingTypeReference = psiFactory.createType(typeSourceCode)
        val toShorten = ArrayList<KtTypeReference>()
        toShorten.add(element.setTypeReference(replacingTypeReference)!!)

        if (element is KtProperty) {
            val getterReturnTypeRef = element.getter?.returnTypeReference
            if (getterReturnTypeRef != null) {
                toShorten.add(getterReturnTypeRef.replace(replacingTypeReference) as KtTypeReference)
            }

            val setterParameterTypeRef = element.setter?.parameter?.typeReference
            if (setterParameterTypeRef != null) {
                toShorten.add(setterParameterTypeRef.replace(replacingTypeReference) as KtTypeReference)
            }
        }

        ShortenReferences.DEFAULT.process(toShorten)
    }

    object ComponentFunctionReturnTypeMismatchFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val entry = ChangeCallableReturnTypeFix.getDestructuringDeclarationEntryThatTypeMismatchComponentFunction(diagnostic)
            val context = entry.analyze()
            val resolvedCall = context.get(BindingContext.COMPONENT_RESOLVED_CALL, entry) ?: return null
            if (DescriptorToSourceUtils.descriptorToDeclaration(resolvedCall.candidateDescriptor) == null) return null
            val expectedType = resolvedCall.candidateDescriptor.returnType ?: return null
            return ChangeVariableTypeFix(entry, expectedType)
        }
    }

    object PropertyOrReturnTypeMismatchOnOverrideFactory : KotlinIntentionActionsFactory() {
        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            val actions = LinkedList<IntentionAction>()

            val element = diagnostic.psiElement as? KtCallableDeclaration
            if (element !is KtProperty && element !is KtParameter) return actions
            val descriptor = element.resolveToDescriptorIfAny(BodyResolveMode.FULL) as? PropertyDescriptor ?: return actions

            var lowerBoundOfOverriddenPropertiesTypes = QuickFixBranchUtil.findLowerBoundOfOverriddenCallablesReturnTypes(descriptor)

            val propertyType = descriptor.returnType ?: error("Property type cannot be null if it mismatches something")

            val overriddenMismatchingProperties = LinkedList<PropertyDescriptor>()
            var canChangeOverriddenPropertyType = true
            for (overriddenProperty in descriptor.overriddenDescriptors) {
                val overriddenPropertyType = overriddenProperty.returnType
                if (overriddenPropertyType != null) {
                    if (!KotlinTypeChecker.DEFAULT.isSubtypeOf(propertyType, overriddenPropertyType)) {
                        overriddenMismatchingProperties.add(overriddenProperty)
                    } else if (overriddenProperty.isVar && !KotlinTypeChecker.DEFAULT.equalTypes(
                            overriddenPropertyType,
                            propertyType
                        )
                    ) {
                        canChangeOverriddenPropertyType = false
                    }
                    if (overriddenProperty.isVar && lowerBoundOfOverriddenPropertiesTypes != null &&
                        !KotlinTypeChecker.DEFAULT.equalTypes(lowerBoundOfOverriddenPropertiesTypes, overriddenPropertyType)
                    ) {
                        lowerBoundOfOverriddenPropertiesTypes = null
                    }
                }
            }

            if (lowerBoundOfOverriddenPropertiesTypes != null) {
                actions.add(OnType(element, lowerBoundOfOverriddenPropertiesTypes))
            }

            if (overriddenMismatchingProperties.size == 1 && canChangeOverriddenPropertyType) {
                val overriddenProperty = DescriptorToSourceUtils.descriptorToDeclaration(overriddenMismatchingProperties.single())
                if (overriddenProperty is KtProperty) {
                    actions.add(ForOverridden(overriddenProperty, propertyType))
                }
            }

            return actions
        }
    }

    object VariableInitializedWithNullFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val binaryExpression = diagnostic.psiElement.getStrictParentOfType<KtBinaryExpression>() ?: return null
            val left = binaryExpression.left ?: return null
            if (binaryExpression.operationToken != KtTokens.EQ) return null
            val property = left.mainReference?.resolve() as? KtProperty ?: return null
            if (!property.isVar || property.typeReference != null || !property.initializer.isNullExpression()) return null
            val actualType = when (diagnostic.factory) {
                Errors.TYPE_MISMATCH -> Errors.TYPE_MISMATCH.cast(diagnostic).b
                Errors.TYPE_MISMATCH_WARNING -> Errors.TYPE_MISMATCH_WARNING.cast(diagnostic).b
                ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS ->
                    ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.cast(diagnostic).b
                else -> null
            } ?: return null
            return ChangeVariableTypeFix(property, actualType.makeNullable())
        }
    }
}
