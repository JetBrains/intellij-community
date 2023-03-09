// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.migration

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryWithPsiElement
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.migration.MigrationInfo
import org.jetbrains.kotlin.idea.migration.isLanguageVersionUpdate
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix


class InlineClassDeprecatedMigrationInspection :
    AbstractDiagnosticBasedMigrationInspection<PsiElement>(PsiElement::class.java),
    MigrationFix,
    CleanupLocalInspectionTool {
    override fun isApplicable(migrationInfo: MigrationInfo): Boolean {
        return migrationInfo.isLanguageVersionUpdate(LanguageVersion.KOTLIN_1_4, LanguageVersion.KOTLIN_1_5)
    }

    override fun descriptionMessage(): String = KotlinBundle.message("inspection.deprecated.inline.class.text")

    override fun getDiagnosticFactory(languageVersionSettings: LanguageVersionSettings): DiagnosticFactoryWithPsiElement<PsiElement, *> =
        Errors.INLINE_CLASS_DEPRECATED
}