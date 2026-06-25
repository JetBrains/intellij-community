// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.actions

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.isProjectViewSplit
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.getActionMenu
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.ProjectViewPaneOptionDTO
import com.intellij.platform.projectView.pane.ProjectViewOptionStateDTO
import com.intellij.platform.projectView.pane.ProjectViewPaneOption
import com.intellij.platform.projectView.pane.ProjectViewPaneOptionImpl
import com.intellij.platform.projectView.pane.ProjectViewSortKeyStateDTO
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.function.Function
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class OpenInPreviewTab : OptionAction(ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB)
internal class AutoscrollToSource : OptionAction(ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE)
internal class OpenDirectoriesWithSingleClick : OptionAction(ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK)
internal class AutoscrollFromSource : OptionAction(ProjectViewPaneOptionDTO.AUTOSCROLL_FROM_SOURCE)
internal class ShowModules : OptionAction(ProjectViewPaneOptionDTO.SHOW_MODULES)
internal class ShowMembers : OptionAction(ProjectViewPaneOptionDTO.SHOW_MEMBERS)
internal class ShowExcludedFiles : OptionAction(ProjectViewPaneOptionDTO.SHOW_EXCLUDED_FILES)
internal class ShowVisibilityIcons : OptionAction(ProjectViewPaneOptionDTO.SHOW_VISIBILITY_ICONS)
internal class ShowLibraryContents : OptionAction(ProjectViewPaneOptionDTO.SHOW_LIBRARY_CONTENTS)
internal class ShowScratchesAndConsoles : OptionAction(ProjectViewPaneOptionDTO.SHOW_SCRATCHES_AND_CONSOLES)
internal class FlattenModules : OptionAction(ProjectViewPaneOptionDTO.FLATTEN_MODULES)
internal class FlattenPackages : OptionAction(ProjectViewPaneOptionDTO.FLATTEN_PACKAGES)
internal class AbbreviatePackageNames : OptionAction(ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES)
internal class HideEmptyMiddlePackages : OptionAction(ProjectViewPaneOptionDTO.HIDE_EMPTY_MIDDLE_PACKAGES)
internal class CompactDirectories : OptionAction(ProjectViewPaneOptionDTO.COMPACT_DIRECTORIES)

internal class SortByName : SortKeyAction(NodeSortKey.BY_NAME)
internal class SortByType : SortKeyAction(NodeSortKey.BY_TYPE)
internal class SortByTimeDescending : SortKeyAction(NodeSortKey.BY_TIME_DESCENDING)
internal class SortByTimeAscending : SortKeyAction(NodeSortKey.BY_TIME_ASCENDING)
internal class FoldersAlwaysOnTop : OptionAction(ProjectViewPaneOptionDTO.FOLDERS_ALWAYS_ON_TOP)
internal class ManualOrder : OptionAction(ProjectViewPaneOptionDTO.MANUAL_ORDER)

internal abstract class OptionAction(
  legacyActionSupplier: () -> ProjectViewImpl.Action,
  frontendOptionSupplier: (AnActionEvent) -> Option,
) : ToggleOptionAction(optionSupplier(legacyActionSupplier, frontendOptionSupplier)), DumbAware, ActionRemoteBehaviorSpecification {
  
  constructor(option: ProjectViewPaneOptionDTO) : this(
    legacyActionSupplier = { legacyProjectViewAction(option) },
    frontendOptionSupplier = { event -> frontendOption(event, option) },
  )
  
  private val legacyAction: ProjectViewImpl.Action by lazy { legacyActionSupplier() }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getBehavior(): ActionRemoteBehavior? = if (isProjectViewSplit()) {
    ActionRemoteBehavior.FrontendOnly
  }
  else {
    (legacyAction as? ActionRemoteBehaviorSpecification)?.getBehavior()
  }
}

