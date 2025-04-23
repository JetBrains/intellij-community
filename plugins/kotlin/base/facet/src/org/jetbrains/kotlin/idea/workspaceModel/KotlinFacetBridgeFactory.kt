// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfigurationImpl

object KotlinFacetBridgeFactory {
    val kotlinFacetBridgeEnabled: Boolean
        get() = Registry.`is`("workspace.model.kotlin.facet.bridge", true)

    fun createFacetConfiguration(): KotlinFacetConfiguration {
        return if (kotlinFacetBridgeEnabled) KotlinFacetConfigurationBridge() else KotlinFacetConfigurationImpl()
    }

    fun createKotlinFacet(
        module: Module,
        name: String,
        configuration: KotlinFacetConfiguration,
    ): KotlinFacet {
        return if (kotlinFacetBridgeEnabled) {
            KotlinFacetBridge(module, name, configuration)
        } else {
            KotlinFacet(module, name, configuration)
        }
    }
}