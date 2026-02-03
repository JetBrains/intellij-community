// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryFallbackDependenciesModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind
import org.jetbrains.kotlin.platform.TargetPlatform

internal fun getOneLineModuleDescriptionForRendering(module: KaModule): String? {
    val string = when (module) {
        is KaBuiltinsModule -> null
        is KaDanglingFileModule -> module.file.name
        is KaLibraryModule -> module.getModuleLibraryNameForRendering()
        is KaLibrarySourceModule -> "library sources of " + getOneLineModuleDescriptionForRendering(module.binaryLibrary)
        is KaLibraryFallbackDependenciesModule -> "fallback dependencies of " + getOneLineModuleDescriptionForRendering(module.dependentLibrary)
        is KaNotUnderContentRootModule -> (module.file?.name ?: "NO_FILE")
        is KaScriptModule -> module.file.name
        is KaSourceModule -> buildString {
            append(module.name)
            append(", ")
            append(
                when (module.sourceModuleKind) {
                    KaSourceModuleKind.PRODUCTION -> "production"
                    KaSourceModuleKind.TEST -> "test"
                }
            )
        }

        else -> error("Unknown module type: ${module::class.java}")
    }
    return string
}

internal fun TargetPlatform.getTargetPlatformDescriptionForRendering(): String =
    componentPlatforms.map { it.toString() }.sorted().joinToString("/") { it }


internal fun KaLibraryModule.getModuleLibraryNameForRendering(): String {
    return if (isSdk) {
        // do not use real sdk description here because sdk name may be different on different machines
        "SDK"
    } else {
        libraryName
    }
}

internal fun String.inParenthesis(): String = "($this)"


internal fun KaModule.getKaModuleClass(): String {
    val baseClass = baseClasses.first { it.isInstance(this) }
    return baseClass.simpleName
}

private val baseClasses = listOf(
    KaBuiltinsModule::class.java,
    KaDanglingFileModule::class.java,
    KaLibraryModule::class.java,
    KaLibrarySourceModule::class.java,
    KaLibraryFallbackDependenciesModule::class.java,
    KaNotUnderContentRootModule::class.java,
    KaScriptModule::class.java,
    KaSourceModule::class.java,
)
