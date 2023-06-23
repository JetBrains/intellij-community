// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberNotImplementedQuickfixFactories
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.idea.quickfix.fixes.*
import org.jetbrains.kotlin.idea.quickfix.fixes.ConvertToBlockBodyFixFactory

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
        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractPropertyInPrimaryConstructorParameters::class,
            RemoveModifierFixBase.removeAbstractModifier
        )
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnLoopParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnFunParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnCatchParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnSecondaryConstructorParameter::class, RemoveValVarFromParameterFix)
        registerApplicators(MakeSuperTypeOpenFixFactory.makeSuperTypeOpenFixFactory)
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

    private val propertyInitialization = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicators(InitializePropertyQuickFixFactories.initializePropertyFactory)
        registerApplicator(AddLateInitFactory.addLateInitFactory)
        registerApplicators(AddAccessorsFactories.addAccessorsToUninitializedProperty)

        registerPsiQuickFixes(KtFirDiagnostic.LocalVariableWithTypeParameters::class, RemovePsiElementSimpleFix.RemoveTypeParametersFactory)
        registerPsiQuickFixes(
            KtFirDiagnostic.LocalVariableWithTypeParametersWarning::class,
            RemovePsiElementSimpleFix.RemoveTypeParametersFactory
        )
    }

    private val overrides = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(ChangeTypeQuickFixFactories.changeFunctionReturnTypeOnOverride)
        registerApplicator(ChangeTypeQuickFixFactories.changePropertyReturnTypeOnOverride)
        registerApplicator(ChangeTypeQuickFixFactories.changeVariableReturnTypeOnOverride)
        registerApplicator(MemberNotImplementedQuickfixFactories.abstractMemberNotImplemented)
        registerApplicator(MemberNotImplementedQuickfixFactories.abstractClassMemberNotImplemented)
        registerApplicator(MemberNotImplementedQuickfixFactories.manyInterfacesMemberNotImplemented)
        registerApplicator(MemberNotImplementedQuickfixFactories.manyImplMemberNotImplemented)
    }

    private val imports = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(ImportQuickFix.FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.ConflictingImport::class, RemovePsiElementSimpleFix.RemoveImportFactory)
    }

    private val mutability = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KtFirDiagnostic.VarOverriddenByVal::class, ChangeVariableMutabilityFix.VAR_OVERRIDDEN_BY_VAL_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.VarAnnotationParameter::class, ChangeVariableMutabilityFix.VAR_ANNOTATION_PARAMETER_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.InapplicableLateinitModifier::class, ChangeVariableMutabilityFix.LATEINIT_VAL_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.ValWithSetter::class, ChangeVariableMutabilityFix.VAL_WITH_SETTER_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitialized::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedWarning::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeFinal::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeFinalWarning::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeAbstract::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrBeAbstractWarning::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
    }

    private val expressions = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(ReplaceWithDotCallFixFactory.replaceWithDotCallFactory)
        registerPsiQuickFixes(KtFirDiagnostic.UnnecessaryNotNullAssertion::class, RemoveExclExclCallFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessElvis::class, RemoveUselessElvisFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessElvisRightIsNull::class, RemoveUselessElvisFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessCast::class, RemoveUselessCastFix)
        registerPsiQuickFixes(KtFirDiagnostic.UselessIsCheck::class, RemoveUselessIsCheckFix, RemoveUselessIsCheckFixForWhen)
        registerApplicator(ReplaceCallFixFactories.unsafeCallFactory)
        registerApplicator(ReplaceCallFixFactories.unsafeInfixCallFactory)
        registerApplicator(ReplaceCallFixFactories.unsafeOperatorCallFactory)
        registerApplicator(ReplaceCallFixFactories.unsafeImplicitInvokeCallFactory)
        registerApplicator(AddExclExclCallFixFactories.unsafeCallFactory)
        registerApplicator(AddExclExclCallFixFactories.unsafeInfixCallFactory)
        registerApplicator(AddExclExclCallFixFactories.unsafeOperatorCallFactory)
        registerApplicator(AddExclExclCallFixFactories.iteratorOnNullableFactory)
        registerApplicator(TypeMismatchFactories.argumentTypeMismatchFactory)
        registerApplicator(TypeMismatchFactories.returnTypeMismatchFactory)
        registerApplicator(TypeMismatchFactories.assignmentTypeMismatch)
        registerApplicator(TypeMismatchFactories.initializerTypeMismatch)
        registerApplicator(TypeMismatchFactories.smartcastImpossibleFactory)

        registerApplicator(WrapWithSafeLetCallFixFactories.forUnsafeCall)
        registerApplicator(WrapWithSafeLetCallFixFactories.forUnsafeImplicitInvokeCall)
        registerApplicator(WrapWithSafeLetCallFixFactories.forUnsafeInfixCall)
        registerApplicator(WrapWithSafeLetCallFixFactories.forUnsafeOperatorCall)
        registerApplicator(WrapWithSafeLetCallFixFactories.forArgumentTypeMismatch)

        registerPsiQuickFixes(KtFirDiagnostic.NullableSupertype::class, RemoveNullableFix.removeForSuperType)
        registerPsiQuickFixes(KtFirDiagnostic.InapplicableLateinitModifier::class, RemoveNullableFix.removeForLateInitProperty)
        registerPsiQuickFixes(
            KtFirDiagnostic.TypeArgumentsRedundantInSuperQualifier::class,
            RemovePsiElementSimpleFix.RemoveTypeArgumentsFactory
        )

        registerApplicator(ConvertToBlockBodyFixFactory.convertToBlockBodyFixFactory)
    }

    private val whenStatements = KtQuickFixesListBuilder.registerPsiQuickFix {
        // TODO: NON_EXHAUSTIVE_WHEN[_ON_SEALED_CLASS] will be replaced in future. We need to register the fix for those diagnostics as well
        registerPsiQuickFixes(KtFirDiagnostic.NoElseInWhen::class, AddWhenElseBranchFix)
        registerApplicator(AddWhenRemainingBranchFixFactories.noElseInWhen)
        registerPsiQuickFixes(KtFirDiagnostic.CommaInWhenConditionWithoutArgument::class, CommaInWhenConditionWithoutArgumentFix)
        registerPsiQuickFixes(KtFirDiagnostic.SenselessNullInWhen::class, RemoveWhenBranchFix)
    }

    private val typeMismatch = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(ChangeTypeQuickFixFactories.componentFunctionReturnTypeMismatch)
        registerApplicator(ChangeTypeQuickFixFactories.returnTypeMismatch)

        registerApplicator(AddToStringFixFactories.typeMismatch)
        registerApplicator(AddToStringFixFactories.argumentTypeMismatch)
        registerApplicator(AddToStringFixFactories.assignmentTypeMismatch)
        registerApplicator(AddToStringFixFactories.returnTypeMismatch)
        registerApplicator(AddToStringFixFactories.initializerTypeMismatch)

        registerApplicator(CastExpressionFixFactories.smartcastImpossible)
        registerApplicator(CastExpressionFixFactories.typeMismatch)
        registerApplicator(CastExpressionFixFactories.throwableTypeMismatch)
        registerApplicator(CastExpressionFixFactories.argumentTypeMismatch)
        registerApplicator(CastExpressionFixFactories.assignmentTypeMismatch)
        registerApplicator(CastExpressionFixFactories.returnTypeMismatch)
        registerApplicator(CastExpressionFixFactories.initializerTypeMismatch)
    }

    private val needExplicitType = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(SpecifyExplicitTypeFixFactories.ambiguousAnonymousTypeInferred)
        registerApplicator(SpecifyExplicitTypeFixFactories.noExplicitReturnTypeInApiMode)
        registerApplicator(SpecifyExplicitTypeFixFactories.noExplicitReturnTypeInApiModeWarning)
    }

    private val superKeyword = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(SpecifySuperTypeFixFactory.ambiguousSuper)
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
        registerApplicator(SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory.assigningSingleElementToVarargInNamedFormFunction)
        registerApplicator(SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory.assigningSingleElementToVarargInNamedFormFunctionWarning)
        registerPsiQuickFixes(KtFirDiagnostic.RedundantSpreadOperatorInNamedFormInAnnotation::class, ReplaceWithArrayCallInAnnotationFix)
        registerPsiQuickFixes(KtFirDiagnostic.RedundantSpreadOperatorInNamedFormInFunction::class, RemoveRedundantSpreadOperatorFix)
        registerPsiQuickFixes(KtFirDiagnostic.NonVarargSpread::class, RemovePsiElementSimpleFix.RemoveSpreadFactory)
    }

    private val visibility = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(ChangeVisibilityFixFactories.noExplicitVisibilityInApiMode)
        registerApplicator(ChangeVisibilityFixFactories.noExplicitVisibilityInApiModeWarning)
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
    }

    override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(
        keywords,
        addAbstract,
        addFinal,
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
    )
}
