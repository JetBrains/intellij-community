// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.settings

import com.intellij.application.options.OptionId
import com.intellij.application.options.OptionsApplicabilityFilter
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper
import com.intellij.ide.scratch.ScratchTreeStructureProvider
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class ProjectViewPaneSettingsService(private val project: Project) {
  companion object {
    @JvmStatic fun getInstance(project: Project): ProjectViewPaneSettingsService = project.service()
  }

  fun isOptionSelected(option: ProjectViewPaneOption): Boolean {
    val projectViewState = ProjectViewState.getInstance(project)
    // The DTO is an enum, so we can use an exhaustive when with it.
    val dto = (option as ProjectViewPaneOptionImpl).dto
    return when (dto) {
      ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB -> UISettings.getInstance().openInPreviewTabIfPossible
      ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE -> projectViewState.autoscrollToSource
      ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK -> projectViewState.openDirectoriesWithSingleClick
      ProjectViewPaneOptionDTO.AUTOSCROLL_FROM_SOURCE -> projectViewState.autoscrollFromSource
      ProjectViewPaneOptionDTO.SHOW_MODULES -> projectViewState.showModules
      ProjectViewPaneOptionDTO.SHOW_MEMBERS -> projectViewState.showMembers
      ProjectViewPaneOptionDTO.SHOW_EXCLUDED_FILES -> projectViewState.showExcludedFiles
      ProjectViewPaneOptionDTO.SHOW_VISIBILITY_ICONS -> projectViewState.showVisibilityIcons
      ProjectViewPaneOptionDTO.SHOW_LIBRARY_CONTENTS -> projectViewState.showLibraryContents
      ProjectViewPaneOptionDTO.SHOW_SCRATCHES_AND_CONSOLES -> projectViewState.showScratchesAndConsoles
      ProjectViewPaneOptionDTO.FLATTEN_MODULES -> projectViewState.flattenModules
      ProjectViewPaneOptionDTO.FLATTEN_PACKAGES -> projectViewState.flattenPackages
      ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES -> projectViewState.abbreviatePackageNames
      ProjectViewPaneOptionDTO.HIDE_EMPTY_MIDDLE_PACKAGES -> projectViewState.hideEmptyMiddlePackages
      ProjectViewPaneOptionDTO.COMPACT_DIRECTORIES -> projectViewState.compactDirectories
      ProjectViewPaneOptionDTO.FOLDERS_ALWAYS_ON_TOP -> projectViewState.foldersAlwaysOnTop
      ProjectViewPaneOptionDTO.MANUAL_ORDER -> projectViewState.manualOrder
    }
  }

  fun isOptionEnabled(option: ProjectViewPaneOption): Boolean {
    val dto = (option as ProjectViewPaneOptionImpl).dto
    return when (dto) {
      ProjectViewPaneOptionDTO.SHOW_VISIBILITY_ICONS -> OptionsApplicabilityFilter.isApplicable(OptionId.PROJECT_VIEW_SHOW_VISIBILITY_ICONS)
      ProjectViewPaneOptionDTO.SHOW_SCRATCHES_AND_CONSOLES -> AdvancedSettings.getBoolean(ScratchTreeStructureProvider.SCRATCHES_NODE_SETTING)
      ProjectViewPaneOptionDTO.FLATTEN_PACKAGES -> ProjectViewDirectoryHelper.getInstance(project).supportsFlattenPackages()
      ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES -> isOptionSelectedAndEnabled(ProjectViewPaneOptionImpl.FlattenPackages)
      ProjectViewPaneOptionDTO.HIDE_EMPTY_MIDDLE_PACKAGES -> ProjectViewDirectoryHelper.getInstance(project).supportsHideEmptyMiddlePackages()
      else -> true
    }
  }

  fun isOptionAlwaysVisible(option: ProjectViewPaneOption): Boolean {
    val dto = (option as ProjectViewPaneOptionImpl).dto
    return when (dto) {
      ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES -> isOptionEnabled(ProjectViewPaneOptionImpl.FlattenPackages)
      else -> false
    }
  }

  private fun isOptionSelectedAndEnabled(option: ProjectViewPaneOption): Boolean {
    return isOptionEnabled(option) && isOptionSelected(option)
  }
}
