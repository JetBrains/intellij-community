// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.ModuleEntityChangeListener
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.base.util.caching.newEntity
import org.jetbrains.kotlin.idea.base.util.caching.oldEntity
import org.jetbrains.kotlin.idea.facet.isKotlinFacet
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule

@Service(Service.Level.PROJECT)
class ModulePlatformCache(project: Project) :
    SynchronizedFineGrainedEntityCache<Module, TargetPlatform>(project, doSelfInitialization = false),
    WorkspaceModelChangeListener {
    override fun subscribe() {
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, ModelChangeListener(project))
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, this)
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

    override fun beforeChanged(event: VersionedStorageChange) {
        val facetChanges = event.getChanges<FacetEntity>() + event.getChanges<KotlinSettingsEntity>()
        if (facetChanges.isEmpty()) return

        val storageBefore = event.storageBefore
        val storageAfter = event.storageAfter
        val outdated = mutableSetOf<Module>()

        for (facetChange in facetChanges) {
            val old = facetChange.oldEntity()?.takeIf { it.isKotlinFacet() }
            val new = facetChange.newEntity()?.takeIf { it.isKotlinFacet() }
            old?.markOutdated(storageBefore, outdated)
            new?.markOutdated(storageAfter, outdated)
        }

        if (outdated.isNotEmpty()) invalidateKeys(outdated)
    }

    private fun ModuleSettingsFacetBridgeEntity.markOutdated(storage: ImmutableEntityStorage, outdated: MutableSet<Module>) {
        val module = when (this) {
            is FacetEntity -> this.module
            is KotlinSettingsEntity -> this.module
            else -> null
        }
        module?.findModule(storage)?.let { outdated.add(it) }
    }

    companion object {
        fun getInstance(project: Project): ModulePlatformCache = project.service()
    }
}