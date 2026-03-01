---
name: Agent Threads Testing
description: Coverage ownership matrix for Swing-based Agent Threads, shared contracts, chat lifecycle, and Codex backends.
targets:
  - ../sessions/testSrc/*.kt
  - ../chat/testSrc/*.kt
  - ../codex/sessions/testSrc/*.kt
---

# Agent Threads Testing

Status: Draft
Date: 2026-02-28

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
- Core contract coverage must include:
  - identity and command mapping,
  - shared editor-tab popup actions,
  - archive gate behavior (including optional unarchive capability contract),
  - visibility primitive persistence.
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

- Archive service coverage must include single-thread archive, multi-target archive with partial provider support, and unarchive restore behavior for supported providers.
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

- Sessions aggregation/service coverage must include ordering, partial warning, blocking error, unknown counts, refresh bootstrap, on-demand dedup, and refresh concurrency.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceConcurrencyIntegrationTest.kt

- Swing tree rendering coverage must include warning/error precedence, empty-state exclusivity, and `More` row exact/unknown behavior.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Swing tree interaction coverage must include single-click select behavior, activation-open policy, double-click open precedence on openable parent rows, path resolution for `More` rows, and context-menu selection retarget rules.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Persisted tree-state coverage must include collapsed-state restoration and expansion parent mapping for selected worktree paths.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeStatePersistenceTest.kt

- Persisted UI-state coverage must include visible-count persistence, preview-provider persistence, and legacy provider fallback.
  [@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt

- New-thread action coverage must include quick-provider eligibility, loading-row exposure, Standard/YOLO popup modeling, dedup, and pending-to-concrete Codex rebinding.
  [@test] ../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Refresh-loading coverage must include per-refreshed-path loading indicators and loading completion semantics (loading is not cleared on first partial provider success).
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeCellRendererTest.kt

- Tool-window factory coverage must include Swing factory registration and title/gear action registration.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowFactorySwingTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Dedicated-frame coverage must include gear toggle setting wiring, routing behavior in both modes, and dedicated-project filtering.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Claude quota hint coverage must include visibility/acknowledgement gating and toggle action registration.
  [@test] ../sessions/testSrc/AgentSessionsSwingQuotaHintTest.kt
  [@test] ../sessions/testSrc/AgentSessionsClaudeQuotaWidgetActionRegistrationTest.kt

- Chat-editor lifecycle coverage must include protocol v2 restore, state round-trip, lazy initialization, tab title refresh, icon mapping fallback, and archive-triggered close+forget.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt
  [@test] ../chat/testSrc/AgentChatTabSelectionServiceTest.kt

- Codex backend coverage must include rollout parser/title/activity behavior, watcher behavior (path-scoped + overflow/full-rescan), app-server sub-agent hierarchy/orphan handling, backend selection defaults/override, and paging no-progress guard behavior.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt
  [@test] ../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt
  [@test] ../codex/sessions/testSrc/CodexSessionsPagingLogicTest.kt

- Codex app-server contract tests must run against mock backend in all environments and real backend when CLI is available.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Real-backend contract assertions must be invariant-based (ordering and archived consistency) and must not depend on user-specific thread IDs.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Mock-backend contract assertions must additionally validate deterministic fixture IDs, archive/unarchive mutation behavior, and idle-timeout lazy restart.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

## Requirement Ownership Matrix
- Core contracts: `AgentSessionCliTest`, `AgentSessionsEditorTabActionsTest`, `AgentSessionsServiceArchiveIntegrationTest`, `AgentSessionsServiceOnDemandIntegrationTest`
- Sessions aggregation/loading: `AgentSessionLoadAggregationTest`, `AgentSessionsServiceRefreshIntegrationTest`, `AgentSessionsServiceOnDemandIntegrationTest`, `AgentSessionsServiceConcurrencyIntegrationTest`
- Swing tree rendering: `AgentSessionsSwingTreeRenderingTest`
- Swing tree interaction: `AgentSessionsSwingTreeInteractionTest`
- Swing tree state persistence: `AgentSessionsSwingTreeStatePersistenceTest`
- Tree UI persisted state: `AgentSessionsTreeUiStateServiceTest`
- New-thread flow: `AgentSessionsSwingNewSessionActionsTest`, `AgentSessionsLoadingCoordinatorTest`, `AgentChatEditorServiceTest`
- Tool-window factory wiring: `AgentSessionsToolWindowFactorySwingTest`, `AgentSessionsGearActionsTest`
- Dedicated frame: `AgentSessionsGearActionsTest`, `AgentSessionsOpenModeRoutingTest`
- Quota hint gating: `AgentSessionsSwingQuotaHintTest`, `AgentSessionsClaudeQuotaWidgetActionRegistrationTest`
- Chat tab lifecycle: `AgentChatEditorServiceTest`, `AgentChatFileEditorProviderTest`, `AgentChatTabSelectionServiceTest`
- Codex rollout/backend selection: `CodexRolloutSessionBackendTest`, `CodexRolloutSessionBackendFileWatchIntegrationTest`, `CodexRolloutSessionsWatcherTest`, `CodexAppServerSessionBackendTest`, `CodexSessionBackendSelectorTest`, `CodexSessionsPagingLogicTest`
- Codex app-server contract: `CodexAppServerClientTest`

## Contract Suite
- `CodexAppServerClientTest` is parameterized for mock backend and optional real `codex app-server` backend.
- Both modes must assert invariant behavior: descending `updatedAt` ordering and archive flag consistency.
- Mock mode additionally asserts deterministic IDs, archive/unarchive mutation, and idle-timeout restart semantics.

## Integration Gating
- Real backend runs only when `codex` CLI is resolvable.
- `CODEX_BIN` may point to explicit binary; otherwise PATH is used.
- Mock backend contract suite is mandatory in CI.

## Isolation
- Test process must use fresh temp `CODEX_HOME`.
- Minimal `config.toml` is generated for spawned real backend process.
- Environment overrides are process-scoped; global environment state is not mutated.

## Running Locally
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsService*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwing*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowFactorySwingTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChat*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRollout*Test'`
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
