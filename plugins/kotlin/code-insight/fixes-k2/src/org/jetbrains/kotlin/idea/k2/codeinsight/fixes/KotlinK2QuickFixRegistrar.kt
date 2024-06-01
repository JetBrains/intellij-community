// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddDependencyQuickFixHelper
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberNotImplementedQuickfixFactories
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix
import org.jetbrains.kotlin.idea.quickfix.*

class KotlinK2QuickFixRegistrar : KotlinQuickFixRegistrar() {
    private val keywords = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KaFirDiagnostic.RedundantModifier::class, RemoveModifierFixBase.removeRedundantModifier)
        registerPsiQuickFixes(KaFirDiagnostic.IncompatibleModifiers::class, RemoveModifierFixBase.removeNonRedundantModifier)
        registerPsiQuickFixes(KaFirDiagnostic.RepeatedModifier::class, RemoveModifierFixBase.removeNonRedundantModifier)
        registerPsiQuickFixes(KaFirDiagnostic.DeprecatedModifierPair::class, RemoveModifierFixBase.removeRedundantModifier)
        registerPsiQuickFixes(KaFirDiagnostic.TypeParametersInEnum::class, RemoveModifierFixBase.removeRedundantModifier)
        registerPsiQuickFixes(KaFirDiagnostic.NonAbstractFunctionWithNoBody::class, AddFunctionBodyFix, AddModifierFix.addAbstractModifier)

        registerPsiQuickFixes(
            KaFirDiagnostic.AbstractPropertyInNonAbstractClass::class,
            AddModifierFix.addAbstractToContainingClass,
            RemoveModifierFixBase.removeAbstractModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.AbstractFunctionInNonAbstractClass::class,
            AddModifierFix.addAbstractToContainingClass,
            RemoveModifierFixBase.removeAbstractModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.NonFinalMemberInFinalClass::class,
            AddModifierFix.addOpenToContainingClass,
            RemoveModifierFixBase.removeOpenModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.NonFinalMemberInObject::class,
            RemoveModifierFixBase.removeOpenModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.PrivateSetterForOpenProperty::class,
            AddModifierFix.addFinalToProperty,
            RemoveModifierFixBase.removePrivateModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.PrivateSetterForAbstractProperty::class,
            RemoveModifierFixBase.removePrivateModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.NestedClassNotAllowed::class,
            AddModifierFix.addInnerModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.WrongModifierTarget::class,
            RemoveModifierFixBase.removeNonRedundantModifier,
            ChangeVariableMutabilityFix.CONST_VAL_FACTORY
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.AbstractMemberNotImplemented::class,
            AddModifierFix.addAbstractModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.AbstractClassMemberNotImplemented::class,
            AddModifierFix.addAbstractModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.VirtualMemberHidden::class,
            AddModifierFix.addOverrideModifier
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.NonDataClassJvmRecord::class,
            AddModifierFix.addDataModifier
        )
        registerFactory(AddDataModifierFixFactory.addDataModifierFixFactory)
        registerPsiQuickFixes(
            KaFirDiagnostic.AbstractPropertyInPrimaryConstructorParameters::class,
            RemoveModifierFixBase.removeAbstractModifier
        )
        registerPsiQuickFixes(KaFirDiagnostic.ValOrVarOnLoopParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KaFirDiagnostic.ValOrVarOnFunParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KaFirDiagnostic.ValOrVarOnCatchParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KaFirDiagnostic.ValOrVarOnSecondaryConstructorParameter::class, RemoveValVarFromParameterFix)
        registerFactory(MakeSuperTypeOpenFixFactory.makeSuperTypeOpenFixFactory)
        registerFactory(MakeSuperTypeOpenFixFactory.makeUpperBoundOpenFixFactory)
        registerFactory(AddFunModifierFixFactory.addFunModifierFixFactory)
        registerFactory(AddSuspendModifierFixFactory.addSuspendModifierFixFactory)
        registerFactory(SpecifyOverrideExplicitlyFixFactory.specifyOverrideExplicitlyFixFactory)
        registerFactory(MakeOverriddenMemberOpenFixFactory.makeOverriddenMemberOpenFixFactory)
        registerFactory(ChangeToStarProjectionFixFactory.uncheckedCastFactory)
        registerFactory(ChangeToStarProjectionFixFactory.cannotCheckForErased)
        registerFactory(AddStarProjectionsFixFactory.addStarProjectionsFixFactory)
        registerFactory(AddTypeAnnotationToValueParameterFixFactory.addTypeAnnotationToValueParameterFixFactory)
        registerFactory(ChangeToFunctionInvocationFixFactory.changeToFunctionInvocationFixFactory)
        registerFactory(TypeOfAnnotationMemberFixFactory.typeOfAnnotationMemberFixFactory)
        registerFactory(TooLongCharLiteralToStringFixFactory.illegalEscapeFactory)
        registerFactory(TooLongCharLiteralToStringFixFactory.tooManyCharactersInCharacterLiteralFactory)
        registerFactory(ConvertCollectionLiteralToIntArrayOfFixFactory.convertCollectionLiteralToIntArrayOfFixFactory)
        registerFactory(AddReturnExpressionFixFactory.addReturnExpressionFixFactory)
        registerFactory(RemoveArgumentFixFactory.removeArgumentFixFactory)
        registerFactory(AddJvmInlineAnnotationFixFactory.addJvmInlineAnnotationFixFactory)
        registerFactory(RemoveNoConstructorFixFactory.removeNoConstructorFixFactory)
        registerFactory(ArgumentTypeMismatchFactory.addArrayOfTypeFixFactory)
        registerFactory(ArgumentTypeMismatchFactory.wrapWithArrayLiteralFixFactory)
        registerFactory(ConvertLateinitPropertyToNotNullDelegateFixFactory.convertLateinitPropertyToNotNullDelegateFixFactory)
    }

    private val addAbstract = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrBeAbstract::class, AddModifierFix.addAbstractModifier)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrBeAbstractWarning::class, AddModifierFix.addAbstractModifier)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class, AddModifierFix.addAbstractModifier)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class, AddModifierFix.addAbstractModifier)
    }

    private val addFinal = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrBeFinal::class, AddModifierFix.addFinalToProperty)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrBeFinalWarning::class, AddModifierFix.addFinalToProperty)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class, AddModifierFix.addFinalToProperty)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class, AddModifierFix.addFinalToProperty)
    }

    private val addInline = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(AddInlineModifierFixFactories.usageIsNotInlinableFactory)
        registerFactory(AddInlineModifierFixFactories.nonLocalReturnNotAllowed)
        registerFactory(AddInlineModifierFixFactories.inlineSuspendFunctionTypeUnsupported)
        registerFactory(MakeTypeParameterReifiedAndFunctionInlineFixFactory.cannotCheckForErasedFactory)
        registerFactory(AddInlineToFunctionFixFactories.illegalInlineParameterModifierFactory)
        registerPsiQuickFixes(KaFirDiagnostic.ReifiedTypeParameterNoInline::class, AddModifierFix.addInlineToFunctionWithReified)
    }

    private val changeToLabeledReturn = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ChangeToLabeledReturnFixFactory.nullForNonnullType)
        registerFactory(ChangeToLabeledReturnFixFactory.returnNotAllowed)
        registerFactory(ChangeToLabeledReturnFixFactory.returnTypeMismatch)
    }

    private val insertDelegationCall = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(InsertDelegationCallFixFactory.primaryConstructorDelegationCallExpected)
        registerFactory(InsertDelegationCallFixFactory.explicitDelegationCallRequiredSuper)
        registerFactory(InsertDelegationCallFixFactory.explicitDelegationCallRequiredThis)
    }

    private val propertyInitialization = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitialized)
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitializedWarning)
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitializedOrBeFinal)
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitializedOrBeFinalWarning)
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitializedOrBeAbstract)
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitializedOrBeAbstractWarning)
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitializedOrFinalOrAbstract)
        registerFactory(InitializePropertyQuickFixFactories.mustBeInitializedOrFinalOrAbstractWarning)
        registerFactory(AddLateInitFactory.addLateInitFactory)
        registerFactory(AddAccessorsFactories.addAccessorsToUninitializedProperty)
        registerFactory(AddAccessorsFactories.addAccessorsToUninitializedOrAbstractProperty)

        registerPsiQuickFixes(KaFirDiagnostic.LocalVariableWithTypeParameters::class, RemovePsiElementSimpleFix.RemoveTypeParametersFactory)
        registerPsiQuickFixes(
            KaFirDiagnostic.LocalVariableWithTypeParametersWarning::class,
            RemovePsiElementSimpleFix.RemoveTypeParametersFactory
        )
    }

    private val overrides = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ChangeTypeQuickFixFactories.changeFunctionReturnTypeOnOverride)
        registerFactory(ChangeTypeQuickFixFactories.changePropertyReturnTypeOnOverride)
        registerFactory(ChangeTypeQuickFixFactories.changeVariableReturnTypeOnOverride)
        registerFactory(MemberNotImplementedQuickfixFactories.abstractMemberNotImplemented)
        registerFactory(MemberNotImplementedQuickfixFactories.abstractClassMemberNotImplemented)
        registerFactory(MemberNotImplementedQuickfixFactories.manyInterfacesMemberNotImplemented)
        registerFactory(MemberNotImplementedQuickfixFactories.manyImplMemberNotImplemented)
        registerFactory(MemberNotImplementedQuickfixFactories.abstractMemberNotImplementedByEnumEntry)
    }

    private val imports = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ImportQuickFix.invisibleReferenceFactory)
        registerPsiQuickFixes(KaFirDiagnostic.ConflictingImport::class, RemovePsiElementSimpleFix.RemoveImportFactory)
        registerPsiQuickFixes(KaFirDiagnostic.UnresolvedImport::class, AddDependencyQuickFixHelper)
    }

    private val mutability = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KaFirDiagnostic.VarOverriddenByVal::class, ChangeVariableMutabilityFix.VAR_OVERRIDDEN_BY_VAL_FACTORY)
        registerPsiQuickFixes(KaFirDiagnostic.VarAnnotationParameter::class, ChangeVariableMutabilityFix.VAR_ANNOTATION_PARAMETER_FACTORY)
        registerPsiQuickFixes(KaFirDiagnostic.InapplicableLateinitModifier::class, ChangeVariableMutabilityFix.LATEINIT_VAL_FACTORY)
        registerPsiQuickFixes(KaFirDiagnostic.ValWithSetter::class, ChangeVariableMutabilityFix.VAL_WITH_SETTER_FACTORY)
        registerFactory(ChangeVariableMutabilityFix.VAL_REASSIGNMENT)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitialized::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedWarning::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrBeFinal::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(
            KaFirDiagnostic.MustBeInitializedOrBeFinalWarning::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(KaFirDiagnostic.MustBeInitializedOrBeAbstract::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(
            KaFirDiagnostic.MustBeInitializedOrBeAbstractWarning::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(KaFirDiagnostic.VolatileOnValue::class, ChangeVariableMutabilityFix.VOLATILE_ON_VALUE_FACTORY)
    }

    private val expressions = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ReplaceWithDotCallFixFactory.replaceWithDotCallFactory)
        registerPsiQuickFixes(KaFirDiagnostic.UnnecessaryNotNullAssertion::class, RemoveExclExclCallFix)
        registerPsiQuickFixes(KaFirDiagnostic.UselessElvis::class, RemoveUselessElvisFix)
        registerPsiQuickFixes(KaFirDiagnostic.UselessElvisRightIsNull::class, RemoveUselessElvisFix)
        registerPsiQuickFixes(KaFirDiagnostic.UselessCast::class, RemoveUselessCastFix)
        registerPsiQuickFixes(KaFirDiagnostic.UselessIsCheck::class, RemoveUselessIsCheckFix, RemoveUselessIsCheckFixForWhen)
        registerFactory(ReplaceCallFixFactories.unsafeCallFactory)
        registerFactory(ReplaceCallFixFactories.unsafeInfixCallFactory)
        registerFactory(ReplaceCallFixFactories.unsafeOperatorCallFactory)
        registerFactory(ReplaceCallFixFactories.unsafeImplicitInvokeCallFactory)
        registerFactory(AddExclExclCallFixFactories.unsafeCallFactory)
        registerFactory(AddExclExclCallFixFactories.unsafeInfixCallFactory)
        registerFactory(AddExclExclCallFixFactories.unsafeOperatorCallFactory)
        registerFactory(AddExclExclCallFixFactories.iteratorOnNullableFactory)
        registerFactory(TypeMismatchFactories.argumentTypeMismatchFactory)
        registerFactory(TypeMismatchFactories.returnTypeMismatchFactory)
        registerFactory(TypeMismatchFactories.assignmentTypeMismatch)
        registerFactory(TypeMismatchFactories.initializerTypeMismatch)
        registerFactory(TypeMismatchFactories.smartcastImpossibleFactory)

        registerFactory(WrapWithSafeLetCallFixFactories.forUnsafeCall)
        registerFactory(WrapWithSafeLetCallFixFactories.forUnsafeImplicitInvokeCall)
        registerFactory(WrapWithSafeLetCallFixFactories.forUnsafeInfixCall)
        registerFactory(WrapWithSafeLetCallFixFactories.forUnsafeOperatorCall)
        registerFactory(WrapWithSafeLetCallFixFactories.forArgumentTypeMismatch)

        registerPsiQuickFixes(KaFirDiagnostic.NullableSupertype::class, RemoveNullableFix.removeForSuperType)
        registerPsiQuickFixes(KaFirDiagnostic.InapplicableLateinitModifier::class, RemoveNullableFix.removeForLateInitProperty)
        registerPsiQuickFixes(
            KaFirDiagnostic.TypeArgumentsRedundantInSuperQualifier::class,
            RemovePsiElementSimpleFix.RemoveTypeArgumentsFactory
        )

        registerFactory(ConvertToBlockBodyFixFactory.convertToBlockBodyFixFactory)
        registerFactory(SimplifyComparisonFixFactory.simplifyComparisonFixFactory)
    }

    private val whenStatements = KtQuickFixesListBuilder.registerPsiQuickFix {
        // TODO: NON_EXHAUSTIVE_WHEN[_ON_SEALED_CLASS] will be replaced in future. We need to register the fix for those diagnostics as well
        registerPsiQuickFixes(KaFirDiagnostic.NoElseInWhen::class, AddWhenElseBranchFix)
        registerFactory(AddWhenRemainingBranchFixFactories.noElseInWhen)
        registerPsiQuickFixes(KaFirDiagnostic.CommaInWhenConditionWithoutArgument::class, CommaInWhenConditionWithoutArgumentFix)
        registerPsiQuickFixes(KaFirDiagnostic.SenselessNullInWhen::class, RemoveWhenBranchFix)
    }

    private val typeMismatch = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ChangeTypeQuickFixFactories.componentFunctionReturnTypeMismatch)
        registerFactory(ChangeTypeQuickFixFactories.returnTypeMismatch)
        registerFactory(ChangeTypeQuickFixFactories.returnTypeNullableTypeMismatch)
        registerFactory(ChangeTypeQuickFixFactories.initializerTypeMismatch)
        registerFactory(ChangeTypeQuickFixFactories.assignmentTypeMismatch)
        registerFactory(ChangeTypeQuickFixFactories.parameterTypeMismatch)
        registerFactory(ChangeTypeQuickFixFactories.typeMismatch)

        registerFactory(AddToStringFixFactories.typeMismatch)
        registerFactory(AddToStringFixFactories.argumentTypeMismatch)
        registerFactory(AddToStringFixFactories.assignmentTypeMismatch)
        registerFactory(AddToStringFixFactories.returnTypeMismatch)
        registerFactory(AddToStringFixFactories.initializerTypeMismatch)

        registerFactory(CastExpressionFixFactories.smartcastImpossible)
        registerFactory(CastExpressionFixFactories.typeMismatch)
        registerFactory(CastExpressionFixFactories.throwableTypeMismatch)
        registerFactory(CastExpressionFixFactories.argumentTypeMismatch)
        registerFactory(CastExpressionFixFactories.assignmentTypeMismatch)
        registerFactory(CastExpressionFixFactories.returnTypeMismatch)
        registerFactory(CastExpressionFixFactories.initializerTypeMismatch)
    }

    private val needExplicitType = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(SpecifyExplicitTypeFixFactories.ambiguousAnonymousTypeInferred)
        registerFactory(SpecifyExplicitTypeFixFactories.noExplicitReturnTypeInApiMode)
        registerFactory(SpecifyExplicitTypeFixFactories.noExplicitReturnTypeInApiModeWarning)
    }

    private val superKeyword = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(SpecifySuperTypeFixFactory.ambiguousSuper)
    }

    private val superType = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(SuperClassNotInitializedFactories.addParenthesis)
    }

    private val vararg = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(
            KaFirDiagnostic.AssigningSingleElementToVarargInNamedFormAnnotationError::class,
            ReplaceWithArrayCallInAnnotationFix
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.AssigningSingleElementToVarargInNamedFormAnnotationWarning::class,
            ReplaceWithArrayCallInAnnotationFix
        )
        registerFactory(SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory.assigningSingleElementToVarargInNamedFormFunction)
        registerFactory(SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory.assigningSingleElementToVarargInNamedFormFunctionWarning)
        registerPsiQuickFixes(KaFirDiagnostic.RedundantSpreadOperatorInNamedFormInAnnotation::class, ReplaceWithArrayCallInAnnotationFix)
        registerPsiQuickFixes(KaFirDiagnostic.RedundantSpreadOperatorInNamedFormInFunction::class, RemoveRedundantSpreadOperatorFix)
        registerPsiQuickFixes(KaFirDiagnostic.NonVarargSpread::class, RemovePsiElementSimpleFix.RemoveSpreadFactory)
    }

    private val visibility = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ChangeVisibilityFixFactories.noExplicitVisibilityInApiMode)
        registerFactory(ChangeVisibilityFixFactories.noExplicitVisibilityInApiModeWarning)
        registerFactory(UseInheritedVisibilityFixFactories.cannotChangeAccessPrivilege)
        registerFactory(UseInheritedVisibilityFixFactories.cannotWeakenAccessPrivilege)
    }

    private val other = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(
            KaFirDiagnostic.InapplicableTargetOnPropertyWarning::class,
            RemoveAnnotationFix.UseSiteGetDoesntHaveAnyEffect,
            RemoveUseSiteTargetFix.UseSiteGetDoesntHaveAnyEffect
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.InapplicableTargetOnProperty::class,
            RemoveAnnotationFix.UseSiteGetDoesntHaveAnyEffect,
            RemoveUseSiteTargetFix.UseSiteGetDoesntHaveAnyEffect
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.DataClassCopyVisibilityWillBeChangedWarning::class,
            AddAnnotationFix.AddConsistentCopyVisibilityAnnotationFactory,
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.DataClassCopyVisibilityWillBeChangedError::class,
            AddAnnotationFix.AddConsistentCopyVisibilityAnnotationFactory,
        )
        registerPsiQuickFixes(KaFirDiagnostic.RedundantAnnotation::class, RemoveAnnotationFix)
        registerPsiQuickFixes(KaFirDiagnostic.DataClassConsistentCopyWrongAnnotationTarget::class, RemoveAnnotationFix)
        registerPsiQuickFixes(KaFirDiagnostic.DataClassConsistentCopyAndExposedCopyAreIncompatibleAnnotations::class, RemoveAnnotationFix)
    }

    private val optIn = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(
            KaFirDiagnostic.OptInMarkerWithWrongRetention::class,
            RemoveAnnotationFix.RemoveForbiddenOptInRetention
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.OptInWithoutArguments::class,
            RemoveAnnotationFix
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.OptInMarkerWithWrongTarget::class,
            RemoveWrongOptInAnnotationTargetFix
        )
        registerPsiQuickFixes(
            KaFirDiagnostic.OptInMarkerOnWrongTarget::class,
            RemoveAnnotationFix
        )

        registerFactory(OptInAnnotationWrongTargetFixFactory.optInAnnotationWrongTargetFixFactory)
        registerFactory(OptInFileLevelFixFactories.optInUsageFactory)
        registerFactory(OptInFileLevelFixFactories.optInUsageErrorFactory)
        registerFactory(OptInFileLevelFixFactories.optInOverrideFactory)
        registerFactory(OptInFileLevelFixFactories.optInOverrideErrorFactory)
        registerFactory(OptInFixFactories.optInUsageFactory)
        registerFactory(OptInFixFactories.optInUsageErrorFactory)
        registerFactory(OptInFixFactories.optInOverrideFactory)
        registerFactory(OptInFixFactories.optInOverrideErrorFactory)
    }

    private val multiplatform = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ActualAnnotationsNotMatchExpectFixFactory.factory)
    }

    private val removePartsFromProperty = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(RemovePartsFromPropertyFixFactory.abstractPropertyWithGetter)
        registerFactory(RemovePartsFromPropertyFixFactory.abstractPropertyWithInitializer)
        registerFactory(RemovePartsFromPropertyFixFactory.abstractPropertyWithSetter)
        registerFactory(RemovePartsFromPropertyFixFactory.inapplicableLateinitModifier)
        registerFactory(RemovePartsFromPropertyFixFactory.propertyInitializerInInterface)
    }

    private val surroundWithNullCheck = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(SurroundWithNullCheckFixFactory.argumentTypeMismatchFactory)
        registerFactory(SurroundWithNullCheckFixFactory.assignmentTypeMismatchFactory)
        registerFactory(SurroundWithNullCheckFixFactory.iteratorOnNullableFactory)
        registerFactory(SurroundWithNullCheckFixFactory.nullabilityMismatchBasedOnJavaAnnotationsFactory)
        registerFactory(SurroundWithNullCheckFixFactory.receiverNullabilityMismatchBasedOnJavaAnnotationsFactory)
        registerFactory(SurroundWithNullCheckFixFactory.unsafeCallFactory)
        registerFactory(SurroundWithNullCheckFixFactory.unsafeImplicitInvokeCallFactory)
        registerFactory(SurroundWithNullCheckFixFactory.unsafeInfixCallFactory)
        registerFactory(SurroundWithNullCheckFixFactory.unsafeOperatorCallFactory)
    }

    override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(
        keywords,
        addAbstract,
        addFinal,
        addInline,
        changeToLabeledReturn,
        insertDelegationCall,
        propertyInitialization,
        overrides,
        imports,
        mutability,
        expressions,
        whenStatements,
        typeMismatch,
        needExplicitType,
        removePartsFromProperty,
        superKeyword,
        surroundWithNullCheck,
        vararg,
        visibility,
        other,
        optIn,
        multiplatform,
        superType,
    )

    override val importOnTheFlyList: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ImportQuickFix.unresolvedReferenceFactory)
        registerFactory(ImportQuickFix.invisibleReferenceFactory)
    }
}
