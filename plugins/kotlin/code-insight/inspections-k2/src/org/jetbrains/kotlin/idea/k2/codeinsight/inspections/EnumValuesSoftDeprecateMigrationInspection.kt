// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.EnumValuesSoftDeprecateMigrationInspectionBase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression

// TODO: make visible in migrations in next commits
internal class EnumValuesSoftDeprecateMigrationInspection : EnumValuesSoftDeprecateMigrationInspectionBase() {

    override fun KtAnalysisSession.isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean {
        // TODO: add check for opt-in in next commits
        return true
    }
}