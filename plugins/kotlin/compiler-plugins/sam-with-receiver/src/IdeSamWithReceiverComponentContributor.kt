// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.samWithReceiver.ide

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.annotation.plugin.ide.getSpecialAnnotations
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.caches.project.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.caches.project.ScriptModuleInfo
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor.Companion.ANNOTATION_OPTION
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor.Companion.PLUGIN_ID
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverResolverExtension

class IdeSamWithReceiverComponentContributor(val project: Project) : StorageComponentContainerContributor {
    private companion object {
        val ANNOTATION_OPTION_PREFIX = "plugin:$PLUGIN_ID:${ANNOTATION_OPTION.optionName}="
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

        val moduleInfo = moduleDescriptor.getCapability(ModuleInfo.Capability)
        val annotations =
            when (moduleInfo) {
                is ScriptModuleInfo -> moduleInfo.scriptDefinition.annotationsForSamWithReceivers
                is ScriptDependenciesInfo.ForFile -> moduleInfo.scriptDefinition.annotationsForSamWithReceivers
                is ModuleProductionSourceInfo -> getAnnotationsForModule(moduleInfo.module)
                else -> null
            } ?: return

        container.useInstance(SamWithReceiverResolverExtension(annotations))
    }
}