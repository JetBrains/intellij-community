// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.EnumValuesSoftDeprecateInspectionBase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker.Companion.isOptInAllowed

class EnumValuesSoftDeprecateInspection : EnumValuesSoftDeprecateInspectionBase() {

    context(_: KaSession)
    override fun isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean {
        return element.isOptInAllowed(annotationClassId.asSingleFqName(), element.languageVersionSettings, element.analyze())
    }
}
