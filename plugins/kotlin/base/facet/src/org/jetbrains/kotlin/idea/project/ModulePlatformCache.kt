// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.ModuleEntityChangeListener
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

@Service(Service.Level.PROJECT)
class ModulePlatformCache(project: Project): SynchronizedFineGrainedEntityCache<Module, TargetPlatform>(project, doSelfInitialization = false) {
    override fun subscribe() {
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, ModelChangeListener(project))
    }

    override fun checkKeyValidity(key: Module) {
        if (key.isDisposed) {
            throw IllegalStateException("Module ${key.name} is already disposed")
        }
    }

    override fun calculate(key: Module): TargetPlatform {
        return KotlinFacetSettingsProvider.getInstance(key.project)?.getInitializedSettings(key)?.targetPlatform
            ?: key.project.platform
            ?: JvmPlatforms.defaultJvmPlatform
    }

    internal class ModelChangeListener(project: Project) : ModuleEntityChangeListener(project) {
        override fun entitiesChanged(outdated: List<Module>) {
            val platformCache = getInstance(project)

            platformCache.invalidateKeys(outdated)
        }
    }

    companion object {
        fun getInstance(project: Project): ModulePlatformCache = project.service()
    }
}