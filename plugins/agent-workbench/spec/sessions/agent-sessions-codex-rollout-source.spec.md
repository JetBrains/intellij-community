---
name: Codex Sessions Source
description: Requirements for Codex thread discovery, backend selection, app-server listing, and archive/write interoperability.
targets:
  - ../../lib-agent/providers/codex/sessions/src/**/*.kt
  - ../../codex/common/src/*.kt
  - ../../sessions/src/service/AgentSessionRefreshCoordinator.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/**/*.kt
  - ../../lib-agent/providers/codex/common/testSrc/CodexAppServerProtocolTest.kt
---

# Codex Sessions Source

Status: Draft
Date: 2026-05-09

## Summary
Codex session listing and Workbench status are app-server backed. Rollout files remain an internal discovery and cost-recovery source; they may trigger source refreshes when new rollout/project-file evidence appears, but they must not provide source-level activity or presentation fallback.

## Requirements
- `CodexSessionSource` must list threads through `CodexAppServerSessionBackend`; legacy backend override inputs, including `rollout` and unknown values, must not switch listing away from app-server.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- App-server listing must request `thread/list` with server-side `cwd` and source-kind filters so top-level sessions and sub-agent sessions can be folded consistently.
  [@test] ../../lib-agent/providers/codex/common/testSrc/CodexAppServerProtocolTest.kt

- App-server thread-scoped refresh must use `thread/read includeTurns=false`, filter snapshots by normalized `cwd`, and return partial updates/removals instead of replacing the whole project path.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- App-server backend folds sub-agent thread-spawn sessions under parent threads and hides orphaned sub-agent sessions from tree rows.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Hidden orphaned sub-agent sessions may be auto-archived with bounded one-shot retry and at most one archive attempt per refresh.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Codex provider bridge advertises archive capability and routes archive/unarchive through app-server write APIs when supported.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/codex/common/testSrc/CodexAppServerProtocolTest.kt

- Shared app-server process starts lazily on first request and stops after a configurable idle timeout when no requests are in flight; default timeout is 60 seconds.
  [@test] ../../lib-agent/providers/codex/common/testSrc/CodexAppServerProtocolTest.kt

- Paging seed logic must detect cursor loops/no-progress iterations, terminate safely, and preserve already collected thread results.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexSessionsPagingLogicTest.kt

- Codex activity normalization is specified in `agent-sessions-codex-activity.spec.md`.

- Rollout parsing, watching, and discovery-event consumption are specified in `agent-sessions-codex-rollout-hints.spec.md`.

## Testing / Local Run
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.CodexSessionBackendSelectorTest`
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.CodexAppServerSessionBackendTest`
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.CodexAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.platform.ai.agent.codex.common.tests --test com.intellij.platform.ai.agent.codex.common.CodexAppServerProtocolTest`

## References
- `agent-sessions-codex-activity.spec.md`
- `agent-sessions-codex-rollout-hints.spec.md`
- `agent-sessions-refresh.spec.md`
- `../actions/codex-thread-rebinding.spec.md`
