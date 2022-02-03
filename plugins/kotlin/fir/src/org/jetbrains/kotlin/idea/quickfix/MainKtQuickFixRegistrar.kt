// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.core.overrideImplement.MemberNotImplementedQuickfixFactories
import org.jetbrains.kotlin.idea.fir.api.fixes.KtQuickFixRegistrar
import org.jetbrains.kotlin.idea.fir.api.fixes.KtQuickFixesList
import org.jetbrains.kotlin.idea.fir.api.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.frontend.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.quickfix.fixes.*

class MainKtQuickFixRegistrar : KtQuickFixRegistrar() {
    private val keywords = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KtFirDiagnostic.RedundantModifier::class, RemoveModifierFix.removeRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.IncompatibleModifiers::class, RemoveModifierFix.removeNonRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.RepeatedModifier::class, RemoveModifierFix.removeNonRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.DeprecatedModifierPair::class, RemoveModifierFix.removeRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.TypeParametersInEnum::class, RemoveModifierFix.removeRedundantModifier)
        registerPsiQuickFixes(KtFirDiagnostic.RedundantOpenInInterface::class, RemoveModifierFix.removeRedundantOpenModifier)
        registerPsiQuickFixes(KtFirDiagnostic.NonAbstractFunctionWithNoBody::class, AddFunctionBodyFix, AddModifierFix.addAbstractModifier)
        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractPropertyInNonAbstractClass::class,
            AddModifierFix.addAbstractToContainingClass,
            RemoveModifierFix.removeAbstractModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.AbstractFunctionInNonAbstractClass::class,
            AddModifierFix.addAbstractToContainingClass,
            RemoveModifierFix.removeAbstractModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.NonFinalMemberInFinalClass::class,
            AddModifierFix.addOpenToContainingClass,
            RemoveModifierFix.removeOpenModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.PrivateSetterForOpenProperty::class,
            AddModifierFix.addFinalToProperty,
            RemoveModifierFix.removePrivateModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.PrivateSetterForAbstractProperty::class,
            RemoveModifierFix.removePrivateModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.NestedClassNotAllowed::class,
            AddModifierFix.addInnerModifier
        )
        registerPsiQuickFixes(
            KtFirDiagnostic.WrongModifierTarget::class,
            RemoveModifierFix.removeNonRedundantModifier,
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
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnLoopParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnFunParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnCatchParameter::class, RemoveValVarFromParameterFix)
        registerPsiQuickFixes(KtFirDiagnostic.ValOrVarOnSecondaryConstructorParameter::class, RemoveValVarFromParameterFix)
    }

    private val propertyInitialization = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(
            KtFirDiagnostic.MustBeInitializedOrBeAbstract::class,
            AddModifierFix.addAbstractModifier,
        )
        registerApplicators(InitializePropertyQuickFixFactories.initializePropertyFactory)
        registerApplicator(AddLateInitFactory.addLateInitFactory)
        registerApplicators(AddAccessorsFactories.addAccessorsToUninitializedProperty)
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
    }

    private val mutability = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerPsiQuickFixes(KtFirDiagnostic.VarOverriddenByVal::class, ChangeVariableMutabilityFix.VAR_OVERRIDDEN_BY_VAL_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.VarAnnotationParameter::class, ChangeVariableMutabilityFix.VAR_ANNOTATION_PARAMETER_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.InapplicableLateinitModifier::class, ChangeVariableMutabilityFix.LATEINIT_VAL_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.ValWithSetter::class, ChangeVariableMutabilityFix.VAL_WITH_SETTER_FACTORY)
        registerPsiQuickFixes(KtFirDiagnostic.MustBeInitialized::class, ChangeVariableMutabilityFix.MUST_BE_INITIALIZED_FACTORY)
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
    }

    private val whenStatements = KtQuickFixesListBuilder.registerPsiQuickFix {
        // TODO: NON_EXHAUSTIVE_WHEN[_ON_SEALED_CLASS] will be replaced in future. We need to register the fix for those diagnostics as well
        registerPsiQuickFixes(KtFirDiagnostic.NoElseInWhen::class, AddWhenElseBranchFix)
        registerApplicator(AddWhenRemainingBranchFixFactories.noElseInWhen)
    }

    private val typeMismatch = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(ChangeTypeQuickFixFactories.componentFunctionReturnTypeMismatch)
        registerApplicator(ChangeTypeQuickFixFactories.returnTypeMismatch)

        registerApplicator(AddToStringFixFactories.typeMismatch)
        registerApplicator(AddToStringFixFactories.argumentTypeMismatch)
        registerApplicator(AddToStringFixFactories.assignmentTypeMismatch)
        registerApplicator(AddToStringFixFactories.returnTypeMismatch)
        registerApplicator(AddToStringFixFactories.initializerTypeMismatch)
    }


    override val list: KtQuickFixesList = KtQuickFixesList.createCombined(
        keywords,
        propertyInitialization,
        overrides,
        imports,
        mutability,
        expressions,
        whenStatements,
        typeMismatch
    )
}
