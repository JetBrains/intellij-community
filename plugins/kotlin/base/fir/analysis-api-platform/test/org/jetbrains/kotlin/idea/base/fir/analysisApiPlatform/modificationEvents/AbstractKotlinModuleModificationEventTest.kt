// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.analysis.api.platform.modification.isModuleLevel
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
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
        module: KaModule,
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
            module.getMainKtSourceModule()!!,
            label,
            additionalAllowedEventKinds,
        )

    protected fun createTracker(
        library: Library,
        label: String,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModuleModificationEventTracker =
        createTracker(
            library.toKaLibraryModules(project).single(),
            label,
            additionalAllowedEventKinds,
        )

    protected fun createTracker(
        file: KtFile,
        label: String,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModuleModificationEventTracker =
        createTracker(
            KotlinProjectStructureProvider.getModule(project, file, useSiteModule = null),
            label,
            additionalAllowedEventKinds,
        )
}
