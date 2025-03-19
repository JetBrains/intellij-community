// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureFilterExtension
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.ide.legacyBridge.findSnapshotModuleEntity
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource

class KotlinScriptsProjectStructureFilter : ModuleStructureFilterExtension() {
    override fun accepts(module: Module): Boolean {
        return Registry.`is`("kotlin.k2.scripting.show.modules") ||
                module.findSnapshotModuleEntity()?.entitySource !is KotlinScriptEntitySource
    }
}
