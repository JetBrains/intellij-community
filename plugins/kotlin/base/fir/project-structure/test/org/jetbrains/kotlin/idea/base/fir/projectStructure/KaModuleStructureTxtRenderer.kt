// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind

object KaModuleStructureTxtRenderer {
    fun render(modules: List<KaModule>) = prettyPrint {
        modules.forEach { module ->
            render(module)
            appendLine()
        }
    }

    private fun PrettyPrinter.render(module: KaModule) {
        appendLine("${module.getKaModuleClass()}: ")
        withIndent {
            renderCommonModuleData(module)
            renderModuleSpecificData(module)
            renderDependencies(module)
        }
    }


    private fun PrettyPrinter.renderCommonModuleData(module: KaModule) {
        if (module is KaLibraryModule && module.isSdk) {
            // do not use real sdk name here because it may be different on different machines
            appendLine("description: <SDK>")
        } else {
            appendLine("description: ${module.moduleDescription}")
        }
        appendLine("targetPlatform: ${module.targetPlatform.getTargetPlatformDescriptionForRendering()}")
    }

    private fun PrettyPrinter.renderModuleSpecificData(module: KaModule) {
        when (module) {
            is KaBuiltinsModule -> {}
            is KaDanglingFileModule -> {}
            is KaLibraryModule -> {
                appendLine("libraryName: ${module.getModuleLibraryNameForRendering()}")
                appendLine("isSdk: ${module.isSdk}")
            }

            is KaLibrarySourceModule -> {}
            is KaNotUnderContentRootModule -> {}
            is KaScriptDependencyModule -> {}
            is KaScriptModule -> {}
            is KaSourceModule -> {
                appendLine("name: ${module.name}")
                appendLine("sourceModuleKind: ${module.sourceModuleKind}")
                appendLine("stableModuleName: ${module.stableModuleName}")
            }

            else -> error("Unknown module type: ${module::class.java}")
        }
    }


    private fun PrettyPrinter.renderDependencies(module: KaModule) {
        fun PrettyPrinter.renderDependenciesByType(type: String, dependencies: Collection<KaModule>) {
            appendLine("$type: ")
            withIndent {
                if (dependencies.isEmpty()) {
                    appendLine("<empty>")
                } else {
                    dependencies.map { "${it.getKaModuleClass()}(${it.getOneLineModuleDescriptionForRendering()})" }
                        .sorted() // todo the order is unstable for moduleinfo-based impl, should be fixed as a part of KTIJ-31422
                        .forEach { appendLine(it) }
                }
            }
        }
        renderDependenciesByType("regularDependencies", module.directRegularDependencies)
        renderDependenciesByType("friendDependencies", module.directFriendDependencies)
        renderDependenciesByType("dependsOnDependencies", module.directDependsOnDependencies)
    }
}