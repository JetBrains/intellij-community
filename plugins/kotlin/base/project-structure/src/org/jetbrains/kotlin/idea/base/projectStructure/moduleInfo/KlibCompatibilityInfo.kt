// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.metadataVersion
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion

/**
 * Whether a certain KLIB is compatible for the purposes of IDE: indexation, resolve, etc.
 */
sealed class KlibCompatibilityInfo(val isCompatible: Boolean) {
    object Compatible : KlibCompatibilityInfo(true)
    class IncompatibleMetadata(val isOlder: Boolean) : KlibCompatibilityInfo(false)
}

val KotlinLibrary?.compatibilityInfo: KlibCompatibilityInfo
    get() {
        val metadataVersion = safeRead(null) { metadataVersion }
        return when {
            metadataVersion == null -> {
                // Too old KLIB format, even doesn't have metadata version
                KlibCompatibilityInfo.IncompatibleMetadata(true)
            }

            !metadataVersion.isCompatibleWithCurrentCompilerVersion() -> {
                val isOlder = metadataVersion.isAtMost(MetadataVersion.INSTANCE_NEXT)
                KlibCompatibilityInfo.IncompatibleMetadata(isOlder)
            }

            else -> KlibCompatibilityInfo.Compatible
        }
    }
