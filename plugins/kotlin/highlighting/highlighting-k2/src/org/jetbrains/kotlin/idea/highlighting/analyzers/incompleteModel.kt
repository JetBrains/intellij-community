// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.AbsenceOfPrimaryConstructorForValueClass
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.AbstractPropertyInNonAbstractClass
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.AnnotationArgumentMustBeConst
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.AnnotationArgumentMustBeEnumConst
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.AnnotationClassConstructorCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.AnnotationParameterDefaultValueMustBeConstant
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.CallableReferenceToAnnotationConstructor
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.CannotWeakenAccessPrivilege
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.CannotWeakenAccessPrivilegeWarning
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ClassifierRedeclaration
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstValNotTopLevelOrObject
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstValWithDelegate
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstValWithGetter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstValWithNonConstInitializer
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstValWithoutInitializer
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstructorInInterface
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstructorInObject
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstructorOrSupertypeOnTypealiasWithTypeProjectionError
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ConstructorOrSupertypeOnTypealiasWithTypeProjectionWarning
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.CyclicConstructorDelegationCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.DelegationSuperCallInEnumConstructor
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.DeprecatedModifier
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.DeprecatedModifierContainingDeclaration
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.DeprecatedModifierForTarget
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.DeprecatedModifierPair
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.EnumClassConstructorCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExpectedClassConstructorDelegationCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExpectedClassConstructorPropertyParameter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExpectedEnumConstructor
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExpectedExternalDeclaration
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExpectedLateinitProperty
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExpectedPrivateDeclaration
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExpectedTailrecFunction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExposedPropertyTypeInConstructorError
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExternalClassConstructorPropertyParameter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ExternalDelegatedConstructorCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.GetterVisibilityDiffersFromPropertyVisibility
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.IllegalConstExpression
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.IllegalInlineParameterModifier
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.InapplicableLateinitModifier
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.IncompatibleModifiers
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.InfixModifierRequired
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.InitializationBeforeDeclaration
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.InlineClassConstructorWrongParametersSize
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.InnerClassConstructorNoReceiver
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.JavaSamInterfaceConstructorReference
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.JvmRecordWithoutPrimaryConstructorParameters
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.JvmStaticOnConstOrJvmField
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.MissingConstructorKeyword
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ModifierFormForNonBuiltInSuspend
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ModifierFormForNonBuiltInSuspendFunError
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ModifierFormForNonBuiltInSuspendFunWarning
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.MultiFieldValueClassPrimaryConstructorDefaultParameter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.NoConstructor
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.NonConstValUsedInConstantExpression
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.NonModifierFormForBuiltInSuspend
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.NonPrivateConstructorInEnum
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.OperatorCallOnConstructor
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.PrimaryConstructorDelegationCallExpected
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.PrivateSetterForAbstractProperty
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.PrivateSetterForOpenProperty
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ProtectedConstructorCallFromPublicInline
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ProtectedConstructorNotInSuperCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.Redeclaration
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.RedundantModalityModifier
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.RedundantModifier
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.RedundantVisibilityModifier
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.RepeatedModifier
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SealedClassConstructorCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SecondaryConstructorWithBodyInsideValueClass
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SelfCallInNestedObjectConstructorError
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SetterVisibilityInconsistentWithPropertyVisibility
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SupertypeInitializedWithoutPrimaryConstructor
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.TypeCantBeUsedForConstVal
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ValReassignment
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ValReassignmentViaBackingFieldError
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ValueClassConstructorNotFinalReadOnlyParameter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ValueClassEmptyConstructor
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.WrongModifierContainingDeclaration
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.WrongModifierTarget
import kotlin.reflect.KClass

/*
    We assume all diagnostics suppressable during incomplete mode, except of these:
 */
