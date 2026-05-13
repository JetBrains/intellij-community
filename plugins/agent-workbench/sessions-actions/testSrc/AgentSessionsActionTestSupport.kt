// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadTarget
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import org.assertj.core.api.Assertions.assertThat
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

internal const val EDITOR_TAB_POPUP_MENU_ID: String = "EditorTabPopupMenu"
internal const val EDITOR_TAB_POPUP_SEPARATOR_BEFORE_CLOSE_ACTIONS_ID: String =
  "AgentWorkbenchSessions.EditorTabPopup.SeparatorBeforeCloseActions"
internal const val ACTION_SEPARATOR_MARKER: String = "<separator>"

internal fun ActionManager.childActionIds(groupId: String): List<String> {
  val group = getAction(groupId) as? ActionGroup
  assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
  return checkNotNull(group).getChildren(TestActionEvent.createTestEvent()).mapNotNull { getId(it) }
}

internal fun ActionManager.childActionEntries(groupId: String): List<String> {
  val group = getAction(groupId) as? ActionGroup
  assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
  return flattenActionEntries(checkNotNull(group).getChildren(TestActionEvent.createTestEvent()))
}

private fun ActionManager.flattenActionEntries(actions: Array<AnAction>): List<String> {
  return actions.mapNotNull { action ->
    when (action) {
      is Separator -> ACTION_SEPARATOR_MARKER
      else -> getId(action)
    }
  }
}

internal fun ActionManager.editorTabPopupEntries(): List<String> {
  val group = getAction(EDITOR_TAB_POPUP_MENU_ID) as? ActionGroup
  assertThat(group).withFailMessage("Action group '%s' is not registered", EDITOR_TAB_POPUP_MENU_ID).isNotNull
  return flattenEditorTabPopupEntries(checkNotNull(group).getChildren(TestActionEvent.createTestEvent()))
}

private fun ActionManager.flattenEditorTabPopupEntries(actions: Array<AnAction>): List<String> {
  return actions.flatMap { action ->
    when (action) {
      is Separator -> listOf(ACTION_SEPARATOR_MARKER)
      is ActionGroup -> {
        if (getId(action) == EDITOR_TAB_POPUP_SEPARATOR_BEFORE_CLOSE_ACTIONS_ID) {
          flattenEditorTabPopupEntries(action.getChildren(TestActionEvent.createTestEvent()))
        }
        else {
          getId(action)?.let(::listOf).orEmpty()
        }
      }

      else -> getId(action)?.let(::listOf).orEmpty()
    }
  }
}

internal fun List<String>.requiredIndex(entry: String): Int {
  val index = indexOf(entry)
  assertThat(index).withFailMessage("Entry '%s' was not found in: %s", entry, this).isNotEqualTo(-1)
  return index
}

internal fun newThreadContext(
  path: String = "/tmp/project",
  project: Project = ProjectManager.getInstance().defaultProject,
  projectPathCandidates: List<AgentPromptProjectPathCandidate> = emptyList(),
): AgentSessionsEditorTabNewThreadContext {
  val target = if (projectPathCandidates.isEmpty()) {
    AgentSessionsEditorTabNewThreadTarget.Direct(normalizeAgentWorkbenchPath(path))
  }
  else {
    AgentSessionsEditorTabNewThreadTarget.Candidates(projectPathCandidates)
  }
  return AgentSessionsEditorTabNewThreadContext(project = project) { target }
}

internal fun eventWithProject(project: Project): AnActionEvent {
  return AnActionEvent(
    { dataId -> if (dataId == CommonDataKeys.PROJECT.name) project else null },
    Presentation(),
    "",
    ActionUiKind.NONE,
    null,
    0,
    ActionManager.getInstance(),
  )
}

internal fun sourceProjectProxy(basePath: String = "/work/repo-a"): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getName" -> "Source Project"
      "getBasePath" -> basePath
      "isOpen" -> true
      "isDisposed" -> false
      "toString" -> "MockProject(Source Project)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> null
    }
  }
  return Proxy.newProxyInstance(
    ProjectManager::class.java.classLoader,
    arrayOf(Project::class.java),
    handler,
  ) as Project
}

internal fun projectCandidate(path: String, displayName: String): AgentPromptProjectPathCandidate {
  return AgentPromptProjectPathCandidate(path = normalizeAgentWorkbenchPath(path), displayName = displayName)
}
