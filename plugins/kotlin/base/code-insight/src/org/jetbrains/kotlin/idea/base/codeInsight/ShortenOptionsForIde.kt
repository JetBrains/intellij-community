// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions

/**
 * An IDE-specific version of [ShortenOptions] with a possibility to extend and add aditional parameters.
 */
@ApiStatus.Internal
data class ShortenOptionsForIde(
    val removeThis: Boolean = false,
    val removeThisLabels: Boolean = false,
    val removeContextSensitiveResolutionQualifiers: Boolean = Registry.`is`("kotlin.shorten.context.sensitive.resolution.qualifiers.by.default"),
    val removeExplicitCompanionReferences: Boolean = true,
) {
    fun toShortenOptions(): ShortenOptions = ShortenOptions(removeThis, removeThisLabels, removeContextSensitiveResolutionQualifiers)

    companion object {
        val DEFAULT: ShortenOptionsForIde = ShortenOptionsForIde()

        val ALL_ENABLED: ShortenOptionsForIde = ShortenOptionsForIde(
            removeThis = true,
            removeThisLabels = true,
            removeContextSensitiveResolutionQualifiers = true,
            removeExplicitCompanionReferences = true,
        )
    }
}