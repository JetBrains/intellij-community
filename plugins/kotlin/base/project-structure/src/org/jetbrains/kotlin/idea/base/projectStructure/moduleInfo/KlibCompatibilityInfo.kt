// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KlibCompatibilityInfoUtils")

package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.asKotlinLogger
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.library.*
import org.jetbrains.kotlin.library.metadata.KlibMetadataVersion
import org.jetbrains.kotlin.library.metadata.isCInteropLibrary
import org.jetbrains.kotlin.library.metadata.isCommonizedCInteropLibrary
import org.jetbrains.kotlin.library.metadata.metadataVersion
import org.jetbrains.kotlin.library.resolveSingleFileKlib
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.platform.TargetPlatform

/**
 * Whether a certain KLIB is compatible for the purposes of IDE: indexation, resolve, etc.
 */
sealed class KlibCompatibilityInfo(val isCompatible: Boolean) {
    object Compatible : KlibCompatibilityInfo(true)
    class IncompatibleMetadata(val isOlder: Boolean) : KlibCompatibilityInfo(false)
}

@K1ModeProjectStructureApi
abstract class AbstractKlibLibraryInfo internal constructor(project: Project, library: LibraryEx, val libraryRoot: String) :
    LibraryInfo(project, library) {
    val resolvedKotlinLibrary: KotlinLibrary = resolveSingleFileKlib(
        libraryFile = File(libraryRoot),
        logger = LOG,
        strategy = ToolingSingleFileKlibResolveStrategy
    )

    val compatibilityInfo: KlibCompatibilityInfo by lazy { resolvedKotlinLibrary.compatibilityInfo }

    final override fun getLibraryRoots(): List<String> = listOf(libraryRoot)

    abstract override val platform: TargetPlatform // must override

    val uniqueName: String? by lazy { resolvedKotlinLibrary.safeRead(null) { uniqueName } }

    val isInterop: Boolean by lazy { resolvedKotlinLibrary.isCInteropLibrary() || resolvedKotlinLibrary.isCommonizedCInteropLibrary() }

    companion object {
        private val LOG: org.jetbrains.kotlin.util.Logger = Logger.getInstance(AbstractKlibLibraryInfo::class.java).asKotlinLogger()
    }
}

val KotlinLibrary.compatibilityInfo: KlibCompatibilityInfo
    get() {
        val metadataVersion = safeRead(null) { metadataVersion }
        return when {
            metadataVersion == null -> {
                // Too old KLIB format, even doesn't have metadata version
                KlibCompatibilityInfo.IncompatibleMetadata(true)
            }

            !metadataVersion.isCompatibleWithCurrentCompilerVersion() -> {
                val isOlder = metadataVersion.isAtLeast(KlibMetadataVersion.INSTANCE)
                KlibCompatibilityInfo.IncompatibleMetadata(!isOlder)
            }

            else -> KlibCompatibilityInfo.Compatible
        }
    }
