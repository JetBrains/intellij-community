// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.CommonizerNativeTargetsCompat.commonizerNativeTargetsCompat
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.konan.properties.propertyList
import org.jetbrains.kotlin.library.BaseKotlinLibrary
import org.jetbrains.kotlin.library.KLIB_PROPERTY_NATIVE_TARGETS
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.metadata.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.KlibModuleOrigin
import org.jetbrains.kotlin.library.nativeTargets
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms.nativePlatformByTargetNames
import org.jetbrains.kotlin.resolve.ImplicitIntegerCoercion
import java.io.IOException

@K1ModeProjectStructureApi
class NativeKlibLibraryInfo internal constructor(project: Project, library: LibraryEx, libraryRoot: String) :
    AbstractKlibLibraryInfo(project, library, libraryRoot) {
    // If you're changing this, please take a look at ideaModelDependencies as well
    val isStdlib: Boolean get() = libraryRoot.endsWith(KONAN_STDLIB_NAME)

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() {
            val capabilities = super.capabilities.toMutableMap()
            capabilities += KlibModuleOrigin.CAPABILITY to DeserializedKlibModuleOrigin(resolvedKotlinLibrary)
            capabilities += ImplicitIntegerCoercion.MODULE_CAPABILITY to resolvedKotlinLibrary.safeRead(false) { isInterop }
            return capabilities
        }

    override val platform: TargetPlatform by lazy {
        val targetNames = resolvedKotlinLibrary.safeRead(null) { commonizerNativeTargetsCompat }
            ?: resolvedKotlinLibrary.safeRead(emptyList()) { nativeTargets }

        nativePlatformByTargetNames(targetNames)
    }
}

/**
 * Provides forward compatibility to klib's 'commonizer_native_targets' property (which is expected in 1.5.20)
 */
@Suppress("SpellCheckingInspection")
private object CommonizerNativeTargetsCompat {
    /**
     * Similar to [KLIB_PROPERTY_NATIVE_TARGETS] but this will also preserve targets
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
fun <T> KotlinLibrary.safeRead(defaultValue: T, action: KotlinLibrary.() -> T) = try {
    action()
} catch (_: IOException) {
    defaultValue
}
