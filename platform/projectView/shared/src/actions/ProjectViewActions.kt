// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.actions

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.ProjectViewImpl
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
import com.intellij.platform.projectView.window.ProjectViewOptionSupport
import com.intellij.platform.projectView.window.isProjectViewSplit
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.function.Function
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal abstract class OptionAction(
  legacyActionSupplier: () -> ProjectViewImpl.Action,
  frontendOptionSupplier: (AnActionEvent) -> Option,
) : ToggleOptionAction(optionSupplier(legacyActionSupplier, frontendOptionSupplier)), DumbAware, ActionRemoteBehaviorSpecification {
  
  constructor(option: ProjectViewOption) : this(
    legacyActionSupplier = { legacyProjectViewAction(option) },
    frontendOptionSupplier = { event -> frontendOption(event, option) },
  )
  
  private val legacyAction: ProjectViewImpl.Action by lazy { legacyActionSupplier() }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getBehavior(): ActionRemoteBehavior? = if (isProjectViewSplit()) {
    ActionRemoteBehavior.FrontendOnly
  }
  else {
    (legacyAction as ActionRemoteBehaviorSpecification?)?.getBehavior()
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
    (legacyAction as ActionRemoteBehaviorSpecification?)?.getBehavior()
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
    ProjectViewOptionSupport.getInstance(project).requestSortKeyChange(sortKey)
    val menu = e.getActionMenu()
    if (menu != null) {
      LOG.debug { "Requested a sort key change to $sortKey, action menu update pending" }
      ProjectViewOptionMenuUpdater.getInstance(project).markMenuNeedsUpdating(menu)
    }
  }

  private fun getSortKeyState(e: AnActionEvent): ProjectViewSortKeyState? {
    return ProjectViewOptionSupport.getInstance(e.project ?: return null).getSortKeyState()
  }
}

internal class OpenInPreviewTab : OptionAction(ProjectViewOption.OPEN_IN_PREVIEW_TAB)
internal class AutoscrollToSource : OptionAction(ProjectViewOption.AUTOSCROLL_TO_SOURCE)
internal class OpenDirectoriesWithSingleClick : OptionAction(ProjectViewOption.OPEN_DIRECTORIES_WITH_SINGLE_CLICK)
internal class AutoscrollFromSource : OptionAction(ProjectViewOption.AUTOSCROLL_FROM_SOURCE)
internal class ShowModules : OptionAction(ProjectViewOption.SHOW_MODULES)
internal class ShowMembers : OptionAction(ProjectViewOption.SHOW_MEMBERS)
internal class ShowExcludedFiles : OptionAction(ProjectViewOption.SHOW_EXCLUDED_FILES)
internal class ShowVisibilityIcons : OptionAction(ProjectViewOption.SHOW_VISIBILITY_ICONS)
internal class ShowLibraryContents : OptionAction(ProjectViewOption.SHOW_LIBRARY_CONTENTS)
internal class ShowScratchesAndConsoles : OptionAction(ProjectViewOption.SHOW_SCRATCHES_AND_CONSOLES)
internal class FlattenModules : OptionAction(ProjectViewOption.FLATTEN_MODULES)
internal class FlattenPackages : OptionAction(ProjectViewOption.FLATTEN_PACKAGES)
internal class AbbreviatePackageNames : OptionAction(ProjectViewOption.ABBREVIATE_PACKAGE_NAMES)
internal class HideEmptyMiddlePackages : OptionAction(ProjectViewOption.HIDE_EMPTY_MIDDLE_PACKAGES)
internal class CompactDirectories : OptionAction(ProjectViewOption.COMPACT_DIRECTORIES)

internal class SortByName : SortKeyAction(NodeSortKey.BY_NAME)
internal class SortByType : SortKeyAction(NodeSortKey.BY_TYPE)
internal class SortByTimeDescending : SortKeyAction(NodeSortKey.BY_TIME_DESCENDING)
internal class SortByTimeAscending : SortKeyAction(NodeSortKey.BY_TIME_ASCENDING)
internal class FoldersAlwaysOnTop : OptionAction(ProjectViewOption.FOLDERS_ALWAYS_ON_TOP)
internal class ManualOrder : OptionAction(ProjectViewOption.MANUAL_ORDER)

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

