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

- Codex launch specs must configure `tui.terminal_title=["thread-id","thread"]` so the terminal title exposes the concrete thread id even after Codex assigns a human thread title. Agent Chat may use this title as an early rebind signal before app-server refresh can reliably read the thread. The `thread-id` title item is supported by stable Codex CLI `0.131.0+` and first appeared in `0.130.0-alpha.8`; stable `0.130.0` and older ignore it as invalid and fall back to the valid `thread` item.
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Codex plan-mode launches with a non-empty stripped prompt use the normal pending PTY path and enqueue ordered post-start dispatch: first ensure the TUI is visibly in Plan mode via BackTab, then submit the plain prompt body. If Plan mode cannot be confirmed, the plain prompt body must not be submitted.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt
  [@test] ../../chat/testSrc/AgentChatInitialMessageDispatcherTest.kt
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRealTuiIntegrationTest.kt

- Title-based rebind must preserve queued post-start dispatch metadata, but startup-command fallback prompts must not be snapshotted after the startup command has been used.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt

- Provider refresh may auto-rebind pending Codex tabs only to newly discovered concrete thread ids for the same normalized path. Matching must be one-to-one, timestamp-bounded, and skip ambiguous candidates.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Rebinding updates tab identity, resume command, stored title/activity fallback, editor-tab presentation, and persisted snapshot without opening a second tab.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Concrete top-level Codex tabs detect exact terminal command `/new`, store a single anchor timestamp, and run bounded scoped-refresh retries for the tab path until the tab-local title signal rebinds or the anchor expires.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../chat/testSrc/AgentChatConcreteThreadRebindControllerTest.kt

- Concrete `/new` rebinding is Codex-only and tab-local. Provider refresh must not match concrete `/new` anchors to timestamp-bounded refresh candidates; the concrete tab identity changes only when the originating tab exposes a concrete thread id through its terminal title.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Provider refresh may clear stale concrete `/new` anchors, but concrete anchors must not reserve, steal, or prioritize candidates over pending new-thread tab rebinding.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
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
- `../chat/agent-chat-editor.spec.md`
- `../sessions/agent-sessions-codex-rollout-source.spec.md`
