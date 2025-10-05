// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.assignment.k1

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.assignment.plugin.diagnostics.AssignmentPluginDeclarationChecker
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.core.script.v1.ScriptModuleInfo
import org.jetbrains.kotlin.platform.TargetPlatform

class IdeAssignmentContainerContributor(private val project: Project) : StorageComponentContainerContributor {

    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        val cache = project.service<AssignmentAnnotationNamesCache>()

        val annotations = when (val moduleInfo = moduleDescriptor.getCapability(ModuleInfo.Capability)) {
            is ScriptModuleInfo -> cache.getNamesForScriptDefinition(moduleInfo.scriptDefinition)
            is ScriptDependenciesInfo.ForFile -> cache.getNamesForScriptDefinition(moduleInfo.scriptDefinition)
            is ModuleProductionSourceInfo -> cache.getNamesForModule(moduleInfo.module)
            else -> emptyList()
        }

        if (annotations.isNotEmpty()) {
            container.useInstance(AssignmentPluginDeclarationChecker(annotations))
        }
    }
}