// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.actionMacro.ActionMacro
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.QuickList
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapExtension
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.Strings
import com.intellij.util.containers.JBTreeTraverser
import org.jetbrains.annotations.ApiStatus
import java.util.Arrays

private const val EDITOR_PREFIX = "Editor"
private const val GROUP_INTENTIONS = "Intentions"

private typealias ActionFilter = (AnAction?) -> Boolean

@ApiStatus.Internal
object ActionTreeGroupUtil {
  @JvmStatic
  fun createMainGroup(project: Project?, keymap: Keymap?, quickLists: Array<QuickList>): Group {
    return createMainGroup(
      project = project,
      keymap = keymap,
      quickLists = quickLists,
      filter = null,
      forceFiltering = false,
      filtered = null,
    )
  }

  @JvmStatic
  fun createMainGroup(
    project: Project?,
    keymap: Keymap?,
    quickLists: Array<QuickList>,
    filter: String?,
    forceFiltering: Boolean,
    filtered: Condition<in AnAction>?,
  ): Group {
    val baseFilter = filtered?.toActionFilter() ?: { true }
    val wrappedFilter = wrapFilter(baseFilter, keymap)
    val mainGroup = Group(KeyMapBundle.message("all.actions.group.title"))
    mainGroup.addGroup(createEditorActionsGroup(wrappedFilter))
    mainGroup.addGroup(createMainMenuGroup(wrappedFilter))
    appendGroupsFromExtensions(project, wrappedFilter, mainGroup, KeymapExtension.KeymapLocation.TOP_LEVEL)
    mainGroup.addGroup(createMacrosGroup(wrappedFilter))
    mainGroup.addGroup(createIntentionsGroup(wrappedFilter))
    mainGroup.addGroup(createQuickListsGroup(wrappedFilter, filter, forceFiltering, quickLists))
    mainGroup.addGroup(createPluginsActionsGroup(wrappedFilter))
    mainGroup.addGroup(createOtherGroup(project, wrappedFilter, mainGroup, keymap))
    removeEmptyFilteredGroups(mainGroup, filter, forceFiltering, filtered != null)
    return mainGroup
  }
}

private fun removeEmptyFilteredGroups(
  mainGroup: Group,
  filter: String?,
  forceFiltering: Boolean,
  hasExternalFilter: Boolean,
) {
  if (Strings.isEmpty(filter) && !hasExternalFilter) {
    return
  }

  val iterator = mainGroup.children.iterator()
  while (iterator.hasNext()) {
    val group = iterator.next() as? Group ?: continue
    if (group.size == 0 &&
        !SearchUtil.isComponentHighlighted(group.name, filter, forceFiltering, null, SearchableOptionsRegistrar.getInstance())) {
      iterator.remove()
    }
  }
}

private fun wrapFilter(filter: ActionFilter, keymap: Keymap?): ActionFilter {
  val actionManager = ActionManager.getInstance()
  val shortcutRestrictions = ActionShortcutRestrictions.getInstance()
  return actionFilter@{ action ->
    if (action == null) {
      return@actionFilter false
    }

    val id = actionManager.getId(action)
    if (id != null) {
      if (!Registry.`is`("keymap.show.alias.actions")) {
        val binding = ActionManagerEx.getInstanceEx().getActionBinding(id)
        val bound = binding != null && actionManager.getAction(binding) != null && !hasAssociatedShortcutsInHierarchy(id, keymap)
        if (bound) {
          return@actionFilter false
        }
      }

      if (!shortcutRestrictions.getForActionId(id).allowChanging) {
        return@actionFilter false
      }
    }

    filter(action)
  }
}

private fun hasAssociatedShortcutsInHierarchy(id: String, keymap: Keymap?): Boolean {
  var current = keymap
  while (current != null) {
    if ((current as KeymapImpl).hasOwnActionId(id)) {
      return true
    }
    current = current.parent
  }
  return false
}

private fun createPluginsActionsGroup(filtered: ActionFilter): Group {
  val pluginsGroup = Group(KeyMapBundle.message("plugins.group.title"), null as String?)
  val actionManager = ActionManagerEx.getInstanceEx()
  val plugins = PluginManagerCore.plugins.sortedBy(IdeaPluginDescriptor::getName)
  val pluginNames = plugins.associate { it.pluginId to it.name }
  val pluginIds = LinkedHashSet<PluginId>()
  plugins.mapTo(pluginIds) { it.pluginId }
  PluginManagerCore.getPluginSet().buildPluginIdMap().keys.sortedBy { it.idString }.forEach(pluginIds::add)
  for (pluginId in pluginIds) {
    if (PluginManagerCore.CORE_ID == pluginId || KeymapExtension.EXTENSION_POINT_NAME.extensionList.any { it.skipPluginGroup(pluginId) }) {
      continue
    }

    val pluginActions = actionManager.getPluginActions(pluginId)
    if (pluginActions.isEmpty()) {
      continue
    }

    val name = pluginNames[pluginId] ?: pluginId.idString
    val pluginGroup = createPluginActionsGroup(name, pluginActions, filtered)
    if (pluginGroup.size > 0) {
      pluginsGroup.addGroup(pluginGroup)
    }
  }
  return pluginsGroup
}

