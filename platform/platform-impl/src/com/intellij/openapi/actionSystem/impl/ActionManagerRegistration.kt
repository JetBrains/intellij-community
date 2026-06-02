// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet",
               "ReplacePutWithAssignment",
               "ReplaceJavaStaticMethodWithKotlinAnalog",
               "OVERRIDE_DEPRECATION",
               "RemoveRedundantQualifierName")

package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.onActionLoadedFromXml
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionStub
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.OverridingAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer.LightCustomizeStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.openapi.project.ProjectType
import com.intellij.util.containers.with
import com.intellij.util.containers.without
import com.intellij.util.xml.dom.XmlElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap

internal sealed interface ActionRegistrar {
  val state: ActionManagerState

  val isPostInit: Boolean

  fun putAction(actionId: String, action: AnAction)

  fun removeAction(actionId: String)

  fun getAction(id: String): AnAction?

  fun bindShortcuts(sourceActionId: String, targetActionId: String)

  fun unbindShortcuts(targetActionId: String)

  fun getActionBinding(actionId: String): String?

  fun actionRegistered(actionId: String, action: AnAction)
}

internal class PostInitActionRegistrar(
  idToAction: Map<String, AnAction>,
  @Volatile private var boundShortcuts: Map<String, String>,
  override val state: ActionManagerState,
) : ActionRegistrar {
  private val idToAction = ConcurrentHashMap(idToAction)

  val ids: Set<String>
    get() = idToAction.keys

  override val isPostInit: Boolean
    get() = true

  /**
   * Stub actions here! Don't use it blindly.
   */
  val actions: Collection<AnAction>
    get() = idToAction.values

  override fun putAction(actionId: String, action: AnAction) {
    idToAction.put(actionId, action)
  }

  override fun removeAction(actionId: String) {
    idToAction.remove(actionId)
  }

  override fun getAction(id: String) = idToAction.get(id)

  fun getBaseAction(overridingAction: OverridingAction): AnAction? {
    val id = getId(overridingAction as AnAction) ?: return null
    return state.getBaseAction(id)
  }

  fun getId(action: AnAction): String? {
    if (action is ActionStubBase) {
      return action.id
    }
    if (action is ChameleonAction) {
      return action.actionId
    }
    return state.getActionId(action)
  }

  fun getActionIdList(idPrefix: String): List<String> {
    return idToAction.keys.filter { it.startsWith(idPrefix) }
  }

  fun actionsOrStubs(): Sequence<AnAction> = idToAction.values.asSequence()

  fun unstubbedActions(filter: (String) -> Boolean): Sequence<AnAction> {
    return idToAction.keys.asSequence().filter(filter).mapNotNull {
      getAction(id = it, canReturnStub = false, actionRegistrar = this)
    }
  }

  fun groupIds(actionId: String): List<String> {
    return state.getParentGroupIds(actionId)
  }

  fun getBoundActions(): Set<String> = boundShortcuts.keys

  override fun getActionBinding(actionId: String) = getActionBinding(actionId, boundShortcuts)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    boundShortcuts = boundShortcuts.with(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    boundShortcuts = boundShortcuts.without(targetActionId)
  }

  override fun actionRegistered(actionId: String, action: AnAction) {
    val schema = ApplicationManager.getApplication().serviceIfCreated<CustomActionsSchema>() ?: return
    for (url in schema.getActions()) {
      if (url.componentId == actionId) {
        schema.incrementModificationStamp()
        break
      }
    }

    updateHandlers(action)
  }
}

