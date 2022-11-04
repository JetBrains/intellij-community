// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.ShortenOption
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.fir.codeInsight.isOptInAllowed
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.EnumValuesSoftDeprecateMigrationInspectionBase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement

internal class EnumValuesSoftDeprecateMigrationInspection : EnumValuesSoftDeprecateMigrationInspectionBase() {

    override fun KtAnalysisSession.isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean {
        return isOptInAllowed(element, annotationClassId, element.languageVersionSettings)
    }

    override fun createQuickFix(castToArrayNeeded: Boolean, enumClassQualifiedName: String): LocalQuickFix {
        return K2ReplaceFix(castToArrayNeeded, enumClassQualifiedName)
    }

    private class K2ReplaceFix(castToArrayNeeded: Boolean, enumClassQualifiedName: String) :
        ReplaceFix(castToArrayNeeded, enumClassQualifiedName) {
        override fun shortenReferences(element: KtElement) {
            shortenReferences(element, callableShortenOption = { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED })
        }
    }
}