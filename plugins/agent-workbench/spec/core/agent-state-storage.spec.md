---
name: Agent Workbench State Storage
description: Inventory of Agent Workbench persistent state components and storage scopes.
targets:
  - ../../thread-view/src/AgentThreadViewFileEditorState.kt
  - ../../thread-view/src/AgentThreadViewTabsStateService.kt
  - ../../lib-agent/providers/terminal/sessions/src/TerminalSessionStateService.kt
  - ../../sessions/src/state/*.kt
  - ../../prompt/ui/src/AgentPromptUiSessionStateService.kt
  - ../../thread-view/testSrc/AgentThreadViewFileEditorProviderTest.kt
  - ../../lib-agent/providers/terminal/sessions/testSrc/TerminalSessionSourceTest.kt
  - ../../sessions/testSrc/AgentSession*StateServiceTest.kt
  - ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt
---

# Agent Workbench State Storage

Status: Draft
Date: 2026-06-15

## Summary

Agent Workbench state is intentionally split by lifetime and scope. Editor-provider state restores open Thread Views after restart,
cache-backed state restores session UI and legacy thread view metadata, roamable app state stores launch profiles, non-roamable app state stores
machine-local preferences, and project workspace state stores the prompt draft only.

## Requirements

- The persisted storage locations are exactly:
  - Agent Thread View editor-provider state, stored by the IntelliJ file editor framework for open `agent-thread-view://2/<tabKey>` tabs.
  - `AgentThreadViewTabsState`, app-level cache file, keyed by thread view `tabKey`, retained for legacy restore migration and cleanup only.
  - `AgentSessionWarmState`, app-level cache file, for warm-start session rows for open paths.
  - `AgentSessionTreeUiState`, app-level cache file, for collapsed project paths.
  - `AgentSessionLaunchProfileStateV2`, app-level roaming file, for user launch profiles, the explicit default profile id, and the separate VCS merge default profile id.
  - `AgentSessionUiPreferencesState`, app-level non-roamable file, for machine-local provider/mode preferences and Claude quota hint state.
  - `AgentWorkbenchTerminalSessions`, app-level non-roamable file, for user-created terminal session rows and bounded terminal restore
    context.
  - `AgentPromptUiState`, project-level workspace file, for prompt draft fields.

- Warm session state must not persist blocking errors, provider warnings, loading flags, or pending `new-*` identities.
  [@test] ../../sessions/testSrc/AgentSessionWarmStateServiceTest.kt

- Agent Thread View persisted tab state must persist tab identity, UI/runtime restore metadata including concrete-tab resume launch mode, and
  command-free startup intent for pending new-session tabs, but must not persist shell command or environment variables. The provider
  variant and stored launch mode are the canonical sources for resume and new-session command construction.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorProviderTest.kt

- Prompt context restore snapshots are runtime-only and must not be serialized into `AgentPromptUiState`.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- State payloads used by `SerializablePersistentStateComponent` must be Kotlin-serializable.
  [@test] ../../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

## Testing / Local Run

-
`./tests.cmd --module intellij.agent.workbench.sessions.tests --test "com.intellij.agent.workbench.sessions.AgentSession*StateServiceTest"`
- `./tests.cmd --module intellij.platform.ai.agent.terminal.sessions.tests --test "com.intellij.platform.ai.agent.terminal.sessions.*Test"`
-
`./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest`
- `./tests.cmd --module intellij.agent.workbench.thread.view.tests --test com.intellij.agent.workbench.thread.view.AgentThreadViewFileEditorProviderTest`

## References

- `../thread-view/agent-thread-view.spec.md`
- `../sessions/agent-sessions.spec.md`
- `../sessions/agent-terminal-sessions.spec.md`
- `../actions/global-prompt-entry.spec.md`