internal abstract class SortKeyAction(
  private val sortKey: NodeSortKey,
  private val legacyActionSupplier: () -> ProjectViewImpl.Action.SortKeyAction,
) : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification {

  constructor(sortKey: NodeSortKey) : this(
    sortKey,
    legacyActionSupplier = { legacyProjectViewAction(sortKey) },
  )

  private val legacyAction: ProjectViewImpl.Action.SortKeyAction by lazy { legacyActionSupplier() }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getBehavior(): ActionRemoteBehavior? = if (isProjectViewSplit()) {
    ActionRemoteBehavior.FrontendOnly
  }
  else {
    (legacyAction as? ActionRemoteBehaviorSpecification)?.getBehavior()
  }

  override fun update(e: AnActionEvent) {
    if (!isProjectViewSplit()) {
      legacyAction.update(e)
      return
    }
    super.update(e)
    if (e.isFromContextMenu) {
      e.presentation.icon = null
    }
    e.presentation.isEnabledAndVisible = getSortKeyState(e)?.availableSortKeys?.contains(sortKey) == true
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    if (!isProjectViewSplit()) {
      return legacyAction.isSelected(e)
    }
    return getSortKeyState(e)?.sortKey == sortKey
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!isProjectViewSplit()) {
      legacyAction.setSelected(e, state)
      return
    }
    if (!state) return
    val project = e.project ?: return
    ProjectViewActionSupport.getInstance(project).requestSortKeyChange(sortKey)
    val menu = e.getActionMenu()
    if (menu != null) {
      LOG.debug { "Requested a sort key change to $sortKey, action menu update pending" }
      ProjectViewOptionMenuUpdater.getInstance(project).markMenuNeedsUpdating(menu)
    }
  }

  private fun getSortKeyState(e: AnActionEvent): ProjectViewSortKeyStateDTO? {
    return ProjectViewActionSupport.getInstance(e.project ?: return null).getActionState()?.sortKeyState
  }
}

private fun optionSupplier(
  legacyActionSupplier: () -> ProjectViewImpl.Action,
  optionSupplier: (AnActionEvent) -> Option,
): Function<in AnActionEvent, out Option> {
  return Function { event ->
    if (isProjectViewSplit()) {
      optionSupplier(event)
    }
    else {
      legacyActionSupplier().optionSupplier.apply(event.project)
    }
  }
}

private fun legacyProjectViewAction(option: ProjectViewPaneOptionDTO): ProjectViewImpl.Action {
  return when (option) {
    ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB -> ProjectViewImpl.Action.OpenInPreviewTab()
    ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE -> ProjectViewImpl.Action.AutoscrollToSource()
    ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK -> ProjectViewImpl.Action.OpenDirectoriesWithSingleClick()
    ProjectViewPaneOptionDTO.AUTOSCROLL_FROM_SOURCE -> ProjectViewImpl.Action.AutoscrollFromSource()
    ProjectViewPaneOptionDTO.SHOW_MODULES -> ProjectViewImpl.Action.ShowModules()
    ProjectViewPaneOptionDTO.SHOW_MEMBERS -> ProjectViewImpl.Action.ShowMembers()
    ProjectViewPaneOptionDTO.SHOW_EXCLUDED_FILES -> ProjectViewImpl.Action.ShowExcludedFiles()
    ProjectViewPaneOptionDTO.SHOW_VISIBILITY_ICONS -> ProjectViewImpl.Action.ShowVisibilityIcons()
    ProjectViewPaneOptionDTO.SHOW_LIBRARY_CONTENTS -> ProjectViewImpl.Action.ShowLibraryContents()
    ProjectViewPaneOptionDTO.SHOW_SCRATCHES_AND_CONSOLES -> ProjectViewImpl.Action.ShowScratchesAndConsoles()
    ProjectViewPaneOptionDTO.FLATTEN_MODULES -> ProjectViewImpl.Action.FlattenModules()
    ProjectViewPaneOptionDTO.FLATTEN_PACKAGES -> ProjectViewImpl.Action.FlattenPackages()
    ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES -> ProjectViewImpl.Action.AbbreviatePackageNames()
    ProjectViewPaneOptionDTO.HIDE_EMPTY_MIDDLE_PACKAGES -> ProjectViewImpl.Action.HideEmptyMiddlePackages()
    ProjectViewPaneOptionDTO.COMPACT_DIRECTORIES -> ProjectViewImpl.Action.CompactDirectories()
    ProjectViewPaneOptionDTO.FOLDERS_ALWAYS_ON_TOP -> ProjectViewImpl.Action.FoldersAlwaysOnTop()
    ProjectViewPaneOptionDTO.MANUAL_ORDER -> ProjectViewImpl.Action.ManualOrder()
  }
}

private fun legacyProjectViewAction(sortKey: NodeSortKey): ProjectViewImpl.Action.SortKeyAction {
  return when (sortKey) {
    NodeSortKey.BY_NAME -> ProjectViewImpl.Action.SortByName()
    NodeSortKey.BY_TYPE -> ProjectViewImpl.Action.SortByType()
    NodeSortKey.BY_TIME_ASCENDING -> ProjectViewImpl.Action.SortByTimeAscending()
    NodeSortKey.BY_TIME_DESCENDING -> ProjectViewImpl.Action.SortByTimeDescending()
  }
}

@ApiStatus.Internal
fun legacyProjectViewOption(project: Project, option: ProjectViewPaneOptionDTO): Option {
  return legacyProjectViewAction(option).optionSupplier.apply(project)
}

@ApiStatus.Internal
fun legacyProjectViewOption(project: Project, option: ProjectViewPaneOption): Option {
  return legacyProjectViewOption(project, (option as ProjectViewPaneOptionImpl).dto)
}

