// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SuspendingUpdateSession
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

interface ActionUpdaterInterceptor {

  fun allowsFastUpdate(project: Project?, group: ActionGroup, place: String): Boolean = true

  suspend fun updateAction(action: AnAction,
                           event: AnActionEvent,
                           original: suspend () -> Boolean): Boolean =
    original()

  suspend fun getGroupChildren(group: ActionGroup,
                               event: AnActionEvent,
                               original: suspend () -> Array<AnAction>): Array<AnAction> =
    original()

  suspend fun expandActionGroup(presentationFactory: PresentationFactory,
                                context: DataContext,
                                place: String,
                                group: ActionGroup,
                                isToolbarAction: Boolean,
                                updateSession: SuspendingUpdateSession,
                                original: suspend () -> List<AnAction>): List<AnAction> =
    original()

  suspend fun <R> runUpdateSessionForInputEvent(actions: List<AnAction>,
                                                place: String,
                                                context: DataContext,
                                                updateSession: SuspendingUpdateSession,
                                                original: suspend (List<AnAction>) -> R): R =
    original(emptyList())

  fun treatDefaultActionGroupAsDynamic(): Boolean = false

  companion object {
    val isDefaultImpl = PluginManagerCore.getPlugin(PluginId.getId("com.intellij.jetbrains.rd.client")) == null

    fun treatDefaultActionGroupAsDynamic(): Boolean =
      if (isDefaultImpl) false
      else serviceIfCreated<ActionUpdaterInterceptor>()?.treatDefaultActionGroupAsDynamic() == true

    suspend inline fun expandActionGroup(presentationFactory: PresentationFactory,
                                         context: DataContext,
                                         place: String,
                                         group: ActionGroup,
                                         isToolbarAction: Boolean,
                                         updateSession: SuspendingUpdateSession,
                                         noinline original: suspend () -> List<AnAction>): List<AnAction> =
      if (isDefaultImpl) original()
      else serviceAsync<ActionUpdaterInterceptor>().expandActionGroup(
        presentationFactory, context, place, group, isToolbarAction, updateSession, original)

    suspend inline fun getGroupChildren(group: ActionGroup,
                                        event: AnActionEvent,
                                        noinline original: suspend () -> Array<AnAction>): Array<AnAction> =
      if (isDefaultImpl) original()
      else serviceAsync<ActionUpdaterInterceptor>().getGroupChildren(group, event, original)

    suspend inline fun updateAction(action: AnAction,
                                    event: AnActionEvent,
                                    noinline original: suspend () -> Boolean): Boolean =
      if (isDefaultImpl) original()
      else serviceAsync<ActionUpdaterInterceptor>().updateAction(action, event, original)

    suspend inline fun <R> runUpdateSessionForInputEvent(actions: List<AnAction>,
                                                         place: String,
                                                         context: DataContext,
                                                         updateSession: SuspendingUpdateSession,
                                                         noinline original: suspend (List<AnAction>) -> R): R =
      if (isDefaultImpl) original(emptyList())
      else serviceAsync<ActionUpdaterInterceptor>().runUpdateSessionForInputEvent(
        actions, place, context, updateSession, original)
  }
}
