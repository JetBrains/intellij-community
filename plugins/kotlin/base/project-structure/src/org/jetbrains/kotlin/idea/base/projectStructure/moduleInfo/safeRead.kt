// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.konan.properties.propertyList
import org.jetbrains.kotlin.library.BaseKotlinLibrary
import org.jetbrains.kotlin.library.KotlinLibrary
import java.io.IOException

/**
 * Provides forward compatibility to klib's 'commonizer_native_targets' property (which is expected in 1.5.20)
 */
@Suppress("SpellCheckingInspection")
@ApiStatus.Internal
object CommonizerNativeTargetsCompat {
    /**
     * Similar to [org.jetbrains.kotlin.library.KLIB_PROPERTY_NATIVE_TARGETS] but this will also preserve targets
     * that were unsupported on the host creating this artifact
     */
    private const val KLIB_PROPERTY_COMMONIZER_NATIVE_TARGETS = "commonizer_native_targets"

    /**
     * Accessor for 'commonizer_native_targets' manifest property.
     * Can be removed once bundled compiler reaches 1.5.20
     */
    val BaseKotlinLibrary.commonizerNativeTargetsCompat: List<String>?
        get() = if (manifestProperties.containsKey(KLIB_PROPERTY_COMMONIZER_NATIVE_TARGETS))
            manifestProperties.propertyList(KLIB_PROPERTY_COMMONIZER_NATIVE_TARGETS)
        else null
}

@ApiStatus.Internal
inline fun <T> KotlinLibrary?.safeRead(defaultValue: T, action: KotlinLibrary.() -> T): T =
    if (this == null) {
        defaultValue
    } else try {
        action()
    } catch (_: IOException) {
        defaultValue
    }