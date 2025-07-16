// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isOptInSatisfied
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement

context(KaSession)
fun KtElement.canUseRangeUntil(): Boolean {
    if (!compilerVersionIsSufficientToUseRangeUntil(this)) return false
    if (!languageVersionSettings.supportsFeature(LanguageFeature.RangeUntilOperator)) return false
    return isOptInSatisfied(
        symbol = findClassLike(OPEN_END_RANGE_CLASS_ID) ?: return false,
        annotationClassId = EXPERIMENTAL_STDLIB_API_CLASS_ID
    )
}

private fun compilerVersionIsSufficientToUseRangeUntil(element: KtElement): Boolean {
    val module = element.module ?: return false
    val compilerVersion = ExternalCompilerVersionProvider.get(module)
        ?: IdeKotlinVersion.opt(KotlinJpsPluginSettings.jpsVersion(element.project))
        ?: return false
    // `rangeUntil` is added to languageVersion 1.8 only since 1.7.20-Beta compiler
    return compilerVersion >= COMPILER_VERSION_WITH_RANGEUNTIL_SUPPORT
}

private val COMPILER_VERSION_WITH_RANGEUNTIL_SUPPORT = IdeKotlinVersion.get("1.7.20-Beta")

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
