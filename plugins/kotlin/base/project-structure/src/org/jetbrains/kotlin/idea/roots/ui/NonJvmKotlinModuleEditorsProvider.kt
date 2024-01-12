// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.roots.ui

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProviderEx
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import com.intellij.openapi.roots.ui.configuration.OutputEditor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.jvm.isJvm

class NonJvmKotlinModuleEditorsProvider : ModuleConfigurationEditorProviderEx {
    override fun isCompleteEditorSet() = true

    override fun createEditors(state: ModuleConfigurationState): Array<ModuleConfigurationEditor> {
        val rootModel = state.rootModel
        val module = rootModel.module

        if (module.moduleTypeName != ModuleTypeId.JAVA_MODULE || module.platform.isJvm()) {
            return ModuleConfigurationEditor.EMPTY
        }

        val moduleName = module.name
        return arrayOf(
            KotlinContentEntriesEditor(moduleName, state),
            object : OutputEditor(state) {}, // Work around protected constructor
            ClasspathEditor(state)
        )
    }
}