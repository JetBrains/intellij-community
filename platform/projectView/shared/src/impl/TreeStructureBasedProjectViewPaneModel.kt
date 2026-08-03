// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateOptions
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.toLegacySortKey
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class TreeStructureBasedProjectViewPaneModel(project: Project) : TreeBasedProjectViewPaneModel<TreeStructureProjectViewNode>(project) {
  override suspend fun createNodeProvider(settingsAccessor: ProjectViewPaneSettingsAccessor): TreeStructureProjectViewNodeProvider {
    return TreeStructureProjectViewNodeProvider(
      project = project,
      structure = createTreeStructure(createTreeStructureViewSettings(settingsAccessor)),
      settings = settingsAccessor,
    )
  }

  protected open fun createTreeStructureViewSettings(settingsAccessor: ProjectViewPaneSettingsAccessor): ProjectViewPaneViewSettings {
    return ProjectViewPaneViewSettings(settingsAccessor)
  }

  protected abstract fun createTreeStructure(viewSettings: ViewSettings): AbstractProjectTreeStructure

  override suspend fun createUpdater(): ProjectViewUpdater {
    return TreeStructureProjectViewUpdater(project)
  }

  override fun supportsOption(option: ProjectViewPaneOption): Boolean {
    // Because tree structure based panes are derived from the respective legacy implementations,
    // the defaults here are also chosen to match AbstractProjectViewPane.
    return when (option) {
      is ProjectViewPaneOption.CompactDirectories -> false
      is ProjectViewPaneOption.FlattenModules -> false
      is ProjectViewPaneOption.ShowLibraryContents -> false
      is ProjectViewPaneOption.ShowModules -> false
      is ProjectViewPaneOption.ShowScratchesAndConsoles -> false
      is ProjectViewPaneOption.ShowExcludedFiles -> false
      is ProjectViewPaneOption.ManualOrder -> false
      else -> true
    }
  }

  override suspend fun navigate(nodeId: Long, options: ProjectViewPaneNavigateOptions) {
    val node = suspendingState?.getNodeById(nodeId) ?: return
    val navigatable = node.userObject.elementDescriptor as? Navigatable? ?: return
    val navigationRequest = readAction { navigatable.navigationRequest() } ?: return
    NavigationService.getInstance(project).navigate(
      request = navigationRequest,
      options = NavigationOptions.defaultOptions()
        .requestFocus(options.requestFocus),
    )
  }

  override fun createSelectNodeVisitorProvider(): ProjectViewSelectNodeVisitorProvider<TreeStructureProjectViewNode> {
    return TreeStructureSelectNodeVisitorProvider()
  }
}

@ApiStatus.Experimental
open class ProjectViewPaneViewSettings(private val settingsAccessor: ProjectViewPaneSettingsAccessor) : ProjectViewSettings {

  override fun isStructureView(): Boolean = false

  override fun isFoldersAlwaysOnTop(): Boolean {
    // Mirror the bug in ProjectViewPaneTreeStructure and alike, as they don't override it,
    // and the platform code is symmetrically broken so it works correctly when we return true.
    // It happens because the comparator accesses the settings directly, bypassing ViewSettings,
    // but PsiDirectoryNode doesn't, and always returns getWeight() == 20, thus delegating to the comparator.
    // Returning the correct value here will break the comparator because PsiDirectoryNode will return a different weight.
    // This, of course, has to be fixed someday, but it's out of scope of PV redesign.
    return true
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

  override fun isShowExcludedFiles(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.ShowExcludedFiles)
  }

  override fun isShowVisibilityIcons(): Boolean {
    return settingsAccessor.isOptionSelected(ProjectViewPaneOptionImpl.ShowVisibilityIcons)
  }

  override fun getSortKey(): NodeSortKey {
    return settingsAccessor.getSortKey().toLegacySortKey()
  }

  override fun isUseFileNestingRules(): Boolean {
    return settingsAccessor.getFileNesting().isFileNestingOn
  }
}
