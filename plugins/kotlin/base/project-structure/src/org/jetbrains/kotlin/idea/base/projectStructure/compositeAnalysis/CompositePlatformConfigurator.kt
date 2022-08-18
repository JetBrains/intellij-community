// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis

import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.composeContainer
import org.jetbrains.kotlin.container.useImpl
import org.jetbrains.kotlin.resolve.PlatformConfigurator
import org.jetbrains.kotlin.resolve.PlatformConfiguratorBase
import org.jetbrains.kotlin.resolve.checkers.ExperimentalMarkerDeclarationAnnotationChecker
import org.jetbrains.kotlin.resolve.configureDefaultCheckers

class CompositePlatformConfigurator(private val componentConfigurators: List<PlatformConfigurator>) : PlatformConfigurator {
    override val platformSpecificContainer: StorageComponentContainer
        get() = composeContainer(this::class.java.simpleName) {
            configureDefaultCheckers()
            for (configurator in componentConfigurators) {
                (configurator as PlatformConfiguratorBase).configureExtensionsAndCheckers(this)
            }
        }

    override fun configureModuleComponents(container: StorageComponentContainer) {
        componentConfigurators.forEach { it.configureModuleComponents(container) }
    }

    override fun configureModuleDependentCheckers(container: StorageComponentContainer) {
        // We (ab)use the fact that currently, platforms don't use that method, so the only injected compnent will be
        // ExperimentalMarkerDeclarationAnnotationChecker.
        // Unfortunately, it is declared in base class, so repeating call to 'configureModuleDependentCheckers' will lead
        // to multiple registrations.
        container.useImpl<ExperimentalMarkerDeclarationAnnotationChecker>()
    }
}