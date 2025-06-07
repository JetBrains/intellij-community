// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JsCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.impl.*
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.jetbrains.kotlin.serialization.deserialization.METADATA_FILE_EXTENSION
import org.jetbrains.kotlin.utils.PathUtil
import java.util.regex.Pattern

@Service(Service.Level.PROJECT)
class IdePlatformKindProjectStructure(private val project: Project) {
    fun getCompilerArguments(platformKind: IdePlatformKind): CommonCompilerArguments? {
        return when (platformKind) {
            is CommonIdePlatformKind -> null
            is JvmIdePlatformKind -> Kotlin2JvmCompilerArgumentsHolder.getInstance(project).settings
            is JsIdePlatformKind -> Kotlin2JsCompilerArgumentsHolder.getInstance(project).settings
            is WasmIdePlatformKind -> Kotlin2JsCompilerArgumentsHolder.getInstance(project).settings
            is NativeIdePlatformKind -> null
            else -> error("Unsupported platform kind: $platformKind")
        }
    }

    fun getLibraryVersionProvider(platformKind: IdePlatformKind): (Library) -> IdeKotlinVersion? {
        return when (platformKind) {
            is CommonIdePlatformKind -> { library ->
                getLibraryKlibVersion(library, KOTLIN_STDLIB_COMMON_KLIB_PATTERN) ?:
                getLibraryJarVersion(library, PathUtil.KOTLIN_STDLIB_COMMON_JAR_PATTERN)
            }
            is JvmIdePlatformKind -> { library ->
                getLibraryJarVersion(library, PathUtil.KOTLIN_RUNTIME_JAR_PATTERN)
            }
            is JsIdePlatformKind -> { library ->
                KotlinJavaScriptStdlibDetectorFacility.getStdlibVersion(project, library)
            }
            is WasmIdePlatformKind, is NativeIdePlatformKind -> { _ -> null }
            else -> error("Unsupported platform kind: $platformKind")
        }
    }

    private fun getLibrary(roots: Array<VirtualFile>, pattern: Pattern): VirtualFile? {
        return roots.firstOrNull { pattern.matcher(it.name).matches() }
    }

    private fun getLibraryKlibVersion(library: Library, klibPattern: Pattern): IdeKotlinVersion? {
        val libraryKlib = getLibrary(library.getFiles(OrderRootType.CLASSES), klibPattern) ?: return null
        return IdeKotlinVersion.fromKLibManifest(libraryKlib)
    }

    private fun getLibraryJarVersion(library: Library, jarPattern: Pattern): IdeKotlinVersion? {
        val libraryJar = getLibrary(library.getFiles(OrderRootType.CLASSES), jarPattern) ?: return null
        return IdeKotlinVersion.fromManifest(libraryJar)
    }

    companion object {
        private val KOTLIN_STDLIB_COMMON_KLIB_PATTERN: Pattern = Pattern.compile(".*kotlin-stdlib-.*common.*\\.klib")

        @JvmStatic
        fun getInstance(project: Project): IdePlatformKindProjectStructure = project.service()

        private val PLATFORM_EXTENSIONS: Map<String, IdePlatformKind> = mapOf(
            METADATA_FILE_EXTENSION to CommonIdePlatformKind,
            "js" to JsIdePlatformKind,
            "kjsm" to JsIdePlatformKind
        )

        fun getLibraryPlatformKind(file: VirtualFile): IdePlatformKind? {
            PLATFORM_EXTENSIONS[file.extension]?.let { return it }

            if (!file.isKLibRootCandidate()) return null

            return when {
                file.isKlibLibraryRootForPlatform(CommonPlatforms.defaultCommonPlatform) -> CommonIdePlatformKind
                file.isKlibLibraryRootForPlatform(JsPlatforms.defaultJsPlatform) -> JsIdePlatformKind
                file.isKlibLibraryRootForPlatform(WasmPlatforms.wasmWasi) -> WasmWasiIdePlatformKind
                file.isKlibLibraryRootForPlatform(WasmPlatforms.wasmJs) -> WasmJsIdePlatformKind
                file.isKlibLibraryRootForPlatform(WasmPlatforms.unspecifiedWasmPlatform) -> WasmJsIdePlatformKind
                file.isKlibLibraryRootForPlatform(NativePlatforms.unspecifiedNativePlatform) -> NativeIdePlatformKind
                else -> null
            }
        }

        fun getLibraryKind(platformKind: IdePlatformKind): PersistentLibraryKind<*> {
            return when (platformKind) {
                is CommonIdePlatformKind -> KotlinCommonLibraryKind
                is JvmIdePlatformKind -> KotlinJvmEffectiveLibraryKind
                is JsIdePlatformKind -> KotlinJavaScriptLibraryKind
                is WasmJsIdePlatformKind -> KotlinWasmJsLibraryKind
                is WasmWasiIdePlatformKind -> KotlinWasmWasiLibraryKind
                is NativeIdePlatformKind -> KotlinNativeLibraryKind
                else -> error("Unsupported platform kind: $platformKind")
            }
        }
    }
}

