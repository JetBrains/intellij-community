---
name: Codex Sessions Source
description: Requirements for Codex thread discovery, backend selection, app-server listing, and archive/write interoperability.
targets:
  - ../../codex/sessions/src/**/*.kt
  - ../../codex/common/src/*.kt
  - ../../sessions/src/service/AgentSessionRefreshCoordinator.kt
  - ../../codex/sessions/testSrc/**/*.kt
  - ../../sessions/testSrc/CodexAppServerClientTest.kt
---

# Codex Sessions Source

Status: Draft
Date: 2026-05-09

## Summary
Codex session listing is app-server backed. Rollout files remain an internal refresh-hints source for needs-input/activity uplift, unread done-output hints, and rebinding fallback; they are not the primary thread-list backend.

## Requirements
- `CodexSessionSource` must list threads through `CodexAppServerSessionBackend`; legacy backend override inputs, including `rollout` and unknown values, must not switch listing away from app-server.
  [@test] ../../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- App-server listing must request `thread/list` with server-side `cwd` and source-kind filters so top-level sessions and sub-agent sessions can be folded consistently.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- App-server thread-scoped refresh must use `thread/read includeTurns=false`, filter snapshots by normalized `cwd`, and return partial updates/removals instead of replacing the whole project path.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- App-server backend folds sub-agent thread-spawn sessions under parent threads and hides orphaned sub-agent sessions from tree rows.
  [@test] ../../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Hidden orphaned sub-agent sessions may be auto-archived with bounded one-shot retry and at most one archive attempt per refresh.
  [@test] ../../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Codex provider bridge advertises archive capability and routes archive/unarchive through app-server write APIs when supported.
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- Shared app-server process starts lazily on first request and stops after a configurable idle timeout when no requests are in flight; default timeout is 60 seconds.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- Paging seed logic must detect cursor loops/no-progress iterations, terminate safely, and preserve already collected thread results.
  [@test] ../../codex/sessions/testSrc/CodexSessionsPagingLogicTest.kt

- Codex activity normalization is specified in `agent-sessions-codex-activity.spec.md`.

- Rollout parsing, watching, and refresh-hint consumption are specified in `agent-sessions-codex-rollout-hints.spec.md`.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexSessionBackendSelectorTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexAppServerSessionBackendTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.CodexAppServerClientTest`

## References
- `agent-sessions-codex-activity.spec.md`
- `agent-sessions-codex-rollout-hints.spec.md`
- `agent-sessions-refresh.spec.md`
- `../actions/codex-thread-rebinding.spec.md`