private fun createPluginActionsGroup(
  @NlsActions.ActionText name: String,
  pluginActions: Array<String>,
  filtered: ActionFilter,
): Group {
  val actionManager = ActionManagerEx.getInstanceEx()
  val pluginGroup = Group(name, null as String?)
  Arrays.sort(pluginActions, Comparator.comparing(ActionsTreeUtil::getTextToCompare))
  for (actionId in pluginActions) {
    val action = actionManager.getActionOrStub(actionId)
    if (isNonExecutableActionGroup(actionId, action)) {
      continue
    }
    if (filtered(action)) {
      pluginGroup.addActionId(actionId)
    }
  }
  return pluginGroup
}

private fun createMainMenuGroup(filtered: ActionFilter): Group {
  val group = Group(ActionsTreeUtil.getMainMenuTitle(), IdeActions.GROUP_MAIN_MENU, AllIcons.Nodes.KeymapMainMenu)
  val filteredCondition = filtered.toCondition()
  for (action in ActionsTreeUtil.getActions(IdeActions.GROUP_MAIN_MENU)) {
    if (action !is ActionGroup) {
      continue
    }

    val subGroup = ActionsTreeUtil.createGroup(action, false, filteredCondition)
    if (subGroup.size > 0) {
      group.addGroup(subGroup)
    }
  }
  return group
}

private fun createEditorActionsGroup(filtered: ActionFilter): Group {
  val actionManager = ActionManager.getInstance()
  val editorGroup = actionManager.getActionOrStub(IdeActions.GROUP_EDITOR) as? DefaultActionGroup
                    ?: throw AssertionError("${IdeActions.GROUP_EDITOR} group not found")
  val ids = ArrayList<String>()
  addEditorActions(filtered, editorGroup, ids)
  ids.sort()
  val group = Group(KeyMapBundle.message("editor.actions.group.title"), IdeActions.GROUP_EDITOR, AllIcons.Nodes.KeymapEditor)
  for (id in ids) {
    group.addActionId(id)
  }
  return group
}

private fun addEditorActions(filtered: ActionFilter, editorGroup: DefaultActionGroup, ids: MutableList<String>) {
  val actionManager = ActionManager.getInstance()
  for (editorAction in editorGroup.getChildActionsOrStubs()) {
    if (editorAction is DefaultActionGroup) {
      addEditorActions(filtered, editorAction, ids)
      continue
    }

    val actionId = actionManager.getId(editorAction) ?: continue
    if (filtered(editorAction)) {
      ids.add(actionId)
    }
  }
}

private fun appendGroupsFromExtensions(
  project: Project?,
  filtered: ActionFilter,
  parentGroup: Group,
  location: KeymapExtension.KeymapLocation,
) {
  val filteredCondition = filtered.toCondition()
  for (extension in KeymapExtension.EXTENSION_POINT_NAME.extensionList) {
    if (extension.groupLocation != location) {
      continue
    }

    val group = extension.createGroup(filteredCondition, project) as? Group ?: continue
    if (location == KeymapExtension.KeymapLocation.OTHER && group.size == 0) {
      continue
    }
    parentGroup.addGroup(group)
  }
}

private fun createMacrosGroup(filtered: ActionFilter): Group {
  val actionManager = ActionManagerEx.getInstanceEx()
  val ids = actionManager.getActionIdList(ActionMacro.MACRO_ACTION_PREFIX)
  val group = Group(KeyMapBundle.message("macros.group.title"), null as String?)
  for (id in ids.sorted()) {
    if (actionMatchesFilter(filtered, actionManager, id)) {
      group.addActionId(id)
    }
  }
  return group
}

private fun createIntentionsGroup(filtered: ActionFilter): Group {
  val actionManager = ActionManagerEx.getInstanceEx()
  val ids = actionManager.getActionIdList(com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX)
  val group = Group(KeyMapBundle.message("intentions.group.title"), GROUP_INTENTIONS)
  for (id in ids.sorted()) {
    if (actionMatchesFilter(filtered, actionManager, id)) {
      group.addActionId(id)
    }
  }
  return group
}

