// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind

object KaModuleStructureMermaidRenderer {
    fun render(modules: List<KaModule>): String {
        val regularDependencies = modules.flatMap { module ->
            module.directRegularDependencies.map { module to it }
        }
        val friendDependencies = modules.flatMap { module ->
            module.directFriendDependencies.map { module to it }
        }
        val dependsOnDependencies = modules.flatMap { module ->
            module.directDependsOnDependencies.map { module to it }
        }

        val moduleToId = modules
            .sortedWith(kaModulesComparatorForStableRendering)
            .withIndex().associate { (index, module) -> module to index }

        fun KaModule.nodeId(): String {
            val moduleForId = when (this) {
                is KaLibraryFallbackDependenciesModule -> dependentLibrary
                else -> this
            }
            return getKaModuleClass() + "_" + moduleToId.getValue(moduleForId)
        }

        return prettyPrint {
            appendLine("graph TD")

            withIndent {
                modules.forEach { module ->
                    appendLine("${module.nodeId()}${module.getModuleDescriptionForRendering()}")
                }

                modules.forEach { module ->
                    val color = when (module) {
                        is KaSourceModule -> when (module.sourceModuleKind) {
                            KaSourceModuleKind.PRODUCTION -> "#608BC1" // blue
                            KaSourceModuleKind.TEST -> "#31511E" // green
                            null -> null
                        }

                        is KaLibraryModule -> {
                            if (module.isSdk) "#CC2B52" // red
                            else "#CB6040" // orange
                        }
                        else -> null
                    }
                    if (color != null) {
                        appendLine("style ${module.nodeId()} fill:${color}")
                    }
                }

                val dependencies = buildList {
                    regularDependencies
                        /* mitigation of KT-74010 */
                        .filter { (_, to) -> to !is KaBuiltinsModule }
                        .map { (from, to) -> "${from.nodeId()} --> ${to.nodeId()}" }.sorted()
                        .mapTo(this) { it to DependencyKind.Regular }
                    friendDependencies.map { (from, to) -> "${from.nodeId()} --friend--> ${to.nodeId()}" }.sorted()
                        .mapTo(this) { it to DependencyKind.Friend }
                    dependsOnDependencies.map { (from, to) -> "${from.nodeId()} --dependsOn--> ${to.nodeId()}" }.sorted()
                        .mapTo(this) { it to DependencyKind.DependsOn }
                }

                dependencies.forEach { (dependency, _) ->
                    appendLine(dependency)
                }

                dependencies
                    .forEachIndexed { i, (_, kind) ->
                        val color = when (kind) {
                            DependencyKind.Regular -> "black"
                            DependencyKind.Friend -> "#E6C767" // yellow
                            DependencyKind.DependsOn -> "#8967B3" // purple
                        }
                        appendLine("linkStyle $i stroke:${color}")
                    }
            }
        }
    }

    enum class DependencyKind {
        Regular, Friend, DependsOn
    }

    private fun KaModule.getModuleDescriptionForRendering(): String {
        val content = "${getKaModuleClass()}${moduleTextForRendering().orEmpty()}"
        return when (this) {
            is KaSourceModule -> """["${content}"]""" // rectangle
            is KaLibraryModule -> """(["${content}"])""" // rounded rectangle
            is KaScriptModule -> """{{"${content}"}}""" // hexagon
            else -> """[/"${content}"/]""" // parallelogram
        }
    }

    private fun KaModule.moduleTextForRendering(): String? = when (this) {
        is KaBuiltinsModule -> null
        is KaDanglingFileModule -> file.name.inParenthesis()
        is KaLibrarySourceModule -> "library sources of ${binaryLibrary.moduleTextForRendering()}"
        is KaLibraryModule -> buildString {
            append(getModuleLibraryNameForRendering().inParenthesis())
            if (this@moduleTextForRendering is KaScriptDependencyModule) {
                append("<br />")
                append("scriptDependency")
            }
        }

        is KaNotUnderContentRootModule -> (file?.name ?: "NO_FILE").inParenthesis()
        is KaScriptModule -> file.name.inParenthesis()
        is KaSourceModule -> buildString {
            append(name.inParenthesis())
            append("<br />")
            append(
                when (sourceModuleKind) {
                    KaSourceModuleKind.PRODUCTION -> "production"
                    KaSourceModuleKind.TEST -> "test"
                    null -> "unknown"
                }
            )
            append("<br />")
            append(targetPlatform.getTargetPlatformDescriptionForRendering())
        }

        else -> error("Unknown module type: ${this::class.java}")
    }
}