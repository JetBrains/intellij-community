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
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Quick start uses `lastUsedProvider` plus `lastUsedLaunchMode`, falling back to `STANDARD` when needed, and launches directly only when the source project is unambiguous.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Provider menus include Standard entries and YOLO entries only for providers that support the requested launch mode.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Provider availability for synchronous new-thread surfaces is read from the project-level provider availability cache. A project-startup activity prewarms the cache so menus render the launch-time answer on first paint without blocking the EDT; first paint treats unknown providers optimistically until the async refresh publishes the resolved state.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Provider/mode menu items render disabled when their CLI is unavailable. The label is suffixed with the resolved `cliMissingMessageKey` text (e.g. "Junie — Junie CLI not found. Install Junie CLI or add it to your PATH.") so the reason is visible inline, not only as a status-bar tooltip.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Tree-row quick-create overlay falls back from a disabled `lastUsedProvider` to the next available standard provider, and hides the `+` button entirely when no provider is runnable. The main-toolbar new-thread action pins the button to `lastUsedProvider` (no silent substitution) and disables it with the `cliMissingMessageKey` description when that provider's CLI is unavailable.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Tree popup new-thread actions resolve context from tree rows only; editor-tab context uses editor-tab actions.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Editor-tab new-thread is contributed to `EditorTabsToolbarActions` as one split-button entry: primary click quick-launches when the source project and last provider/mode are eligible, and the chevron opens the provider/mode picker. It is visible in dedicated Agent frames and hidden in normal project frames when dedicated-frame mode is enabled.
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

- Launching must go through `AgentSessionLaunchService.createNewSession(...)`, update shared provider preferences on accepted launches for prompt-capable providers, and deduplicate semantically identical in-flight launches with single-flight drop semantics.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Command construction for each provider and launch mode follows `../core/agent-core-contracts.spec.md`.
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
- `../core/agent-core-contracts.spec.md`
- `../sessions/agent-sessions.spec.md`
- `../frame/agent-dedicated-frame.spec.md`
- `codex-thread-rebinding.spec.md`
