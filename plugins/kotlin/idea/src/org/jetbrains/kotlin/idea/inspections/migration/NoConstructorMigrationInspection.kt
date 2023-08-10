// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.migration

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.migration.MigrationInfo
import org.jetbrains.kotlin.idea.migration.isLanguageVersionUpdate
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix

class NoConstructorMigrationInspection :
    AbstractDiagnosticBasedMigrationInspection<PsiElement>(PsiElement::class.java),
    MigrationFix {
    override fun isApplicable(migrationInfo: MigrationInfo): Boolean = migrationInfo.isLanguageVersionUpdate(
        untilOldVersion = LanguageVersion.KOTLIN_1_8,
        sinceNewVersion = LanguageVersion.KOTLIN_1_7,
    )

    override fun getDiagnosticFactory(languageVersionSettings: LanguageVersionSettings): DiagnosticFactory0<PsiElement> =
        Errors.NO_CONSTRUCTOR_WARNING
}