internal fun ProjectViewPaneOptionDTO.fromDTO(): ProjectViewPaneOption {
  return when (this) {
    ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB -> ProjectViewPaneOptionImpl.OpenInPreviewTab
    ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE -> ProjectViewPaneOptionImpl.AutoscrollToSource
    ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK -> ProjectViewPaneOptionImpl.OpenDirectoriesWithSingleClick
    ProjectViewPaneOptionDTO.AUTOSCROLL_FROM_SOURCE -> ProjectViewPaneOptionImpl.AutoscrollFromSource
    ProjectViewPaneOptionDTO.SHOW_MODULES -> ProjectViewPaneOptionImpl.ShowModules
    ProjectViewPaneOptionDTO.SHOW_MEMBERS -> ProjectViewPaneOptionImpl.ShowMembers
    ProjectViewPaneOptionDTO.SHOW_EXCLUDED_FILES -> ProjectViewPaneOptionImpl.ShowExcludedFiles
    ProjectViewPaneOptionDTO.SHOW_VISIBILITY_ICONS -> ProjectViewPaneOptionImpl.ShowVisibilityIcons
    ProjectViewPaneOptionDTO.SHOW_LIBRARY_CONTENTS -> ProjectViewPaneOptionImpl.ShowLibraryContents
    ProjectViewPaneOptionDTO.SHOW_SCRATCHES_AND_CONSOLES -> ProjectViewPaneOptionImpl.ShowScratchesAndConsoles
    ProjectViewPaneOptionDTO.FLATTEN_MODULES -> ProjectViewPaneOptionImpl.FlattenModules
    ProjectViewPaneOptionDTO.FLATTEN_PACKAGES -> ProjectViewPaneOptionImpl.FlattenPackages
    ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES -> ProjectViewPaneOptionImpl.AbbreviatePackageNames
    ProjectViewPaneOptionDTO.HIDE_EMPTY_MIDDLE_PACKAGES -> ProjectViewPaneOptionImpl.HideEmptyMiddlePackages
    ProjectViewPaneOptionDTO.COMPACT_DIRECTORIES -> ProjectViewPaneOptionImpl.CompactDirectories
    ProjectViewPaneOptionDTO.FOLDERS_ALWAYS_ON_TOP -> ProjectViewPaneOptionImpl.FoldersAlwaysOnTop
    ProjectViewPaneOptionDTO.MANUAL_ORDER -> ProjectViewPaneOptionImpl.ManualOrder
  }
}

private fun frontendOption(
  event: AnActionEvent,
  option: ProjectViewPaneOptionDTO,
): Option = FrontendOption(event, option)

private class FrontendOption(private val event: AnActionEvent, private val option: ProjectViewPaneOptionDTO) : Option {
  override fun isSelected(): Boolean = getOptionState()?.isSelected == true

  override fun isEnabled(): Boolean = getOptionState()?.isEnabled == true

  override fun isAlwaysVisible(): Boolean = getOptionState()?.isAlwaysVisible == true

  private fun getOptionState(): ProjectViewOptionStateDTO? {
    val result = service()?.getActionState()?.optionStates?.get(option)
    LOG.trace { "FrontendOption.getOptionState($option): $result" }
    return result
  }

  override fun setSelected(selected: Boolean) {
    service()?.requestOptionValueChange(option, selected)
    val project = event.project
    val menu = event.getActionMenu()
    if (project != null && menu != null) {
      LOG.debug { "Requested $option to change its value to $selected, action menu update pending" }
      ProjectViewOptionMenuUpdater.getInstance(project).markMenuNeedsUpdating(menu)
    }
  }
  
  private fun service(): ProjectViewActionSupport? {
    val project = event.project ?: return null
    return ProjectViewActionSupport.getInstance(project)
  }
}

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class ProjectViewOptionMenuUpdater(private val coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic fun getInstance(project: Project): ProjectViewOptionMenuUpdater = project.service()
  }

  private val menuNeedsUpdating = AtomicReference(WeakReference<ActionMenu?>(null))

  fun markMenuNeedsUpdating(menu: ActionMenu) {
    menuNeedsUpdating.store(WeakReference(menu))
  }
  
  fun updateMenu() {
    // EDT is a must here, as action updates need the lock, and using UI will throw an exception inside,
    // which, at the moment of the implementation, is silently swallowed by the action system.
    coroutineScope.launch(
      Dispatchers.EDT + CoroutineName("ProjectViewOptionMenuUpdater")
    ) {
      val menu = menuNeedsUpdating.load().get() ?: return@launch
      if (menu.popupMenu.isShowing) {
        LOG.debug { "Updating project view option menu" }
        menu.updateMenuItems()
      }
    }
  }
}

private val LOG = fileLogger()
