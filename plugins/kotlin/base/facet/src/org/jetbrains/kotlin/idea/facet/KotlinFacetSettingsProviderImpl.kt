// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.base.util.caching.newEntity
import org.jetbrains.kotlin.idea.base.util.caching.oldEntity
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity

class KotlinFacetSettingsProviderImpl(project: Project) :
    SynchronizedFineGrainedEntityCache<Module, IKotlinFacetSettings>(project, doSelfInitialization = false),
    WorkspaceModelChangeListener,
    KotlinCompilerSettingsListener,
    KotlinFacetSettingsProvider {

    override fun getSettings(module: Module) = KotlinFacet.get(module)?.configuration?.settings

    override fun getInitializedSettings(module: Module): IKotlinFacetSettings = runReadAction { get(module) }

    override fun calculate(key: Module): IKotlinFacetSettings {
        val kotlinFacetSettings = getSettings(key) ?: KotlinFacetSettings()
        kotlinFacetSettings.initializeIfNeeded(key, null)
        return kotlinFacetSettings
    }

    override fun subscribe() {
        val busConnection = project.messageBus.connect(this)
        busConnection.subscribe(WorkspaceModelTopics.CHANGED, this)
        busConnection.subscribe(KotlinCompilerSettingsListener.TOPIC, this)
    }

    override fun checkKeyValidity(key: Module) {
        if (key.isDisposed) {
            throw AlreadyDisposedException("Module '${key.name}' is already disposed")
        }
    }

    override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
        ApplicationManager.getApplication().runReadAction { invalidate() }
    }

    override fun beforeChanged(event: VersionedStorageChange) {
        val moduleChanges = event.getChanges<ModuleEntity>()
        val facetChanges = event.getChanges<FacetEntity>() + event.getChanges<KotlinSettingsEntity>()
        if (moduleChanges.isEmpty() && facetChanges.isEmpty()) return

        val storageBefore = event.storageBefore
        val storageAfter = event.storageAfter
        val outdated = mutableSetOf<Module>()

        for (moduleChange in moduleChanges) {
            val moduleEntity = moduleChange.oldEntity() ?: continue
            // it does not matter is it explicit kotlin facet module or not
            // there is a default kotlin facet for non-explicit kotlin facet modules
            moduleEntity.findModule(storageBefore)?.let { outdated.add(it) }
        }

        for (facetChange in facetChanges) {
            facetChange.oldEntity()?.takeIf { it.isKotlinFacet() }?.let { oldKotlinFacetEntity ->
                val module = when (oldKotlinFacetEntity) {
                    is FacetEntity -> oldKotlinFacetEntity.module
                    is KotlinSettingsEntity -> oldKotlinFacetEntity.module
                    else -> null
                }
                module?.findModule(storageBefore)?.let { outdated.add(it) }
            }
            facetChange.newEntity()?.takeIf { it.isKotlinFacet() }?.let { newKotlinFacetEntity ->
                val module = when (newKotlinFacetEntity) {
                    is FacetEntity -> newKotlinFacetEntity.module
                    is KotlinSettingsEntity -> newKotlinFacetEntity.module
                    else -> null
                }
                module?.findModule(storageAfter)?.let { outdated.add(it) }
            }
        }

        invalidateKeys(outdated)
    }
}
