// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryForDeprecation
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.idea.core.overrideImplement.ImplementAsConstructorParameter
import org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
import org.jetbrains.kotlin.idea.inspections.AddModifierFixFactory
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.*
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromCallWithConstructorCalleeActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromConstructorCallActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromReferenceExpressionActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromTypeReferenceActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterByUnresolvedRefActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterUnmatchedTypeArgumentActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterByNamedArgumentActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterByRefActionFactory
import org.jetbrains.kotlin.idea.quickfix.expectactual.ActualAnnotationsNotMatchExpectFixFactory
import org.jetbrains.kotlin.idea.quickfix.expectactual.AddActualFix
import org.jetbrains.kotlin.idea.quickfix.expectactual.CreateExpectedFix
import org.jetbrains.kotlin.idea.quickfix.expectactual.CreateMissedActualsFix
import org.jetbrains.kotlin.idea.quickfix.migration.MigrateExperimentalToRequiresOptInFix
import org.jetbrains.kotlin.idea.quickfix.migration.MigrateExternalExtensionFix
import org.jetbrains.kotlin.idea.quickfix.migration.MigrateTypeParameterListFix
import org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
import org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageInWholeProjectFix
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFixFactory
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs.EXTENSION_FUNCTION_IN_EXTERNAL_DECLARATION
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs.WRONG_EXTERNAL_DECLARATION
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm.*
import org.jetbrains.kotlin.resolve.konan.diagnostics.ErrorsNative.INCOMPATIBLE_THROWS_OVERRIDE
import org.jetbrains.kotlin.resolve.konan.diagnostics.ErrorsNative.MISSING_EXCEPTION_IN_THROWS_ON_SUSPEND
import org.jetbrains.kotlin.resolve.konan.diagnostics.ErrorsNative.THROWS_LIST_EMPTY

