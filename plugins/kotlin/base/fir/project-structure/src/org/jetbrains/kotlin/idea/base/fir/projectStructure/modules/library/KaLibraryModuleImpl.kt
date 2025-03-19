// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource.KaLibrarySourceModuleImpl
import org.jetbrains.kotlin.idea.base.platforms.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.CommonizerNativeTargetsCompat.commonizerNativeTargetsCompat
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.safeRead
import org.jetbrains.kotlin.idea.base.util.asKotlinLogger
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.library.nativeTargets
import org.jetbrains.kotlin.library.resolveSingleFileKlib
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.*
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.jetbrains.kotlin.konan.file.File as KonanFile

internal class KaLibraryModuleImpl(
    override val entityId: LibraryId,
    override val project: Project,
) : KaLibraryEntityBasedLibraryModuleBase() {

    override val librarySources: KaLibrarySourceModule? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaLibrarySourceModuleImpl(this)
    }

    override val targetPlatform: TargetPlatform by lazy(LazyThreadSafetyMode.PUBLICATION) {
        when (idePlatformKind) {
            is JvmIdePlatformKind -> JvmPlatforms.defaultJvmPlatform
            is CommonIdePlatformKind -> CommonPlatforms.defaultCommonPlatform
            is JsIdePlatformKind -> JsPlatforms.defaultJsPlatform
            is WasmIdePlatformKind -> when (library.kind) {
                is KotlinWasmJsLibraryKind -> WasmPlatforms.wasmJs
                is KotlinWasmWasiLibraryKind -> WasmPlatforms.wasmWasi
                else -> error("Unexpected Wasm library kind `${library.kind}`")
            }

            is NativeIdePlatformKind -> {
                val platformNames = resolvedKotlinLibraries.flatMap { resolvedKotlinLibrary ->
                    resolvedKotlinLibrary.safeRead(null) { commonizerNativeTargetsCompat }
                        ?: resolvedKotlinLibrary.safeRead(emptyList()) { nativeTargets }
                }
                NativePlatforms.nativePlatformByTargetNames(platformNames)
            }

            else -> error("Unexpected platform kind: $idePlatformKind")
        }
    }

    private val idePlatformKind: IdePlatformKind by lazy(LazyThreadSafetyMode.PUBLICATION) {
        detectLibraryKind(library, project).platform.idePlatformKind
    }

    val resolvedKotlinLibraries: List<KotlinLibrary> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val defaultPlatform = idePlatformKind.defaultPlatform
        val roots = binaryVirtualFiles.filter { it.isKlibLibraryRootForPlatform(defaultPlatform) }
        roots.mapNotNull { root ->
            val path = PathUtil.getLocalPath(root) ?: return@mapNotNull null
            resolveSingleFileKlib(
                libraryFile = KonanFile(path),
                logger = KOTLIN_LOGGER,
                strategy = ToolingSingleFileKlibResolveStrategy
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaLibraryModuleImpl
                && entityId == other.entityId
    }

    override fun hashCode(): Int {
        return entityId.hashCode()
    }

    override fun toString(): String {
        return super.toString() + "idePlatformKind=$idePlatformKind"
    }

    companion object {
        private val KOTLIN_LOGGER = Logger.getInstance(KaLibraryModuleBase::class.java).asKotlinLogger()
    }
}