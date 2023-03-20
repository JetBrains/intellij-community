// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.compilerPreferences.facet

import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorValidator
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.idea.base.compilerPreferences.facet.KotlinFacetEditorGeneralTab.EditorComponent

abstract class KotlinFacetValidatorCreator {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinFacetValidatorCreator> =
            ExtensionPointName.create("org.jetbrains.kotlin.facetValidatorCreator")
    }

    abstract fun create(
        editor: EditorComponent,
        validatorsManager: FacetValidatorsManager,
        editorContext: FacetEditorContext
    ): FacetEditorValidator
}