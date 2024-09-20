// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KLibUtils")

package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion.Companion.klibManifestProperties
import org.jetbrains.kotlin.library.*
import org.jetbrains.kotlin.library.impl.BuiltInsPlatform
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.platform.wasm.WasmPlatformUnspecifiedTarget
import org.jetbrains.kotlin.platform.wasm.WasmPlatformWithTarget
import org.jetbrains.kotlin.platform.wasm.WasmTarget
import java.util.*

@ApiStatus.Internal
internal fun VirtualFile.isKLibRootCandidate(): Boolean {
    return (nameSequence.endsWith(KLIB_FILE_EXTENSION_WITH_DOT) || !FileTypeRegistry.getInstance().isFileOfType(this, ArchiveFileType.INSTANCE)) && isDirectory
}

@ApiStatus.Internal
fun VirtualFile.isKlibLibraryRootForPlatform(targetPlatform: TargetPlatform): Boolean {
    // The virtual file for a library packed in a ZIP file will have path like "/some/path/to/the/file.klib!/",
    // and therefore will be recognized by VFS as a directory (isDirectory == true).
    // So, first, let's check the file type and file extension.
    if (!isKLibRootCandidate()) {
        return false
    }

    // run check for library root too
    // this is necessary to recognize old style KLIBs that do not have components, and report tem to user appropriately
    // (relevant only for Kotlin/Native KLIBs)
    val requestedBuiltInsPlatform = targetPlatform.toBuiltInsPlatform()
    if (
        requestedBuiltInsPlatform == BuiltInsPlatform.NATIVE &&
        checkKlibComponent(this, targetPlatform, requestedBuiltInsPlatform)
    ) {
        return true
    }


    try {
        return children?.any { checkKlibComponent(it, targetPlatform, requestedBuiltInsPlatform) } == true
    } catch (_: InvalidVirtualFileAccessException) {
        return false
    }
}

private fun checkKlibComponent(
    componentFile: VirtualFile,
    targetPlatform: TargetPlatform,
    requestedBuiltInsPlatform: BuiltInsPlatform
): Boolean {
    val manifestProperties = klibManifestProperties(componentFile) ?: return false

    if (!manifestProperties.containsKey(KLIB_PROPERTY_UNIQUE_NAME)) return false

    val builtInsPlatformProperty = manifestProperties.getProperty(KLIB_PROPERTY_BUILTINS_PLATFORM)
    // No builtins_platform property => either a new common klib (we don't write builtins_platform for common) or old Native klib
        ?: return when (requestedBuiltInsPlatform) {
            BuiltInsPlatform.NATIVE -> componentFile.isLegacyNativeKlibComponent // TODO(dsavvinov): drop additional legacy check after 1.4
            BuiltInsPlatform.COMMON -> !componentFile.isLegacyNativeKlibComponent
            else -> false
        }

    val builtInsPlatform = BuiltInsPlatform.parseFromString(builtInsPlatformProperty) ?: return false

    if (builtInsPlatform == requestedBuiltInsPlatform && requestedBuiltInsPlatform == BuiltInsPlatform.WASM) {
        return checkKlibWasmTarget(manifestProperties, targetPlatform)
    }

    return builtInsPlatform == requestedBuiltInsPlatform
}

/**
 * Returns if [manifestProperties] is compatible with [targetPlatform]
 * In case [WasmPlatformUnspecifiedTarget] we consider compatible for compatibility with previous Kotlin versions
 * In case [manifestProperties] does not contain [KLIB_PROPERTY_WASM_TARGETS] we consider it as [WasmTarget.JS] library for compatibility
 */
private fun checkKlibWasmTarget(manifestProperties: Properties, targetPlatform: TargetPlatform): Boolean {
    val platformWithTarget = targetPlatform.componentPlatforms.singleOrNull()?.takeIf { it is WasmPlatform } ?: return false

    if (platformWithTarget is WasmPlatformUnspecifiedTarget) return true

    val wasmTarget = (platformWithTarget as WasmPlatformWithTarget).target

    val parsedWasmTarget = manifestProperties.getProperty(KLIB_PROPERTY_WASM_TARGETS) ?: return wasmTarget == WasmTarget.JS

    return wasmTarget == WasmTarget.fromName(parsedWasmTarget)
}

private fun TargetPlatform.toBuiltInsPlatform() = when {
    isCommon() -> BuiltInsPlatform.COMMON
    isNative() -> BuiltInsPlatform.NATIVE
    isJvm() -> BuiltInsPlatform.JVM
    isJs() -> BuiltInsPlatform.JS
    isWasm() -> BuiltInsPlatform.WASM
    else -> throw IllegalArgumentException("Unknown platform $this")
}

private val VirtualFile.isLegacyNativeKlibComponent: Boolean
    get() {
        val irFolder = findChild(KLIB_IR_FOLDER_NAME)
        return irFolder != null && irFolder.children.isNotEmpty()
    }