// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.openapi.project.Project
import javax.swing.tree.DefaultTreeModel

/**
 * Model for the modules tree that doesn't depend on UI and publishes changes to StateFlow.
 * Delegates to ModulesTreeModelService.
 */
class ModulesTreeModel(project: Project) {
    private val service = ModulesTreeModelService.getInstance(project)

    /**
     * Updates the list of modules in the tree based on the current filter.
     */
    suspend fun updateModulesList(prefix: String = ""): DefaultTreeModel {
        return service.updateModulesList(prefix)
    }
}