internal class PreInitActionRuntimeRegistrar(
  private val idToAction: HashMap<String, AnAction>,
  private val actionRegistrar: ActionPreInitRegistrar,
) : ActionRuntimeRegistrar {
  override fun unregisterActionByIdPrefix(idPrefix: String) {
    for (oldId in idToAction.keys.filter { it.startsWith(idPrefix) }) {
      unregisterAction(actionId = oldId, actionRegistrar = actionRegistrar)
    }
  }

  override fun unregisterAction(actionId: String) {
    unregisterAction(actionId = actionId, actionRegistrar = actionRegistrar)
  }

  override fun getActionOrStub(actionId: String): AnAction? = idToAction.get(actionId)

  override fun getUnstubbedAction(actionId: String): AnAction? {
    return getAction(id = actionId, canReturnStub = false, actionRegistrar = actionRegistrar)
  }

  override fun addToGroup(group: AnAction, action: AnAction, constraints: Constraints) {
    addToGroup(group = group,
               action = action,
               constraints = constraints,
               module = null,
               state = actionRegistrar.state,
               secondary = false)
  }

  override fun replaceAction(actionId: String, newAction: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId, actionRegistrar = actionRegistrar)
  }

  override fun getId(action: AnAction): String? {
    return if (action is ActionStubBase) action.id else actionRegistrar.state.getActionId(action)
  }

  override fun getBaseAction(overridingAction: OverridingAction): AnAction? {
    val id = getId(overridingAction as AnAction) ?: return null
    return actionRegistrar.state.getBaseAction(id)
  }

  override fun registerAction(actionId: String, action: AnAction) {
    registerAction(actionId = actionId,
                   action = action,
                   pluginId = null,
                   projectType = null,
                   actionRegistrar = actionRegistrar)
  }
}

