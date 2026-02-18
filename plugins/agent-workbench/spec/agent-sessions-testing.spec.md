---
name: Agent Threads Testing
description: Coverage ownership matrix for Swing-based Agent Threads, shared contracts, chat lifecycle, and Codex backends.
targets:
  - ../sessions/testSrc/*.kt
  - ../chat/testSrc/*.kt
  - ../codex/sessions/testSrc/**/*.kt
---

# Agent Threads Testing

Status: Draft
Date: 2026-03-07

## Summary
Define required coverage ownership for Agent Workbench specs after the hard Swing cutover.

This file does not redefine runtime behavior; it maps each contract area to required test suites.

## Goals
- Keep each contract area owned by explicit tests.
- Avoid overlap-heavy coverage where failures are hard to triage.
- Keep mock-vs-real backend expectations explicit for Codex app-server tests.
- Keep Swing tree interaction/state coverage explicit and separate from backend/service coverage.

## Non-goals
- Defining runtime behavior (owned by feature specs).
- End-to-end UI automation outside module tests.
- Performance benchmarking in default CI path.

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
  - archive action removing the thread from state and preserving remaining threads after refresh.
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
  - open-tab title refresh via editor presentation updates.

## Requirement Ownership Matrix
- Core contracts: `AgentSessionCliTest`, `AgentSessionsEditorTabActionsTest`, `AgentSessionArchiveServiceIntegrationTest`, `AgentSessionRefreshOnDemandIntegrationTest`
- Sessions aggregation/loading: `AgentSessionLoadAggregationTest`, `AgentSessionRefreshServiceIntegrationTest`, `AgentSessionRefreshOnDemandIntegrationTest`, `AgentSessionRefreshConcurrencyIntegrationTest`
- Swing tree rendering: `AgentSessionsSwingTreeRenderingTest`, `AgentSessionsSwingTreeCellRendererTest`
- Swing tree interaction: `AgentSessionsSwingTreeInteractionTest`
- Swing tree state persistence: `AgentSessionsSwingTreeStatePersistenceTest`
- Tree UI persisted state: `AgentSessionTreeUiStateServiceTest`
- Warm snapshot persisted state: `AgentSessionWarmStateServiceTest`
- Shared UI preferences state: `AgentSessionUiPreferencesStateServiceTest`
- New-thread flow: `AgentSessionsSwingNewSessionActionsTest`, `AgentSessionRefreshCoordinatorTest`, `AgentChatEditorServiceTest`
- Tool-window factory wiring: `AgentSessionsToolWindowFactorySwingTest`, `AgentSessionsGearActionsTest`
- Dedicated frame: `AgentSessionsGearActionsTest`, `AgentSessionsOpenModeRoutingTest`
- Quota hint gating: `AgentSessionsSwingQuotaHintTest`, `AgentSessionsClaudeQuotaWidgetActionRegistrationTest`
- Chat tab lifecycle: `AgentChatEditorServiceTest`, `AgentChatFileEditorProviderTest`, `AgentChatTabSelectionServiceTest`
- Codex rollout/app-server selection + hint wiring: `CodexRolloutSessionBackendTest`, `CodexRolloutSessionBackendFileWatchIntegrationTest`, `CodexRolloutSessionsWatcherTest`, `CodexSessionActivityResolverTest`, `CodexAppServerSessionBackendTest`, `CodexAppServerRefreshHintsProviderTest`, `CodexSessionSourceRefreshHintsTest`, `CodexSessionSourceRolloutIntegrationTest`, `CodexSessionSourceRealTuiIntegrationTest`, `CodexSessionBackendSelectorTest`, `CodexSessionsPagingLogicTest`, `AgentSessionRefreshCoordinatorTest`
- Codex app-server contract: `CodexAppServerClientTest`

## Contract Suite
- `CodexAppServerClientTest` is parameterized for mock backend and optional real `codex app-server` backend.
- Both modes must assert invariant behavior: descending `updatedAt` ordering and archive flag consistency.
- Mock mode additionally asserts deterministic IDs, archive/unarchive mutation, and idle-timeout restart semantics.
- `CodexSessionSourceRealTuiIntegrationTest` runs the real `codex` TUI against a local mock Responses provider and asserts rollout ingestion only through production Workbench components (`CodexRolloutSessionBackend`, `CodexRolloutRefreshHintsProvider`, `CodexSessionSource`), including the real limited-rollout `request_user_input` tool-call shape and passive-unread read-tracking suppression.
- Deterministic rollout parser/source tests remain the mandatory CI owner for event-shape matrices and review-mode normalization; the real TUI suite is a local-gated integration layer.

## Integration Gating
- Real backend runs only when `codex` CLI is resolvable.
- `CODEX_BIN` may point to explicit binary; otherwise PATH is used.
- Real TUI rollout integration also requires PTY support and therefore runs only on macOS/Linux hosts.
- Mock backend contract suite is mandatory in CI.

## Isolation
- Test process must use fresh temp `CODEX_HOME`.
- Minimal `config.toml` is generated for spawned real backend process.
- Environment overrides are process-scoped; global environment state is not mutated.
- Real TUI rollout integration uses a temp trusted project, local mock Responses HTTP server, and no live network/auth dependency.

## Running Locally
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSession*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwing*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowFactorySwingTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChat*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRollout*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProviderTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRefreshHintsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRealTuiIntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.CodexAppServerClientTest -Dintellij.build.test.main.module=intellij.agent.workbench.sessions'`

Optional real-backend override:

```bash
export CODEX_BIN=/path/to/codex
```

## Open Questions / Risks
- Claude backend still lacks a contract suite equivalent to `CodexAppServerClientTest`.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/agent-sessions-thread-visibility.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-sessions-codex-rollout-source.spec.md`
- `spec/actions/new-thread.spec.md`
