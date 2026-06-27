---
name: Agent Sessions New-Thread Actions
description: Requirements for new-thread affordances in the Sessions tree and main toolbar.
targets:
  - ../../sessions-toolwindow/src/**/*.kt
  - ../../sessions-actions/src/**/*.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../chat/src/AgentChatDeferredStartContent.kt
  - ../../chat/src/AgentChatFileEditor.kt
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
New-thread actions let users start provider-backed threads from project/worktree rows and the main toolbar. This spec owns action availability, launch-profile menus, target resolution, inline prompt handoff, and launch deduplication. Codex pending/concrete rebind behavior is specified separately.

## Requirements
- Project/worktree rows expose new-thread controls only while hovered or selected, suppress them while the row is loading, and hide tree
  new-thread controls entirely in effective current-project-only scope.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Quick start uses `lastUsedProvider` plus `lastUsedLaunchMode`, falling back to `STANDARD` when needed, and launches directly only when the source project is unambiguous.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Provider menus include Standard entries and YOLO entries only for providers that support the requested launch mode.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Provider availability for synchronous new-thread surfaces is read from the project-level provider availability cache. A project-startup activity prewarms the cache so menus render the launch-time answer on first paint without blocking the EDT; first paint treats unknown providers optimistically until the async refresh publishes the resolved state.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Provider/mode menu items render disabled when their CLI is unavailable. The label is suffixed with the resolved `cliMissingMessageKey` text (e.g. "Junie — Junie CLI not found. Install Junie CLI or add it to your PATH.") so the reason is visible inline, not only as a status-bar tooltip.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Tree-row quick-create overlay falls back from a disabled `lastUsedProvider` to the next available standard provider, and hides the `+` button entirely when no provider is runnable. The main-toolbar new-thread action pins the button to `lastUsedProvider` (no silent substitution) and disables it with the `cliMissingMessageKey` description when that provider's CLI is unavailable.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Tree popup new-thread actions resolve context from tree rows only and are hidden in effective current-project-only scope; the main
  toolbar action is the supported new-thread entry point for the single-project UI.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Dedicated Agent frame main-toolbar new-thread resolves source projects lazily on click or popup expansion. Multiple source candidates require explicit selection; a single candidate may be used directly; selected chat-tab source path is a fallback when no open source-project candidate exists.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Source-project labels in dedicated-frame new-thread popups reuse Sessions tree naming and fall back to full normalized paths for collisions.
  [@test] ../../sessions/testSrc/AgentSessionProjectCatalogTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Main-toolbar new-thread is one split-button action on `MainToolbarRight`, after `NewUiRunWidget`. Icon click quick-launches only for a direct eligible target; otherwise it opens the same launch-profile picker as the chevron.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Main-toolbar target resolution prefers chat context path, selected chat source project path, then `project.basePath`; in dedicated Agent frames it uses the lazy source-candidate path.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Launching must go through `AgentSessionLaunchService.createNewSession(...)`, update persisted provider-option state on accepted prompt-capable launches, and deduplicate semantically identical in-flight launches with single-flight drop semantics.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Main-toolbar new-thread opens a deferred chat tab with an inline prompt when `agent.workbench.new.thread.inline.prompt` is enabled and the selected provider supports prompt launch. The inline prompt is seeded with the selected launch profile, submits through `AgentDeferredNewSessionHandle.launch(...)`, and completes the same pending chat tab instead of opening a separate TUI tab first.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSessionControllerTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Generic new-thread deferred tabs show provider-neutral centered progress copy. The spinner appears only after a short delay so quick launches do not flash progress chrome.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Inline new-thread routing falls back to direct `createNewSession(...)` when the registry key is disabled, the provider descriptor is missing, the provider does not support prompt launch, deferred chat opening fails, or inline prompt installation fails.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- Command construction for each provider and launch mode follows `../core/agent-core-contracts.spec.md`.
  [@test] ../../lib-agent/providers/claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt

## User Experience
- Quick actions use the active launch profile when that profile is still launchable, otherwise they fall back to the first launchable built-in/user profile.
- Launch-profile pickers keep Standard and YOLO choices explicit.
- Registry-gated inline new-thread uses the chat tab itself as the prompt surface; failed submit keeps that prompt visible for correction or retry.
- Dedicated-frame source selection appears only when the user invokes the action, not during toolbar update.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsSwingNewSessionActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreePopupActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsMainToolbarNewThreadActionsTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFileEditorLifecycleTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteSessionControllerTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLaunchServiceTest`

## References
- `../core/agent-core-contracts.spec.md`
- `../sessions/agent-sessions.spec.md`
- `../frame/agent-dedicated-frame.spec.md`
- `codex-thread-rebinding.spec.md`
