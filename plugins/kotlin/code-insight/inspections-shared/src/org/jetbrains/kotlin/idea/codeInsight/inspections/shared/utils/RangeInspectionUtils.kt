// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isOptInSatisfied
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement

context(KaSession)
fun KtElement.canUseRangeUntil(): Boolean {
    if (!languageVersionSettings.supportsFeature(LanguageFeature.RangeUntilOperator)) return false
    return isOptInSatisfied(
        symbol = findClassLike(OPEN_END_RANGE_CLASS_ID) ?: return false,
        annotationClassId = EXPERIMENTAL_STDLIB_API_CLASS_ID
    )
}

context(KaSession)
val KaType.isSignedIntegralType: Boolean
    get() = isIntType || isLongType || isShortType || isByteType

context(KaSession)
val KaType.isUnsignedIntegralType: Boolean
    get() = isUIntType || isULongType || isUShortType || isUByteType

context(KaSession)
val KaType.isIntegralType: Boolean
    get() = isSignedIntegralType || isUnsignedIntegralType

context(KaSession)
val KaType.isFloatingPointType: Boolean
    get() = isFloatType || isDoubleType

private val OPEN_END_RANGE_CLASS_ID = ClassId.fromString("kotlin/ranges/OpenEndRange")
private val EXPERIMENTAL_STDLIB_API_CLASS_ID = ClassId.fromString("kotlin/ExperimentalStdlibApi")
