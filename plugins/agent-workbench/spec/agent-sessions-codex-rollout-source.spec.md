---
name: Codex Sessions Rollout Source
description: Requirements for Codex thread discovery, activity derivation, backend selection, and archive/write interoperability.
targets:
  - ../codex/sessions/src/**/*.kt
  - ../codex/common/src/CodexAppServerClient.kt
  - ../sessions/src/CodexSessionsCompatibility.kt
  - ../sessions/src/AgentSessionModels.kt
  - ../sessions/src/service/AgentSessionRefreshCoordinator.kt
  - ../codex/sessions/testSrc/**/*.kt
  - ../sessions/testSrc/CodexAppServerClientTest.kt
---

# Codex Sessions Rollout Source

Status: Draft
Date: 2026-03-07

## Summary
Define Codex thread-list behavior where discovery and primary status projection come from app-server (`thread/list` + `thread/read`), while rollout parsing is used only as a refresh-hints fallback (pending-tab rebinding and unread uplift). This spec owns backend selection, app-server sub-agent mapping, rollout hint wiring, and Codex activity derivation.

## Goals
- Keep Codex thread indicators aligned with app-server status snapshots, with rollout fallback limited to missing-thread or unread uplift cases.
- Keep rollout parser/index/watcher available as an internal hints pipeline (not as a thread-list backend).
- Support archive and archive-undo unarchive operations for rollout-discovered threads through shared app-server write path.
- Keep backend policy independent from new-thread UI contracts.

## Non-goals
- Archived-thread browsing and standalone unarchive entry points outside archive-undo flow.
- Claude backend behavior.
- Full-state periodic refresh loops when no pending tabs exist.

## Requirements
- Codex session listing must be implemented behind `CodexSessionBackend` interface.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- `CodexAppServerSessionBackend` must be the only backend used by `CodexSessionSource` for thread listing.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Legacy backend override inputs (including `rollout`) must not switch listing away from app-server backend.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Unknown backend override values must keep app-server backend selected.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- App-server backend must request `thread/list` with server-side `cwd` and `sourceKinds` filters so sub-agent sessions are included in listing results.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- App-server backend must fold sub-agent thread-spawn sessions under parent threads and hide orphaned sub-agent sessions from tree rows.
  [@test] ../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Hidden orphaned sub-agent sessions must be auto-archived with one-shot retry policy and at most one archive attempt per refresh.
  [@test] ../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Pending Codex chat tabs must trigger pending-only polling refresh to rebind pending identities when source update notifications are unavailable.
  [@test] ../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Rollout backend scan scope must be limited to `~/.codex/sessions/**/rollout-*.jsonl`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Rollout hints must be consumed for pending-tab rebinding and Codex activity projection; rollout-discovered IDs must not create persisted thread rows.
  [@test] ../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- App-server refresh hints must map `thread/read` snapshot status and flags to Codex activity states (`unread`, `reviewing`, `processing`, `ready`) using the normalization rules below.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- Workbench must treat `CodexThreadStatusKind` as raw provider status, not as UI activity.
  - `NOT_LOADED`: thread is not currently loaded by the app-server.
  - `IDLE`: thread is loaded and has no in-progress turn or action-required flag.
  - `ACTIVE`: thread is loaded and either running or blocked on approval/user input.
  - `SYSTEM_ERROR`: thread is loaded and currently in a provider runtime failure state.
  - `UNKNOWN`: thread status was absent or unrecognized and must be treated as a non-fatal fallback state.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Workbench must treat `CodexThreadActiveFlag` as response-required raw signals only.
  - `WAITING_ON_APPROVAL` and `WAITING_ON_USER_INPUT` both mean action is required outside the running agent turn.
  - `responseRequired` must be `true` only for those two flags.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../codex/sessions/testSrc/CodexSessionSourceRefreshHintsTest.kt

- `REVIEWING` must remain a derived workbench activity only; it must come from snapshot or rollout review-mode signals and never from raw `CodexThreadStatusKind`.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt

