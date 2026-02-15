---
name: Agent Sessions New-Session Actions
description: Requirements for project/worktree new-session actions and provider-specific new-session routing in Agent Threads.
targets:
  - ../../sessions/src/SessionTreeNewSessionActions.kt
  - ../../sessions/src/SessionTreeRows.kt
  - ../../sessions/src/AgentSessionsToolWindow.kt
  - ../../sessions/src/AgentSessionsService.kt
  - ../../codex/sessions/src/SharedCodexAppServerService.kt
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
- Avoid reintroducing legacy Codex "fresh session without thread id" flow.

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
- Codex new-session must not use direct `codex`/`--full-auto` command construction for session creation.
  [@test] ../../sessions/testSrc/AgentSessionCliTest.kt

- Codex new-session must call app-server `thread/start`, persist the new thread id, then open chat with `codex resume <threadId>`.
- Codex `yolo=true` must call `thread/start` with `approvalPolicy="on-request"` and `sandbox="workspace-write"`.
- Codex `yolo=false` must call `thread/start` with default app-server parameters (no forced approval/sandbox overrides).
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- `Codex (Full Auto)` semantics are documented in spec and do not require extra warning text in UI.

## User Experience
- Hovering a project/worktree row reveals new-session controls without opening the project.
- The quick-provider icon provides one-click repeat creation.
- Popup grouping keeps normal and YOLO choices explicit.

## Data & Backend
- Codex action flow uses `SharedCodexAppServerService.createThread(cwd, yolo)` and persists created threads before resume.
- Codex new-session identity is concrete (`CODEX:<threadId>`), not synthetic `new-*`.

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
