// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.settings

import com.intellij.application.options.OptionId
import com.intellij.application.options.OptionsApplicabilityFilter
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
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

  private val state = ProjectViewState.getInstance(project)

  fun isOptionSelected(option: ProjectViewPaneOption): Boolean {
    // The DTO is an enum, so we can use an exhaustive when with it.
    val dto = (option as ProjectViewPaneOptionImpl).dto
    return when (dto) {
      ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB -> UISettings.getInstance().openInPreviewTabIfPossible
      ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE -> state.autoscrollToSource
      ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK -> state.openDirectoriesWithSingleClick
      ProjectViewPaneOptionDTO.AUTOSCROLL_FROM_SOURCE -> state.autoscrollFromSource
      ProjectViewPaneOptionDTO.SHOW_MODULES -> state.showModules
      ProjectViewPaneOptionDTO.SHOW_MEMBERS -> state.showMembers
      ProjectViewPaneOptionDTO.SHOW_EXCLUDED_FILES -> state.showExcludedFiles
      ProjectViewPaneOptionDTO.SHOW_VISIBILITY_ICONS -> state.showVisibilityIcons
      ProjectViewPaneOptionDTO.SHOW_LIBRARY_CONTENTS -> state.showLibraryContents
      ProjectViewPaneOptionDTO.SHOW_SCRATCHES_AND_CONSOLES -> state.showScratchesAndConsoles
      ProjectViewPaneOptionDTO.FLATTEN_MODULES -> state.flattenModules
      ProjectViewPaneOptionDTO.FLATTEN_PACKAGES -> state.flattenPackages
      ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES -> state.abbreviatePackageNames
      ProjectViewPaneOptionDTO.HIDE_EMPTY_MIDDLE_PACKAGES -> state.hideEmptyMiddlePackages
      ProjectViewPaneOptionDTO.COMPACT_DIRECTORIES -> state.compactDirectories
      ProjectViewPaneOptionDTO.FOLDERS_ALWAYS_ON_TOP -> state.foldersAlwaysOnTop
      ProjectViewPaneOptionDTO.MANUAL_ORDER -> state.manualOrder
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

  fun getSortKey(): ProjectViewPaneSortKey {
    return state.sortKey.toSettingValue()
  }

  fun getFileNesting(): ProjectViewPaneFileNestingValue {
    return ProjectViewPaneFileNestingValueImpl(
      state.useFileNestingRules,
      ProjectViewFileNestingService.getInstance().getRules(),
    )
  }
}
