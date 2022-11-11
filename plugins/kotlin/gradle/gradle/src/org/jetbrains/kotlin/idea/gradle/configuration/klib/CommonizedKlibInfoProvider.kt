// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration.klib

import org.jetbrains.kotlin.idea.gradle.configuration.klib.KlibInfo.NativeTargets.CommonizerIdentity
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KlibInfo.NativeTargets.NativeTargetsList
import org.jetbrains.kotlin.konan.library.KONAN_DISTRIBUTION_SOURCES_DIR
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.konan.properties.hasProperty
import org.jetbrains.kotlin.library.KLIB_PROPERTY_NATIVE_TARGETS
import org.jetbrains.kotlin.library.KLIB_PROPERTY_SHORT_NAME
import org.jetbrains.kotlin.library.KLIB_PROPERTY_UNIQUE_NAME
import java.io.File
import java.util.*

// TODO: Can be replaced with org.jetbrains.kotlin.library.KLIB_PROPERTY_COMMONIZER_TARGET after compiler update to 1.5.20
private const val KLIB_PROPERTY_COMMONIZER_TARGET = "commonizer_target"

internal class DefaultKlibInfoProvider(
    private val kotlinNativeHome: File,
    private val manifestProvider: KlibManifestProvider = KlibManifestProvider.default()
) : KlibInfoProvider {

    private val nativeDistributionLibrarySourceFiles by lazy {
        kotlinNativeHome.resolve(KONAN_DISTRIBUTION_SOURCES_DIR)
            .takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.maxDepth(1)
            ?.filter { it.isFile && it.name.endsWith(".zip") }
            ?.toList()
            ?: emptyList()
    }

    override fun getKlibInfo(libraryFile: File): KlibInfo? {
        val manifest = manifestProvider.getManifest(libraryFile.toPath()) ?: return null

        if (manifest.hasProperty(KLIB_PROPERTY_COMMONIZER_TARGET)) {
            return getCommonizedKlibInfo(libraryFile, manifest)
        }

        return getKlibInfo(libraryFile, manifest)
    }

    private fun getCommonizedKlibInfo(
        libraryFile: File,
        manifest: Properties,
    ): KlibInfo? {
        return KlibInfo(
            path = libraryFile,
            sourcePaths = emptyList(),
            libraryName = getLibraryNameFromManifest(manifest) ?: return null,
            isStdlib = false,
            isCommonized = true,
            isFromNativeDistribution = libraryFile.startsWith(kotlinNativeHome),
            targets = CommonizerIdentity(manifest.getProperty(KLIB_PROPERTY_COMMONIZER_TARGET) ?: return null)
        )
    }

    private fun getKlibInfo(
        libraryFile: File,
        manifest: Properties
    ): KlibInfo? {
        val libraryName = getLibraryNameFromManifest(manifest) ?: return null
        val isFromNativeDistribution = libraryFile.startsWith(kotlinNativeHome)
        val isStdlib = isFromNativeDistribution && libraryName == KONAN_STDLIB_NAME
        return KlibInfo(
            path = libraryFile,
            sourcePaths = findLibrarySources(libraryFile, manifest),
            libraryName = libraryName,
            isStdlib = isStdlib,
            isCommonized = false,
            isFromNativeDistribution = isFromNativeDistribution,
            targets = manifest.getProperty(KLIB_PROPERTY_NATIVE_TARGETS)?.let(::NativeTargetsList)
        )
    }

    private fun getLibraryNameFromManifest(manifest: Properties): String? {
        return manifest.getProperty(KLIB_PROPERTY_SHORT_NAME) ?: manifest.getProperty(KLIB_PROPERTY_UNIQUE_NAME)
    }

    private fun findLibrarySources(libraryFile: File, manifest: Properties): Set<File> {
        if (!libraryFile.startsWith(kotlinNativeHome)) return emptySet()
        val libraryName = getLibraryNameFromManifest(manifest) ?: return emptySet()

        val nameFilter: (String) -> Boolean = if (libraryName == KONAN_STDLIB_NAME) {
            // stdlib is a special case
            { it.startsWith("kotlin-stdlib") || it.startsWith("kotlin-test") }
        } else {
            { it.startsWith(libraryName) }
        }

        return nativeDistributionLibrarySourceFiles.filterTo(mutableSetOf()) { nameFilter(it.name) }
    }
}
