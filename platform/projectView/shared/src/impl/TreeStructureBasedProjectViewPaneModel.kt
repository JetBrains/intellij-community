// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class TreeStructureBasedProjectViewPaneModel(project: Project) : TreeBasedProjectViewPaneModel<TreeStructureProjectViewNode>(project) {
  override suspend fun createNodeProvider(settingsAccessor: ProjectViewPaneSettingsAccessor): TreeStructureProjectViewNodeProvider {
    return TreeStructureProjectViewNodeProvider(
      project = project,
      structure = createTreeStructure(ProjectViewPaneViewSettings(settingsAccessor)),
      settings = settingsAccessor,
    )
  }

  protected abstract fun createTreeStructure(viewSettings: ViewSettings): AbstractProjectTreeStructure
}

private class ProjectViewPaneViewSettings(private val settingsAccessor: ProjectViewPaneSettingsAccessor) : ViewSettings {

  override fun isStructureView(): Boolean = false

  override fun isFoldersAlwaysOnTop(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.FoldersAlwaysOnTop)
  }

  override fun isShowMembers(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.ShowMembers)
  }

  override fun isShowModules(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.ShowModules)
  }

  override fun isShowScratchesAndConsoles(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.ShowScratchesAndConsoles)
  }

  override fun isFlattenModules(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.FlattenModules)
  }

  override fun isShowURL(): Boolean {
    return Registry.`is`("project.tree.structure.show.url")
  }

  override fun isFlattenPackages(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.FlattenPackages)
  }

  override fun isAbbreviatePackageNames(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.AbbreviatePackageNames)
  }

  override fun isHideEmptyMiddlePackages(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.HideEmptyMiddlePackages)
  }

  override fun isCompactDirectories(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.CompactDirectories)
  }

  override fun isShowLibraryContents(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.ShowLibraryContents)
  }
}
