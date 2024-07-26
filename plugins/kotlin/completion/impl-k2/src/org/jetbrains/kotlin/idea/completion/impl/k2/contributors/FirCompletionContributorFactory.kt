// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.withPolicyController

internal class FirCompletionContributorFactory(
    private val basicContext: FirBasicCompletionContext,
    private val policyController: PolicyController,
) {
    fun keywordContributor(priority: Int = 0) =
        FirKeywordCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun classReferenceContributor(priority: Int = 0) =
        FirClassReferenceCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun callableContributor(priority: Int = 0) =
        FirCallableCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun superMemberContributor(priority: Int) =
        FirSuperMemberCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun infixCallableContributor(priority: Int = 0) =
        FirInfixCallableCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun callableReferenceContributor(priority: Int = 0) =
        FirCallableReferenceCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun classifierContributor(priority: Int = 0) =
        FirClassifierCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun classifierReferenceContributor(priority: Int = 0) =
        FirClassifierReferenceCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun annotationsContributor(priority: Int = 0) =
        FirAnnotationCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun packageCompletionContributor(priority: Int = 0) =
        FirPackageCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun importDirectivePackageMembersContributor(priority: Int = 0) =
        FirImportDirectivePackageMembersCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun typeParameterConstraintNameInWhereClauseContributor(priority: Int = 0) =
        FirTypeParameterConstraintNameInWhereClauseCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun classifierNameContributor(priority: Int = 0) =
        FirSameAsFileClassifierNameCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun whenWithSubjectConditionContributor(priority: Int = 0) =
        FirWhenWithSubjectConditionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun superEntryContributor(priority: Int) =
        FirSuperEntryContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun declarationFromUnresolvedNameContributor(priority: Int) =
        FirDeclarationFromUnresolvedNameContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun declarationFromOverridableMembersContributor(priority: Int) =
        FirDeclarationFromOverridableMembersContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun namedArgumentContributor(priority: Int = 0) =
        FirNamedArgumentCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun variableOrParameterNameWithTypeContributor(priority: Int) =
        FirVariableOrParameterNameWithTypeCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun kDocParameterNameContributor(priority: Int) =
        FirKDocParameterNameContributor(basicContext, priority)
            .withPolicyController(policyController)

    fun kDocCallableContributor(priority: Int) =
        FirKDocCallableCompletionContributor(basicContext, priority)
            .withPolicyController(policyController)
}