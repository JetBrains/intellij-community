// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.assignment.plugin.diagnostics.AssignmentPluginDeclarationChecker
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.compilerPlugin.getSpecialAnnotations
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

class IdeAssignmentContainerContributor(private val project: Project) : StorageComponentContainerContributor {

    private companion object {
        const val ANNOTATION_OPTION_PREFIX = "plugin:$PLUGIN_ID:$ANNOTATION_OPTION_NAME="
    }

    private val moduleCache =
        CachedValuesManager.getManager(project).createCachedValue(
            {
                CachedValueProvider.Result.create(
                    ContainerUtil.createConcurrentWeakMap<Module, List<String>>(),
                    ProjectRootModificationTracker.getInstance(
                        project
                    )
                )
            }, false
        )

    private val scriptCache =
        CachedValuesManager.getManager(project).createCachedValue(
            {
                CachedValueProvider.Result.create(
                    ContainerUtil.createConcurrentWeakMap<String, List<String>>(),
                    ProjectRootModificationTracker.getInstance(
                        project
                    )
                )
            }, false
        )

    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        val annotations =
            when (val moduleInfo = moduleDescriptor.getCapability(ModuleInfo.Capability)) {
                is ScriptModuleInfo -> getAnnotationsForScriptDefinition(moduleInfo.scriptDefinition)
                is ScriptDependenciesInfo.ForFile -> getAnnotationsForScriptDefinition(moduleInfo.scriptDefinition)
                is ModuleProductionSourceInfo -> getAnnotationsForModule(moduleInfo.module)
                else -> null
            } ?: return
        container.useInstance(AssignmentPluginDeclarationChecker(annotations))
    }

    private fun getAnnotationsForScriptDefinition(scriptDefinition: ScriptDefinition): List<String> {
        return scriptCache.value.getOrPut(scriptDefinition.definitionId) { scriptDefinition.getSpecialAnnotations() }
    }

    private fun ScriptDefinition.getSpecialAnnotations(): List<String> {
        val arguments = object : CommonCompilerArguments() {}
        parseCommandLineArguments(compilerOptions.toList(), arguments)
        return arguments.pluginOptions
            ?.filter { it.startsWith(ANNOTATION_OPTION_PREFIX) }
            ?.map { it.substring(ANNOTATION_OPTION_PREFIX.length) }
            ?: emptyList()
    }

    private fun getAnnotationsForModule(module: Module): List<String> {
        return moduleCache.value.getOrPut(module) { module.getSpecialAnnotations(ANNOTATION_OPTION_PREFIX) }
    }
}