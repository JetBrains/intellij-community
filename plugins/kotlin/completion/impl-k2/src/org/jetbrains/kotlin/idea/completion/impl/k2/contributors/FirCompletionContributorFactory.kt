// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.recording

internal class FirCompletionContributorFactory(
    private val basicContext: FirBasicCompletionContext,
    private val resultController: PolicyController,
) {
    fun keywordContributor(priority: Int = 0) =
        FirKeywordCompletionContributor(basicContext, priority).recording(resultController)

    fun classReferenceContributor(priority: Int = 0) =
        FirClassReferenceCompletionContributor(basicContext, priority).recording(resultController)

    fun callableContributor(priority: Int = 0) =
        FirCallableCompletionContributor(basicContext, priority).recording(resultController)

    fun superMemberContributor(priority: Int) =
        FirSuperMemberCompletionContributor(basicContext, priority).recording(resultController)

    fun infixCallableContributor(priority: Int = 0) =
        FirInfixCallableCompletionContributor(basicContext, priority).recording(resultController)

    fun callableReferenceContributor(priority: Int = 0) =
        FirCallableReferenceCompletionContributor(basicContext, priority).recording(resultController)

    fun classifierContributor(priority: Int = 0) =
        FirClassifierCompletionContributor(basicContext, priority).recording(resultController)

    fun classifierReferenceContributor(priority: Int = 0) =
        FirClassifierReferenceCompletionContributor(basicContext, priority).recording(resultController)

    fun annotationsContributor(priority: Int = 0) =
        FirAnnotationCompletionContributor(basicContext, priority).recording(resultController)

    fun packageCompletionContributor(priority: Int = 0) =
        FirPackageCompletionContributor(basicContext, priority).recording(resultController)

    fun importDirectivePackageMembersContributor(priority: Int = 0) =
        FirImportDirectivePackageMembersCompletionContributor(basicContext, priority).recording(resultController)

    fun typeParameterConstraintNameInWhereClauseContributor(priority: Int = 0) =
        FirTypeParameterConstraintNameInWhereClauseCompletionContributor(basicContext, priority).recording(resultController)

    fun classifierNameContributor(priority: Int = 0) =
        FirSameAsFileClassifierNameCompletionContributor(basicContext, priority).recording(resultController)

    fun whenWithSubjectConditionContributor(priority: Int = 0) =
        FirWhenWithSubjectConditionContributor(basicContext, priority).recording(resultController)

    fun superEntryContributor(priority: Int) =
        FirSuperEntryContributor(basicContext, priority).recording(resultController)

    fun declarationFromUnresolvedNameContributor(priority: Int) =
        FirDeclarationFromUnresolvedNameContributor(basicContext, priority).recording(resultController)

    fun declarationFromOverridableMembersContributor(priority: Int) =
        FirDeclarationFromOverridableMembersContributor(basicContext, priority).recording(resultController)

    fun namedArgumentContributor(priority: Int = 0) =
        FirNamedArgumentCompletionContributor(basicContext, priority).recording(resultController)

    fun variableOrParameterNameWithTypeContributor(priority: Int) =
        FirVariableOrParameterNameWithTypeCompletionContributor(basicContext, priority).recording(resultController)

    fun kDocParameterNameContributor(priority: Int) =
        FirKDocParameterNameContributor(basicContext, priority).recording(resultController)

    fun kDocCallableContributor(priority: Int) =
        FirKDocCallableCompletionContributor(basicContext, priority).recording(resultController)
}