val ignoreIncompleteModeDiagnostics: Set<KClass<out KaFirDiagnostic<out PsiElement>>> = setOf(
    ValReassignment::class,
    ValReassignmentViaBackingFieldError::class,
    MissingConstructorKeyword::class,
    NoConstructor::class,
    InnerClassConstructorNoReceiver::class,
    SelfCallInNestedObjectConstructorError::class,
    ConstructorInObject::class,
    ConstructorInInterface::class,
    NonPrivateConstructorInEnum::class,
    CyclicConstructorDelegationCall::class,
    PrimaryConstructorDelegationCallExpected::class,
    ProtectedConstructorNotInSuperCall::class,
    SupertypeInitializedWithoutPrimaryConstructor::class,
    DelegationSuperCallInEnumConstructor::class,
    SealedClassConstructorCall::class,
    AnnotationClassConstructorCall::class,
    EnumClassConstructorCall::class,
    ExposedPropertyTypeInConstructorError::class,
    OperatorCallOnConstructor::class,
    AbsenceOfPrimaryConstructorForValueClass::class,
    InlineClassConstructorWrongParametersSize::class,
    ValueClassEmptyConstructor::class,
    ValueClassConstructorNotFinalReadOnlyParameter::class,
    MultiFieldValueClassPrimaryConstructorDefaultParameter::class,
    SecondaryConstructorWithBodyInsideValueClass::class,
    CallableReferenceToAnnotationConstructor::class,
    ExpectedClassConstructorDelegationCall::class,
    ExpectedClassConstructorPropertyParameter::class,
    ExpectedEnumConstructor::class,
    ConstructorOrSupertypeOnTypealiasWithTypeProjectionError::class,
    ConstructorOrSupertypeOnTypealiasWithTypeProjectionWarning::class,
    ProtectedConstructorCallFromPublicInline::class,
    JvmRecordWithoutPrimaryConstructorParameters::class,
    JavaSamInterfaceConstructorReference::class,
    ExternalClassConstructorPropertyParameter::class,
    ExternalDelegatedConstructorCall::class,
    IllegalConstExpression::class,
    AnnotationArgumentMustBeConst::class,
    AnnotationArgumentMustBeEnumConst::class,
    AnnotationParameterDefaultValueMustBeConstant::class,
    NonConstValUsedInConstantExpression::class,
    ConstValNotTopLevelOrObject::class,
    ConstValWithGetter::class,
    ConstValWithDelegate::class,
    TypeCantBeUsedForConstVal::class,
    ConstValWithoutInitializer::class,
    ConstValWithNonConstInitializer::class,
    JvmStaticOnConstOrJvmField::class,
    Redeclaration::class,
    ClassifierRedeclaration::class,
    InitializationBeforeDeclaration::class,
    RedundantVisibilityModifier::class,
    RedundantModalityModifier::class,
    IllegalInlineParameterModifier::class,
    NonModifierFormForBuiltInSuspend::class,
    ModifierFormForNonBuiltInSuspend::class,
    ModifierFormForNonBuiltInSuspendFunError::class,
    ModifierFormForNonBuiltInSuspendFunWarning::class,
    RepeatedModifier::class,
    RedundantModifier::class,
    DeprecatedModifier::class,
    DeprecatedModifierPair::class,
    DeprecatedModifierContainingDeclaration::class,
    DeprecatedModifierForTarget::class,
    IncompatibleModifiers::class,
    WrongModifierTarget::class,
    InfixModifierRequired::class,
    WrongModifierContainingDeclaration::class,
    InapplicableLateinitModifier::class,
    CannotWeakenAccessPrivilege::class,
    CannotWeakenAccessPrivilegeWarning::class,
    AbstractPropertyInNonAbstractClass::class,
    PrivateSetterForAbstractProperty::class,
    PrivateSetterForOpenProperty::class,
    GetterVisibilityDiffersFromPropertyVisibility::class,
    SetterVisibilityInconsistentWithPropertyVisibility::class,
    ExpectedLateinitProperty::class,
    ExpectedPrivateDeclaration::class,
    ExpectedExternalDeclaration::class,
    ExpectedTailrecFunction::class,
)