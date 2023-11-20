// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractKotlinModuleModificationEventTest<TRACKER : ModuleModificationEventTracker> : AbstractKotlinModificationEventTest<TRACKER>() {
    protected abstract fun constructTracker(module: KtModule): TRACKER

    /**
     * Creates and initializes a tracker to track module modification events. The tracker will be disposed with the test root disposable and
     * does not need to be disposed manually.
     */
    protected fun createTracker(module: KtModule): TRACKER = constructTracker(module).apply { initialize(testRootDisposable) }

    protected fun createTracker(module: Module): TRACKER = createTracker(module.productionSourceInfo!!.toKtModule())

    protected fun createTracker(library: Library): TRACKER =
        createTracker(LibraryInfoCache.getInstance(myProject)[library].single().toKtModule())

    protected fun createTracker(file: KtFile): TRACKER =
        createTracker(ProjectStructureProvider.getModule(myProject, file, contextualModule = null))
}

abstract class ModuleModificationEventTracker(
    private val module: KtModule,
    eventKind: String,
) : ModificationEventTracker(module.project, eventKind) {
    fun handleEvent(eventModule: KtModule, modificationKind: KotlinModuleStateModificationKind) {
        if (eventModule == module) {
            receivedEvents.add(ReceivedEvent(modificationKind == KotlinModuleStateModificationKind.REMOVAL))
        }
    }

    fun handleEvent(eventModule: KtModule) {
        handleEvent(eventModule, KotlinModuleStateModificationKind.UPDATE)
    }
}
