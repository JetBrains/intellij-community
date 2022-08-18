// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.compilerPreferences

import com.intellij.facet.ui.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.compilerPreferences.facet.KotlinFacetCompilerPluginsTab
import org.jetbrains.kotlin.idea.base.compilerPreferences.facet.KotlinFacetEditorGeneralTab
import org.jetbrains.kotlin.idea.base.compilerPreferences.facet.MultipleKotlinFacetEditor
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetEditorProviderService

class KotlinFacetEditorProviderServiceImpl : KotlinFacetEditorProviderService {
    override fun getEditorTabs(
        configuration: KotlinFacetConfiguration,
        editorContext: FacetEditorContext,
        validatorsManager: FacetValidatorsManager
    ): List<FacetEditorTab> {
        val tabs = ArrayList<FacetEditorTab>(2)
        tabs += KotlinFacetEditorGeneralTab(configuration, editorContext, validatorsManager)
        if (KotlinFacetCompilerPluginsTab.parsePluginOptions(configuration).isNotEmpty()) {
            tabs += KotlinFacetCompilerPluginsTab(configuration, validatorsManager)
        }
        return tabs
    }

    override fun getMultipleConfigurationEditor(project: Project, editors: Array<out FacetEditor>): MultipleFacetSettingsEditor {
        return MultipleKotlinFacetEditor(project, editors)
    }
}