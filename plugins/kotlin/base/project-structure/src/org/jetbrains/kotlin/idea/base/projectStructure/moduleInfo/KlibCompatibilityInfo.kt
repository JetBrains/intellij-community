// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KlibCompatibilityInfoUtils")

package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.idea.base.util.asKotlinLogger
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.file.file
import org.jetbrains.kotlin.konan.file.withZipFileSystem
import org.jetbrains.kotlin.library.*
import org.jetbrains.kotlin.library.impl.createKotlinLibrary
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.library.metadata.KlibMetadataVersion
import org.jetbrains.kotlin.library.metadata.isCInteropLibrary
import org.jetbrains.kotlin.library.metadata.isCommonizedCInteropLibrary
import org.jetbrains.kotlin.library.metadata.metadataVersion
import org.jetbrains.kotlin.library.resolveSingleFileKlib
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.platform.TargetPlatform
import java.io.IOException

/**
 * Whether a certain KLIB is compatible for the purposes of IDE: indexation, resolve, etc.
 */
sealed class KlibCompatibilityInfo(val isCompatible: Boolean) {
    object Compatible : KlibCompatibilityInfo(true)
    class IncompatibleMetadata(val isOlder: Boolean) : KlibCompatibilityInfo(false)
}

abstract class AbstractKlibLibraryInfo internal constructor(project: Project, library: LibraryEx, val libraryRoot: String) :
    LibraryInfo(project, library) {
    val resolvedKotlinLibrary: KotlinLibrary = resolveSingleFileKlib(
        libraryFile = File(libraryRoot),
        logger = LOG,
        strategy = IdeToolingSingleFileKlibResolveStrategy
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

// TODO: KTIJ-30828 Workaround for kotlin-stdlib-common.jar that is effectively klib
// Use ToolingSingleFileKlibResolveStrategy
private object IdeToolingSingleFileKlibResolveStrategy : SingleFileKlibResolveStrategy {
    override fun resolve(libraryFile: File, logger: org.jetbrains.kotlin.util.Logger): KotlinLibrary =
        tryResolve(libraryFile, logger)
            ?: fakeLibrary(libraryFile)

    fun tryResolve(libraryFile: File, logger: org.jetbrains.kotlin.util.Logger): KotlinLibrary? =
        withSafeAccess(libraryFile) { localRoot ->
            if (localRoot.looksLikeKlibComponent) {
                // old style library
                null
            } else {
                val components = localRoot.listFiles.filter { it.looksLikeKlibComponent }
                when (components.size) {
                    0 -> null
                    1 -> {
                        // single component library
                        createKotlinLibrary(libraryFile, components.single().name)
                    }
                    else -> { // TODO: choose the best fit among all available candidates
                        // mimic as old style library and warn
                        logger.strongWarning(
                            "KLIB resolver: Library '$libraryFile' can not be read." +
                                    " Multiple components found: ${components.map { it.path.substringAfter(localRoot.path) }}"
                        )

                        null
                    }
                }
            }
        }

    private const val NONEXISTENT_COMPONENT_NAME = "__nonexistent_component_name__"

    private fun fakeLibrary(libraryFile: File): KotlinLibrary = createKotlinLibrary(libraryFile, NONEXISTENT_COMPONENT_NAME)

    private fun <T : Any> withSafeAccess(libraryFile: File, action: (localRoot: File) -> T?): T? {
        val extension = libraryFile.extension

        val wrappedAction: () -> T? = when {
            libraryFile.isDirectory -> {
                { action(libraryFile) }
            }

            libraryFile.isFile && (
                    extension == KLIB_FILE_EXTENSION ||
                            extension == "jar" // TODO: reason for KTIJ-30828 workaround
                    ) -> {
                { libraryFile.withZipFileSystem { fs -> action(fs.file("/")) } }
            }

            else -> return null
        }

        return try {
            wrappedAction()
        } catch (_: IOException) {
            null
        }
    }

    private val File.looksLikeKlibComponent: Boolean
        get() = child(KLIB_MANIFEST_FILE_NAME).isFile
}
