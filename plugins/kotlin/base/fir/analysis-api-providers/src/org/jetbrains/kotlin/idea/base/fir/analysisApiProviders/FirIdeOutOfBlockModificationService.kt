// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleOutOfBlockModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.util.AbstractSingleFileModuleFileListener

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

    init {
        val busConnection = project.messageBus.connect(this)
        busConnection.subscribe(VirtualFileManager.VFS_CHANGES, SingleFileModuleFileListener(project))
    }

    override fun dispose() {}

    /**
     * Publishes out-of-block modification for [module]'s [KtModule]s.
     */
    fun publishModuleOnlyOutOfBlockModification(module: Module) {
        module.sourceModuleInfos.forEach {
            project.analysisMessageBus.syncPublisher(KotlinTopics.MODULE_OUT_OF_BLOCK_MODIFICATION).afterModification(it.toKtModule())
        }
    }

    /**
     * Publishes out-of-block modification for [module]'s [KtModule]s and the project itself.
     *
     * [publishModuleAndProjectOutOfBlockModification] cannot be used for single file-based [KtModule]s (such as scripts), because they
     * don't have an associated IntelliJ [Module].
     */
    fun publishModuleAndProjectOutOfBlockModification(module: Module?) {
        if (module != null) {
            publishModuleOnlyOutOfBlockModification(module)
        }
        projectOutOfBlockModificationTracker.incModificationCount()
    }

    fun publishGlobalOutOfBlockModification() {
        projectOutOfBlockModificationTracker.incModificationCount()

        // We should not invalidate binary module content here, because global PSI tree changes have no effect on binary modules.
        project.analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION).afterModification()
    }

    companion object {
        fun getInstance(project: Project): FirIdeOutOfBlockModificationService =
            project.getService(FirIdeOutOfBlockModificationService::class.java)
    }
}

/**
 * An [AbstractSingleFileModuleFileListener] that publishes out-of-block modification events for the associated [KtModule]s.
 *
 * Any file modification triggers an out-of-block modification event, not just actual out-of-block modifications, which is allowed per the
 * contract of [KotlinModuleOutOfBlockModificationListener].
 *
 * Because Kotlin script and not-under-content-root files aren't associated with a [Module],
 * [FirIdeOutOfBlockModificationService.publishModuleAndProjectOutOfBlockModification] cannot be used to publish out-of-block modification
 * events for such [KtModule]s, hence the existence of this listener.
 */
private class SingleFileModuleFileListener(private val project: Project) : AbstractSingleFileModuleFileListener(project) {
    override fun shouldProcessEvent(event: VFileEvent): Boolean = event is VFileContentChangeEvent

    override fun processEvent(event: VFileEvent, module: KtModule) {
        project.analysisMessageBus.syncPublisher(KotlinTopics.MODULE_OUT_OF_BLOCK_MODIFICATION).afterModification(module)
    }
}
