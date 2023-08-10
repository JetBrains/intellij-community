// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.facet

import com.intellij.facet.ui.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

interface KotlinFacetEditorProviderService {
    fun getEditorTabs(
        configuration: KotlinFacetConfiguration,
        editorContext: FacetEditorContext,
        validatorsManager: FacetValidatorsManager
    ): List<FacetEditorTab>

    fun getMultipleConfigurationEditor(project: Project, editors: Array<out FacetEditor>): MultipleFacetSettingsEditor

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinFacetEditorProviderService = project.service()
    }
}