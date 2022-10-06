// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.assignment.plugin.diagnostics.AssignmentPluginDeclarationChecker
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.compilerPlugin.CachedAnnotationNames
import org.jetbrains.kotlin.idea.compilerPlugin.assignment.ScriptAnnotationNames.Companion.ANNOTATION_OPTION_PREFIX
import org.jetbrains.kotlin.platform.TargetPlatform

class IdeAssignmentContainerContributor(project: Project) : StorageComponentContainerContributor {

    private val scriptCache = ScriptAnnotationNames(project)
    private val moduleCache = CachedAnnotationNames(project, ANNOTATION_OPTION_PREFIX)

    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        val annotations =
            when (val moduleInfo = moduleDescriptor.getCapability(ModuleInfo.Capability)) {
                is ScriptModuleInfo -> scriptCache.getNamesForScriptDefinition(moduleInfo.scriptDefinition)
                is ScriptDependenciesInfo.ForFile -> scriptCache.getNamesForScriptDefinition(moduleInfo.scriptDefinition)
                is ModuleProductionSourceInfo -> moduleCache.getNamesForModule(moduleInfo.module)
                else -> emptyList()
            }
        if (annotations.isNotEmpty()) {
            container.useInstance(AssignmentPluginDeclarationChecker(annotations))
        }
    }
}