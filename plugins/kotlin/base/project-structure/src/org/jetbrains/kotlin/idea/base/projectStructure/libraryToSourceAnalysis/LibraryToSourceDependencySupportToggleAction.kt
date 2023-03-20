// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle

internal class LibraryToSourceDependencySupportToggleAction : ToggleAction(
    KotlinBaseProjectStructureBundle.message("title.toggle.library.to.source.dependency.support"),
    KotlinBaseProjectStructureBundle.message("enable.components.for.library.to.source.analysis.in.kotlin"),
    null
) {
    override fun isSelected(e: AnActionEvent): Boolean {
        return e.project?.useLibraryToSourceAnalysis == true
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        project.useLibraryToSourceAnalysis = state
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}