// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModificationEventKind
import org.jetbrains.kotlin.analysis.providers.topics.isModuleLevel
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractKotlinModuleModificationEventTest : AbstractKotlinModificationEventTest() {
    override fun setUp() {
        super.setUp()

        require(expectedEventKind.isModuleLevel)
    }

    /**
     * Creates and initializes a tracker to track module modification events. The tracker will be disposed with the test root disposable and
     * does not need to be disposed manually.
     */
    protected fun createTracker(
        module: KtModule,
        label: String,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModuleModificationEventTracker =
        createModuleTracker(module, label, additionalAllowedEventKinds)

    protected fun createTracker(
        module: Module,
        label: String,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModuleModificationEventTracker =
        createTracker(
            module.productionSourceInfo!!.toKtModule(),
            label,
            additionalAllowedEventKinds,
        )

    protected fun createTracker(
        library: Library,
        label: String,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModuleModificationEventTracker =
        createTracker(
            LibraryInfoCache.getInstance(project)[library].single().toKtModule(),
            label,
            additionalAllowedEventKinds,
        )

    protected fun createTracker(
        file: KtFile,
        label: String,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModuleModificationEventTracker =
        createTracker(
            ProjectStructureProvider.getModule(project, file, contextualModule = null),
            label,
            additionalAllowedEventKinds,
        )
}
