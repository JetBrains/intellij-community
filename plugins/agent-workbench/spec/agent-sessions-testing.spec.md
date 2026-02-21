---
name: Agent Threads Testing
description: Coverage requirements for provider aggregation, tree rendering, and backend contracts in Agent Threads.
targets:
  - ../sessions/src/AgentSessionsTreeUiStateService.kt
  - ../codex/common/src/CodexAppServerClient.kt
  - ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
  - ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt
  - ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt
  - ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt
  - ../codex/sessions/testSrc/CodexSessionsPagingLogicTest.kt
  - ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceConcurrencyIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceIntegrationTestSupport.kt
  - ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  - ../sessions/testSrc/AgentSessionsGearActionsTest.kt
  - ../sessions/testSrc/AgentSessionsToolWindowTest.kt
  - ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt
  - ../sessions/testSrc/CodexAppServerClientTest.kt
  - ../sessions/testSrc/CodexAppServerClientTestSupport.kt
  - ../sessions/testSrc/CodexTestAppServer.kt
---

# Agent Threads Testing

Status: Draft
Date: 2026-02-19

## Summary
Define required test coverage for the multi-provider Agent Threads stack: source aggregation, service behavior, tree/UI rendering, and Codex backend protocol compatibility.

## Goals
- Keep provider merge behavior stable across refactors.
- Validate refresh/on-demand/concurrency flows against realistic service state transitions.
- Keep UI expectations explicit for warning/error/empty/More states.
- Preserve Codex protocol compatibility with mock and optional real backend tests.

## Non-goals
- End-to-end UI automation outside current module tests.
- Performance benchmarking as part of default test runs.

## Requirements
- Aggregation unit tests must cover:
  - merged ordering by `updatedAt`,
  - partial-provider warnings,
  - all-provider-failure blocking error,
  - unknown total propagation.
- Service integration tests must cover:
  - mixed-provider refresh merge,
  - provider warning and blocking error paths,
  - unknown-count behavior when unknown provider fails/succeeds,
  - cached preview rows rendered before open-path provider load completes,
  - persisted visible thread count restoration during refresh bootstrap,
  - archive action removing the thread from state and preserving remaining threads after refresh,
  - archive action invoking chat cleanup (close tabs + delete metadata) only on successful archive.
- On-demand integration tests must cover:
  - project request deduplication,
  - worktree request deduplication with refresh interaction,
  - `showMoreThreads` visible-count persistence,
  - `ensureThreadVisible` visible-count persistence.
- Concurrency integration tests must verify refresh mutex deduplicates overlapping refresh calls.
- Codex rollout backend tests must cover rollout parsing/activity behavior as the default thread-discovery path.
- Codex backend selector tests must verify rollout default behavior and explicit app-server override behavior.
- Tree UI tests must cover:
  - provider warning rendering,
  - error row precedence over warnings,
  - `More…` rendering for unknown count,
  - `More (N)` rendering for exact count,
  - persisted collapsed state blocking default auto-expand,
  - collapsed-state persistence across content refresh/recreation when persistent tree UI state is used.
- Editor-tab action tests must cover:
  - action registration in `EditorTabPopupMenu`,
  - action visibility/enablement for selected Agent chat tab context,
  - `Select in Agent Threads` invoking visibility synchronization and tool-window activation,
  - `Archive Thread` delegating to archive flow,
  - `Copy Thread ID` using selected tab thread id.
- Tree UI state service tests must cover:
  - collapsed/visible-count/open-preview state round-trip,
  - preview provider identity persistence,
  - backward-compatible provider default for legacy preview entries with missing provider value.
- Codex compatibility tests must cover cursor-loop/no-progress guard behavior in `seedInitialVisibleThreads`.
- Codex app-server contract tests must run against mock backend always and real backend when available.
- Codex app-server client tests must cover:
  - `thread/archive` behavior moving a thread from active to archived lists,
  - lazy process restart behavior after idle-timeout shutdown.
