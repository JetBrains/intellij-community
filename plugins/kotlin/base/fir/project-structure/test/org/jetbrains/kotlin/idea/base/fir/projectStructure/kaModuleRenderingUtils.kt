// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind
import org.jetbrains.kotlin.platform.TargetPlatform

internal fun KaModule.getOneLineModuleDescriptionForRendering(): String? {
    val string = when (this) {
        is KaBuiltinsModule -> null
        is KaDanglingFileModule -> this.file.name
        is KaLibraryModule -> buildString {
            append(this@getOneLineModuleDescriptionForRendering.getModuleLibraryNameForRendering())
            if (this@getOneLineModuleDescriptionForRendering is KaScriptDependencyModule) {
                append(", scriptDependency")
            }
        }

        is KaLibrarySourceModule -> "library sources of " + binaryLibrary.getOneLineModuleDescriptionForRendering()
        is KaLibraryFallbackDependenciesModule -> "fallback dependencies of " + dependentLibrary.getOneLineModuleDescriptionForRendering()
        is KaNotUnderContentRootModule -> (this.file?.name ?: "NO_FILE")
        is KaScriptDependencyModule -> (this.file?.name ?: "NO_FILE")
        is KaScriptModule -> this.file.name
        is KaSourceModule -> buildString {
            append(this@getOneLineModuleDescriptionForRendering.name)
            append(", ")
            append(
                when (this@getOneLineModuleDescriptionForRendering.sourceModuleKind) {
                    KaSourceModuleKind.PRODUCTION -> "production"
                    KaSourceModuleKind.TEST -> "test"
                    null -> "unknown"
                }
            )
        }

        else -> error("Unknown module type: ${this::class.java}")
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
