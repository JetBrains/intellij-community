// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.facet.FacetConfiguration
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import org.jetbrains.kotlin.config.IKotlinFacetSettings

interface KotlinFacetConfiguration : FacetConfiguration {
    val settings: IKotlinFacetSettings

    override fun createEditorTabs(editorContext: FacetEditorContext, validatorsManager: FacetValidatorsManager): Array<FacetEditorTab> {
        settings.initializeIfNeeded(editorContext.module, editorContext.rootModel)

        val tabs = arrayListOf<FacetEditorTab>()
        tabs += KotlinFacetEditorProviderService.getInstance(editorContext.project).getEditorTabs(this, editorContext, validatorsManager)
        KotlinFacetConfigurationExtension.EP_NAME.extensionList.flatMapTo(tabs) { it.createEditorTabs(editorContext, validatorsManager) }
        return tabs.toTypedArray()
    }
}