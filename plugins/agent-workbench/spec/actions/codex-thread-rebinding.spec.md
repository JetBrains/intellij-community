---
name: Codex Thread Rebinding
description: Requirements for Codex app-server preallocated threads and concrete /new editor-tab rebinding.
targets:
  - ../../chat/src/**/*.kt
  - ../../sessions/src/service/*.kt
  - ../../codex/chat/src/**/*.kt
  - ../../codex/chat/testSrc/**/*.kt
  - ../../lib-agent/providers/codex/sessions/src/**/*.kt
  - ../../chat/testSrc/AgentChatEditorServiceTest.kt
  - ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  - ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt
  - ../../sessions/testSrc/PendingThreadRebindTargetResolverTest.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/**/*.kt
---

# Codex Thread Rebinding

Status: Draft
Date: 2026-05-09

## Summary
Codex new-thread launches preallocate a concrete app-server thread before Workbench opens Agent Chat. Workbench opens the tab with `codex:<threadId>` immediately and uses terminal-title rebinding only for already-concrete tabs after exact `/new` or `/fork` terminal commands.

## Requirements
- Codex new-thread opens must prestart an app-server thread, launch the terminal by remote-resuming that thread, and open Agent Chat with the concrete provider identity. Codex must not create normal `codex:new-*` pending tabs or enable pending editor-tab rebinding.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

- Codex launch specs must configure `tui.terminal_title=["thread-id","thread"]` so the terminal title exposes the concrete thread id even after Codex assigns a human thread title. Agent Chat may use this title as an early rebind signal before app-server refresh can reliably read the thread. The `thread-id` title item is supported by stable Codex CLI `0.131.0+` and first appeared in `0.130.0-alpha.8`; stable `0.130.0` and older ignore it as invalid and fall back to the valid `thread` item.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Codex prompt launches with a non-empty stripped prompt retain a single app-server provider-dispatch initial-message step for delivery status tracking. Standard prompts start a plain app-server turn; Plan prompts update collaboration mode before `turn/start`. Prompt bodies are not bootstrapped through terminal startup-command arguments or `/plan` commands.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexPlanPromptRealAppServerIntegrationTest.kt

- Title-based rebind must preserve queued post-start dispatch metadata, but startup-command fallback prompts must not be snapshotted after the startup command has been used.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt

- Codex must not participate in pending-thread refresh retry. Generic pending matching remains available to providers that still open pending new-thread tabs.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

- Rebinding updates tab identity, resume command, stored title/activity fallback, editor-tab presentation, and persisted snapshot without opening a second tab.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Concrete top-level Codex tabs detect exact terminal commands `/new` and `/fork`, store a single anchor timestamp, and run bounded scoped-refresh retries for the tab path until the tab-local title signal rebinds or the anchor expires.
  [@test] ../../codex/chat/testSrc/CodexAgentChatProviderBehaviorTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../chat/testSrc/AgentChatConcreteThreadRebindControllerTest.kt

- Concrete `/new` and `/fork` rebinding is Codex-only and tab-local. Provider refresh must not match concrete anchors to timestamp-bounded refresh candidates; the concrete tab identity changes only when the originating tab exposes a concrete thread id through its terminal title.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Provider refresh may clear stale concrete `/new` anchors, but concrete anchors must not reserve, steal, or prioritize refresh candidates.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Manual `Bind Pending Thread` is available only for pending tabs from providers that support pending editor-tab rebinding; Codex does not support that action for new app-server-preallocated tabs, and the action must not apply to already-concrete `/new` rebinding.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions/testSrc/PendingThreadRebindTargetResolverTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatEditorServiceTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatConcreteThreadRebindControllerTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFileEditorLifecycleTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshCoordinatorTest`
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProviderTest`

## References
- `new-thread.spec.md`
- `../chat/agent-chat-editor.spec.md`
- `../sessions/agent-sessions-codex-rollout-source.spec.md`
