// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.migration

import com.intellij.codeInspection.CleanupLocalInspectionTool
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryWithPsiElement
import org.jetbrains.kotlin.idea.migration.MigrationInfo
import org.jetbrains.kotlin.idea.migration.isLanguageVersionUpdate
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm


class ProhibitJvmOverloadsOnConstructorsOfAnnotationClassesMigrationInspection :
    AbstractDiagnosticBasedMigrationInspection<KtAnnotationEntry>(KtAnnotationEntry::class.java),
    MigrationFix,
    CleanupLocalInspectionTool {
    override fun isApplicable(migrationInfo: MigrationInfo): Boolean {
        return migrationInfo.isLanguageVersionUpdate(LanguageVersion.KOTLIN_1_3, LanguageVersion.KOTLIN_1_4)
    }

    override fun getDiagnosticFactory(languageVersionSettings: LanguageVersionSettings): DiagnosticFactoryWithPsiElement<KtAnnotationEntry, *> =
        with(ErrorsJvm.OVERLOADS_ANNOTATION_CLASS_CONSTRUCTOR) { languageVersionSettings.chooseFactory() }
}

