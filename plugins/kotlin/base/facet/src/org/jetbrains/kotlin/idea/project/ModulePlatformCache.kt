// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.base.util.caching.FineGrainedEntityCache
import org.jetbrains.kotlin.base.util.caching.WorkspaceEntityChangeListener
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.DefaultIdeTargetPlatformKindProvider
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm

class ModulePlatformCache(project: Project): FineGrainedEntityCache<Module, TargetPlatform>(project, cleanOnLowMemory = false) {
    @Volatile
    private var allModulesSupportJvm: Boolean? = null

    override fun subscribe() {
        val busConnection = project.messageBus.connect(this)
        WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, ModelChangeListener(project))
    }

    override fun globalDependencies(key: Module, value: TargetPlatform): List<Any> {
        return listOf(ProjectRootModificationTracker.getInstance(key.project))
    }

    override fun checkValidity(key: Module) {
        if (key.isDisposed) {
            throw IllegalStateException("Module ${key.name} is already disposed")
        }
    }

    override fun calculate(key: Module): TargetPlatform {
        return KotlinFacetSettingsProvider.getInstance(key.project)?.getInitializedSettings(key)?.targetPlatform
            ?: key.project.platform
            ?: DefaultIdeTargetPlatformKindProvider.defaultPlatform
    }

    override fun dispose() {
        super.dispose()
        allModulesSupportJvm = null
    }

    fun allModulesSupportJvm(): Boolean {
        return allModulesSupportJvm ?: run {
            val value = ModuleManager.getInstance(project).modules.all { module ->
                ProgressManager.checkCanceled()
                module.platform.isJvm()
            }
            allModulesSupportJvm = value
            value
        }
    }

    private fun resetAllModulesSupportJvm() {
        allModulesSupportJvm = null
    }

    internal class ModelChangeListener(project: Project) : WorkspaceEntityChangeListener<ModuleEntity, Module>(project) {
        override val entityClass: Class<ModuleEntity>
            get() = ModuleEntity::class.java

        override fun map(storage: EntityStorage, entity: ModuleEntity): Module? = storage.findModuleByEntity(entity)

        override fun entitiesChanged(outdated: List<Module>) {
            val platformCache = getInstance(project)

            // Any change of modules might affect `allModulesSupportJvm`
            platformCache.resetAllModulesSupportJvm()

            platformCache.invalidateKeys(outdated)
        }
    }

    companion object {
        fun getInstance(project: Project): ModulePlatformCache = project.service()
    }
}