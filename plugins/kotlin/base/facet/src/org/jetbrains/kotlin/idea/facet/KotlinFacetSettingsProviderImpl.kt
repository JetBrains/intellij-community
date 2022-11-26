// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.facet

import com.intellij.ProjectTopics
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.util.caching.*
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.facet.KotlinFacetModificationTracker.Companion.isKotlinFacet

class KotlinFacetSettingsProviderImpl(project: Project) :
    SynchronizedFineGrainedEntityCache<Module, KotlinFacetSettings>(project),
    WorkspaceModelChangeListener,
    KotlinCompilerSettingsListener,
    ModuleRootListener,
    KotlinFacetSettingsProvider {

    override fun getSettings(module: Module) = KotlinFacet.get(module)?.configuration?.settings

    override fun getInitializedSettings(module: Module): KotlinFacetSettings = runReadAction { get(module) }

    override fun calculate(key: Module): KotlinFacetSettings {
        val kotlinFacetSettings = getSettings(key) ?: KotlinFacetSettings()
        kotlinFacetSettings.initializeIfNeeded(key, null)
        return kotlinFacetSettings
    }

    override fun subscribe() {
        val busConnection = project.messageBus.connect(this)
        busConnection.subscribe(WorkspaceModelTopics.CHANGED, this)
        busConnection.subscribe(KotlinCompilerSettingsListener.TOPIC, this)
        busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
    }

    override fun checkKeyValidity(key: Module) {
        if (key.isDisposed) {
            throw AlreadyDisposedException("Module '${key.name}' is already disposed")
        }
    }

    override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
        invalidate()
    }

    override fun rootsChanged(event: ModuleRootEvent) {
        // TODO: entire method to be drop when IDEA-298694 is fixed.
        //  Reason: unload modules are untracked with WorkspaceModel
        if (event.isCausedByWorkspaceModelChangesOnly) return

        invalidate(writeAccessRequired = true)
    }

    override fun beforeChanged(event: VersionedStorageChange) {
        val moduleChanges = event.getChanges(ModuleEntity::class.java)
        val facetChanges = event.getChanges(FacetEntity::class.java)
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
                oldKotlinFacetEntity.module.findModule(storageBefore)?.let { outdated.add(it) }
            }
            facetChange.newEntity()?.takeIf { it.isKotlinFacet() }?.let { newKotlinFacetEntity ->
                newKotlinFacetEntity.module.findModule(storageAfter)?.let { outdated.add(it) }
            }
        }

        invalidateKeys(outdated)
    }

}