private fun createQuickListsGroup(
  filtered: ActionFilter,
  filter: String?,
  forceFiltering: Boolean,
  quickLists: Array<QuickList>,
): Group {
  Arrays.sort(quickLists, Comparator.comparing(QuickList::getActionId))

  val actionManager = ActionManager.getInstance()
  val group = Group(KeyMapBundle.message("quick.lists.group.title"))
  for (quickList in quickLists) {
    if (actionMatchesFilter(filtered, actionManager, quickList.actionId) ||
        SearchUtil.isComponentHighlighted(quickList.name, filter, forceFiltering, null, SearchableOptionsRegistrar.getInstance())) {
      group.addQuickList(quickList)
    }
  }
  return group
}

private fun createOtherGroup(project: Project?, filtered: ActionFilter, mainGroup: Group, keymap: Keymap?): Group {
  val actionManager = ActionManagerEx.getInstanceEx() as ActionManagerImpl
  val filteredCondition = filtered.toCondition()
  val otherGroup = Group(KeyMapBundle.message("other.group.title"), null as String?) { AllIcons.Nodes.KeymapOther }
  for (action in ActionsTreeUtil.getActions("Other.KeymapGroup")) {
    ActionsTreeUtil.addAction(otherGroup, action, actionManager, filteredCondition, false)
  }
  appendGroupsFromExtensions(project, filtered, otherGroup, KeymapExtension.KeymapLocation.OTHER)

  mainGroup.initIds()
  otherGroup.initIds()

  val result = HashSet<String>()
  if (keymap != null) {
    for (id in keymap.actionIdList) {
      if (id.startsWith(EDITOR_PREFIX) && actionManager.getActionOrStub("$" + id.substring(EDITOR_PREFIX.length)) != null) {
        continue
      }
      if (id.startsWith(QuickList.QUICK_LIST_PREFIX) || mainGroup.containsId(id) || otherGroup.containsId(id)) {
        continue
      }
      result.add(id)
    }
  }

  val namedGroups = ArrayList<String>()
  for (id in actionManager.getActionIdList("")) {
    val actionOrStub = actionManager.getActionOrStub(id)
    if (isNonExecutableActionGroup(id, actionOrStub) ||
        id.startsWith(QuickList.QUICK_LIST_PREFIX) ||
        mainGroup.containsId(id) ||
        otherGroup.containsId(id) ||
        result.contains(id)) {
      continue
    }

    if (actionOrStub is ActionGroup) {
      namedGroups.add(id)
    }
    else {
      result.add(id)
    }
  }

  filterOtherActionsGroup(result)

  val traverser = JBTreeTraverser.from<String> { actionManager.getParentGroupIds(it) }
  for (actionId in namedGroups) {
    if (traverser.withRoot(actionId).unique().traverse().filter { mainGroup.containsId(it) || otherGroup.containsId(it) }.isNotEmpty) {
      continue
    }
    result.add(actionId)
  }

  otherGroup.children.sortBy { (it as Group).name }

  for (id in result.sortedBy(ActionsTreeUtil::getTextToCompare)) {
    val actionOrStub = actionManager.getActionOrStub(id)
    if ((actionOrStub == null || isSearchable(actionOrStub)) && filtered(actionOrStub)) {
      otherGroup.addActionId(id)
    }
  }
  return otherGroup
}

private fun isSearchable(action: AnAction): Boolean {
  return action !is ActionGroup || action.isSearchable
}

private fun isNonExecutableActionGroup(id: String, actionOrStub: AnAction?): Boolean {
  return actionOrStub is ActionGroup &&
         (actionOrStub.isPopup ||
          actionOrStub.templatePresentation.text.isNullOrEmpty() ||
          id.contains("Popup", ignoreCase = true) ||
          id.contains("Toolbar", ignoreCase = true))
}

private fun filterOtherActionsGroup(actions: MutableSet<String>) {
  filterOutGroup(actions, IdeActions.GROUP_GENERATE)
  filterOutGroup(actions, IdeActions.GROUP_NEW)
  filterOutGroup(actions, IdeActions.GROUP_CHANGE_SCHEME)
}

private fun filterOutGroup(actions: MutableSet<String>, groupId: String) {
  val actionManager = ActionManager.getInstance()
  val action = actionManager.getActionOrStub(groupId)
  if (action !is DefaultActionGroup) {
    return
  }

  for (child in action.getChildActionsOrStubs()) {
    val childId = actionManager.getId(child) ?: continue
    if (child is DefaultActionGroup) {
      filterOutGroup(actions, childId)
    }
    else {
      actions.remove(childId)
    }
  }
}

private fun Condition<in AnAction>.toActionFilter(): ActionFilter {
  return { action -> value(action) }
}

private fun ActionFilter.toCondition(): Condition<AnAction> {
  return Condition { action -> this(action) }
}

private inline fun actionMatchesFilter(filtered: ActionFilter, actionManager: ActionManager, actionId: String): Boolean {
  return filtered(actionManager.getActionOrStub(actionId))
}
