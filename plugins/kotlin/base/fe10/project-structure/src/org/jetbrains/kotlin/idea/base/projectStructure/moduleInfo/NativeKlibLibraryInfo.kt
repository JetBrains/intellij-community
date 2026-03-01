// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.CommonizerNativeTargetsCompat.commonizerNativeTargetsCompat
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.library.metadata.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.KlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.SyntheticModulesOrigin
import org.jetbrains.kotlin.library.nativeTargets
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms.nativePlatformByTargetNames
import org.jetbrains.kotlin.resolve.ImplicitIntegerCoercion

@K1ModeProjectStructureApi
class NativeKlibLibraryInfo internal constructor(project: Project, library: LibraryEx, libraryRoot: String) :
    AbstractKlibLibraryInfo(project, library, libraryRoot) {
    // If you're changing this, please take a look at ideaModelDependencies as well
    val isStdlib: Boolean get() = libraryRoot.endsWith(KONAN_STDLIB_NAME)

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() {
            val capabilities = super.capabilities.toMutableMap()
            capabilities += KlibModuleOrigin.CAPABILITY to (resolvedKotlinLibrary?.let(::DeserializedKlibModuleOrigin) ?: SyntheticModulesOrigin)
            capabilities += ImplicitIntegerCoercion.MODULE_CAPABILITY to resolvedKotlinLibrary.safeRead(false) { isInterop }
            return capabilities
        }

    override val platform: TargetPlatform by lazy {
        val targetNames = resolvedKotlinLibrary.safeRead(null) { commonizerNativeTargetsCompat }
            ?: resolvedKotlinLibrary.safeRead(emptyList()) { nativeTargets }

        nativePlatformByTargetNames(targetNames)
    }
}