- Activity normalization from Codex raw signals must be:
  - `UNREAD` when active flags indicate response required.
  - `REVIEWING` when `isReviewing = true` and response-required activity is not already selected.
  - `PROCESSING` when `hasInProgressTurn = true` or `statusKind = ACTIVE` without response-required flags.
  - `UNREAD` when `hasUnreadAssistantMessage = true` and no higher-priority reviewing, processing, or response-required activity is present.
  - `READY` otherwise, including `IDLE`, `SYSTEM_ERROR`, `NOT_LOADED`, and `UNKNOWN`.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- `thread/started` fallback and `thread/status/changed` notifications must use only raw `statusKind` and `activeFlags`; snapshot-only promotions (`hasUnreadAssistantMessage`, `isReviewing`, `hasInProgressTurn`) require `thread/read` or rollout fallback.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- For Codex activity projection, app-server activity must remain primary for overlapping thread ids; rollout activity may apply only when app-server activity is missing or rollout reports `UNREAD`.
  [@test] ../codex/sessions/testSrc/CodexSessionSourceRefreshHintsTest.kt

- Rollout change detection must use `AgentWorkbenchDirectoryWatcher` stack; Java NIO `WatchService` must not be used.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt

- Rollout refresh behavior must be event-driven; periodic polling timers are not allowed.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt

- Rollout backend must filter sessions by normalized `cwd` matching requested project/worktree path.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Multi-path prefetch must use a shared scan and return per-path filtered thread lists.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Cache invalidation must be path-scoped for rollout file changes; full rescan is allowed only for overflow/ambiguous directory events.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt

- Path-scoped invalidation must force reparse of dirty rollout paths even when file size and mtime are unchanged.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt

- Non-rollout file events under `~/.codex/sessions` must still trigger event-driven refresh (without forced full reparse) to support atomic temp/rename write patterns.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt

- Thread id source must be `session_meta.payload.id`; rollout filename fallback is forbidden.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Files missing `session_meta.payload.id` must be skipped.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Title extraction must use first qualifying `event_msg` with `payload.type=user_message`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- `thread_name_updated` messages with non-blank `payload.thread_name` must override derived title.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Title normalization must:
  - strip `## My request for Codex:` marker when present,
  - ignore session-prefix messages (`<environment_context>`, `<turn_aborted>`),
  - normalize whitespace and apply bounded trim.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- If no qualifying title is found, fallback title must be `Thread <id-prefix>`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Activity precedence must be `unread > reviewing > processing > ready`; this order must apply consistently to direct snapshot resolution, started-thread fallback, folded parent/sub-agent aggregation, and rollout unread uplift merge.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
  [@test] ../codex/sessions/testSrc/CodexSessionSourceRefreshHintsTest.kt

- `SYSTEM_ERROR` must not introduce a separate session-tree activity state; it normalizes to `ready` unless a higher-priority unread, reviewing, or processing signal exists, and provider failures continue to surface through existing warning and error channels.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Session-tree indicator colors for Codex activity must map to:
  - unread: `#4DA3FF`,
  - reviewing: `#2FD1C4`,
  - processing: `#FF9F43`,
  - ready: `#3FE47E`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Codex provider bridge must advertise archive capability and route archive/unarchive calls through shared app-server service when unarchive is supported.
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Shared app-server process must start lazily on first request.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Shared app-server process must stop after configurable idle timeout when no requests are in flight; default timeout is 60 seconds.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Paging seed logic must guard against cursor loops and no-progress iterations; it must terminate safely without infinite looping and preserve already collected thread results.
  [@test] ../codex/sessions/testSrc/CodexSessionsPagingLogicTest.kt

## User Experience
- Codex activity indicators should reflect normalized workbench activity derived from app-server `thread/read` snapshots; raw Codex status kinds are not shown directly, and rollout hints are used only for missing-thread fallback and unread uplift.
- Archive action remains available for Codex threads discovered from rollout source.
- Archive undo should be available when Codex unarchive is supported by the active provider bridge.

## Data & Backend
- `updatedAt` derives from latest event timestamp with file mtime fallback.
- `response_item` contributes to activity timing but not title source extraction.
- Branch value comes from rollout session metadata when present; no branch fallback store is used.
- Listing stays app-server-backed; write operations (`thread/start`, `thread/archive`, `thread/unarchive`, persistence calls) remain app-server RPC.
- Refresh hints merge app-server and rollout signals after app-server raw status normalization, with rollout able to fill missing activity or raise activity to unread.

## Error Handling
- Invalid override values must not disable Codex listing; fallback to app-server must apply.
- Parse failures must isolate to failing files and preserve valid thread rows from other files.
- Watcher overflow events may trigger full rescan to recover correctness.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendFileWatchIntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionsWatcherTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexSessionBackendSelectorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivityResolverTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexAppServerSessionBackendTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProviderTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRefreshHintsTest'`

## Open Questions / Risks
- Cross-platform filesystem event differences can still produce edge-case rescan spikes under heavy write churn.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/actions/new-thread.spec.md`
