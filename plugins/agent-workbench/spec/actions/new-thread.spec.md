---
name: Agent Sessions New-Session Actions
description: Requirements for tree and editor-tab new-thread affordances, provider selection popup parity, and service-level creation flow.
targets:
  - ../../sessions/src/AgentSessionsToolWindow.kt
  - ../../sessions/src/SessionTree.kt
  - ../../sessions/src/AgentSessionsTreePopupActions.kt
  - ../../sessions/src/AgentSessionsService.kt
  - ../../sessions/src/AgentSessionCli.kt
  - ../../chat/src/AgentChatFileEditor.kt
  - ../../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../../codex/sessions/src/CodexAgentSessionProviderBridge.kt
  - ../../codex/sessions/src/backend/CodexSessionBackendSelector.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../chat/testSrc/AgentChatEditorServiceTest.kt
  - ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  - ../../sessions/testSrc/AgentSessionsTreePopupActionsTest.kt
  - ../../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  - ../../sessions/testSrc/AgentSessionsGearActionsTest.kt
  - ../../sessions/testSrc/AgentSessionCliTest.kt
  - ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt
  - ../../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt
---

# Agent Sessions New-Session Actions

Status: Draft
Date: 2026-02-27

## Summary
Define project/worktree `New Thread` behavior across tree and editor-tab actions:
- hover/selection row affordances,
- quick-create with last used provider,
- provider popup with Standard and YOLO entries,
- creation-flow deduplication and Codex pending-to-concrete identity rebinding (strict auto-match + manual bind fallback).

Canonical command mapping is owned by `spec/agent-core-contracts.spec.md`.

## Goals
- Keep new-thread behavior identical for project and worktree rows.
- Keep provider and YOLO mode choices explicit and testable.
- Keep tree and editor-tab new-thread controls consistent in labels, provider order, and mode sections.
- Prevent duplicate creation from repeated clicks.
- Keep Codex pending-thread creation flow compatible with rollout-default discovery.

## Non-goals
- Aggregation/sorting/paging behavior.
- Dedicated-frame policy details beyond routing integration.
- Compose/Jewel row-action components.

## Requirements
- Project/worktree rows must expose new-session affordances only when rows are hovered or selected.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Loading project/worktree rows must suppress new-session affordances.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Quick-provider action (when eligible) must launch standard mode with `lastUsedProvider`.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Quick-provider eligibility must require:
  - a non-null `lastUsedProvider`,
  - bridge support for `STANDARD`.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Provider popup must include Standard entries and YOLO entries (`toolwindow.action.new.session.section.auto`) when launch mode support is available.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Tree popup new-thread action must resolve launch context from tree rows only; editor-tab context must use dedicated editor-tab actions.
  [@test] ../../sessions/testSrc/AgentSessionsTreePopupActionsTest.kt

- Editor-tab new-thread controls must expose two separate actions:
  - quick-provider action that launches `STANDARD` mode for eligible `lastUsedProvider`,
  - popup-only Add action with provider entries and optional YOLO section.
  [@test] ../../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Swing row popup implementation must use IntelliJ action-system popup infrastructure; no Compose popup code is allowed.
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowFactorySwingTest.kt

- Service entry point must be `AgentSessionsService.createNewSession(path, provider, mode, currentProject)`.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- `createNewSession` must deduplicate in-flight actions by normalized `path + provider + mode` using single-flight `DROP` semantics.
  [@test] ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- `createNewSession` must set `lastUsedProvider` to selected provider before opening chat.
  [@test] ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- Command selection for new-thread launches must follow canonical mapping in `spec/agent-core-contracts.spec.md`.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt

- Codex new-thread opens must start in pending identity state (`codex:new-*`) with `sessionId = null`.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Pending Codex tabs must persist pending metadata (`pendingCreatedAtMs`, optional `pendingFirstInputAtMs`, optional `pendingLaunchMode`) so rebind matching can use deterministic time windows.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- App-server backend remains the default discovery source; rollout discovery remains an explicit compatibility override.
  [@test] ../../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Optional app-server mode must surface concrete thread id after first user input.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- Provider refresh must rebind pending Codex chat tabs only to newly discovered concrete thread ids for the path, switch shell command to canonical resume mapping, and skip rebinding when baseline thread ids are not known for that path.
  Matching must use strict path-local one-to-one assignment with timestamp windows; ambiguous candidates must not be rebound automatically.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- When automatic pending-Codex matching is ambiguous or unmatched, users must be able to manually rebind from editor tab actions via `Bind Pending Codex Thread`.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- `Codex (Full Auto)` semantics are defined by command mapping and require no additional warning text in this flow.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt

## User Experience
- Hovering a project/worktree row reveals new-thread controls without opening the project.
- Quick-provider icon enables one-click repeat creation.
- Provider popup keeps normal and YOLO options explicit.
- Editor tabs expose the same quick-provider + Add-popup language as tree new-thread actions.

## Data & Backend
- Codex creation flow starts with pending identity and is resolved asynchronously on provider refresh.
- Concrete identity rebinding updates tab identity and command to resume form.

## Error Handling
- Provider CLI/app-server failures must continue through provider-specific error paths in existing service flow.
- Duplicate clicks for same action tuple must be dropped.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwingNewSessionActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsTreePopupActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionCliTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsLoadingCoordinatorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexAgentSessionProviderBridgeTest'`

## Open Questions / Risks
- Pending-to-concrete binding timing can vary by backend update latency; user feedback for long delay may need dedicated UX later.

## References
- `../agent-core-contracts.spec.md`
- `../agent-sessions.spec.md`
- `../agent-sessions-codex-rollout-source.spec.md`
- `../agent-dedicated-frame.spec.md`
