// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.context.MANUAL_PROJECT_PATHS_SOURCE_ID
import com.intellij.agent.workbench.prompt.context.buildManualPathsContextItem
import com.intellij.agent.workbench.prompt.context.extractCurrentPaths
import com.intellij.agent.workbench.prompt.context.resolveInitialManualPathSelection
import com.intellij.agent.workbench.prompt.context.resolveInitialTreePreselection
import com.intellij.agent.workbench.prompt.context.resolvePickerBrowseRootPaths
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

private val LOG = logger<AgentPromptProjectPathsManualContextSource>()

internal class AgentPromptProjectPathsManualContextSource : AgentPromptManualContextSourceBridge {
    override val sourceId: String
        get() = MANUAL_PROJECT_PATHS_SOURCE_ID

    override val order: Int
        get() = 10

    override fun getDisplayName(): String {
        return AgentPromptBundle.message("manual.context.paths.display.name")
    }

    override fun showPicker(request: AgentPromptManualContextPickerRequest) {
        try {
            val scopedRootPaths = resolvePickerBrowseRootPaths(request.sourceProject)
            if (scopedRootPaths.isEmpty()) {
                request.onError(AgentPromptBundle.message("manual.context.paths.error.empty"))
                return
            }

            val currentSelection = extractCurrentPaths(request.currentItem)
            val initialSelection = resolveInitialManualPathSelection(
                selection = currentSelection,
                scopedRootPaths = scopedRootPaths,
            )

            showProjectPathsChooserPopup(
                project = request.sourceProject,
                scopedRootPaths = scopedRootPaths,
                initialSelection = initialSelection,
                initialTreePreselection = resolveInitialTreePreselection(
                    initialSelection = initialSelection,
                    invocationData = request.invocationData,
                    scopedRootPaths = scopedRootPaths,
                ),
                anchorComponent = request.anchorComponent,
            ) { selection ->
                request.onSelected(buildManualPathsContextItem(selection))
            }
        } catch (error: Throwable) {
            LOG.warn(error)
            request.onError(AgentPromptBundle.message("manual.context.paths.error.load"))
        }
    }
}

private fun resolvePickerBrowseRootPaths(
    project: Project,
): List<String> {
    return runReadActionBlocking {
        val contentRoots = ProjectRootManager.getInstance(project).contentRootsFromAllModules
            .distinctBy { it.path }
        val scratchRoots = resolveVisibleScratchRoots()
        resolvePickerBrowseRootPaths(
            contentRootPaths = contentRoots.map { it.path },
            scratchRootPaths = scratchRoots.map { it.path },
        )
    }
}

private fun resolveVisibleScratchRoots(): List<VirtualFile> {
    val scratchFileService = ScratchFileService.getInstance()
    return RootType.getAllRootTypes()
        .filterNot { it.isHidden }
        .mapNotNull(scratchFileService::getVirtualFile)
        .distinctBy { it.path }
}
