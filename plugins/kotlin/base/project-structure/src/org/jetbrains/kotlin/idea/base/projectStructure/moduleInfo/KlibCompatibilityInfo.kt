// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KlibCompatibilityInfoUtils")

package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.idea.base.util.asKotlinLogger
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.library.*
import org.jetbrains.kotlin.library.metadata.KlibMetadataVersion
import org.jetbrains.kotlin.library.metadata.metadataVersion
import org.jetbrains.kotlin.platform.TargetPlatform

/**
 * Whether a certain KLIB is compatible for the purposes of IDE: indexation, resolve, etc.
 */
sealed class KlibCompatibilityInfo(val isCompatible: Boolean) {
    object Compatible : KlibCompatibilityInfo(true)
    object Pre14Layout : KlibCompatibilityInfo(false)
    class IncompatibleMetadata(val isOlder: Boolean) : KlibCompatibilityInfo(false)
}

abstract class AbstractKlibLibraryInfo internal constructor(project: Project, library: LibraryEx, val libraryRoot: String) :
    LibraryInfo(project, library) {
    val resolvedKotlinLibrary: KotlinLibrary = resolveSingleFileKlib(
        libraryFile = File(libraryRoot),
        logger = LOG,
        strategy = ToolingSingleFileKlibResolveStrategy
    )

    val compatibilityInfo: KlibCompatibilityInfo by lazy { resolvedKotlinLibrary.compatibilityInfo }

    final override fun getLibraryRoots() = listOf(libraryRoot)

    abstract override val platform: TargetPlatform // must override

    val uniqueName: String? by lazy { resolvedKotlinLibrary.safeRead(null) { uniqueName } }

    val isInterop: Boolean by lazy { resolvedKotlinLibrary.safeRead(false) { isInterop } }

    companion object {
        private val LOG = Logger.getInstance(AbstractKlibLibraryInfo::class.java).asKotlinLogger()
    }
}

val KotlinLibrary.compatibilityInfo: KlibCompatibilityInfo
    get() {
        val hasPre14Manifest = safeRead(false) { has_pre_1_4_manifest }
        if (hasPre14Manifest)
            return KlibCompatibilityInfo.Pre14Layout

        val metadataVersion = safeRead(null) { metadataVersion }
        return when {
            metadataVersion == null -> {
                // Too old KLIB format, even doesn't have metadata version
                KlibCompatibilityInfo.IncompatibleMetadata(true)
            }

            !metadataVersion.isCompatible() -> {
                val isOlder = metadataVersion.isAtLeast(KlibMetadataVersion.INSTANCE)
                KlibCompatibilityInfo.IncompatibleMetadata(!isOlder)
            }

            else -> KlibCompatibilityInfo.Compatible
        }
    }
