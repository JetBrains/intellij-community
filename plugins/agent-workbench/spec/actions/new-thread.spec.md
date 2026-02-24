---
name: Agent Sessions New-Session Actions
description: Requirements for project/worktree new-thread actions, provider selection UX, and service-level creation flow.
targets:
  - ../../sessions/src/SessionTreeNewSessionActions.kt
  - ../../sessions/src/SessionTreeRows.kt
  - ../../sessions/src/AgentSessionsToolWindow.kt
  - ../../sessions/src/AgentSessionsService.kt
  - ../../sessions/src/AgentSessionCli.kt
  - ../../codex/sessions/src/CodexAgentSessionProviderBridge.kt
  - ../../codex/sessions/src/backend/appserver/SharedCodexAppServerService.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../chat/testSrc/AgentChatEditorServiceTest.kt
  - ../../sessions/testSrc/AgentSessionsToolWindowTest.kt
  - ../../sessions/testSrc/AgentSessionCliTest.kt
  - ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt
  - ../../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt
---

# Agent Sessions New-Session Actions

Status: Draft
Date: 2026-02-22

## Summary
Define project/worktree `New Thread` actions (quick provider icon and provider menu), creation-flow deduplication, and pending-to-concrete Codex identity rebinding. Canonical command mapping is owned by `spec/agent-core-contracts.spec.md`.

## Goals
- Keep new-thread behavior identical for project and worktree rows.
- Keep provider and YOLO mode choices explicit and testable.
- Prevent duplicate creation from repeated clicks.
- Keep Codex pending-thread creation flow compatible with rollout default listing.

## Non-goals
- Aggregation/sorting/paging behavior.
- Dedicated-frame policy details beyond routing integration.
- Additional warning copy for `Codex (Full Auto)`.

## Requirements
- Project/worktree row hover actions must route through `onCreateSession(path, provider, yolo)` only; separate create-thread callback paths are forbidden.
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Quick-provider icon (when `lastUsedProvider` exists) must create a new thread with `yolo=false`.
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Provider popup must expose exactly four entries:
  - `Claude`
  - `Codex`
  - `Claude YOLO`
  - `Codex (Full Auto)`
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Popup YOLO section label must remain `YOLO`.
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Service entry point must be `AgentSessionsService.createNewSession(path, provider, yolo, currentProject)`.
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowTest.kt

- `createNewSession` must deduplicate in-flight actions by normalized `path + provider + yolo` using single-flight `DROP` semantics.
  [@test] ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- `createNewSession` must set `lastUsedProvider` to selected provider before opening chat.
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Command selection for new-thread launches must follow canonical mapping in `spec/agent-core-contracts.spec.md`.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt

- Codex new-thread opens must start in pending identity state (`codex:new-*`) with `sessionId = null`.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- App-server backend remains the default discovery source and must surface concrete thread id after first user input.
  [@test] ../../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- Provider refresh must rebind pending Codex chat tabs to concrete identities and switch shell command to canonical resume mapping.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- `Codex (Full Auto)` semantics are defined by command mapping and require no additional warning text in this flow.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt

## User Experience
- Hovering a project/worktree row reveals new-thread controls without opening the project.
- Quick-provider icon enables one-click repeat creation.
- Popup keeps normal and YOLO options explicit.

## Data & Backend
- Codex creation flow starts with pending identity and is resolved asynchronously by app-server refresh.
- Concrete identity rebinding updates tab identity and command to resume form.

## Error Handling
- Provider CLI/app-server failures must continue through provider-specific error paths in existing service flow.
- Duplicate clicks for same action tuple must be dropped.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`
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
