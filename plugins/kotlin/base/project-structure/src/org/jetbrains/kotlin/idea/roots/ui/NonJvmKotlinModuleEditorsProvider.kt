// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roots.ui

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProviderEx
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import com.intellij.openapi.roots.ui.configuration.OutputEditor
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.jvm.isJvm

private class NonJvmKotlinModuleEditorsProvider : ModuleConfigurationEditorProviderEx {
    override fun isCompleteEditorSet() = true

    override fun createEditors(state: ModuleConfigurationState): Array<ModuleConfigurationEditor> {
        val rootModel = state.rootModel
        val module = rootModel.module

        if (module.moduleTypeName != JAVA_MODULE_ENTITY_TYPE_ID_NAME || module.platform.isJvm()) {
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