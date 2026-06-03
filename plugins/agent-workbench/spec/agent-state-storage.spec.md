---
name: Agent Workbench State Storage
description: Inventory of Agent Workbench persistent state components and storage scopes.
targets:
  - ../chat/src/AgentChatFileEditorState.kt
  - ../chat/src/AgentChatTabsStateService.kt
  - ../terminal/sessions/src/TerminalSessionStateService.kt
  - ../sessions/src/state/*.kt
  - ../prompt/ui/src/AgentPromptUiSessionStateService.kt
  - ../chat/testSrc/AgentChatFileEditorProviderTest.kt
  - ../terminal/sessions/testSrc/TerminalSessionSourceTest.kt
  - ../sessions/testSrc/AgentSession*StateServiceTest.kt
  - ../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt
---

# Agent Workbench State Storage

Status: Draft
Date: 2026-05-09

## Summary
Agent Workbench state is intentionally split by lifetime and scope. Editor-provider state restores open chat tabs after restart, cache-backed state restores session UI and legacy chat metadata, non-roamable app state stores shared preferences and user-provided thread title overrides, and project workspace state stores the prompt draft only.

## Requirements
- The persisted storage locations are exactly:
  - Agent Chat editor-provider state, stored by the IntelliJ file editor framework for open `agent-chat://2/<tabKey>` tabs.
  - `AgentChatTabsState`, app-level cache file, keyed by chat `tabKey`, retained for legacy restore migration and cleanup only.
  - `AgentSessionWarmState`, app-level cache file, for warm-start session rows for open paths.
  - `AgentSessionTreeUiState`, app-level cache file, for collapsed project paths.
  - `AgentSessionUiPreferencesState`, app-level non-roamable file, for shared provider/mode preferences and Claude quota hint state.
  - `AgentSessionThreadTitleOverrideState`, app-level non-roamable file, for user-provided thread title overrides keyed by normalized path, provider, and thread id.
  - `AgentWorkbenchTerminalSessions`, app-level non-roamable file, for user-created terminal session rows and bounded terminal restore context.
  - `AgentPromptUiState`, project-level workspace file, for prompt draft fields.

- Warm session state must not persist blocking errors, provider warnings, loading flags, or pending `new-*` identities.
  [@test] ../sessions/testSrc/AgentSessionWarmStateServiceTest.kt

- Agent Chat persisted tab state must persist tab identity, UI/runtime restore metadata including concrete-tab resume launch mode, and command-free startup intent for pending new-session tabs, but must not persist shell command or environment variables. The provider variant and stored launch mode are the canonical sources for resume and new-session command construction.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Prompt context restore snapshots are runtime-only and must not be serialized into `AgentPromptUiState`.
  [@test] ../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- State payloads used by `SerializablePersistentStateComponent` must be Kotlin-serializable.
  [@test] ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionThreadTitleOverrideStateServiceTest.kt
  [@test] ../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test "com.intellij.agent.workbench.sessions.AgentSession*StateServiceTest"`
- `./tests.cmd --module intellij.agent.workbench.terminal.sessions.tests --test "com.intellij.agent.workbench.terminal.sessions.*Test"`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFileEditorProviderTest`

## References
- `spec/agent-chat-editor.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/agent-terminal-sessions.spec.md`
- `spec/actions/global-prompt-entry.spec.md`