- Chat editor tests must cover metadata-backed restore and title refresh semantics:
  - v2 `agent-chat://2/<tabKey>` path parsing,
  - metadata file round-trip for shell command/thread identity/title,
  - provider-specific tab icon mapping for `codex:*` and `claude:*` identities with unknown-provider fallback,
  - icon mapping resolved through typed module icon holders (no inline provider icon-path loading in tab provider/widget code),
  - open-tab title refresh via editor presentation updates,
  - archive-triggered close-and-forget behavior for matching thread tabs,
  - immediate metadata deletion on restore validation failure and terminal initialization failure.

## Requirement Ownership Matrix
Primary ownership is singular by design to avoid overlap-heavy tests and keep failures actionable.

- Aggregation ordering/warnings/errors/unknown total: `AgentSessionLoadAggregationTest`
- Refresh merge + warning/error + unknown-count + cached preview + visible-count restore: `AgentSessionsServiceRefreshIntegrationTest`
- On-demand dedup + visible-count persistence: `AgentSessionsServiceOnDemandIntegrationTest`
- Refresh mutex dedup: `AgentSessionsServiceConcurrencyIntegrationTest`
- Archive refresh semantics: `AgentSessionsServiceArchiveIntegrationTest`
- Rollout parsing/title/activity + branch + cwd filtering + prefetch: `CodexRolloutSessionBackendTest`
- Rollout file-watch end-to-end updates (in-place + atomic replace): `CodexRolloutSessionBackendFileWatchIntegrationTest`
- Watch-event classification (path-scoped/full-rescan/refresh-ping): `CodexRolloutSessionsWatcherTest`
- Backend selection defaults/override: `CodexSessionBackendSelectorTest`
- Paging loop/no-progress guards: `CodexSessionsPagingLogicTest`
- App-server protocol contract (mock required, real optional): `CodexAppServerClientTest`
- Tree rendering and `More` state behavior: `AgentSessionsToolWindowTest`
- Editor-tab action behavior: `AgentSessionsEditorTabActionsTest`
- Editor-tab action registration in plugin descriptor: `AgentSessionsGearActionsTest`
- Chat tab icon-provider mapping + registration: `AgentChatFileEditorProviderTest`
- Tree UI persisted state round-trip/backward compatibility: `AgentSessionsTreeUiStateServiceTest`

[@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceConcurrencyIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
[@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt
[@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt
[@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt
[@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
[@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt
[@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt
[@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt
[@test] ../codex/sessions/testSrc/CodexSessionsPagingLogicTest.kt
[@test] ../sessions/testSrc/CodexAppServerClientTest.kt

## Contract Suite
- `CodexAppServerClientTest` is a parameterized contract test that executes against:
  - Mock app-server (`CodexTestAppServer`) using a synthetic config file.
  - Real `codex app-server` when the CLI is available (skipped otherwise).
- Both backends share the same invariant assertions:
  - Threads are sorted by `updatedAt` descending.
  - `archived` flags are consistent with the requested list.
- The mock backend additionally asserts exact thread IDs because the fixture is deterministic. The real backend uses only invariant assertions because thread data is user-specific and unstable.
- Mock-only assertions cover archive mutation and idle-timeout lazy restart semantics.

[@test] ../sessions/testSrc/CodexAppServerClientTest.kt

## Integration Gating
- The real backend runs when the `codex` CLI is resolvable.
- `CODEX_BIN` can be used to point at a specific binary; otherwise PATH is used.
- Mock backend contract tests are mandatory and must run in CI.

## Isolation
- The test creates a fresh `CODEX_HOME` directory in a temp location.
- A minimal `config.toml` is generated there for the real backend.
- `CODEX_HOME` is set only for the spawned `codex app-server` via per-process environment overrides.
- No global environment state is mutated.

## Running Locally
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsService*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackend*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionsWatcherTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexSessionsPagingLogicTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.CodexAppServerClientTest -Dintellij.build.test.main.module=intellij.agent.workbench.sessions'`

Optional real-backend override:
```bash
export CODEX_BIN=/path/to/codex
```

The real backend requires Codex CLI authentication to be available in the environment.

## Open Questions / Risks
- Claude backend contract tests equivalent to `CodexAppServerClientTest` are not yet present; adding them would improve provider parity guarantees.

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-sessions-thread-visibility.spec.md`
