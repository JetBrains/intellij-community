// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SuspendingUpdateSession
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
interface ActionUpdaterInterceptor {

  /**
   * Marks the fast pass of [runUpdateSessionForInputEvent] in `FrontendActionUpdateInterceptor`
   */
  class FastPass : AbstractCoroutineContextElement(FastPass) {
    companion object : CoroutineContext.Key<FastPass> {
      suspend fun isActive(): Boolean = currentCoroutineContext()[FastPass] != null
    }
  }

  /**
   * Intercepts the update of one action.
   *
   * The session calls it at most once per action, because it caches the updated presentation.
   * [event.presentation][AnActionEvent.getPresentation] is a copy of the factory presentation.
   * It is enabled and visible when [original] starts, and it holds the result when [original] returns.
   *
   * [original] runs [AnAction.update] on the update thread of the action.
   * It only applies the text override when the action does not override `update`.
   *
   * The session caches the presentation only when the result [is performed][AnActionResult.isPerformed].
   * Otherwise the action counts as not updated, and the session uses the initial presentation.
   * Throw [com.intellij.openapi.actionSystem.impl.SkipOperation] to abort the update of this action without an error.
   * In a nested operation, the exception goes to the outer operation.
   */
  suspend fun updateAction(
    action: AnAction,
    event: AnActionEvent,
    original: suspend () -> AnActionResult,
  ): AnActionResult {
    return original()
  }

  /**
   * Intercepts the children request of one group.
   *
   * The session calls it at most once per group, because it caches the children.
   * Each group of an expansion goes through it, the root group and every nested group.
   * [event.presentation][AnActionEvent.getPresentation] is the updated presentation of the group,
   * or the initial presentation when the group is not updated yet.
   *
   * [original] runs [ActionGroup.getChildren] on the update thread of the group and drops a null child.
   *
   * The returned list is the children of the group for the rest of the session.
   * The session updates each child after that through [updateAction],
   * so the list can hold actions that [original] did not return, for example actions of a backend.
   */
  suspend fun getGroupChildren(
    group: ActionGroup,
    event: AnActionEvent,
    original: suspend () -> List<AnAction>
  ): List<AnAction> {
    return original()
  }

  /**
   * Intercepts the expansion of a group into the visible actions of a toolbar, a menu, or a popup.
   *
   * The session calls it for the root group of an expansion,
   * and for a group passed to [SuspendingUpdateSession.expandedChildrenEx].
   * A nested group does not go through it. It goes through [getGroupChildren] and [updateAction].
   *
   * [original] runs the whole expansion: it updates [group], requests the children,
   * flattens a non-popup group, drops an invisible action, and calls [ActionGroup.postProcessVisibleChildren].
   * The result is the visible actions and popup groups in the display order.
   *
   * The interceptor can call [original] more than once.
   * For example, the frontend first expands locally to collect the backend ids,
   * then requests the backend data and expands again.
   * The session keeps the caches between the calls. Drop a stale cache with
   * [com.intellij.openapi.actionSystem.impl.SuspendingUpdateSessionInternal.dropCaches] before the next call.
   */
  suspend fun expandActionGroup(
    group: ActionGroup,
    context: DataContext,
    place: String,
    uiKind: ActionUiKind,
    presentationFactory: PresentationFactory,
    updateSession: SuspendingUpdateSession,
    original: suspend () -> List<AnAction>,
  ): List<AnAction> {
    return original()
  }

  /**
   * Intercepts the update session that chooses the action to perform for an input event.
   *
   * [com.intellij.openapi.actionSystem.impl.Utils.runUpdateSessionForInputEvent] calls it for a keyboard, mouse, or gesture shortcut.
   * The split-mode host calls it for a `rearrange` request of a frontend.
   *
   * [actions] are the candidate actions bound to the input event, in the keymap order.
   * They are not rearranged by the [ActionPromoter] extensions yet.
   *
   * [original] is the rest of the processing. It updates the actions it receives one by one,
   * in the given order, and stops at the first enabled action. Its argument is the list to try:
   * - An empty list means that the interceptor has no opinion.
   *   The caller then rearranges [actions] with [com.intellij.openapi.actionSystem.impl.rearrangeByPromoters].
   * - A non-empty list is used as is. The interceptor must rearrange it with the promoters itself.
   *   The list can contain actions that are absent in [actions], for example actions merged from a backend.
   *
   * The interceptor can call [original] more than once.
   * For example, the frontend first tries a subset of local actions, and then the full list merged with the backend response.
   * In a Rider or CLion remote-dev host process, the frontend interceptor marks the call with the subset with [FastPass].
   * All calls share [updateSession], so a presentation computed in an earlier call is reused.
   * The result of the method is the result that the caller uses. The caller defines what [R] means.
   */
  suspend fun <R> runUpdateSessionForInputEvent(
    actions: List<AnAction>,
    context: DataContext,
    place: String,
    updateSession: SuspendingUpdateSession,
    original: suspend (List<AnAction>) -> R,
  ): R {
    return original(emptyList())
  }

  // TODO drop this notion
  fun treatDefaultActionGroupAsDynamic(): Boolean = false

  companion object {
    val isDefaultImpl: Boolean =
      PluginManagerCore.getPlugin(PluginId.getId("com.intellij.jetbrains.rd.client")) == null &&
      PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId.getId("intellij.platform.frontend.split.baseline", "jetbrains")) == null

    fun treatDefaultActionGroupAsDynamic(): Boolean = when {
      isDefaultImpl -> false
      else -> serviceIfCreated<ActionUpdaterInterceptor>()?.treatDefaultActionGroupAsDynamic() == true
    }

    suspend inline fun expandActionGroup(
      group: ActionGroup,
      context: DataContext,
      place: String,
      uiKind: ActionUiKind,
      presentationFactory: PresentationFactory,
      updateSession: SuspendingUpdateSession,
      noinline original: suspend () -> List<AnAction>,
    ): List<AnAction> = when {
      isDefaultImpl -> original()
      else -> serviceAsync<ActionUpdaterInterceptor>().expandActionGroup(
        group, context, place, uiKind, presentationFactory, updateSession, original)
    }

    suspend inline fun getGroupChildren(
      group: ActionGroup,
      event: AnActionEvent,
      noinline original: suspend () -> List<AnAction>,
    ): List<AnAction> = when {
      isDefaultImpl -> original()
      else -> serviceAsync<ActionUpdaterInterceptor>().getGroupChildren(group, event, original)
    }

    suspend inline fun updateAction(
      action: AnAction,
      event: AnActionEvent,
      noinline original: suspend () -> AnActionResult,
    ): AnActionResult = when {
      isDefaultImpl -> original()
      else -> serviceAsync<ActionUpdaterInterceptor>().updateAction(action, event, original)
    }

    suspend inline fun <R> runUpdateSessionForInputEvent(
      actions: List<AnAction>,
      context: DataContext,
      place: String,
      updateSession: SuspendingUpdateSession,
      noinline original: suspend (List<AnAction>) -> R,
    ): R = when {
      isDefaultImpl -> original(emptyList())
      else -> serviceAsync<ActionUpdaterInterceptor>().runUpdateSessionForInputEvent(
        actions, context, place, updateSession, original)
    }
  }
}
