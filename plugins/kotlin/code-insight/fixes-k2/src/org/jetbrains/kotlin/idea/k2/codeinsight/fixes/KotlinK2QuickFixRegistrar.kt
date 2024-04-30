// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
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
        registerPsiQuickFixes(KtFirDiagnostic.RedundantModifier::class, RemoveModifierFixBase.removeRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.IncompatibleModifiers::class, RemoveModifierFixBase.removeNonRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.RepeatedModifier::class, RemoveModifierFixBase.removeNonRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.DeprecatedModifierPair::class, RemoveModifierFixBase.removeRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.TypeParametersInEnum::class, RemoveModifierFixBase.removeRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.NonAbstractFunctionWithNoBody::class, AddFunctionBodyFix, AddModifierFix.addAbstractModifier)

        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractPropertyInNonAbstractClass::class,
            AddModifierFix.addAbstractToContainingClass,
            RemoveModifierFixBase.removeAbstractModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractFunctionInNonAbstractClass::class,
            AddModifierFix.addAbstractToContainingClass,
            RemoveModifierFixBase.removeAbstractModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.NonFinalMemberInFinalClass::class,
            AddModifierFix.addOpenToContainingClass,
            RemoveModifierFixBase.removeOpenModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.NonFinalMemberInObject::class,
            RemoveModifierFixBase.removeOpenModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.PrivateSetterForOpenProperty::class,
            AddModifierFix.addFinalToProperty,
            RemoveModifierFixBase.removePrivateModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.PrivateSetterForAbstractProperty::class,
            RemoveModifierFixBase.removePrivateModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.NestedClassNotAllowed::class,
            AddModifierFix.addInnerModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.WrongModifierTarget::class,
            RemoveModifierFixBase.removeNonRedundantModifier,
            ChangeVariableMutabilityFix.CONST_VAL_FACTORY
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractMemberNotImplemented::class,
            AddModifierFix.addAbstractModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractClassMemberNotImplemented::class,
            AddModifierFix.addAbstractModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.VirtualMemberHidden::class,
            AddModifierFix.addOverrideModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.NonDataClassJvmRecord::class,
            AddModifierFix.addDataModifier
        )
        registerFactory(AddDataModifierFixFactory.addDataModifierFixFactory)
        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractPropertyInPrimaryConstructorParameters::class,
            RemoveModifierFixBase.removeAbstractModifier
        )
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnLoopParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnFunParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnCatchParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnSecondaryConstructorParameter::class, RemoveValVarFromParameterFix)
        registerFactory(MakeSuperTypeOpenFixFactory.makeSuperTypeOpenFixFactory)
        registerFactory(MakeSuperTypeOpenFixFactory.makeUpperBoundOpenFixFactory)
        registerFactory(AddFunModifierFixFactory.addFunModifierFixFactory)
        registerFactory(AddSuspendModifierFixFactory.addSuspendModifierFixFactory)
        registerFactory(SpecifyOverrideExplicitlyFixFactory.specifyOverrideExplicitlyFixFactory)
        registerFactory(MakeOverriddenMemberOpenFixFactory.makeOverriddenMemberOpenFixFactory)
        registerFactory(ChangeToStarProjectionFixFactory.uncheckedCastFactory)
        registerFactory(ChangeToStarProjectionFixFactory.cannotCheckForErased)
        registerFactory(AddStarProjectionsFixFactory.addStarProjectionsFixFactory)
    }

    private val addAbstract = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeAbstract::class, AddModifierFix.addAbstractModifier)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeAbstractWarning::class, AddModifierFix.addAbstractModifier)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class, AddModifierFix.addAbstractModifier)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class, AddModifierFix.addAbstractModifier)
    }

    private val addFinal = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeFinal::class, AddModifierFix.addFinalToProperty)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeFinalWarning::class, AddModifierFix.addFinalToProperty)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class, AddModifierFix.addFinalToProperty)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class, AddModifierFix.addFinalToProperty)
    }

    private val addInline = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(AddInlineModifierFixFactories.usageIsNotInlinableFactory)
        registerFactory(AddInlineModifierFixFactories.nonLocalReturnNotAllowed)
        registerFactory(AddInlineModifierFixFactories.inlineSuspendFunctionTypeUnsupported)
        registerFactory(MakeTypeParameterReifiedAndFunctionInlineFixFactory.cannotCheckForErasedFactory)
        registerFactory(AddInlineToFunctionFixFactories.illegalInlineParameterModifierFactory)
        registerPsiQuickFixes(KtFirDiagnostic.ReifiedTypeParameterNoInline::class, AddModifierFix.addInlineToFunctionWithReified)
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

        registerPsiQuickFixes(KtFirDiagnostic.LocalVariableWithTypeParameters::class, RemovePsiElementSimpleFix.RemoveTypeParametersFactory)
        registerPsiQuickFixes(
            KtFirDiagnostic.LocalVariableWithTypeParametersWarning::class,
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
        registerPsiQuickFixes(KtFirDiagnostic.ConflictingImport::class, RemovePsiElementSimpleFix.RemoveImportFactory)
        registerPsiQuickFixes(KtFirDiagnostic.UnresolvedImport::class, AddDependencyQuickFixHelper)
    }

    private val mutability = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KtFirDiagnostic.VarOverriddenByVal::class, ChangeVariableMutabilityFix.VAR_OVERRIDDEN_BY_VAL_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.VarAnnotationParameter::class, ChangeVariableMutabilityFix.VAR_ANNOTATION_PARAMETER_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.InapplicableLateinitModifier::class, ChangeVariableMutabilityFix.LATEINIT_VAL_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.ValWithSetter::class, ChangeVariableMutabilityFix.VAL_WITH_SETTER_FACTORY)
        registerFactory(ChangeVariableMutabilityFix.VAL_REASSIGNMENT)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitialized::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedWarning::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeFinal::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(
            KtFirDiagnostic.MustBeInitializedOrBeFinalWarning::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeAbstract::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(
            KtFirDiagnostic.MustBeInitializedOrBeAbstractWarning::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class,
            ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY
        )
        registerPsiQuickFixes(KtFirDiagnostic.VolatileOnValue::class, ChangeVariableMutabilityFix.VOLATILE_ON_VALUE_FACTORY)
    }

    private val expressions = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ReplaceWithDotCallFixFactory.replaceWithDotCallFactory)
        registerPsiQuickFixes(KtFirDiagnostic.UnnecessaryNotNullAssertion::class, RemoveExclExclCallFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessElvis::class, RemoveUselessElvisFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessElvisRightIsNull::class, RemoveUselessElvisFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessCast::class, RemoveUselessCastFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessIsCheck::class, RemoveUselessIsCheckFix, RemoveUselessIsCheckFixForWhen)
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

        registerPsiQuickFixes(KtFirDiagnostic.NullableSupertype::class, RemoveNullableFix.removeForSuperType)
        registerPsiQuickFixes(KtFirDiagnostic.InapplicableLateinitModifier::class, RemoveNullableFix.removeForLateInitProperty)
        registerPsiQuickFixes(
            KtFirDiagnostic.TypeArgumentsRedundantInSuperQualifier::class,
            RemovePsiElementSimpleFix.RemoveTypeArgumentsFactory
        )

        registerFactory(ConvertToBlockBodyFixFactory.convertToBlockBodyFixFactory)
        registerFactory(SimplifyComparisonFixFactory.simplifyComparisonFixFactory)
    }

    private val whenStatements = KtQuickFixesListBuilder.registerPsiQuickFix {
        // TODO: NON_EXHAUSTIVE_WHEN[_ON_SEALED_CLASS] will be replaced in future. We need to register the fix for those diagnostics as well
        registerPsiQuickFixes(KtFirDiagnostic.NoElseInWhen::class, AddWhenElseBranchFix)
        registerFactory(AddWhenRemainingBranchFixFactories.noElseInWhen)
        registerPsiQuickFixes(KtFirDiagnostic.CommaInWhenConditionWithoutArgument::class, CommaInWhenConditionWithoutArgumentFix)
        registerPsiQuickFixes(KtFirDiagnostic.SenselessNullInWhen::class, RemoveWhenBranchFix)
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
            KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormAnnotationError::class,
            ReplaceWithArrayCallInAnnotationFix
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormAnnotationWarning::class,
            ReplaceWithArrayCallInAnnotationFix
        )
        registerFactory(SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory.assigningSingleElementToVarargInNamedFormFunction)
        registerFactory(SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory.assigningSingleElementToVarargInNamedFormFunctionWarning)
        registerPsiQuickFixes(KtFirDiagnostic.RedundantSpreadOperatorInNamedFormInAnnotation::class, ReplaceWithArrayCallInAnnotationFix)
        registerPsiQuickFixes(KtFirDiagnostic.RedundantSpreadOperatorInNamedFormInFunction::class, RemoveRedundantSpreadOperatorFix)
        registerPsiQuickFixes(KtFirDiagnostic.NonVarargSpread::class, RemovePsiElementSimpleFix.RemoveSpreadFactory)
    }

    private val visibility = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ChangeVisibilityFixFactories.noExplicitVisibilityInApiMode)
        registerFactory(ChangeVisibilityFixFactories.noExplicitVisibilityInApiModeWarning)
        registerFactory(UseInheritedVisibilityFixFactories.cannotChangeAccessPrivilege)
        registerFactory(UseInheritedVisibilityFixFactories.cannotWeakenAccessPrivilege)
    }

    private val other = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(
            KtFirDiagnostic.InapplicableTargetOnPropertyWarning::class,
            RemoveAnnotationFix.UseSiteGetDoesntHaveAnyEffect,
            RemoveUseSiteTargetFix.UseSiteGetDoesntHaveAnyEffect
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.InapplicableTargetOnProperty::class,
            RemoveAnnotationFix.UseSiteGetDoesntHaveAnyEffect,
            RemoveUseSiteTargetFix.UseSiteGetDoesntHaveAnyEffect
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.DataClassCopyVisibilityWillBeChangedWarning::class,
            AddAnnotationFix.AddConsistentCopyVisibilityAnnotationFactory,
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.DataClassCopyVisibilityWillBeChangedError::class,
            AddAnnotationFix.AddConsistentCopyVisibilityAnnotationFactory,
        )
        registerPsiQuickFixes(KtFirDiagnostic.RedundantAnnotation::class, RemoveAnnotationFix)
        registerPsiQuickFixes(KtFirDiagnostic.DataClassConsistentCopyWrongAnnotationTarget::class, RemoveAnnotationFix)
        registerPsiQuickFixes(KtFirDiagnostic.DataClassConsistentCopyAndExposedCopyAreIncompatibleAnnotations::class, RemoveAnnotationFix)
    }

    private val optIn = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(
            KtFirDiagnostic.OptInMarkerWithWrongRetention::class,
            RemoveAnnotationFix.RemoveForbiddenOptInRetention
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.OptInWithoutArguments::class,
            RemoveAnnotationFix
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.OptInMarkerWithWrongTarget::class,
            RemoveWrongOptInAnnotationTargetFix
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.OptInMarkerOnWrongTarget::class,
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

    override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(
        keywords,
        addAbstract,
        addFinal,
        addInline,
        propertyInitialization,
        overrides,
        imports,
        mutability,
        expressions,
        whenStatements,
        typeMismatch,
        needExplicitType,
        superKeyword,
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
