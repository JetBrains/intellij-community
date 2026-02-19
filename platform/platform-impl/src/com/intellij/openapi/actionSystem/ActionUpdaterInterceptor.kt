// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SuspendingUpdateSession
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ActionUpdaterInterceptor {

  suspend fun updateAction(
    action: AnAction,
    event: AnActionEvent,
    original: suspend () -> AnActionResult,
  ): AnActionResult {
    return original()
  }

  suspend fun getGroupChildren(
    group: ActionGroup,
    event: AnActionEvent,
    original: suspend () -> List<AnAction>
  ): List<AnAction> {
    return original()
  }

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
    val isDefaultImpl: Boolean = PluginManagerCore.getPlugin(
      PluginId.getId("com.intellij.jetbrains.rd.client")) == null

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
