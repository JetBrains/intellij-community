// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.EnumValuesSoftDeprecateInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isOptInAllowed
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*

internal class EnumValuesSoftDeprecateInspection : EnumValuesSoftDeprecateInspectionBase() {

    context(_: KaSession)
    override fun isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean =
        element.isOptInAllowed(annotationClassId)

    override fun createQuickFix(fixType: ReplaceFixType, enumClassQualifiedName: String): LocalQuickFix {
        return K2ReplaceFix(fixType, enumClassQualifiedName)
    }

    private class K2ReplaceFix(fixType: ReplaceFixType, enumClassQualifiedName: String) :
        ReplaceFix(fixType, enumClassQualifiedName) {
        override fun shortenReferences(element: KtElement) {
            shortenReferences(element, callableShortenStrategy = { ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED })
        }
    }
}