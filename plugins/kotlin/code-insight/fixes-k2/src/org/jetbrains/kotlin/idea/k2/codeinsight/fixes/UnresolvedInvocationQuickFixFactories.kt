// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.longestUnsafeDollarSequenceLengthForPlainTextConversion
import org.jetbrains.kotlin.idea.quickfix.AddInterpolationPrefixFix
import org.jetbrains.kotlin.idea.quickfix.UnresolvedInvocationQuickFix
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object UnresolvedInvocationQuickFixFactories {

    val changeToPropertyAccessQuickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.FunctionExpected ->
        val expression = UnresolvedInvocationQuickFix.findAcceptableParentCallExpression(diagnostic.psi)
            ?: return@ModCommandBased emptyList()

        listOf(UnresolvedInvocationQuickFix.ChangeToPropertyAccessQuickFix(expression))
    }

    val removeParentInvocationQuickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        val expression = UnresolvedInvocationQuickFix.findAcceptableParentCallExpression(diagnostic.psi)
            ?: return@ModCommandBased emptyList()

        listOf(UnresolvedInvocationQuickFix.RemoveInvocationQuickFix(expression))
    }

    val addInterpolationPrefixFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        val stringTemplateExpression = diagnostic.psi.parent.safeAs<KtStringTemplateEntry>()?.parent?.safeAs<KtStringTemplateExpression>()
            ?: return@ModCommandBased emptyList()

        listOfNotNull(createInterpolationPrefixFixIfApplicable(stringTemplateExpression))
    }

    private fun createInterpolationPrefixFixIfApplicable(stringTemplateExpression: KtStringTemplateExpression): AddInterpolationPrefixFix? {
        if (!stringTemplateExpression.languageVersionSettings.supportsFeature(LanguageFeature.MultiDollarInterpolation)) return null
        if (stringTemplateExpression.interpolationPrefix != null) return null
        if (stringTemplateExpression.isSingleQuoted()) return null
        if (stringTemplateExpression.entries.filterIsInstance<KtBlockStringTemplateEntry>().isNotEmpty()) return null
        if (containsResolvedReferences(stringTemplateExpression)) return null
        val longestSequence = longestUnsafeDollarSequenceLengthForPlainTextConversion(stringTemplateExpression)
        return AddInterpolationPrefixFix(stringTemplateExpression, prefixLength = longestSequence + 1)
    }

    private fun containsResolvedReferences(stringTemplateExpression: KtStringTemplateExpression): Boolean {
        return analyze(stringTemplateExpression) {
            stringTemplateExpression.entries.filterIsInstance<KtSimpleNameStringTemplateEntry>().any { nameEntry ->
                val reference = nameEntry.expression?.references?.singleOrNull()
                reference?.resolve() != null
            }
        }
    }

    val removeInvocationQuickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoValueForParameter ->
        val expression = UnresolvedInvocationQuickFix.findAcceptableCallExpression(diagnostic.psi)
            ?: return@ModCommandBased emptyList()

        listOf(UnresolvedInvocationQuickFix.RemoveInvocationQuickFix(expression))
    }
}