internal class PostInitActionRuntimeRegistrar(private val actionPostInitRegistrar: PostInitActionRegistrar) : ActionRuntimeRegistrar {
  override fun registerAction(actionId: String, action: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    actionPostInitRegistrar.state.withLock {
      registerAction(actionId = actionId,
                     action = action,
                     pluginId = plugin?.pluginId,
                     projectType = null,
                     actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun unregisterActionByIdPrefix(idPrefix: String) {
    for (oldId in actionPostInitRegistrar.getActionIdList(idPrefix)) {
      actionPostInitRegistrar.state.withLock {
        unregisterAction(actionId = oldId, actionRegistrar = actionPostInitRegistrar)
      }
    }
  }

  override fun unregisterAction(actionId: String) {
    actionPostInitRegistrar.state.withLock {
      unregisterAction(actionId = actionId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun getActionOrStub(actionId: String): AnAction? = actionPostInitRegistrar.getAction(actionId)

  override fun getUnstubbedAction(actionId: String): AnAction? {
    return getAction(id = actionId, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
  }

  override fun addToGroup(group: AnAction, action: AnAction, constraints: Constraints) {
    addToGroup(group = group as DefaultActionGroup,
               action = action,
               constraints = constraints,
               module = null,
               state = actionPostInitRegistrar.state,
               secondary = false)
  }

  override fun replaceAction(actionId: String, newAction: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    actionPostInitRegistrar.state.withLock {
      replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun getId(action: AnAction): String? = actionPostInitRegistrar.getId(action)

  override fun getBaseAction(overridingAction: OverridingAction): AnAction? = actionPostInitRegistrar.getBaseAction(overridingAction)

}

internal fun getActionBinding(actionId: String, boundShortcuts: Map<String, String>): String? {
  var visited: MutableSet<String>? = null
  var id = actionId
  while (true) {
    val next = boundShortcuts.get(id) ?: break
    if (visited == null) {
      visited = HashSet()
    }

    id = next
    if (!visited.add(id)) {
      break
    }
  }
  return if (id == actionId) null else id
}

internal class ActionPreInitRegistrar(
  private val idToAction: HashMap<String, AnAction>,
  private val boundShortcuts: HashMap<String, String>,
  override val state: ActionManagerState,
) : ActionRegistrar {
  override val isPostInit: Boolean
    get() = false

  override fun putAction(actionId: String, action: AnAction) {
    idToAction.put(actionId, action)
  }

  override fun removeAction(actionId: String) {
    idToAction.remove(actionId)
  }

  override fun getAction(id: String) = idToAction.get(id)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    boundShortcuts.put(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    boundShortcuts.remove(targetActionId)
  }

  override fun getActionBinding(actionId: String) = getActionBinding(actionId, boundShortcuts)

  override fun actionRegistered(actionId: String, action: AnAction) {
  }
}

internal fun reportActionIdCollision(
  actionId: String,
  action: AnAction,
  pluginId: PluginId?,
  oldAction: AnAction?,
  oldPluginId: PluginId?,
) {
  val oldPluginInfo = oldPluginId?.let { getPluginInfo(it) }
  val message = "ID '$actionId' is already taken by action ${actionToString(oldAction)} $oldPluginInfo. " +
                "Action ${actionToString(action)} cannot use the same ID"
  if (pluginId == null) {
    actionManagerImplLog.error(message)
  }
  else {
    actionManagerImplLog.error(PluginException("$message (plugin $pluginId)", null, pluginId))
  }
}

internal fun actionToString(action: AnAction?): @NonNls String {
  if (action == null) return "null"
  when (action) {
    is ChameleonAction -> return "ChameleonAction(" + action.actions.values.joinToString { actionToString(it) } + ")"
    is ActionStub -> return "'$action' (${action.className})"
    else -> return "'$action' (${action.javaClass})"
  }
}

internal val walker = StackWalker.getInstance(setOf(StackWalker.Option.RETAIN_CLASS_REFERENCE), 3)

// Group mutation is intentionally outside ActionManagerState.withLock. The group may sort by action id under its own monitor,
// so we pass a state-free resolver built from a snapshot instead of letting the group call back into ActionManager.getId().
internal fun addToGroup(
  group: AnAction,
  action: AnAction,
  constraints: Constraints,
  module: IdeaPluginDescriptor?,
  state: ActionManagerState,
  secondary: Boolean,
) {
  try {
    val actionGroup = group as DefaultActionGroup
    val children = actionGroup.childActionsOrStubs
    val resolverItems = ArrayList<AnAction>(children.size + 2)
    resolverItems.add(action)
    resolverItems.add(group)
    resolverItems.addAll(children)
    val actionToId = state.createActionIdResolver(resolverItems)
    val actionId = actionToId(action)
    if (module != null && action !is Separator && actionGroup.containsAction(action)) {
      reportActionManagerError(module, "Cannot add an action twice: $actionId " +
                                       "(${if (action is ActionStub) action.className else action.javaClass.name})")
      return
    }

    actionGroup
      .addAction(action, constraints, actionToId)
      .setAsSecondary(secondary)
    if (actionId != null) {
      actionToId(group)?.let { groupId ->
        state.addGroupMapping(actionId, groupId)
      }
    }
  }
  catch (e: IllegalArgumentException) {
    if (module == null) {
      throw e
    }
    else {
      reportActionManagerError(module, e.message!!, e)
    }
  }
}

internal fun removeFromGroup(
  group: DefaultActionGroup,
  action: AnAction,
  actionId: String?,
  groupId: String?,
  state: ActionManagerState,
) {
  // Keep the group monitor outside the state monitor; group membership metadata is removed after the group is updated.
  group.remove(action, actionId)
  if (groupId != null && actionId != null) {
    state.removeGroupMapping(actionId, groupId)
  }
}

// executed under lock
internal fun replaceStub(stub: ActionStubBase, convertedAction: AnAction, actionRegistrar: ActionRegistrar): AnAction {
  if (actionRegistrar.state.removeActionId(stub) == null) {
    throw IllegalStateException("No action in actionToId by stub (stub=$stub)")
  }

  updateHandlers(convertedAction)

  actionRegistrar.state.setActionId(convertedAction, stub.id)

  val projectType = (stub as? ActionStub)?.projectType
  val result = when {
    projectType != null -> ChameleonAction(stub.id, convertedAction, projectType) { actionRegistrar.getAction(it) }
    else -> convertedAction
  }
  actionRegistrar.putAction(stub.id, result)
  return result
}

// Post-init callers execute this under ActionManagerState.withLock; pre-init registration is single-threaded by construction.
// Registration binds action->id before publishing id->action so failed duplicate registration cannot leave a findable action id.
// If id publication fails, the action binding is rolled back in the same registration operation.
internal fun registerAction(
  actionId: String,
  action: AnAction,
  pluginId: PluginId?,
  projectType: ProjectType?,
  actionRegistrar: ActionRegistrar,
  oldIndex: Int = -1,
  oldGroups: List<String>? = null,
): Boolean {
  val state = actionRegistrar.state
  if (state.isActionProhibited(actionId)) {
    return false
  }

  val existingByAction = state.putActionId(action, actionId)
  if (existingByAction != null) {
    val module = if (pluginId == null) null else PluginManagerCore.getPluginSet().findEnabledPlugin(pluginId)
    val message = "ID '$existingByAction' is already taken by action ${actionToString(action)}." +
                  " ID '$actionId' cannot be registered for the same action"
    if (module == null) {
      actionManagerImplLog.error(PluginException("$message $pluginId", null, pluginId))
    }
    else {
      reportActionManagerError(module, message)
    }
    return false
  }

  val existing = actionRegistrar.getAction(actionId)
  if (!addToMap(actionId = actionId, existing = existing, action = action, projectType = projectType, actionRegistrar)) {
    state.removeActionId(action)
    reportActionIdCollision(actionId = actionId,
                            action = action,
                            pluginId = pluginId,
                            oldAction = actionRegistrar.getAction(actionId),
                            oldPluginId = state.getPluginId(actionId))
    return false
  }

  action.setShortcutSet(ProxyShortcutSet(actionId))
  state.registerAction(actionId = actionId, pluginId = pluginId, oldIndex = oldIndex, oldGroups = oldGroups)

  actionRegistrar.actionRegistered(actionId, action)
  ModifierKeyDoubleClickHandler.scheduleKeymapShortcutSyncIfCreated()
  return true
}

/**
 * We want to avoid leaking scope for `actionPerformed`
 * So we await until all children coroutines finish and then terminate the scope
 */

internal fun getAction(
  id: String,
  canReturnStub: Boolean,
  actionRegistrar: ActionRegistrar,
  actionSupplier: (String) -> AnAction? = { actionRegistrar.getAction(it) },
): AnAction? {
  var action = actionRegistrar.getAction(id)
  if (canReturnStub || action !is ActionStubBase) {
    return action
  }

  val converted = if (action is ActionStub) {
    convertActionStub(stub = action, actionSupplier = actionSupplier)
  }
  else {
    convertGroupStub(stub = action as ActionGroupStub, actionRegistrar = actionRegistrar)
  }

  if (converted == null) {
    unregisterAction(actionId = id, actionRegistrar = actionRegistrar)
    return null
  }

  return actionRegistrar.state.withLock {
    // get under lock - maybe already replaced in parallel
    action = actionRegistrar.getAction(id)
    val stub = action as? ActionStubBase ?: return@withLock action
    replaceStub(stub = stub, convertedAction = converted, actionRegistrar = actionRegistrar)
  }
}

// must be called under lock
internal fun unregisterAction(actionId: String, actionRegistrar: ActionRegistrar, removeFromGroups: Boolean = true) {
  val actionToRemove = actionRegistrar.getAction(actionId)
  if (actionToRemove == null) {
    actionManagerImplLog.debug { "action with ID $actionId wasn't registered" }
    return
  }

  actionRegistrar.removeAction(actionId)

  val state = actionRegistrar.state
  state.removeActionId(actionToRemove)
  val parentGroupIds = state.removeAction(actionId)
  if (removeFromGroups && parentGroupIds.isNotEmpty()) {
    val customActionSchema = serviceIfCreated<CustomActionsSchema>()
    for (groupId in parentGroupIds) {
      customActionSchema?.invalidateCustomizedActionGroup(groupId)
      val group = getAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
      if (group == null) {
        actionManagerImplLog.error("Trying to remove action $actionId from non-existing group $groupId")
        continue
      }

      group.remove(actionToRemove, actionId)
      if (group is ActionGroupStub) {
        continue
      }

      // group can be used as a stub in other actions
      for (parentOfGroup in state.getParentGroupIds(groupId)) {
        val parentOfGroupAction =
          getAction(id = parentOfGroup, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
        if (parentOfGroupAction == null) {
          actionManagerImplLog.error("Trying to remove action $actionId from non-existing group $parentOfGroup")
          continue
        }

        for (stub in parentOfGroupAction.childActionsOrStubs) {
          if (stub is ActionGroupStub && groupId == stub.id) {
            stub.remove(actionToRemove, actionId)
          }
        }
      }
    }
  }

  if (actionToRemove is ActionGroup) {
    state.removeGroupMappingFromAll(actionId)
  }
  updateHandlers(actionToRemove)
  ModifierKeyDoubleClickHandler.scheduleKeymapShortcutSyncIfCreated()
}

internal fun replaceAction(actionId: String, newAction: AnAction, pluginId: PluginId?, actionRegistrar: ActionRegistrar): AnAction? {
  val state = actionRegistrar.state
  if (state.isActionProhibited(actionId)) {
    return null
  }

  val existingNewActionId = state.getActionId(newAction)
  if (existingNewActionId != null && existingNewActionId != actionId) {
    val module = if (pluginId == null) null else PluginManagerCore.getPluginSet().findEnabledPlugin(pluginId)
    val message = "ID '$existingNewActionId' is already taken by action ${actionToString(newAction)}." +
                  " ID '$actionId' cannot be registered for the same action"
    if (module == null) {
      actionManagerImplLog.error(PluginException("$message $pluginId", null, pluginId))
    }
    else {
      reportActionManagerError(module, message)
    }
    return null
  }

  val oldAction = if (newAction is OverridingAction) {
    getAction(id = actionId, canReturnStub = false, actionRegistrar = actionRegistrar)
  }
  else {
    getAction(id = actionId, canReturnStub = true, actionRegistrar = actionRegistrar)
  }

  val oldGroups = state.getParentGroupIds(actionId)
  // valid indices >= 0
  val oldIndex = state.getRegistrationIndex(actionId)
  if (oldAction != null) {
    state.putBaseAction(actionId, oldAction)
    val isGroup = oldAction is ActionGroup
    check(isGroup == newAction is ActionGroup) {
      "cannot replace a group with an action and vice versa: $actionId"
    }

    if (oldGroups.isNotEmpty()) {
      for (groupId in oldGroups) {
        val group = getAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
                    ?: throw IllegalStateException("Trying to replace action which has been added to a non-existing group $groupId")
        group.replaceAction(oldAction, newAction)
      }
    }
    unregisterAction(actionId = actionId, removeFromGroups = false, actionRegistrar = actionRegistrar)
  }

  if (!registerAction(actionId = actionId,
                      action = newAction,
                      pluginId = pluginId,
                      projectType = null,
                      actionRegistrar = actionRegistrar,
                      oldIndex = oldIndex,
                      oldGroups = oldGroups.takeIf { it.isNotEmpty() })) {
    return null
  }
  return oldAction
}

@Suppress("RedundantIf")
internal fun registerOrReplaceActionInner(
  element: XmlElement,
  id: String,
  action: AnAction,
  plugin: IdeaPluginDescriptor,
  actionRegistrar: ActionRegistrar,
): Boolean {
  if (actionRegistrar.state.isActionProhibited(id)) {
    return false
  }

  // XML add-to-group links are processed by callers only after this returns true. This keeps failed registrations from leaving
  // stale group children or groupMappings entries.
  return actionRegistrar.state.withLock {
    if (element.attributes.get(OVERRIDES_ATTR_NAME).toBoolean()) {
      val actionOrStub = getAction(id = id, canReturnStub = true, actionRegistrar = actionRegistrar)
      if (actionOrStub == null) {
        actionManagerImplLog.error("'$id' action group in '${plugin.name}' does not override anything")
        return@withLock false
      }
      if (action is ActionGroup && actionOrStub is ActionGroup && action.isPopup != actionOrStub.isPopup) {
        actionManagerImplLog.info("'$id' action group in '${plugin.name}' sets isPopup=$action.isPopup")
      }

      val prev = replaceAction(actionId = id, newAction = action, pluginId = plugin.pluginId, actionRegistrar = actionRegistrar)
      if (prev == null) {
        return@withLock false
      }
      if (action is DefaultActionGroup && prev is DefaultActionGroup) {
        if (element.attributes.get("keep-content").toBoolean()) {
          action.copyFromGroup(prev)
        }
      }
    }
    else {
      if (!registerAction(actionId = id,
                          action = action,
                          pluginId = plugin.pluginId,
                          projectType = element.attributes.get(PROJECT_TYPE)?.let { ProjectType.create(it) },
                          actionRegistrar = actionRegistrar)) {
        return@withLock false
      }
    }
    onActionLoadedFromXml(actionId = id, plugin = plugin)
    true
  }
}

internal fun preInitRegistration(
  idToAction: HashMap<String, AnAction>,
  actionPreInitRegistrar: ActionPreInitRegistrar,
  coroutineScope: CoroutineScope,
): MutableList<ActionConfigurationCustomizer.CustomizeStrategy> {
  val mutator = PreInitActionRuntimeRegistrar(idToAction = idToAction, actionRegistrar = actionPreInitRegistrar)

  val heavyTasks = mutableListOf<ActionConfigurationCustomizer.CustomizeStrategy>()
  ActionConfigurationCustomizer.EP.forEachExtensionSafe { extension ->
    val customizeStrategy = extension.customize()
    if (customizeStrategy is LightCustomizeStrategy) {
      // same thread - mutator is not thread-safe by intention
      // todo use plugin-aware coroutineScope
      coroutineScope.launch(Dispatchers.Unconfined) {
        customizeStrategy.customize(mutator)
      }
    }
    else {
      heavyTasks.add(customizeStrategy)
    }
  }
  return heavyTasks
}
