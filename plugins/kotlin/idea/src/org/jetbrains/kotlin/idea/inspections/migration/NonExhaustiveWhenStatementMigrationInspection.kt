// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.migration

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.migration.MigrationInfo
import org.jetbrains.kotlin.idea.migration.isLanguageVersionUpdate
import org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix
import org.jetbrains.kotlin.psi.KtWhenExpression

class NonExhaustiveWhenStatementMigrationInspection :
    AbstractDiagnosticBasedMigrationInspection<KtWhenExpression>(KtWhenExpression::class.java),
    MigrationFix {
    override fun isApplicable(migrationInfo: MigrationInfo): Boolean =
        migrationInfo.isLanguageVersionUpdate(LanguageVersion.KOTLIN_1_5, LanguageVersion.KOTLIN_1_6)

    override fun getDiagnosticFactory(languageVersionSettings: LanguageVersionSettings): DiagnosticFactory2<KtWhenExpression, String, MutableList<WhenMissingCase>> =
        Errors.NON_EXHAUSTIVE_WHEN_STATEMENT

    override fun customIntentionFactory(): ((Diagnostic) -> IntentionAction?) = { diagnostic ->
        AddWhenElseBranchFix.createQuickFix(diagnostic.psiElement).singleOrNull()
    }

    override fun customHighlightRangeIn(element: KtWhenExpression): TextRange = element.whenKeyword.textRangeInParent
}