private fun legacyProjectViewAction(option: ProjectViewOption): ProjectViewImpl.Action {
  return when (option) {
    ProjectViewOption.OPEN_IN_PREVIEW_TAB -> ProjectViewImpl.Action.OpenInPreviewTab()
    ProjectViewOption.AUTOSCROLL_TO_SOURCE -> ProjectViewImpl.Action.AutoscrollToSource()
    ProjectViewOption.OPEN_DIRECTORIES_WITH_SINGLE_CLICK -> ProjectViewImpl.Action.OpenDirectoriesWithSingleClick()
    ProjectViewOption.AUTOSCROLL_FROM_SOURCE -> ProjectViewImpl.Action.AutoscrollFromSource()
    ProjectViewOption.SHOW_MODULES -> ProjectViewImpl.Action.ShowModules()
    ProjectViewOption.SHOW_MEMBERS -> ProjectViewImpl.Action.ShowMembers()
    ProjectViewOption.SHOW_EXCLUDED_FILES -> ProjectViewImpl.Action.ShowExcludedFiles()
    ProjectViewOption.SHOW_VISIBILITY_ICONS -> ProjectViewImpl.Action.ShowVisibilityIcons()
    ProjectViewOption.SHOW_LIBRARY_CONTENTS -> ProjectViewImpl.Action.ShowLibraryContents()
    ProjectViewOption.SHOW_SCRATCHES_AND_CONSOLES -> ProjectViewImpl.Action.ShowScratchesAndConsoles()
    ProjectViewOption.FLATTEN_MODULES -> ProjectViewImpl.Action.FlattenModules()
    ProjectViewOption.FLATTEN_PACKAGES -> ProjectViewImpl.Action.FlattenPackages()
    ProjectViewOption.ABBREVIATE_PACKAGE_NAMES -> ProjectViewImpl.Action.AbbreviatePackageNames()
    ProjectViewOption.HIDE_EMPTY_MIDDLE_PACKAGES -> ProjectViewImpl.Action.HideEmptyMiddlePackages()
    ProjectViewOption.COMPACT_DIRECTORIES -> ProjectViewImpl.Action.CompactDirectories()
    ProjectViewOption.FOLDERS_ALWAYS_ON_TOP -> ProjectViewImpl.Action.FoldersAlwaysOnTop()
    ProjectViewOption.MANUAL_ORDER -> ProjectViewImpl.Action.ManualOrder()
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
fun legacyProjectViewOption(project: Project, option: ProjectViewOption): Option {
  return legacyProjectViewAction(option).optionSupplier.apply(project)
}

private fun frontendOption(
  event: AnActionEvent,
  option: ProjectViewOption,
): Option = FrontendOption(event, option)

private class FrontendOption(private val event: AnActionEvent, private val option: ProjectViewOption) : Option {
  override fun isSelected(): Boolean = getOptionState()?.isSelected == true

  override fun isEnabled(): Boolean = getOptionState()?.isEnabled == true

  override fun isAlwaysVisible(): Boolean = getOptionState()?.isAlwaysVisible == true

  private fun getOptionState(): ProjectViewOptionState? {
    val result = service()?.getOptionState(option)
    LOG.trace { "FrontendOption.getOptionState($option): $result" }
    return result
  }

  override fun setSelected(selected: Boolean) {
    service()?.requestOptionValueUpdate(option, selected)
    val project = event.project
    val menu = event.getActionMenu()
    if (project != null && menu != null) {
      LOG.debug { "Requested $option to change its value to $selected, action menu update pending" }
      ProjectViewOptionMenuUpdater.getInstance(project).markMenuNeedsUpdating(menu)
    }
  }
  
  private fun service(): ProjectViewOptionSupport? {
    val project = event.project ?: return null
    return ProjectViewOptionSupport.getInstance(project)
  }
}

@ApiStatus.Internal
enum class ProjectViewOption {
  OPEN_IN_PREVIEW_TAB,
  AUTOSCROLL_TO_SOURCE,
  OPEN_DIRECTORIES_WITH_SINGLE_CLICK,
  AUTOSCROLL_FROM_SOURCE,
  SHOW_MODULES,
  SHOW_MEMBERS,
  SHOW_EXCLUDED_FILES,
  SHOW_VISIBILITY_ICONS,
  SHOW_LIBRARY_CONTENTS,
  SHOW_SCRATCHES_AND_CONSOLES,
  FLATTEN_MODULES,
  FLATTEN_PACKAGES,
  ABBREVIATE_PACKAGE_NAMES,
  HIDE_EMPTY_MIDDLE_PACKAGES,
  COMPACT_DIRECTORIES,
  FOLDERS_ALWAYS_ON_TOP,
  MANUAL_ORDER,
}

@ApiStatus.Internal
@Serializable
data class ProjectViewActionState(
  val optionStates: Map<ProjectViewOption, ProjectViewOptionState>,
  val sortKeyState: ProjectViewSortKeyState,
)

@ApiStatus.Internal
@Serializable
data class ProjectViewOptionState(
  val isSelected: Boolean,
  val isEnabled: Boolean,
  val isAlwaysVisible: Boolean,
)

@ApiStatus.Internal
@Serializable
data class ProjectViewSortKeyState(
  val sortKey: NodeSortKey,
  val availableSortKeys: Set<NodeSortKey>,
)

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
