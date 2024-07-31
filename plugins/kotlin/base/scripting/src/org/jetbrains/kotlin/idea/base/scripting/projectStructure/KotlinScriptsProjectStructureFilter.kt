// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureFilterExtension
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySourceK2

class KotlinScriptsProjectStructureFilter : ModuleStructureFilterExtension() {
    override fun accepts(module: Module): Boolean {
        val currentSnapshot = module.project.workspaceModel.currentSnapshot

        return Registry.`is`("kotlin.k2.scripting.show.modules") ||
                module.findModuleEntity(currentSnapshot)?.entitySource !is KotlinScriptEntitySourceK2
    }
}