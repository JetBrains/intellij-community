// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.facet.Facet
import com.intellij.facet.ui.FacetEditor
import com.intellij.facet.ui.MultipleFacetSettingsEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetBridgeFactory

class KotlinFacetTypeImpl : KotlinFacetType<KotlinFacetConfiguration>() {
    override fun createDefaultConfiguration() = KotlinFacetBridgeFactory.createFacetConfiguration()

    override fun createFacet(
        module: Module,
        name: String,
        configuration: KotlinFacetConfiguration,
        underlyingFacet: Facet<*>?
    ) = KotlinFacetBridgeFactory.createKotlinFacet(module, name, configuration)

    override fun createMultipleConfigurationsEditor(project: Project, editors: Array<out FacetEditor>): MultipleFacetSettingsEditor {
        return KotlinFacetEditorProviderService.getInstance(project).getMultipleConfigurationEditor(project, editors)
    }
}