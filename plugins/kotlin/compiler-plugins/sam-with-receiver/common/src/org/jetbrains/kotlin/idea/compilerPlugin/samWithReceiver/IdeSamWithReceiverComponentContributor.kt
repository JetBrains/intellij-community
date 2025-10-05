// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.samWithReceiver

import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.compilerPlugin.getSpecialAnnotations
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.core.script.v1.ScriptModuleInfo
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverResolverExtension

@InternalIgnoreDependencyViolation
private class IdeSamWithReceiverComponentContributor(private val project: Project) : StorageComponentContainerContributor {
    private companion object {
        val ANNOTATION_OPTION_PREFIX = "plugin:$PLUGIN_ID:${ANNOTATION_OPTION_NAME}="
    }

    private val cache = CachedValuesManager.getManager(project).createCachedValue({
        Result.create(
            ContainerUtil.createConcurrentWeakMap<Module, List<String>>(),
            ProjectRootModificationTracker.getInstance(
                project
            )
        )
    }, /* trackValue = */ false)

    private fun getAnnotationsForModule(module: Module): List<String> {
        return cache.value.getOrPut(module) { module.getSpecialAnnotations(ANNOTATION_OPTION_PREFIX) }
    }


    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        if (!platform.isJvm()) return

        val annotations =
            when (val moduleInfo = moduleDescriptor.getCapability(ModuleInfo.Capability)) {
                is ScriptModuleInfo -> moduleInfo.scriptDefinition.annotationsForSamWithReceivers
                is ScriptDependenciesInfo.ForFile -> moduleInfo.scriptDefinition.annotationsForSamWithReceivers
                is ModuleProductionSourceInfo -> getAnnotationsForModule(moduleInfo.module)
                else -> null
            } ?: return

        container.useInstance(SamWithReceiverResolverExtension(annotations))
    }
}