class QuickFixRegistrar : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
        fun DiagnosticFactory<*>.registerFactory(vararg factory: QuickFixFactory) {
            quickFixes.register(this, *factory)
        }

        fun DiagnosticFactoryForDeprecation<*, *, *>.registerFactory(vararg factory: QuickFixFactory) {
            quickFixes.register(this.errorFactory, *factory)
            quickFixes.register(this.warningFactory, *factory)
        }

        fun DiagnosticFactory<*>.registerActions(vararg action: IntentionAction) {
            quickFixes.register(this, *action)
        }

        val addAbstractModifierFactory = AddModifierFixFE10.createFactory(ABSTRACT_KEYWORD)

        ABSTRACT_PROPERTY_IN_PRIMARY_CONSTRUCTOR_PARAMETERS.registerFactory(RemoveModifierFixBase.removeAbstractModifier)

        ABSTRACT_PROPERTY_WITH_INITIALIZER.registerFactory(RemoveModifierFixBase.removeAbstractModifier, RemovePartsFromPropertyFix)
        ABSTRACT_PROPERTY_WITH_GETTER.registerFactory(RemoveModifierFixBase.removeAbstractModifier, RemovePartsFromPropertyFix)
        ABSTRACT_PROPERTY_WITH_SETTER.registerFactory(RemoveModifierFixBase.removeAbstractModifier, RemovePartsFromPropertyFix)

        PROPERTY_INITIALIZER_IN_INTERFACE.registerFactory(RemovePartsFromPropertyFix, ConvertPropertyInitializerToGetterIntention.Factory)

        MUST_BE_INITIALIZED_OR_BE_ABSTRACT.registerFactory(addAbstractModifierFactory)
        MUST_BE_INITIALIZED_OR_BE_ABSTRACT_WARNING.registerFactory(addAbstractModifierFactory)
        MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT.registerFactory(addAbstractModifierFactory)
        MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT_WARNING.registerFactory(addAbstractModifierFactory)
        ABSTRACT_MEMBER_NOT_IMPLEMENTED.registerFactory(addAbstractModifierFactory)
        ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED.registerFactory(addAbstractModifierFactory)
        ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED_WARNING.registerFactory(addAbstractModifierFactory)

        MUST_BE_INITIALIZED.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED_WARNING.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED_OR_BE_FINAL.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED_OR_BE_FINAL_WARNING.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED_OR_BE_ABSTRACT.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED_OR_BE_ABSTRACT_WARNING.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT_WARNING.registerFactory(InitializePropertyQuickFixFactory)

        val addFinalToProperty = AddModifierFixFE10.createFactory(FINAL_KEYWORD, KtProperty::class.java)
        MUST_BE_INITIALIZED_OR_BE_FINAL.registerFactory(addFinalToProperty)
        MUST_BE_INITIALIZED_OR_BE_FINAL_WARNING.registerFactory(addFinalToProperty)
        MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT.registerFactory(addFinalToProperty)
        MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT_WARNING.registerFactory(addFinalToProperty)

        val addAbstractToClassFactory = AddModifierFixFE10.createFactory(ABSTRACT_KEYWORD, KtClassOrObject::class.java)
        ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS.registerFactory(RemoveModifierFixBase.removeAbstractModifier, addAbstractToClassFactory)

        ABSTRACT_FUNCTION_IN_NON_ABSTRACT_CLASS.registerFactory(RemoveModifierFixBase.removeAbstractModifier, addAbstractToClassFactory)

        ABSTRACT_FUNCTION_WITH_BODY.registerFactory(RemoveModifierFixBase.removeAbstractModifier, RemoveFunctionBodyFixFactory)

        NON_ABSTRACT_FUNCTION_WITH_NO_BODY.registerFactory(addAbstractModifierFactory, AddFunctionBodyFix)

        NON_VARARG_SPREAD.registerFactory(RemovePsiElementSimpleFix.RemoveSpreadFactory)

        MIXING_NAMED_AND_POSITIONED_ARGUMENTS.registerFactory(AddNameToArgumentFix)

        NON_MEMBER_FUNCTION_NO_BODY.registerFactory(AddFunctionBodyFix)

        NOTHING_TO_OVERRIDE.registerFactory(
            RemoveModifierFixBase.createRemoveModifierFromListOwnerPsiBasedFactory(OVERRIDE_KEYWORD),
            ChangeMemberFunctionSignatureFix,
            AddFunctionToSupertypeFix,
            AddPropertyToSupertypeFix
        )
        VIRTUAL_MEMBER_HIDDEN.registerFactory(AddModifierFixFE10.createFactory(OVERRIDE_KEYWORD))

        USELESS_CAST.registerFactory(RemoveUselessCastFix)

        USELESS_IS_CHECK.registerFactory(RemoveUselessIsCheckFix, RemoveUselessIsCheckFixForWhen)

        WRONG_SETTER_PARAMETER_TYPE.registerFactory(ChangeAccessorTypeFixFactory)
        WRONG_GETTER_RETURN_TYPE.registerFactory(ChangeAccessorTypeFixFactory)

        USELESS_ELVIS.registerFactory(RemoveUselessElvisFix)
        USELESS_ELVIS_RIGHT_IS_NULL.registerFactory(RemoveUselessElvisFix)

        REDUNDANT_MODIFIER.registerFactory(RemoveModifierFixBase.removeRedundantModifier)
        REDUNDANT_OPEN_IN_INTERFACE.registerFactory(RemoveModifierFixBase.createRemoveModifierFromListOwnerPsiBasedFactory(OPEN_KEYWORD, true))
        REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE.registerFactory(RemoveModifierFixBase.createRemoveSuspendFactory())
        UNNECESSARY_LATEINIT.registerFactory(RemoveModifierFixBase.createRemoveModifierFromListOwnerPsiBasedFactory(LATEINIT_KEYWORD))

        REDUNDANT_PROJECTION.registerFactory(RemoveModifierFixBase.createRemoveProjectionFactory(true))
        INCOMPATIBLE_MODIFIERS.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        VARIANCE_ON_TYPE_PARAMETER_NOT_ALLOWED.registerFactory(RemoveModifierFixBase.createRemoveVarianceFactory())

        NON_FINAL_MEMBER_IN_FINAL_CLASS.registerFactory(
            AddModifierFixFE10.createFactory(OPEN_KEYWORD, KtClass::class.java),
            RemoveModifierFixBase.removeOpenModifier
        )
        NON_FINAL_MEMBER_IN_OBJECT.registerFactory(RemoveModifierFixBase.removeOpenModifier)

        GETTER_VISIBILITY_DIFFERS_FROM_PROPERTY_VISIBILITY.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        SETTER_VISIBILITY_INCONSISTENT_WITH_PROPERTY_VISIBILITY.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        PRIVATE_SETTER_FOR_OPEN_PROPERTY.registerFactory(addFinalToProperty, RemoveModifierFixBase.removeNonRedundantModifier)
        REDUNDANT_MODIFIER_IN_GETTER.registerFactory(RemoveModifierFixBase.removeRedundantModifier)
        WRONG_MODIFIER_TARGET.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier, ChangeVariableMutabilityFix.CONST_VAL_FACTORY)
        REDUNDANT_MODIFIER_FOR_TARGET.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        WRONG_MODIFIER_CONTAINING_DECLARATION.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        REPEATED_MODIFIER.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        NON_PRIVATE_CONSTRUCTOR_IN_ENUM.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        NON_PRIVATE_CONSTRUCTOR_IN_SEALED.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        NON_PRIVATE_OR_PROTECTED_CONSTRUCTOR_IN_SEALED.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        TYPE_CANT_BE_USED_FOR_CONST_VAL.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        DEPRECATED_BINARY_MOD.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        DEPRECATED_BINARY_MOD.registerFactory(RenameModToRemFixFactory)
        FORBIDDEN_BINARY_MOD.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)
        FORBIDDEN_BINARY_MOD.registerFactory(RenameModToRemFixFactory)

        NO_EXPLICIT_VISIBILITY_IN_API_MODE.registerFactory(ChangeVisibilityFix.SetExplicitVisibilityFactory)
        NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING.registerFactory(ChangeVisibilityFix.SetExplicitVisibilityFactory)
        NO_EXPLICIT_RETURN_TYPE_IN_API_MODE.registerActions(SpecifyTypeExplicitlyFix())
        NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING.registerActions(SpecifyTypeExplicitlyFix())

        INVISIBLE_REFERENCE.registerFactory(ImportFix)
        INVISIBLE_MEMBER.registerFactory(ImportFix)
        UNRESOLVED_REFERENCE.registerFactory(ImportFix)

        UNRESOLVED_REFERENCE.registerFactory(ImportConstructorReferenceFix)
        DEPRECATED_ACCESS_BY_SHORT_NAME.registerFactory(AddExplicitImportForDeprecatedVisibilityFix.Factory)
        TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG.registerFactory(AddSpreadOperatorForArrayAsVarargAfterSamFixFactory)

        TOO_MANY_ARGUMENTS.registerFactory(ImportForMismatchingArgumentsFixFactoryWithUnresolvedReferenceQuickFix)
        NO_VALUE_FOR_PARAMETER.registerFactory(ImportForMismatchingArgumentsFix)
        TYPE_MISMATCH.registerFactory(ImportForMismatchingArgumentsFix)
        TYPE_MISMATCH_WARNING.registerFactory(ImportForMismatchingArgumentsFix)
        CONSTANT_EXPECTED_TYPE_MISMATCH.registerFactory(ImportForMismatchingArgumentsFix)
        NAMED_PARAMETER_NOT_FOUND.registerFactory(ImportForMismatchingArgumentsFix)
        NONE_APPLICABLE.registerFactory(ImportForMismatchingArgumentsFix)
        WRONG_NUMBER_OF_TYPE_ARGUMENTS.registerFactory(ImportForMismatchingArgumentsFix)
        NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER.registerFactory(ImportForMismatchingArgumentsFix)
        TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER.registerFactory(ImportForMismatchingArgumentsFix)

        INAPPLICABLE_TARGET_ON_PROPERTY.registerFactory(
            RemoveAnnotationFix.UseSiteGetDoesntHaveAnyEffect,
            RemoveUseSiteTargetFix.UseSiteGetDoesntHaveAnyEffect
        )
        INAPPLICABLE_TARGET_ON_PROPERTY_WARNING.registerFactory(
            RemoveAnnotationFix.UseSiteGetDoesntHaveAnyEffect,
            RemoveUseSiteTargetFix.UseSiteGetDoesntHaveAnyEffect
        )

        INFERRED_INTO_DECLARED_UPPER_BOUNDS.registerFactory(InsertExplicitTypeArgumentsIntention)

        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(ImportFix)

        FUNCTION_EXPECTED.registerFactory(InvokeImportFix)

        ITERATOR_MISSING.registerFactory(IteratorImportFix)

        DELEGATE_SPECIAL_FUNCTION_MISSING.registerFactory(DelegateAccessorsImportFix)
        DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE.registerFactory(DelegateAccessorsImportFix)
        COMPONENT_FUNCTION_MISSING.registerFactory(ComponentsImportFix, AddDataModifierFix)

        NO_GET_METHOD.registerFactory(ArrayAccessorImportFix)
        NO_SET_METHOD.registerFactory(ArrayAccessorImportFix)

        CONFLICTING_IMPORT.registerFactory(RemovePsiElementSimpleFix.RemoveImportFactory)

        SUPERTYPE_NOT_INITIALIZED.registerFactory(SuperClassNotInitialized)
        FUNCTION_CALL_EXPECTED.registerFactory(ChangeToFunctionInvocationFixFactory)
        FUNCTION_EXPECTED.registerFactory(ChangeToPropertyAccessFix)

        CANNOT_CHANGE_ACCESS_PRIVILEGE.registerFactory(UseInheritedVisibilityFixFactory)
        CANNOT_WEAKEN_ACCESS_PRIVILEGE.registerFactory(UseInheritedVisibilityFixFactory)

        INVISIBLE_REFERENCE.registerFactory(MakeVisibleFactory)
        INVISIBLE_MEMBER.registerFactory(MakeVisibleFactory)
        INVISIBLE_SETTER.registerFactory(MakeVisibleFactory)

        UNSUPPORTED_WARNING.registerFactory(ConvertCollectionLiteralToIntArrayOfFixFactory)
        UNSUPPORTED.registerFactory(ConvertCollectionLiteralToIntArrayOfFixFactory)

        MODIFIER_FORM_FOR_NON_BUILT_IN_SUSPEND_FUN.registerFactory(WrapWithParenthesesFixFactory)
        MODIFIER_FORM_FOR_NON_BUILT_IN_SUSPEND.registerFactory(WrapWithParenthesesFixFactory)

        for (exposed in listOf(
            EXPOSED_FUNCTION_RETURN_TYPE, EXPOSED_PARAMETER_TYPE, EXPOSED_PROPERTY_TYPE,
            EXPOSED_RECEIVER_TYPE, EXPOSED_SUPER_CLASS, EXPOSED_SUPER_INTERFACE, EXPOSED_TYPE_PARAMETER_BOUND, EXPOSED_FROM_PRIVATE_IN_FILE
        )) {
            exposed.registerFactory(ChangeVisibilityOnExposureFactory)
        }

        EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR.registerFactory(ChangeVisibilityOnExposureFactory)

        REDUNDANT_NULLABLE.registerFactory(RemoveNullableFix.removeForRedundant)
        NULLABLE_SUPERTYPE.registerFactory(RemoveNullableFix.removeForSuperType)
        USELESS_NULLABLE_CHECK.registerFactory(RemoveNullableFix.removeForUseless)


        val implementMembersHandler = ImplementMembersHandler()
        val implementMembersAsParametersHandler = ImplementAsConstructorParameter()
        ABSTRACT_MEMBER_NOT_IMPLEMENTED.registerActions(implementMembersHandler, implementMembersAsParametersHandler)
        ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED.registerActions(implementMembersHandler, implementMembersAsParametersHandler)
        ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED_WARNING.registerActions(implementMembersHandler, implementMembersAsParametersHandler)
        MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED.registerActions(implementMembersHandler, implementMembersAsParametersHandler)
        MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED_WARNING.registerActions(implementMembersHandler, implementMembersAsParametersHandler)
        MANY_IMPL_MEMBER_NOT_IMPLEMENTED.registerActions(implementMembersHandler)

        VAL_WITH_SETTER.registerFactory(ChangeVariableMutabilityFix.VAL_WITH_SETTER_FACTORY)
        VAL_REASSIGNMENT.registerFactory(
            ReassignmentActionFactory(VAL_REASSIGNMENT), LiftAssignmentOutOfTryFix, AssignToPropertyFix
        )
        CAPTURED_VAL_INITIALIZATION.registerFactory(ReassignmentActionFactory(CAPTURED_VAL_INITIALIZATION))
        CAPTURED_MEMBER_VAL_INITIALIZATION.registerFactory(ReassignmentActionFactory(CAPTURED_MEMBER_VAL_INITIALIZATION))
        VAR_OVERRIDDEN_BY_VAL.registerFactory(ChangeVariableMutabilityFix.VAR_OVERRIDDEN_BY_VAL_FACTORY)
        VAR_ANNOTATION_PARAMETER.registerFactory(ChangeVariableMutabilityFix.VAR_ANNOTATION_PARAMETER_FACTORY)

        VAL_OR_VAR_ON_FUN_PARAMETER.registerFactory(RemoveValVarFromParameterFix)
        VAL_OR_VAR_ON_LOOP_PARAMETER.registerFactory(RemoveValVarFromParameterFix)
        VAL_OR_VAR_ON_CATCH_PARAMETER.registerFactory(RemoveValVarFromParameterFix)
        VAL_OR_VAR_ON_SECONDARY_CONSTRUCTOR_PARAMETER.registerFactory(RemoveValVarFromParameterFix)

        UNUSED_VARIABLE.registerFactory(RemovePsiElementSimpleFix.RemoveVariableFactory)
        UNUSED_VARIABLE.registerFactory(RenameToUnderscoreFix.Factory)

        NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.registerFactory(AddReturnExpressionFixFactory)
        NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.registerFactory(AddReturnToLastExpressionInFunctionFixFactory)
        UNUSED_EXPRESSION.registerFactory(AddReturnToUnusedLastExpressionInFunctionFixFactory)

        UNUSED_DESTRUCTURED_PARAMETER_ENTRY.registerFactory(RenameToUnderscoreFix.Factory)

        SENSELESS_COMPARISON.registerFactory(SimplifyComparisonFix)

        UNNECESSARY_SAFE_CALL.registerFactory(Fe10ReplaceWithDotCallFixFactory)
        UNSAFE_CALL.registerFactory(ReplaceWithSafeCallFixFactory)
        SAFE_CALL_WILL_CHANGE_NULLABILITY.registerFactory(Fe10ReplaceWithDotCallFixFactory)
        RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(ReplaceWithSafeCallFixFactory)
        NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(ReplaceWithSafeCallFixFactory)

        UNSAFE_CALL.registerFactory(SurroundWithNullCheckFix)
        UNSAFE_IMPLICIT_INVOKE_CALL.registerFactory(SurroundWithNullCheckFix)
        UNSAFE_INFIX_CALL.registerFactory(SurroundWithNullCheckFix)
        UNSAFE_OPERATOR_CALL.registerFactory(SurroundWithNullCheckFix)
        ITERATOR_ON_NULLABLE.registerFactory(SurroundWithNullCheckFix.IteratorOnNullableFactory)
        TYPE_MISMATCH.registerFactory(SurroundWithNullCheckFix.TypeMismatchFactory)
        TYPE_MISMATCH_WARNING.registerFactory(SurroundWithNullCheckFix.TypeMismatchFactory)
        RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(SurroundWithNullCheckFix)
        NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(SurroundWithNullCheckFix.TypeMismatchFactory)

        UNSAFE_CALL.registerFactory(WrapWithSafeLetCallFix.UnsafeFactory)
        UNSAFE_IMPLICIT_INVOKE_CALL.registerFactory(WrapWithSafeLetCallFix.UnsafeFactory)
        UNSAFE_INFIX_CALL.registerFactory(WrapWithSafeLetCallFix.UnsafeFactory)
        UNSAFE_OPERATOR_CALL.registerFactory(WrapWithSafeLetCallFix.UnsafeFactory)
        TYPE_MISMATCH.registerFactory(WrapWithSafeLetCallFix.TypeMismatchFactory)
        TYPE_MISMATCH_WARNING.registerFactory(WrapWithSafeLetCallFix.TypeMismatchFactory)
        RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(WrapWithSafeLetCallFix.UnsafeFactory)
        NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(WrapWithSafeLetCallFix.TypeMismatchFactory)

        UNSAFE_CALL.registerFactory(UnsafeCallExclExclFixFactory)
        UNSAFE_INFIX_CALL.registerFactory(UnsafeCallExclExclFixFactory)
        UNSAFE_OPERATOR_CALL.registerFactory(UnsafeCallExclExclFixFactory)
        UNNECESSARY_NOT_NULL_ASSERTION.registerFactory(RemoveExclExclCallFix)
        UNSAFE_INFIX_CALL.registerFactory(ReplaceInfixOrOperatorCallFixFactory)
        UNSAFE_OPERATOR_CALL.registerFactory(ReplaceInfixOrOperatorCallFixFactory)
        UNSAFE_CALL.registerFactory(ReplaceInfixOrOperatorCallFixFactory) // [] only
        UNSAFE_IMPLICIT_INVOKE_CALL.registerFactory(ReplaceInfixOrOperatorCallFixFactory)
        UNSAFE_CALL.registerFactory(ReplaceWithSafeCallForScopeFunctionFixFactory)
        RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(UnsafeCallExclExclFixFactory)
        RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(ReplaceInfixOrOperatorCallFixFactory)
        RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(ReplaceWithSafeCallForScopeFunctionFixFactory)

        APPROXIMATED_LOCAL_TYPE_WILL_BECOME_NULLABLE.registerActions(SpecifyTypeExplicitlyFix(convertToNullable = true))
        AMBIGUOUS_ANONYMOUS_TYPE_INFERRED.registerActions(SpecifyTypeExplicitlyFix())
        PROPERTY_WITH_NO_TYPE_NO_INITIALIZER.registerActions(SpecifyTypeExplicitlyFix())
        MUST_BE_INITIALIZED.registerActions(SpecifyTypeExplicitlyFix())

        ELSE_MISPLACED_IN_WHEN.registerFactory(MoveWhenElseBranchFixFactory)
        REDUNDANT_ELSE_IN_WHEN.registerFactory(RemoveWhenBranchFix)
        SENSELESS_NULL_IN_WHEN.registerFactory(RemoveWhenBranchFix, RemoveWhenConditionFixFactory)
        BREAK_OR_CONTINUE_IN_WHEN.registerFactory(AddLoopLabelFixFactory)
        NO_ELSE_IN_WHEN.registerFactory(AddWhenElseBranchFix, AddWhenRemainingBranchesFix)
        NO_ELSE_IN_WHEN_WARNING.registerFactory(AddWhenElseBranchFix, AddWhenRemainingBranchesFix)
        NON_EXHAUSTIVE_WHEN.registerFactory(AddWhenElseBranchFix, AddWhenRemainingBranchesFix)
        NON_EXHAUSTIVE_WHEN_ON_SEALED_CLASS.registerFactory(AddWhenElseBranchFix, AddWhenRemainingBranchesFix)
        NON_EXHAUSTIVE_WHEN_STATEMENT.registerFactory(AddWhenElseBranchFix, AddWhenRemainingBranchesFix)

        INVALID_IF_AS_EXPRESSION.registerFactory(AddIfElseBranchFix)
        INVALID_IF_AS_EXPRESSION_WARNING.registerFactory(AddIfElseBranchFix)

        INTEGER_OPERATOR_RESOLVE_WILL_CHANGE.registerFactory(AddConversionCallFix)

        NO_TYPE_ARGUMENTS_ON_RHS.registerFactory(AddStarProjectionsFixFactory)

        TYPE_ARGUMENTS_REDUNDANT_IN_SUPER_QUALIFIER.registerFactory(RemovePsiElementSimpleFix.RemoveTypeArgumentsFactory)

        LOCAL_VARIABLE_WITH_TYPE_PARAMETERS.registerFactory(RemovePsiElementSimpleFix.RemoveTypeParametersFactory)
        LOCAL_VARIABLE_WITH_TYPE_PARAMETERS_WARNING.registerFactory(RemovePsiElementSimpleFix.RemoveTypeParametersFactory)

        UNCHECKED_CAST.registerFactory(ChangeToStarProjectionFixFactory)
        CANNOT_CHECK_FOR_ERASED.registerFactory(ChangeToStarProjectionFixFactory)

        CANNOT_CHECK_FOR_ERASED.registerFactory(ConvertToIsArrayOfCallFixFactory)

        UNINITIALIZED_PARAMETER.registerFactory(ReorderParametersFix)
        UNINITIALIZED_PARAMETER_WARNING.registerFactory(ReorderParametersFix)

        INACCESSIBLE_OUTER_CLASS_EXPRESSION.registerFactory(AddModifierFixFE10.createFactory(INNER_KEYWORD, KtClass::class.java))

        FINAL_SUPERTYPE.registerFactory(AddModifierFixFE10.MakeClassOpenFactory)
        FINAL_UPPER_BOUND.registerFactory(AddModifierFixFE10.MakeClassOpenFactory)

        OVERRIDING_FINAL_MEMBER.registerFactory(MakeOverriddenMemberOpenFix)

        PARAMETER_NAME_CHANGED_ON_OVERRIDE.registerFactory(RenameParameterToMatchOverriddenMethodFix)

        NESTED_CLASS_NOT_ALLOWED.registerFactory(AddModifierFixFE10.createFactory(INNER_KEYWORD))

        CONFLICTING_PROJECTION.registerFactory(RemoveModifierFixBase.createRemoveProjectionFactory(false))
        PROJECTION_ON_NON_CLASS_TYPE_ARGUMENT.registerFactory(RemoveModifierFixBase.createRemoveProjectionFactory(false))
        PROJECTION_IN_IMMEDIATE_ARGUMENT_TO_SUPERTYPE.registerFactory(RemoveModifierFixBase.createRemoveProjectionFactory(false))

        NOT_AN_ANNOTATION_CLASS.registerFactory(MakeClassAnAnnotationClassFixFactory)

        val changeVariableTypeFix = ChangeVariableTypeFix.PropertyOrReturnTypeMismatchOnOverrideFactory
        RETURN_TYPE_MISMATCH_ON_OVERRIDE.registerFactory(changeVariableTypeFix)
        PROPERTY_TYPE_MISMATCH_ON_OVERRIDE.registerFactory(changeVariableTypeFix)
        VAR_TYPE_MISMATCH_ON_OVERRIDE.registerFactory(changeVariableTypeFix)
        COMPONENT_FUNCTION_RETURN_TYPE_MISMATCH.registerFactory(ChangeVariableTypeFix.ComponentFunctionReturnTypeMismatchFactory)
        TYPE_MISMATCH.registerFactory(ChangeVariableTypeFix.VariableInitializedWithNullFactory)
        TYPE_MISMATCH_WARNING.registerFactory(ChangeVariableTypeFix.VariableInitializedWithNullFactory)

        val changeFunctionReturnTypeFix = ChangeCallableReturnTypeFix.ChangingReturnTypeToUnitFactory
        RETURN_TYPE_MISMATCH.registerFactory(changeFunctionReturnTypeFix)
        NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.registerFactory(changeFunctionReturnTypeFix)
        NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY_MIGRATION.registerFactory(changeFunctionReturnTypeFix)
        RETURN_TYPE_MISMATCH_ON_OVERRIDE.registerFactory(ChangeCallableReturnTypeFix.ReturnTypeMismatchOnOverrideFactory)
        COMPONENT_FUNCTION_RETURN_TYPE_MISMATCH.registerFactory(ChangeCallableReturnTypeFix.ComponentFunctionReturnTypeMismatchFactory)
        HAS_NEXT_FUNCTION_TYPE_MISMATCH.registerFactory(ChangeCallableReturnTypeFix.HasNextFunctionTypeMismatchFactory)
        COMPARE_TO_TYPE_MISMATCH.registerFactory(ChangeCallableReturnTypeFix.CompareToTypeMismatchFactory)
        IMPLICIT_NOTHING_RETURN_TYPE.registerFactory(ChangeCallableReturnTypeFix.ChangingReturnTypeToNothingFactory)

        RETURN_TYPE_MISMATCH_ON_OVERRIDE.registerFactory(ChangeSuperTypeListEntryTypeArgumentFix)
        PROPERTY_TYPE_MISMATCH_ON_OVERRIDE.registerFactory(ChangeSuperTypeListEntryTypeArgumentFix)

        EXTENSION_FUNCTION_IN_EXTERNAL_DECLARATION.registerFactory(ConvertFunctionTypeReceiverToParameterIntention.Factory)
        TOO_MANY_ARGUMENTS.registerFactory(ChangeFunctionSignatureFix)
        NO_VALUE_FOR_PARAMETER.registerFactory(ChangeFunctionSignatureFix)

        TYPE_MISMATCH.registerFactory(AddFunctionParametersFix)
        TYPE_MISMATCH_WARNING.registerFactory(AddFunctionParametersFix)
        CONSTANT_EXPECTED_TYPE_MISMATCH.registerFactory(AddFunctionParametersFix)
        NULL_FOR_NONNULL_TYPE.registerFactory(AddFunctionParametersFix)

        UNUSED_PARAMETER.registerFactory(RemoveUnusedFunctionParameterFix)
        UNUSED_ANONYMOUS_PARAMETER.registerFactory(RenameToUnderscoreFix.Factory)
        UNUSED_ANONYMOUS_PARAMETER.registerFactory(RemoveSingleLambdaParameterFix)
        EXPECTED_PARAMETERS_NUMBER_MISMATCH.registerFactory(ChangeFunctionLiteralSignatureFix)

        EXPECTED_PARAMETER_TYPE_MISMATCH.registerFactory(ChangeTypeFix)

        EXTENSION_PROPERTY_WITH_BACKING_FIELD.registerFactory(ConvertExtensionPropertyInitializerToGetterFix)

        EXPECTED_TYPE_MISMATCH.registerFactory(ChangeFunctionLiteralReturnTypeFix)
        ASSIGNMENT_TYPE_MISMATCH.registerFactory(ChangeFunctionLiteralReturnTypeFix)

        UNRESOLVED_REFERENCE.registerFactory(CreateUnaryOperationActionFactory)
        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(CreateUnaryOperationActionFactory)
        NO_VALUE_FOR_PARAMETER.registerFactory(CreateUnaryOperationActionFactory)

        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(CreateBinaryOperationActionFactory)
        UNRESOLVED_REFERENCE.registerFactory(CreateBinaryOperationActionFactory)
        NONE_APPLICABLE.registerFactory(CreateBinaryOperationActionFactory)
        NO_VALUE_FOR_PARAMETER.registerFactory(CreateBinaryOperationActionFactory)
        TOO_MANY_ARGUMENTS.registerFactory(CreateBinaryOperationActionFactory)
        TYPE_MISMATCH_ERRORS.forEach { it.registerFactory(CreateBinaryOperationActionFactory) }
        TYPE_MISMATCH_WARNING.registerFactory(CreateBinaryOperationActionFactory)

        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        UNRESOLVED_REFERENCE.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        UNRESOLVED_REFERENCE.registerFactory(CreateFunctionFromCallableReferenceActionFactory)
        NO_VALUE_FOR_PARAMETER.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        TOO_MANY_ARGUMENTS.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        NONE_APPLICABLE.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        TYPE_MISMATCH.registerFactory(*CreateCallableFromCallActionFactory.FUNCTIONS)
        TYPE_MISMATCH_WARNING.registerFactory(*CreateCallableFromCallActionFactory.FUNCTIONS)

        NO_VALUE_FOR_PARAMETER.registerFactory(CreateConstructorFromDelegationCallActionFactory)
        TOO_MANY_ARGUMENTS.registerFactory(CreateConstructorFromDelegationCallActionFactory)

        NO_VALUE_FOR_PARAMETER.registerFactory(CreateConstructorFromSuperTypeCallActionFactory)
        TOO_MANY_ARGUMENTS.registerFactory(CreateConstructorFromSuperTypeCallActionFactory)
        NONE_APPLICABLE.registerFactory(CreateConstructorFromSuperTypeCallActionFactory)

        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(CreateClassFromConstructorCallActionFactory)
        UNRESOLVED_REFERENCE.registerFactory(CreateClassFromConstructorCallActionFactory)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(CreateClassFromConstructorCallActionFactory)

        UNRESOLVED_REFERENCE.registerFactory(CreateLocalVariableActionFactory)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(CreateLocalVariableActionFactory)

        UNRESOLVED_REFERENCE.registerFactory(CreateParameterByRefActionFactory)
        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(CreateParameterByRefActionFactory)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(CreateParameterByRefActionFactory)

        NAMED_PARAMETER_NOT_FOUND.registerFactory(CreateParameterByNamedArgumentActionFactory)

        FUNCTION_EXPECTED.registerFactory(CreateInvokeFunctionActionFactory)

        val factoryForTypeMismatchError = QuickFixFactoryForTypeMismatchError()
        TYPE_MISMATCH.registerFactory(factoryForTypeMismatchError)
        TYPE_MISMATCH_WARNING.registerFactory(factoryForTypeMismatchError)
        NULL_FOR_NONNULL_TYPE.registerFactory(factoryForTypeMismatchError)
        CONSTANT_EXPECTED_TYPE_MISMATCH.registerFactory(factoryForTypeMismatchError)
        TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH.registerFactory(factoryForTypeMismatchError)
        SIGNED_CONSTANT_CONVERTED_TO_UNSIGNED.registerFactory(factoryForTypeMismatchError)
        NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.registerFactory(factoryForTypeMismatchError)
        INCOMPATIBLE_TYPES.registerFactory(factoryForTypeMismatchError)

        EQUALITY_NOT_APPLICABLE.registerFactory(EqualityNotApplicableFactory)

        SMARTCAST_IMPOSSIBLE.registerFactory(SmartCastImpossibleExclExclFixFactory)
        SMARTCAST_IMPOSSIBLE.registerFactory(CastExpressionFix.SmartCastImpossibleFactory)
        SMARTCAST_IMPOSSIBLE.registerFactory(SmartCastImpossibleInIfThenFactory)

        PLATFORM_CLASS_MAPPED_TO_KOTLIN.registerFactory(MapPlatformClassToKotlinFix)

        MANY_CLASSES_IN_SUPERTYPE_LIST.registerFactory(RemoveSupertypeFixFactory)

        NO_GET_METHOD.registerFactory(CreateGetFunctionActionFactory)
        TYPE_MISMATCH_ERRORS.forEach { it.registerFactory(CreateGetFunctionActionFactory) }
        TYPE_MISMATCH_WARNING.registerFactory(CreateGetFunctionActionFactory)
        NO_SET_METHOD.registerFactory(CreateSetFunctionActionFactory)
        TYPE_MISMATCH_ERRORS.forEach { it.registerFactory(CreateSetFunctionActionFactory) }
        TYPE_MISMATCH_WARNING.registerFactory(CreateSetFunctionActionFactory)
        HAS_NEXT_MISSING.registerFactory(CreateHasNextFunctionActionFactory)
        HAS_NEXT_FUNCTION_NONE_APPLICABLE.registerFactory(CreateHasNextFunctionActionFactory)
        NEXT_MISSING.registerFactory(CreateNextFunctionActionFactory)
        NEXT_NONE_APPLICABLE.registerFactory(CreateNextFunctionActionFactory)
        ITERATOR_MISSING.registerFactory(CreateIteratorFunctionActionFactory)
        ITERATOR_ON_NULLABLE.registerFactory(MissingIteratorExclExclFixFactory)
        COMPONENT_FUNCTION_MISSING.registerFactory(
            CreateComponentFunctionActionFactory,
            CreateDataClassPropertyFromDestructuringActionFactory
        )

        DELEGATE_SPECIAL_FUNCTION_MISSING.registerFactory(DelegatedPropertyValFactory)
        DELEGATE_SPECIAL_FUNCTION_MISSING.registerFactory(CreatePropertyDelegateAccessorsActionFactory)
        DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE.registerFactory(CreatePropertyDelegateAccessorsActionFactory)

        UNRESOLVED_REFERENCE.registerFactory(
            CreateClassFromTypeReferenceActionFactory,
            CreateClassFromReferenceExpressionActionFactory,
            CreateClassFromCallWithConstructorCalleeActionFactory
        )

        PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED.registerFactory(InsertDelegationCallQuickfix.InsertThisDelegationCallFactory)

        EXPLICIT_DELEGATION_CALL_REQUIRED.registerFactory(InsertDelegationCallQuickfix.InsertThisDelegationCallFactory)
        EXPLICIT_DELEGATION_CALL_REQUIRED.registerFactory(InsertDelegationCallQuickfix.InsertSuperDelegationCallFactory)

        MISSING_CONSTRUCTOR_KEYWORD.registerFactory(MissingConstructorKeywordFixFactory)

        MISSING_CONSTRUCTOR_BRACKETS.registerFactory(MissingConstructorBracketsFixFactory)

        ANONYMOUS_FUNCTION_WITH_NAME.registerFactory(RemoveNameFromFunctionExpressionFix)

        UNRESOLVED_REFERENCE.registerFactory(ReplaceObsoleteLabelSyntaxFix)

        DEPRECATION.registerFactory(
            DeprecatedSymbolUsageFix,
            DeprecatedSymbolUsageInWholeProjectFix,
            MigrateExternalExtensionFix,
            MigrateExperimentalToRequiresOptInFix
        )
        DEPRECATION_ERROR.registerFactory(
            DeprecatedSymbolUsageFix,
            DeprecatedSymbolUsageInWholeProjectFix,
            MigrateExternalExtensionFix,
            MigrateExperimentalToRequiresOptInFix
        )
        TYPEALIAS_EXPANSION_DEPRECATION.registerFactory(
            DeprecatedSymbolUsageFix,
            DeprecatedSymbolUsageInWholeProjectFix,
            MigrateExternalExtensionFix
        )
        TYPEALIAS_EXPANSION_DEPRECATION_ERROR.registerFactory(
            DeprecatedSymbolUsageFix,
            DeprecatedSymbolUsageInWholeProjectFix,
            MigrateExternalExtensionFix
        )
        PROTECTED_CALL_FROM_PUBLIC_INLINE.registerFactory(ReplaceProtectedToPublishedApiCallFixFactory)

        POSITIONED_VALUE_ARGUMENT_FOR_JAVA_ANNOTATION.registerFactory(ReplaceJavaAnnotationPositionedArgumentsFix)

        JAVA_TYPE_MISMATCH.registerFactory(CastExpressionFix.GenericVarianceConversion)

        UPPER_BOUND_VIOLATED.registerFactory(AddGenericUpperBoundFix.Factory)
        TYPE_INFERENCE_UPPER_BOUND_VIOLATED.registerFactory(AddGenericUpperBoundFix.Factory)
        UPPER_BOUND_VIOLATED_BASED_ON_JAVA_ANNOTATIONS.registerFactory(AddGenericUpperBoundFix.Factory)

        TYPE_MISMATCH.registerFactory(ConvertClassToKClassFix)

        NON_CONST_VAL_USED_IN_CONSTANT_EXPRESSION.registerFactory(ConstFixFactory)

        OPERATOR_MODIFIER_REQUIRED.registerFactory(AddModifierFixFactory(OPERATOR_KEYWORD))
        OPERATOR_MODIFIER_REQUIRED.registerFactory(ImportForMissingOperatorFactory)

        INFIX_MODIFIER_REQUIRED.registerFactory(AddModifierFixFactory(INFIX_KEYWORD))

        UNDERSCORE_IS_RESERVED.registerFactory(RenameUnderscoreFix)

        DEPRECATED_TYPE_PARAMETER_SYNTAX.registerFactory(MigrateTypeParameterListFix)
        DEPRECATED_JAVA_ANNOTATION.registerFactory(DeprecatedJavaAnnotationFix)

        UNRESOLVED_REFERENCE.registerFactory(KotlinAddOrderEntryActionFactory)

        UNRESOLVED_REFERENCE.registerFactory(RenameUnresolvedReferenceActionFactory)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(RenameUnresolvedReferenceActionFactory)

        MISPLACED_TYPE_PARAMETER_CONSTRAINTS.registerFactory(MoveTypeParameterConstraintFixFactory)

        COMMA_IN_WHEN_CONDITION_WITHOUT_ARGUMENT.registerFactory(CommaInWhenConditionWithoutArgumentFix)

        DATA_CLASS_NOT_PROPERTY_PARAMETER.registerFactory(AddValVarToConstructorParameterAction.DataClassConstructorNotPropertyQuickFixFactory)
        MISSING_VAL_ON_ANNOTATION_PARAMETER.registerFactory(AddValVarToConstructorParameterAction.AnnotationClassConstructorNotValPropertyQuickFixFactory)
        VALUE_CLASS_CONSTRUCTOR_NOT_FINAL_READ_ONLY_PARAMETER.registerFactory(AddValVarToConstructorParameterAction.ValueClassConstructorNotValPropertyQuickFixFactory)

        VALUE_CLASS_WITHOUT_JVM_INLINE_ANNOTATION.registerFactory(AddJvmInlineAnnotationFixFactory)

        NON_LOCAL_RETURN_NOT_ALLOWED.registerFactory(AddInlineModifierFix.CrossInlineFactory)
        USAGE_IS_NOT_INLINABLE.registerFactory(AddInlineModifierFix.NoInlineFactory)
        USAGE_IS_NOT_INLINABLE_WARNING.registerFactory(AddInlineModifierFix.NoInlineFactory)

        UNRESOLVED_REFERENCE.registerFactory(MakeConstructorParameterPropertyFix)
        DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE.registerFactory(SpecifyOverrideExplicitlyFix)

        SUPERTYPE_IS_EXTENSION_FUNCTION_TYPE.registerFactory(ConvertExtensionToFunctionTypeFixFactory)

        UNUSED_LAMBDA_EXPRESSION.registerFactory(AddRunToLambdaFix)

        WRONG_LONG_SUFFIX.registerFactory(WrongLongSuffixFixFactory)

        REIFIED_TYPE_PARAMETER_NO_INLINE.registerFactory(AddInlineToFunctionWithReifiedFix)

        VALUE_PARAMETER_WITH_NO_TYPE_ANNOTATION.registerFactory(AddTypeAnnotationToValueParameterFix)

        UNRESOLVED_REFERENCE.registerFactory(CreateTypeParameterByUnresolvedRefActionFactory)
        WRONG_NUMBER_OF_TYPE_ARGUMENTS.registerFactory(CreateTypeParameterUnmatchedTypeArgumentActionFactory)

        FINAL_UPPER_BOUND.registerFactory(InlineTypeParameterFix)
        FINAL_UPPER_BOUND.registerFactory(RemoveFinalUpperBoundFixFactory)

        TYPE_PARAMETER_AS_REIFIED.registerFactory(AddReifiedToTypeParameterOfFunctionFixFactory)

        CANNOT_CHECK_FOR_ERASED.registerFactory(MakeTypeParameterReifiedAndFunctionInlineFix)

        TOO_MANY_CHARACTERS_IN_CHARACTER_LITERAL.registerFactory(TooLongCharLiteralToStringFixFactory)
        ILLEGAL_ESCAPE.registerFactory(TooLongCharLiteralToStringFixFactory)

        UNUSED_VALUE.registerFactory(RemoveUnusedValueFix)

        ANNOTATIONS_ON_BLOCK_LEVEL_EXPRESSION_ON_THE_SAME_LINE.registerFactory(AddNewLineAfterAnnotationsFix)

        INAPPLICABLE_LATEINIT_MODIFIER.registerFactory(ChangeVariableMutabilityFix.LATEINIT_VAL_FACTORY)
        INAPPLICABLE_LATEINIT_MODIFIER.registerFactory(RemoveNullableFix.removeForLateInitProperty)
        INAPPLICABLE_LATEINIT_MODIFIER.registerFactory(RemovePartsFromPropertyFix.LateInitFactory)
        INAPPLICABLE_LATEINIT_MODIFIER.registerFactory(RemoveModifierFixBase.createRemoveLateinitFactory())
        INAPPLICABLE_LATEINIT_MODIFIER.registerFactory(ConvertLateinitPropertyToNotNullDelegateFixFactory)

        VARIABLE_WITH_REDUNDANT_INITIALIZER.registerFactory(RemoveRedundantInitializerFix)

        OVERLOADS_ABSTRACT.registerFactory(RemoveAnnotationFix.JvmOverloads)
        OVERLOADS_INTERFACE.registerFactory(RemoveAnnotationFix.JvmOverloads)
        OVERLOADS_PRIVATE.registerFactory(RemoveAnnotationFix.JvmOverloads)
        OVERLOADS_LOCAL.registerFactory(RemoveAnnotationFix.JvmOverloads)
        OVERLOADS_WITHOUT_DEFAULT_ARGUMENTS.registerFactory(RemoveAnnotationFix.JvmOverloads)
        OVERLOADS_ANNOTATION_CLASS_CONSTRUCTOR.registerFactory(RemoveAnnotationFix.JvmOverloads)

        ACTUAL_WITHOUT_EXPECT.registerFactory(RemoveModifierFixBase.createRemoveModifierFromListOwnerPsiBasedFactory(ACTUAL_KEYWORD))
        ACTUAL_WITHOUT_EXPECT.registerFactory(CreateExpectedFix)
        NO_ACTUAL_FOR_EXPECT.registerFactory(CreateMissedActualsFix)
        NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS.registerFactory(AddActualFix)

        ACTUAL_MISSING.registerFactory(AddModifierFixFE10.createFactory(ACTUAL_KEYWORD))

        ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT.registerFactory(ActualAnnotationsNotMatchExpectFixFactory)

        CAST_NEVER_SUCCEEDS.registerFactory(ReplacePrimitiveCastWithNumberConversionFixFactory)

        WRONG_EXTERNAL_DECLARATION.registerFactory(MigrateExternalExtensionFix)

        ILLEGAL_SUSPEND_FUNCTION_CALL.registerFactory(AddSuspendModifierFix)
        INLINE_SUSPEND_FUNCTION_TYPE_UNSUPPORTED.registerFactory(AddInlineModifierFix.NoInlineSuspendFactory)
        INLINE_SUSPEND_FUNCTION_TYPE_UNSUPPORTED.registerFactory(AddInlineModifierFix.CrossInlineSuspendFactory)

        UNRESOLVED_REFERENCE.registerFactory(AddSuspendModifierFix.UnresolvedReferenceFactory)
        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(AddSuspendModifierFix.UnresolvedReferenceFactory)

        UNRESOLVED_REFERENCE.registerFactory(CreateLabelFixFactory)
        YIELD_IS_RESERVED.registerFactory(UnsupportedYieldFix)
        INVALID_TYPE_OF_ANNOTATION_MEMBER.registerFactory(TypeOfAnnotationMemberFixFactory)

        ILLEGAL_INLINE_PARAMETER_MODIFIER.registerFactory(AddInlineToFunctionFix)

        INAPPLICABLE_JVM_FIELD.registerFactory(ReplaceJvmFieldWithConstFixFactory, RemoveAnnotationFix.JvmField)

        CONFLICTING_OVERLOADS.registerFactory(ChangeSuspendInHierarchyFix)

        MUST_BE_INITIALIZED_OR_BE_ABSTRACT.registerFactory(AddModifierFixFE10.AddLateinitFactory)

        RETURN_NOT_ALLOWED.registerFactory(ChangeToLabeledReturnFixFactory)
        TYPE_MISMATCH.registerFactory(ChangeToLabeledReturnFixFactory)
        TYPE_MISMATCH_WARNING.registerFactory(ChangeToLabeledReturnFixFactory)
        CONSTANT_EXPECTED_TYPE_MISMATCH.registerFactory(ChangeToLabeledReturnFixFactory)
        NULL_FOR_NONNULL_TYPE.registerFactory(ChangeToLabeledReturnFixFactory)

        WRONG_ANNOTATION_TARGET.registerFactory(AddAnnotationTargetFix, AddAnnotationUseSiteTargetFix)
        WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET.registerFactory(MoveReceiverAnnotationFixFactory, AddAnnotationTargetFix)

        NO_CONSTRUCTOR.registerFactory(RemoveNoConstructorFixFactory)
        NO_CONSTRUCTOR.registerFactory(AddDefaultConstructorFixFactory)
        NO_CONSTRUCTOR_WARNING.registerFactory(RemoveNoConstructorFixFactory)

        ANNOTATION_USED_AS_ANNOTATION_ARGUMENT.registerFactory(RemoveAtFromAnnotationArgument)

        ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_ANNOTATION.registerFactory(ReplaceWithArrayCallInAnnotationFix)
        ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_FUNCTION.registerFactory(SurroundWithArrayOfWithSpreadOperatorInFunctionFix)

        REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_ANNOTATION.registerFactory(ReplaceWithArrayCallInAnnotationFix)
        REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_FUNCTION.registerFactory(RemoveRedundantSpreadOperatorFix)

        JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE.registerFactory(KotlinAddRequiredModuleFix)

        OPT_IN_USAGE.registerFactory(OptInFixesFactory)
        OPT_IN_USAGE.registerFactory(OptInFileLevelFixesFactory)
        OPT_IN_USAGE_ERROR.registerFactory(OptInFixesFactory)
        OPT_IN_USAGE_ERROR.registerFactory(OptInFileLevelFixesFactory)
        OPT_IN_OVERRIDE.registerFactory(OptInFixesFactory)
        OPT_IN_OVERRIDE.registerFactory(OptInFileLevelFixesFactory)
        OPT_IN_OVERRIDE_ERROR.registerFactory(OptInFixesFactory)
        OPT_IN_OVERRIDE_ERROR.registerFactory(OptInFileLevelFixesFactory)
        OPT_IN_IS_NOT_ENABLED.registerFactory(MakeModuleOptInFix)
        OPT_IN_MARKER_ON_WRONG_TARGET.registerFactory(OptInAnnotationWrongTargetFixesFactory)
        OPT_IN_MARKER_ON_WRONG_TARGET.registerFactory(RemoveAnnotationFix)
        OPT_IN_MARKER_WITH_WRONG_TARGET.registerFactory(RemoveWrongOptInAnnotationTargetFix)
        OPT_IN_MARKER_WITH_WRONG_RETENTION.registerFactory(RemoveAnnotationFix.RemoveForbiddenOptInRetention)
        OPT_IN_MARKER_ON_OVERRIDE.registerFactory(RemoveAnnotationFix)
        OPT_IN_MARKER_ON_OVERRIDE_WARNING.registerFactory(RemoveAnnotationFix)
        OPT_IN_WITHOUT_ARGUMENTS.registerFactory(RemoveAnnotationFix)

        TYPE_VARIANCE_CONFLICT.registerFactory(RemoveTypeVarianceFixFactory, AddAnnotationFix.TypeVarianceConflictFactory)

        CONST_VAL_NOT_TOP_LEVEL_OR_OBJECT.registerFactory(
            MoveMemberToCompanionObjectIntention.Factory,
            RemoveModifierFixBase.removeNonRedundantModifier
        )

        NO_COMPANION_OBJECT.registerFactory(AddIsToWhenConditionFixFactory)

        DEFAULT_VALUE_NOT_ALLOWED_IN_OVERRIDE.registerFactory(RemoveDefaultParameterValueFixFactory)
        ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS.registerFactory(RemoveDefaultParameterValueFixFactory)

        RESOLUTION_TO_CLASSIFIER.registerFactory(ConvertToAnonymousObjectFix, AddFunModifierFix)

        NOTHING_TO_INLINE.registerFactory(RemoveModifierFixBase.removeNonRedundantModifier)

        DECLARATION_CANT_BE_INLINED.registerFactory(DeclarationCantBeInlinedFactory)

        ASSIGN_OPERATOR_AMBIGUITY.registerFactory(AssignOperatorAmbiguityFactory)

        TYPE_MISMATCH.registerFactory(SurroundWithLambdaFix)
        TYPE_MISMATCH_WARNING.registerFactory(SurroundWithLambdaFix)
        CONSTANT_EXPECTED_TYPE_MISMATCH.registerFactory(SurroundWithLambdaFix)

        NO_SET_METHOD.registerFactory(ChangeToMutableCollectionFix)

        MUST_BE_INITIALIZED_OR_BE_ABSTRACT.registerFactory(AddAccessorsIntention)
        MUST_BE_INITIALIZED.registerFactory(AddAccessorsIntention)

        RESTRICTED_RETENTION_FOR_EXPRESSION_ANNOTATION.registerFactory(RestrictedRetentionForExpressionAnnotationFactory)

        NO_VALUE_FOR_PARAMETER.registerFactory(AddConstructorParameterFromSuperTypeCallFixFactory)
        NO_VALUE_FOR_PARAMETER.registerFactory(SpecifyRemainingArgumentsByNameFixFactory)

        UNEXPECTED_TRAILING_LAMBDA_ON_A_NEW_LINE.registerFactory(AddSemicolonBeforeLambdaExpressionFix.Factory)

        CONSTRUCTOR_IN_OBJECT.registerFactory(ChangeObjectToClassFixFactory)

        REDUNDANT_LABEL_WARNING.registerFactory(RemoveRedundantLabelFix)
        NOT_A_FUNCTION_LABEL.registerFactory(RemoveReturnLabelFixFactory)
        NOT_A_FUNCTION_LABEL_WARNING.registerFactory(RemoveReturnLabelFixFactory)

        ANNOTATION_ON_SUPERCLASS.registerFactory(RemoveAnnotationFix)

        REPEATED_ANNOTATION.registerFactory(RemoveAnnotationFix)
        REPEATED_ANNOTATION_WARNING.registerFactory(RemoveAnnotationFix)

        ACCIDENTAL_OVERRIDE.registerFactory(MakePrivateAndOverrideMemberFix.AccidentalOverrideFactory)

        MUST_BE_INITIALIZED.registerFactory(ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)

        TOO_MANY_ARGUMENTS.registerFactory(RemoveArgumentFixFactory)

        AMBIGUOUS_SUPER.registerFactory(SpecifySuperTypeFix)

        FUN_INTERFACE_WRONG_COUNT_OF_ABSTRACT_MEMBERS.registerFactory(RemoveModifierFixBase.createRemoveFunFromInterfaceFactory())

        TOPLEVEL_TYPEALIASES_ONLY.registerFactory(MoveTypeAliasToTopLevelFixFactory)

        CONFLICTING_JVM_DECLARATIONS.registerFactory(AddJvmNameAnnotationFix)

        MISSING_EXCEPTION_IN_THROWS_ON_SUSPEND.registerFactory(AddExceptionToThrowsFix)
        THROWS_LIST_EMPTY.registerFactory(RemoveAnnotationFix)
        INCOMPATIBLE_THROWS_OVERRIDE.registerFactory(RemoveAnnotationFix)

        COMPATIBILITY_WARNING.registerFactory(UseFullyQualifiedCallFix)

        INLINE_CLASS_DEPRECATED.registerFactory(InlineClassDeprecatedFixFactory)

        SUBCLASS_CANT_CALL_COMPANION_PROTECTED_NON_STATIC.registerFactory(AddJvmStaticAnnotationFixFactory)

        SEALED_INHERITOR_IN_DIFFERENT_PACKAGE.registerFactory(MoveToSealedMatchingPackageFix)
        SEALED_INHERITOR_IN_DIFFERENT_MODULE.registerFactory(MoveToSealedMatchingPackageFix)

        JAVA_CLASS_ON_COMPANION.registerFactory(JavaClassOnCompanionFixes)

        ILLEGAL_ESCAPE.registerFactory(ConvertIllegalEscapeToUnicodeEscapeFixFactory)

        MODIFIER_FORM_FOR_NON_BUILT_IN_SUSPEND.registerFactory(AddEmptyArgumentListFixFactory)

        NON_PUBLIC_CALL_FROM_PUBLIC_INLINE.registerFactory(CallFromPublicInlineFactory)
        PROTECTED_CALL_FROM_PUBLIC_INLINE.registerFactory(CallFromPublicInlineFactory)
        SUPER_CALL_FROM_PUBLIC_INLINE.registerFactory(CallFromPublicInlineFactory)

        IS_ENUM_ENTRY.registerFactory(IsEnumEntryFactory)

        OVERRIDE_DEPRECATION.registerFactory(AddAnnotationWithArgumentsFix.CopyDeprecatedAnnotation)

        NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER.registerFactory(MakeUpperBoundNonNullableFix)
        WRONG_TYPE_PARAMETER_NULLABILITY_FOR_JAVA_OVERRIDE.registerFactory(MakeUpperBoundNonNullableFix)
        WRONG_NULLABILITY_FOR_JAVA_OVERRIDE.registerFactory(MakeUpperBoundNonNullableFix)
        TYPE_MISMATCH.registerFactory(MakeUpperBoundNonNullableFix)
        TYPE_MISMATCH_WARNING.registerFactory(MakeUpperBoundNonNullableFix)
        NOTHING_TO_OVERRIDE.registerFactory(MakeUpperBoundNonNullableFix)

        WRONG_NULLABILITY_FOR_JAVA_OVERRIDE.registerFactory(ChangeMemberFunctionSignatureFix)
        CONFUSING_BRANCH_CONDITION.registerFactory(ConfusingExpressionInWhenBranchFix)
        PROGRESSIONS_CHANGING_RESOLVE.registerFactory(OverloadResolutionChangeFix)

        ENUM_DECLARING_CLASS_DEPRECATED.registerFactory(DeclaringJavaClassMigrationFix)

        ABSTRACT_SUPER_CALL.registerFactory(AbstractSuperCallFix)
        ABSTRACT_SUPER_CALL_WARNING.registerFactory(AbstractSuperCallFix)

        WRONG_EXTENSION_FUNCTION_TYPE.registerFactory(RemoveAnnotationFix.ExtensionFunctionType)
        WRONG_EXTENSION_FUNCTION_TYPE_WARNING.registerFactory(RemoveAnnotationFix.ExtensionFunctionType)

        NON_DATA_CLASS_JVM_RECORD.registerFactory(AddModifierFixFE10.createFactory(DATA_KEYWORD))

        RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY.registerFactory(ConvertToBlockBodyFixFactory)

        VOLATILE_ON_VALUE.registerFactory(ChangeVariableMutabilityFix.VOLATILE_ON_VALUE_FACTORY)
    }
}
