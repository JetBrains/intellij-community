// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.util.toKtModulesForModificationEvents

/**
 * [FirIdeOutOfBlockModificationService] increments modification trackers and publishes subscription events on out-of-block modification
 * after PSI tree changes and for tests.
 */
@Service(Service.Level.PROJECT)
internal class FirIdeOutOfBlockModificationService(val project: Project) : Disposable {
    /**
     * A project-wide out-of-block modification tracker for Kotlin sources which will be incremented on global PSI tree changes, on any
     * module out-of-block modification, and by tests.
     */
    val projectOutOfBlockModificationTracker: SimpleModificationTracker = SimpleModificationTracker()

    override fun dispose() {}

    /**
     * Publishes out-of-block modification for [module]. Must be called in a write action.
     */
    fun publishModuleOutOfBlockModification(module: KtModule) {
        ApplicationManager.getApplication().assertWriteAccessAllowed()

        project.analysisMessageBus.syncPublisher(KotlinTopics.MODULE_OUT_OF_BLOCK_MODIFICATION).onModification(module)
    }

    /**
     * Publishes out-of-block modification for [module]'s production and test source [KtModule]s. Must be called in a write action.
     */
    fun publishModuleOutOfBlockModification(module: Module) {
        module.toKtModulesForModificationEvents().forEach { publishModuleOutOfBlockModification(it) }
    }

    /**
     * Publishes out-of-block modification for [module] and the project itself. Must be called in a write action.
     */
    fun publishModuleAndProjectOutOfBlockModification(module: KtModule) {
        publishModuleOutOfBlockModification(module)
        projectOutOfBlockModificationTracker.incModificationCount()
    }

    /**
     * Publishes out-of-block modification for [module]'s production and test source [KtModule]s and the project itself. Must be called in a
     * write action.
     */
    fun publishModuleAndProjectOutOfBlockModification(module: Module) {
        publishModuleOutOfBlockModification(module)
        projectOutOfBlockModificationTracker.incModificationCount()
    }

    fun publishGlobalSourceOutOfBlockModification() {
        projectOutOfBlockModificationTracker.incModificationCount()

        // We should not invalidate binary module content here, because global PSI tree changes have no effect on binary modules.
        project.analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION).onModification()
    }

    companion object {
        fun getInstance(project: Project): FirIdeOutOfBlockModificationService =
            project.getService(FirIdeOutOfBlockModificationService::class.java)
    }
}
