// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.facet

import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import org.jdom.Element
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.deserializeFacetSettings
import org.jetbrains.kotlin.config.serializeFacetSettings

class KotlinFacetConfigurationImpl : KotlinFacetConfiguration {
    override var settings = KotlinFacetSettings()
        private set

    @Suppress("OVERRIDE_DEPRECATION")
    override fun readExternal(element: Element) {
        settings = deserializeFacetSettings(element).also { it.updateMergedArguments() }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun writeExternal(element: Element) {
        settings.serializeFacetSettings(element)
    }

    override fun createEditorTabs(
        editorContext: FacetEditorContext,
        validatorsManager: FacetValidatorsManager
    ): Array<FacetEditorTab> {
        settings.initializeIfNeeded(editorContext.module, editorContext.rootModel)

        val tabs = arrayListOf<FacetEditorTab>()
        tabs += KotlinFacetEditorProviderService.getInstance(editorContext.project).getEditorTabs(this, editorContext, validatorsManager)
        KotlinFacetConfigurationExtension.EP_NAME.extensionList.flatMapTo(tabs) { it.createEditorTabs(editorContext, validatorsManager) }
        return tabs.toTypedArray()
    }
}
