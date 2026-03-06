---
name: Agent Workbench State Storage
description: Inventory and persistence requirements for all Agent Workbench @State components and their storage locations.
targets:
  - ../chat/src/AgentChatTabsStateService.kt
  - ../sessions/src/state/AgentSessionTreeUiStateService.kt
  - ../sessions/src/state/AgentSessionUiPreferencesStateService.kt
  - ../prompt/src/ui/AgentPromptUiSessionStateService.kt
  - ../chat/testSrc/AgentChatFileEditorProviderTest.kt
  - ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  - ../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt
  - ../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt
---

# Agent Workbench State Storage

Status: Draft
Date: 2026-03-06

## Summary
Define the canonical list of Agent Workbench persistent state components and their storage details.

This file is the single inventory for where Agent Workbench state is persisted and what data is intentionally runtime-only.

## Goals
- Keep all Agent Workbench `@State` components documented in one place.
- Prevent accidental storage changes or persistence-scope drift during refactors.
- Keep runtime-only state explicitly documented.

## Non-goals
- Defining UI behavior or backend protocols.
- Replacing feature-level specs for chat/sessions/prompt behavior.

## Requirements
- Agent Workbench must keep exactly these `@State` components:
  - `AgentChatTabsState` (`Service.Level.APP`, `StoragePathMacros.CACHE_FILE`).
  - `AgentSessionTreeUiState` (`Service.Level.APP`, `StoragePathMacros.CACHE_FILE`).
  - `AgentSessionUiPreferencesState` (`Service.Level.APP`, `StoragePathMacros.CACHE_FILE`).
  - `AgentPromptUiState` (`Service.Level.PROJECT`, `StoragePathMacros.PRODUCT_WORKSPACE_FILE`).

- `AgentChatTabsState` persistence must remain cache-scoped app state and must store tab snapshots keyed by `tabKey`.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- `AgentSessionTreeUiState` persistence must remain cache-scoped app state and must store only tree UI preferences (collapsed paths, visible counts, thread previews).
  [@test] ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt

- `AgentSessionUiPreferencesState` persistence must remain cache-scoped app state and must store shared UI preferences (`lastUsedProvider`, Claude quota hint eligibility/acknowledgement).
  [@test] ../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt

- `AgentPromptUiState` persistence must remain workspace-scoped project state and must store only prompt draft fields.
  [@test] ../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt

- Prompt context restore snapshot (`contextRestoreSnapshot`) is runtime-only session state and must not be part of persisted `AgentPromptUiState`.
  [@test] ../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt

- State payload classes used by `SerializablePersistentStateComponent` in Agent Workbench must be Kotlin-serializable (`@Serializable`).
  [@test] ../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt

## State Inventory
- `AgentChatTabsStateService`
  - Component name: `AgentChatTabsState`
  - Service level: `APP`
  - Storage: `StoragePathMacros.CACHE_FILE`
  - Persisted root state type: `AgentChatTabsState`

- `AgentSessionTreeUiStateService`
  - Component name: `AgentSessionTreeUiState`
  - Service level: `APP`
  - Storage: `StoragePathMacros.CACHE_FILE`
  - Persisted root state type: `SessionTreeUiStateState`

- `AgentSessionUiPreferencesStateService`
  - Component name: `AgentSessionUiPreferencesState`
  - Service level: `APP`
  - Storage: `StoragePathMacros.CACHE_FILE`
  - Persisted root state type: `UiPreferencesState`

- `AgentPromptUiSessionStateService`
  - Component name: `AgentPromptUiState`
  - Service level: `PROJECT`
  - Storage: `StoragePathMacros.PRODUCT_WORKSPACE_FILE`
  - Persisted root state type: `AgentPromptUiState`
  - Runtime-only non-persisted field: `contextRestoreSnapshot`

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionTreeUiStateServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionUiPreferencesStateServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatFileEditorProviderTest'`

## Open Questions / Risks
- If additional Agent Workbench `@State` components are added, this inventory must be updated in the same change.

## References
- `spec/actions/global-prompt-entry.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/agent-sessions.spec.md`
