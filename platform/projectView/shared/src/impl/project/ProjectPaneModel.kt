// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.impl.project

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.nodes.ModuleGroupNode
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.module.isQualifiedModuleNamesEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.ProjectViewBundle
import com.intellij.platform.projectView.impl.DefaultTreeStructurePsiExtractor
import com.intellij.platform.projectView.impl.ProjectViewPaneViewSettings
import com.intellij.platform.projectView.impl.ProjectViewPsiExtractor
import com.intellij.platform.projectView.impl.TreeStructureBasedProjectViewPaneModel
import com.intellij.platform.projectView.impl.TreeStructureProjectViewNode
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.SuspendingBackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@ApiStatus.Internal
class ProjectPaneModel(project: Project) : TreeStructureBasedProjectViewPaneModel(project) {
  companion object {
    val ID: ProjectViewPaneId = projectViewPaneId(ProjectViewPane.ID)
  }

  private val hasSeveralTopLevelModuleNodes = AtomicBoolean(false)

  override val psi: ProjectViewPsiExtractor<TreeStructureProjectViewNode> = DefaultTreeStructurePsiExtractor(project)

  override suspend fun id(): ProjectViewPaneId = ID

  override suspend fun presentableName(): @NlsSafe String = ProjectViewBundle.message("project.view.pane.project.title")

  override suspend fun order(): Int = 0

  override fun createTreeStructureViewSettings(settingsAccessor: ProjectViewPaneSettingsAccessor): ProjectViewPaneViewSettings {
    return ProjectPaneSettings(settingsAccessor)
  }

  override fun createTreeStructure(viewSettings: ViewSettings): AbstractProjectTreeStructure {
    return ProjectPaneTreeStructure(project, viewSettings)
  }

  override fun supportsOption(option: ProjectViewPaneOption): Boolean {
    return when (option) {
      is ProjectViewPaneOption.FlattenModules -> {
        PlatformUtils.isIntelliJ() &&
        isQualifiedModuleNamesEnabled(project) &&
        hasSeveralTopLevelModuleNodes.load()
      }
      is ProjectViewPaneOption.ShowExcludedFiles -> true
      is ProjectViewPaneOption.ShowScratchesAndConsoles -> true
      else -> super.supportsOption(option)
    }
  }

  override fun supportsFileNesting(): Boolean = true

  override suspend fun onStateChanged(state: SuspendingBackendProjectViewPaneStateAccessor<TreeStructureProjectViewNode>) {
    val newValue = hasSeveralTopLevelModuleNodes(state)
    val oldValue = hasSeveralTopLevelModuleNodes.exchange(newValue)
    if (newValue != oldValue) { // this avoids endless onStateChanged -> updateSettings -> onStateChanged loop
      updateSettings()
    }
  }

  private suspend fun hasSeveralTopLevelModuleNodes(state: SuspendingBackendProjectViewPaneStateAccessor<TreeStructureProjectViewNode>): Boolean {
    val root = state.getChildren(null) ?: return false
    val topLevelNodes = state.getChildren(root.single()) ?: return false
    var topLevelModules = 0
    for (nodeModel in topLevelNodes) {
      val descriptor = nodeModel.userObject.elementDescriptor
      if (descriptor is ProjectViewModuleNode || descriptor is PsiDirectoryNode) {
        ++topLevelModules
        if (topLevelModules > 1) return true
      }
      else if (descriptor is ModuleGroupNode) {
        return true
      }
    }
    return false
  }
}

private class ProjectPaneSettings(settingsAccessor: ProjectViewPaneSettingsAccessor) : ProjectViewPaneViewSettings(settingsAccessor) {
  override fun isShowLibraryContents(): Boolean = true
}

private class ProjectPaneTreeStructure(project: Project, viewSettings: ViewSettings) : AbstractProjectTreeStructure(project, viewSettings)
