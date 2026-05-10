---
name: Agent Sessions New-Thread Actions
description: Requirements for new-thread affordances in the Sessions tree, editor tabs, and main toolbar.
targets:
  - ../../sessions-toolwindow/src/**/*.kt
  - ../../sessions-actions/src/**/*.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../sessions/src/service/AgentSessionProjectCatalog.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../sessions-actions/resources/intellij.agent.workbench.sessions.actions.xml
  - ../../sessions-toolwindow/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  - ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  - ../../sessions-actions/testSrc/*.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
---

# Agent Sessions New-Thread Actions

Status: Draft
Date: 2026-05-09

## Summary
New-thread actions let users start provider-backed threads from project/worktree rows, editor tabs, and the main toolbar. This spec owns action availability, provider/mode menus, target resolution, and launch deduplication. Codex pending/concrete rebind behavior is specified separately.

## Requirements
- Project/worktree rows expose new-thread controls only while hovered or selected, and suppress them while the row is loading.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Quick start uses `lastUsedProvider` plus `lastUsedLaunchMode`, falling back to `STANDARD` when needed, and launches directly only when the source project is unambiguous.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Provider menus include Standard entries and YOLO entries only for providers that support the requested launch mode.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Tree popup new-thread actions resolve context from tree rows only; editor-tab context uses editor-tab actions.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Editor-tab new-thread actions are contributed to `EditorTabsToolbarActions` as quick-start and Add-popup entries. They are visible in dedicated Agent frames and hidden in normal project frames when dedicated-frame mode is enabled.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Dedicated Agent frame new-thread actions resolve source projects lazily on click or popup expansion. Multiple source candidates require explicit selection; a single candidate may be used directly; selected chat-tab source path is a fallback when no open source-project candidate exists.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Source-project labels in dedicated-frame popups reuse Sessions tree naming and fall back to full normalized paths for collisions.
  [@test] ../../sessions/testSrc/AgentSessionProjectCatalogTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Main-toolbar new-thread is one split-button action on `MainToolbarRight`, after `NewUiRunWidget`. Icon click quick-launches only for a direct eligible target; otherwise it opens the same provider/mode picker as the chevron.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Main-toolbar target resolution prefers chat context path, selected chat source project path, then `project.basePath`; in dedicated Agent frames it uses the same lazy source-candidate path as editor-tab actions.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Launching must go through `AgentSessionLaunchService.createNewSession(...)`, update shared provider preferences on accepted launches, and deduplicate semantically identical in-flight launches with single-flight drop semantics.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Command construction for each provider and launch mode follows `spec/agent-core-contracts.spec.md`.
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt

## User Experience
- Quick actions repeat the last successful provider/mode when that choice is still valid.
- Provider pickers keep Standard and YOLO choices explicit.
- Dedicated-frame source selection appears only when the user invokes the action, not during toolbar update.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsSwingNewSessionActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreePopupActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsMainToolbarNewThreadActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`

## References
- `../agent-core-contracts.spec.md`
- `../agent-sessions.spec.md`
- `../agent-dedicated-frame.spec.md`
- `codex-thread-rebinding.spec.md`
