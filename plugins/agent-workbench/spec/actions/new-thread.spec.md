---
name: Agent Sessions New-Session Actions
description: Requirements for tree and editor-tab new-thread affordances, provider selection popup parity, and service-level creation flow.
targets:
  - ../../sessions/src/AgentSessionsToolWindow.kt
  - ../../sessions/src/SessionTree.kt
  - ../../sessions/src/AgentSessionsTreePopupActions.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../sessions/src/AgentSessionCli.kt
  - ../../chat/src/AgentChatFileEditor.kt
  - ../../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../../codex/sessions/src/CodexAgentSessionProviderBridge.kt
  - ../../codex/sessions/src/backend/CodexSessionBackendSelector.kt
  - ../../codex/sessions/src/backend/rollout/CodexRolloutRefreshHintsProvider.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../chat/testSrc/AgentChatEditorServiceTest.kt
  - ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  - ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  - ../../sessions/testSrc/AgentSessionsTreePopupActionsTest.kt
  - ../../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  - ../../sessions/testSrc/AgentSessionsGearActionsTest.kt
  - ../../sessions/testSrc/AgentSessionCliTest.kt
  - ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt
  - ../../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt
  - ../../codex/sessions/testSrc/backend/rollout/CodexRolloutRefreshHintsProviderTest.kt
---

# Agent Sessions New-Session Actions

Status: Draft
Date: 2026-03-09

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
- Keep Codex pending-thread creation flow compatible with app-server discovery and rollout refresh-hints fallback.

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
- Quick-provider action and provider-popup entries must follow the global dedicated-frame routing policy; they do not force the clicked source frame.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

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

- Service entry point must be `AgentSessionLaunchService.createNewSession(path, provider, mode, currentProject)`.
  [@test] ../../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- `createNewSession` must deduplicate in-flight actions by normalized `path + provider + mode` using single-flight `DROP` semantics.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- `createNewSession` must set `lastUsedProvider` to selected provider before opening chat.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Command selection for new-thread launches must follow canonical mapping in `spec/agent-core-contracts.spec.md`.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt

- Codex new-thread opens must start in pending identity state (`codex:new-*`) with `sessionId = null`.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Pending Codex tabs must persist pending metadata (`pendingCreatedAtMs`, optional `pendingFirstInputAtMs`, optional `pendingLaunchMode`).
  - Note: rebind matching uses these timestamps for deterministic time windows.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Already-concrete top-level Codex tabs must treat exact terminal command `/new` as a request to migrate the open tab to the next concrete thread created for the same path.
- The open concrete tab must persist `/new` anchor metadata (`newThreadRebindRequestedAtMs`) so refresh can rebind by `tabKey + currentThreadIdentity + request timestamp`.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- App-server backend remains the only Codex discovery source for listing; backend override values are ignored and rollout stays refresh-hints-only fallback.
  [@test] ../../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Optional app-server mode must surface concrete thread id after first user input.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- Provider refresh must rebind pending Codex chat tabs only to newly discovered concrete thread ids for the path and switch shell command to canonical resume mapping.
- Rebinding must skip when baseline thread ids are not known for that path, except for recent create-flow pending tabs that include `pendingCreatedAtMs` and `pendingLaunchMode` metadata (max age: 120s).
- Matching must use strict path-local one-to-one assignment with timestamp windows; ambiguous candidates must not be rebound automatically.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Refresh may also rebind an already-concrete top-level Codex tab after exact terminal `/new`, but only from rollout/app-server refresh hints for the same normalized path, never from arbitrary listed rows.
- Concrete `/new` rebinding must consider only top-level CLI thread candidates, use a bounded timestamp window around the `/new` request, clear stale anchors after 30 seconds, validate the stored `/new` anchor timestamp before applying, and skip rebinding if the candidate target is already open.
- When the same candidate could satisfy both a pending Codex tab and an explicit concrete `/new` rebind, the explicit `/new` rebind wins and the pending tab remains pending.
- Concrete `/new` rebinding must require an unambiguous one-to-one match; if multiple candidates fall in the window, the tab remains anchored and is not rebound automatically.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt
  [@test] ../../codex/sessions/testSrc/backend/rollout/CodexRolloutRefreshHintsProviderTest.kt

- When automatic pending-Codex matching is ambiguous or unmatched, users must be able to manually rebind from editor tab actions via `Bind Pending Codex Thread`.
- Manual bind remains pending-tab-only and must not repurpose the editor-tab action for already-concrete `/new` rebinding.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- `Codex (Full Auto)` semantics are defined by command mapping and require no additional warning text in this flow.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt

## User Experience
- Hovering a project/worktree row reveals new-thread controls without opening the project.
- Quick-provider icon enables one-click repeat creation.
- Provider popup keeps normal and YOLO options explicit.
- Editor tabs expose the same quick-provider + Add-popup language as tree new-thread actions.

## Data & Backend
- Codex creation flow starts with pending identity and is resolved asynchronously from app-server listing plus refresh hints.
- Concrete identity rebinding updates tab identity and command to resume form.
- Exact terminal `/new` on an already-concrete top-level Codex tab keeps the same editor tab open while migrating that tab to the newly created concrete thread when a matching refresh-hint candidate appears.

## Error Handling
- Provider CLI/app-server failures must continue through provider-specific error paths in existing service flow.
- Duplicate clicks for same action tuple must be dropped.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwingNewSessionActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsTreePopupActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionCliTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionRefreshCoordinatorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexAgentSessionProviderBridgeTest'`

## Open Questions / Risks
- Pending-to-concrete binding timing can vary by backend update latency; user feedback for long delay may need dedicated UX later.

## References
- `../agent-core-contracts.spec.md`
- `../agent-sessions.spec.md`
- `../agent-sessions-codex-rollout-source.spec.md`
- `../agent-dedicated-frame.spec.md`
