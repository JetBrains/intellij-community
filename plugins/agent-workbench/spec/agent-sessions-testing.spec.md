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
Date: 2026-03-15

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
  - archive gate behavior,
  - visibility primitive persistence.
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

- Sessions aggregation/service coverage must include ordering, partial warning, blocking error, unknown counts, refresh bootstrap, on-demand dedup, and refresh concurrency.
- Swing tree rendering coverage must include warning/error precedence, empty-state exclusivity, `More` row exact/unknown behavior, and thread-row metadata presentation (badge + time, no inline status text, tooltip status preserved).
- Swing tree interaction coverage must include single-click select behavior, activation-open policy, double-click open precedence on openable parent rows, path resolution for `More` rows, and context-menu selection retarget rules.
- Persisted tree-state coverage must include collapsed-state restoration and expansion parent mapping for selected worktree paths.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeStatePersistenceTest.kt

- Persisted UI-state coverage must include visible-count persistence, preview-provider persistence, and legacy provider fallback.
  [@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt

- New-thread action coverage must include quick-provider eligibility, loading-row suppression, Standard/YOLO popup modeling, dedup, and pending-to-concrete Codex rebinding.
  [@test] ../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Tool-window factory coverage must include Swing factory registration and gear action registration.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowFactorySwingTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Dedicated-frame coverage must include gear toggle setting wiring, routing behavior in both modes, and dedicated-project filtering.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Claude quota hint coverage must include visibility/acknowledgement gating and toggle action registration.
  [@test] ../sessions/testSrc/AgentSessionsSwingQuotaHintTest.kt
  [@test] ../sessions/testSrc/AgentSessionsClaudeQuotaWidgetActionRegistrationTest.kt

- Chat-editor lifecycle coverage must include protocol v2 restore, state round-trip, lazy initialization, tab title refresh, icon mapping fallback, and archive-triggered close+forget.
- Codex backend coverage must include raw status-kind parsing, rollout parser/title/activity behavior, watcher behavior (path-scoped + overflow/full-rescan), app-server sub-agent hierarchy/orphan handling, app-server-only backend selection, app-server `thread/read` status-and-flag normalization, response-required/read-tracker behavior, started-thread fallback mapping, app-server-first refresh-hints merge with rollout unread fallback, real-TUI rollout ingestion through the production rollout path, prompt-suggestion streamed turn handling, and prompt-suggestion interrupt cleanup.
- Codex app-server contract tests must run against mock backend in all environments and real backend when CLI is available.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Real-backend contract assertions must be invariant-based (ordering and archived consistency) and must not depend on user-specific thread IDs.
- Mock-backend contract assertions must additionally validate deterministic fixture IDs, archive/unarchive mutation behavior, idle-timeout lazy restart, prompt-suggestion transport shape, streamed pre-completion notifications, unrelated-notification filtering, `turn/interrupt` cleanup on timeout or cancellation, interrupted or failed prompt-turn outcomes, and dedicated prompt-suggestion client reset when cleanup cannot confirm terminal completion.

## Requirement Ownership Matrix
- Core contracts: `AgentSessionCliTest`, `AgentSessionsEditorTabActionsTest`, `AgentSessionArchiveServiceIntegrationTest`, `AgentSessionRefreshOnDemandIntegrationTest`
- Sessions aggregation/loading: `AgentSessionLoadAggregationTest`, `AgentSessionRefreshServiceIntegrationTest`, `AgentSessionRefreshOnDemandIntegrationTest`, `AgentSessionRefreshConcurrencyIntegrationTest`
- Swing tree rendering: `AgentSessionsSwingTreeRenderingTest`, `AgentSessionsSwingTreeCellRendererTest`
- Swing tree interaction: `AgentSessionsSwingTreeInteractionTest`
- Swing tree state persistence: `AgentSessionsSwingTreeStatePersistenceTest`
- Tree UI persisted state: `AgentSessionsTreeUiStateServiceTest`
- New-thread flow: `AgentSessionsSwingNewSessionActionsTest`, `AgentSessionRefreshCoordinatorTest`, `AgentChatEditorServiceTest`
- Tool-window factory wiring: `AgentSessionsToolWindowFactorySwingTest`, `AgentSessionsGearActionsTest`
- Dedicated frame: `AgentSessionsGearActionsTest`, `AgentSessionsOpenModeRoutingTest`
- Quota hint gating: `AgentSessionsSwingQuotaHintTest`, `AgentSessionsClaudeQuotaWidgetActionRegistrationTest`
- Chat tab lifecycle: `AgentChatEditorServiceTest`, `AgentChatFileEditorProviderTest`, `AgentChatTabSelectionServiceTest`
- Codex rollout/app-server selection + hint wiring: `CodexRolloutSessionBackendTest`, `CodexRolloutSessionBackendFileWatchIntegrationTest`, `CodexRolloutSessionsWatcherTest`, `CodexAppServerSessionBackendTest`, `CodexAppServerRefreshHintsProviderTest`, `CodexSessionSourceRefreshHintsTest`, `CodexSessionBackendSelectorTest`, `CodexSessionsPagingLogicTest`, `AgentSessionRefreshCoordinatorTest`
- Codex app-server contract: `CodexAppServerClientTest`
- Prompt-suggestion AI mapping: `CodexAppServerPromptSuggestionBackendTest`

## Contract Suite
- `CodexAppServerClientTest` is parameterized for mock backend and optional real `codex app-server` backend.
- Both modes must assert invariant behavior: descending `updatedAt` ordering and archive flag consistency.
- Mock mode additionally asserts deterministic IDs, archive/unarchive mutation, idle-timeout restart semantics, prompt-suggestion `thread/start` + `turn/start` transport, streamed pre-completion notifications, unrelated-notification filtering, `turn/interrupt` cleanup on timeout or cancellation, interrupted or failed prompt-turn outcomes, and dedicated prompt-suggestion client reset when cleanup cannot confirm terminal completion.
- `CodexAppServerPromptSuggestionBackendTest` owns `AI_POLISHED` versus `AI_GENERATED` mapping, fallback-slot id and order validation, invalid or duplicate candidate filtering, and fallback preservation when Codex output is unusable.
- `CodexSessionSourceRealTuiIntegrationTest` runs the real `codex` TUI against a local mock Responses provider and asserts rollout ingestion only through production Workbench components (`CodexRolloutSessionBackend`, `CodexRolloutRefreshHintsProvider`, `CodexSessionSource`), including the real limited-rollout `request_user_input` tool-call shape and passive-unread read-tracking suppression.
- Deterministic rollout parser/source tests remain the mandatory CI owner for event-shape matrices and review-mode normalization; the real TUI suite is a local-gated integration layer.

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
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSession*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwing*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowFactorySwingTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChat*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRollout*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProviderTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerPromptSuggestionBackendTest'`
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
