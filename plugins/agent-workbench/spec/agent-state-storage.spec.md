---
name: Agent Workbench State Storage
description: Inventory of Agent Workbench persistent state components and storage scopes.
targets:
  - ../chat/src/AgentChatTabsStateService.kt
  - ../sessions/src/state/*.kt
  - ../prompt/ui/src/AgentPromptUiSessionStateService.kt
  - ../chat/testSrc/AgentChatFileEditorProviderTest.kt
  - ../sessions/testSrc/AgentSession*StateServiceTest.kt
  - ../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt
---

# Agent Workbench State Storage

Status: Draft
Date: 2026-05-09

## Summary
Agent Workbench state is intentionally split by lifetime and scope. Cache-backed state restores open chat/session UI after restart, non-roamable app state stores shared preferences, and project workspace state stores the prompt draft only.

## Requirements
- The persisted `@State` components are exactly:
  - `AgentChatTabsState`, app-level cache file, keyed by chat `tabKey`.
  - `AgentSessionWarmState`, app-level cache file, for warm-start session rows for open paths.
  - `AgentSessionTreeUiState`, app-level cache file, for collapsed project paths.
  - `AgentSessionUiPreferencesState`, app-level non-roamable file, for shared provider/mode preferences and Claude quota hint state.
  - `AgentPromptUiState`, project-level workspace file, for prompt draft fields.

- Warm session state must not persist blocking errors, provider warnings, loading flags, or pending `new-*` identities.
  [@test] ../sessions/testSrc/AgentSessionWarmStateServiceTest.kt

- Prompt context restore snapshots are runtime-only and must not be serialized into `AgentPromptUiState`.
  [@test] ../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- State payloads used by `SerializablePersistentStateComponent` must be Kotlin-serializable.
  [@test] ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt
  [@test] ../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test "com.intellij.agent.workbench.sessions.AgentSession*StateServiceTest"`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFileEditorProviderTest`

## References
- `spec/agent-chat-editor.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/actions/global-prompt-entry.spec.md`
