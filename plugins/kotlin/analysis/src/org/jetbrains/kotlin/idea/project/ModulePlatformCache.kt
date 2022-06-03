// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.orDefault

class ModulePlatformCache(private val project: Project): Disposable {
    private val cache: MutableMap<Module, TargetPlatform> = hashMapOf()

    @Volatile
    private var allModulesSupportJvm: Boolean? = null

    override fun dispose() {
        allModulesSupportJvm = null
        synchronized(cache) {
            cache.clear()
        }
    }

    fun allModulesSupportJvm(): Boolean = allModulesSupportJvm ?: run {
        val value = ModuleManager.getInstance(project).modules.all { module ->
            ProgressManager.checkCanceled()
            TargetPlatformDetector.getPlatform(module).isJvm()
        }
        allModulesSupportJvm = value
        value
    }

    fun getPlatformForModule(module: Module) : TargetPlatform {
        module.takeIf { it.isDisposed }?.let {
            synchronized(cache) {
                cache.remove(module)
            }
            throw AlreadyDisposedException("${module.name} is already disposed")
        }

        // fast check
        synchronized(cache) {
            cache[module]?.let { return it }
        }

        ProgressManager.checkCanceled()

        val platform = module.platform.orDefault()

        ProgressManager.checkCanceled()
        synchronized(cache) {
            cache.putIfAbsent(module, platform)?.let { return it }
        }

        return platform
    }

    private fun cleanupCache(modules: Collection<Module>) {
        synchronized(cache) {
            modules.forEach(cache::remove)
        }
    }

    private fun resetAllModulesSupportJvm() {
        allModulesSupportJvm = null
    }

    internal class ModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val changes = event.getChanges(ModuleEntity::class.java).ifEmpty { return }

            val platformCache = getInstance(project)
            // any change of modules could change `allModulesSupportJvm`
            platformCache.resetAllModulesSupportJvm()

            val outdatedModules: List<Module> = changes.asSequence()
                .mapNotNull {
                    when (it) {
                        is EntityChange.Added -> null
                        is EntityChange.Removed -> it.entity
                        is EntityChange.Replaced -> it.oldEntity
                    }
                }
                .mapNotNull { storageBefore.findModuleByEntity(it) }
                .toList()
            platformCache.cleanupCache(outdatedModules)
        }
    }

    companion object {
        fun getInstance(project: Project): ModulePlatformCache = project.service()
    }
}