// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsTracker

class KotlinFacetSettingsProviderImpl(private val project: Project) : KotlinFacetSettingsProvider {
    override fun getSettings(module: Module) = KotlinFacet.get(module)?.configuration?.settings

    override fun getInitializedSettings(module: Module): KotlinFacetSettings =
        CachedValuesManager.getManager(project).getCachedValue(module) {
            val kotlinFacetSettings = getSettings(module) ?: KotlinFacetSettings()
            kotlinFacetSettings.initializeIfNeeded(module, null)
            CachedValueProvider.Result.create(
                kotlinFacetSettings,
                KotlinCompilerSettingsTracker.getInstance(project),
                KotlinFacetModificationTracker.getInstance(project),
            )
        }
}
