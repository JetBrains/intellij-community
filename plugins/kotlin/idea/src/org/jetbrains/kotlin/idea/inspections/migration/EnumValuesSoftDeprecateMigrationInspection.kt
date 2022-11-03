// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.migration

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.EnumValuesSoftDeprecateMigrationInspectionBase
import org.jetbrains.kotlin.idea.migration.MigrationInfo
import org.jetbrains.kotlin.idea.migration.isLanguageVersionUpdate
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker.Companion.isOptInAllowed

internal class EnumValuesSoftDeprecateMigrationInspection : EnumValuesSoftDeprecateMigrationInspectionBase(), MigrationFix {
    override fun isApplicable(migrationInfo: MigrationInfo): Boolean {
        return migrationInfo.isLanguageVersionUpdate(LanguageVersion.KOTLIN_1_8, LanguageVersion.KOTLIN_1_9)
    }

    override fun KtAnalysisSession.isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean {
        return element.isOptInAllowed(annotationClassId.asSingleFqName(), element.languageVersionSettings, element.analyze())
    }
}
