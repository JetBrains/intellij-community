// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageBuilder.generateCreateMethodActions
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry

class K2CreateFromUsageQuickFixesRegistrar : KotlinQuickFixRegistrar() {

    private val createFunctionForArgumentTypeMismatch: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.ArgumentTypeMismatch> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
            functionOrConstructorBuilder(diagnostic.psi)
        }
    private val createFunctionForTooManyArguments: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.TooManyArguments> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.TooManyArguments ->
            functionOrConstructorBuilder(diagnostic.psi)
        }
    private val createFunctionForNoValueForParameter: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.NoValueForParameter> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NoValueForParameter ->
            functionOrConstructorBuilder(diagnostic.psi)
        }

    private val createFunctionForNoneApplicable: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.NoneApplicable> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NoneApplicable ->
            functionOrConstructorBuilder(diagnostic.psi)
        }

    private val createVariableForExpressionExpectedPackageFound: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.ExpressionExpectedPackageFound> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ExpressionExpectedPackageFound ->
            listOfNotNull(K2CreateLocalVariableFromUsageBuilder.generateCreateLocalVariableAction(diagnostic.psi)) +
                    K2CreatePropertyFromUsageBuilder.generateCreatePropertyActions(diagnostic.psi) +
                    (K2CreateParameterFromUsageBuilder.generateCreateParameterAction(diagnostic.psi) ?: emptyList())
        }
    private val createParameterForNamedParameterNotFound: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.NamedParameterNotFound> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NamedParameterNotFound ->
            listOfNotNull(K2CreateParameterFromUsageBuilder.generateCreateParameterActionForNamedParameterNotFound(diagnostic.psi))
        }
    private val createParameterForComponentFunctionMissing: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.ComponentFunctionMissing> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ComponentFunctionMissing ->
            listOfNotNull(K2CreateParameterFromUsageBuilder.generateCreateParameterActionForComponentFunctionMissing(diagnostic.psi, diagnostic.destructingType))
        }
    private val createClassFromUsageForUnresolvedImport: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.UnresolvedImport> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedImport ->
            createClassFromUsageForUnresolvedImport(diagnostic)
        }

    private fun createClassFromUsageForUnresolvedImport(diagnostic: KaFirDiagnostic.UnresolvedImport): List<IntentionAction> {
        val unresolvedName = diagnostic.reference
        val simpleReferences = (diagnostic.psi as? KtImportDirective)?.importedReference?.children ?: emptyArray()
        for (simpleReference in simpleReferences) {
            if (unresolvedName == simpleReference.text) {
                return K2CreateClassFromUsageBuilder.generateCreateClassActions(simpleReference as KtElement)
            }
        }
        return listOf()
    }

    override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(createFunctionForArgumentTypeMismatch)
        registerFactory(createFunctionForTooManyArguments)
        registerFactory(createFunctionForNoValueForParameter)
        registerFactory(createFunctionForNoneApplicable)
        registerFactory(createVariableForExpressionExpectedPackageFound)
        registerFactory(createParameterForNamedParameterNotFound)
        registerFactory(createParameterForComponentFunctionMissing)
        registerFactory(createClassFromUsageForUnresolvedImport)
    }
}

private fun functionOrConstructorBuilder(psi: PsiElement): List<IntentionAction> {
    val expression = if (psi is KtQualifiedExpression) psi.selectorExpression else psi
    val callElement = PsiTreeUtil.getParentOfType(expression, KtCallElement::class.java, false)
    if (callElement == null) {
        return if (psi is KtElement) generateCreateMethodActions(psi) else emptyList()
    }
    if (callElement is KtConstructorDelegationCall || callElement is KtSuperTypeCallEntry) {
        return K2CreateSecondaryConstructorFromUsageBuilder.buildRequestsAndActions(callElement)
    }
    val callExpression = callElement as? KtCallExpression ?: return emptyList()
    return if (K2CreateSecondaryConstructorFromUsageBuilder.findTargetClassForCallExpression(callExpression) != null) {
        K2CreateSecondaryConstructorFromUsageBuilder.buildRequestsAndActions(callExpression)
    } else {
        K2CreateFunctionFromUsageBuilder.buildRequestsAndActions(callExpression)
    }
}
