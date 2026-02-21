---
name: Agent Sessions New-Session Actions
description: Requirements for project/worktree new-session actions and provider-specific new-session routing in Agent Threads.
targets:
  - ../../sessions/src/SessionTreeNewSessionActions.kt
  - ../../sessions/src/SessionTreeRows.kt
  - ../../sessions/src/AgentSessionsToolWindow.kt
  - ../../sessions/src/AgentSessionsService.kt
  - ../../codex/sessions/src/backend/appserver/SharedCodexAppServerService.kt
  - ../../sessions/src/AgentSessionCli.kt
  - ../../sessions/src/providers/codex/CodexCliCommands.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../sessions/testSrc/AgentSessionsToolWindowTest.kt
  - ../../sessions/testSrc/AgentSessionCliTest.kt
  - ../../sessions/testSrc/CodexAppServerClientTest.kt
---

# Agent Sessions New-Session Actions

Status: Draft
Date: 2026-02-13

## Summary
Define project/worktree `New Session` actions (`+` and quick-provider icon) and map each provider + mode to the chat-open flow.

## Goals
- Keep session-creation behavior identical across project and worktree rows.
- Keep provider/mode semantics explicit and testable.
- Keep Codex new-session launch independent from app-server pre-creation.

## Non-goals
- Thread loading/sorting/paging behavior.
- Dedicated-frame routing rules outside new-session open progress integration.
- New user-facing warning text for `Codex (Full Auto)`.

## Requirements
- Project/worktree row hover actions must route through `onCreateSession(path, provider, yolo)`; no separate create-thread callback path may be used.
  [@test] ../../sessions/testSrc/AgentSessionsToolWindowTest.kt
- Quick provider icon (when `lastUsedProvider` exists) must create a session with `yolo=false`.
- Popup must expose four selectable entries: Claude, Codex, Claude YOLO, Codex YOLO.
- Popup section label remains `YOLO`; Codex YOLO entry label remains `Codex (Full Auto)`.

- `AgentSessionsService.createNewSession(path, provider, yolo, currentProject)` is the service entry point for row actions.
- `createNewSession` must deduplicate in-flight actions by normalized `path + provider + yolo` with single-flight `DROP`.
- `createNewSession` must set `lastUsedProvider` to the selected provider before opening chat.

- Claude `yolo=false` new-session command must be `claude`.
- Claude `yolo=true` new-session command must be `claude --dangerously-skip-permissions`.
- Codex new-session command construction must use direct CLI launch: `codex` for standard, `codex --full-auto` for YOLO.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt

- Codex new-session must open chat in a pending state (`codex:new-*`) with `sessionId = null`; first user input creates the concrete thread in rollout backend.
- Rollout remains the default thread-discovery backend and is responsible for surfacing the concrete thread id after creation.
- Provider refresh must rebind pending Codex chat tabs to concrete identities and update shell commands to `codex resume <threadId>`.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- `Codex (Full Auto)` semantics are documented in spec and do not require extra warning text in UI.

## User Experience
- Hovering a project/worktree row reveals new-session controls without opening the project.
- The quick-provider icon provides one-click repeat creation.
- Popup grouping keeps normal and YOLO choices explicit.

## Data & Backend
- Codex action flow launches direct CLI new sessions and starts with pending identity (`CODEX:new-*`).
- Concrete thread id binding happens asynchronously from rollout refresh and updates the tab to `codex resume <threadId>`.

## Error Handling
- Provider CLI/app-server failures must continue using provider-specific error paths in existing service flow.
- Duplicate clicks on the same action tuple must be dropped rather than opening multiple sessions.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionCliTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.CodexAppServerClientTest'`

## Open Questions / Risks
- `createNewSession` dedup + yolo parameter mapping currently has limited direct service-level test coverage.

## References
- `../agent-sessions.spec.md`
- `../agent-sessions-codex-rollout-source.spec.md`
- `../agent-dedicated-frame.spec.md`
