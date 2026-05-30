---
name: Codex Thread Rebinding
description: Requirements for Codex pending-thread and concrete /new editor-tab rebinding.
targets:
  - ../../chat/src/**/*.kt
  - ../../sessions/src/service/*.kt
  - ../../codex/sessions/src/**/*.kt
  - ../../chat/testSrc/AgentChatEditorServiceTest.kt
  - ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  - ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt
  - ../../sessions/testSrc/PendingThreadRebindTargetResolverTest.kt
  - ../../codex/sessions/testSrc/**/*.kt
---

# Codex Thread Rebinding

Status: Draft
Date: 2026-05-09

## Summary
Codex starts new threads before the concrete provider thread id is known. Workbench opens a pending chat tab, then rebinds it to a concrete thread when app-server or rollout refresh hints produce an unambiguous match. Existing concrete Codex tabs can also rebind after the user runs exact `/new` in the terminal.

## Requirements
- Codex new-thread opens allocate a pending identity (`codex:new-*`) before a concrete thread id exists and persist pending metadata used for bounded matching.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Codex launch specs must configure `tui.terminal_title=["thread"]` so the terminal title exposes the concrete thread id. Agent Chat may use this title as an early rebind signal before app-server refresh can reliably read the thread.
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Codex plan-mode launches with a non-empty stripped prompt use the normal pending PTY path and enqueue one atomic post-start dispatch step: `/plan <prompt body>`.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRealTuiIntegrationTest.kt

- Title-based rebind must preserve queued post-start dispatch metadata, but startup-command fallback prompts must not be snapshotted after the startup command has been used.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt

- Provider refresh may auto-rebind pending Codex tabs only to newly discovered concrete thread ids for the same normalized path. Matching must be one-to-one, timestamp-bounded, and skip ambiguous candidates.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Rebinding updates tab identity, resume command, stored title/activity fallback, editor-tab presentation, and persisted snapshot without opening a second tab.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Concrete top-level Codex tabs detect exact terminal command `/new`, store a single anchor timestamp, and run bounded scoped-refresh retries for the tab path until the tab rebinds or the anchor expires.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../chat/testSrc/AgentChatConcreteThreadRebindControllerTest.kt

- Concrete `/new` rebinding is Codex-only. It must use bounded refresh-hint candidates for the same normalized path, require an unambiguous target, skip already-open targets, and clear stale anchors.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt
  [@test] ../../codex/sessions/testSrc/backend/rollout/CodexRolloutRefreshHintsProviderTest.kt

- If one candidate could satisfy both a pending tab and an explicit concrete `/new` rebind, the explicit `/new` rebind wins.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Manual `Bind Pending Thread` is available only for pending tabs from providers that support pending editor-tab rebinding; it must not apply to already-concrete `/new` rebinding.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions/testSrc/PendingThreadRebindTargetResolverTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatEditorServiceTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatConcreteThreadRebindControllerTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFileEditorLifecycleTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshCoordinatorTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProviderTest`

## References
- `new-thread.spec.md`
- `../agent-chat-editor.spec.md`
- `../agent-sessions-codex-rollout-source.spec.md`
