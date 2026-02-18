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
Date: 2026-03-09

## Summary
Define Codex thread-list behavior where discovery and primary status projection come from app-server (`thread/list` + `thread/read`), while rollout parsing is used only as a refresh-hints fallback (pending-tab rebinding, concrete `/new` rebinding, and unread uplift). This spec owns backend selection, app-server sub-agent mapping, rollout hint wiring, and Codex activity derivation.

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
- Introduce `CodexSessionBackend` interface (singular naming) for Codex thread loading.
- Provide `CodexRolloutSessionBackend` as default backend and keep `CodexAppServerSessionBackend` as alternate.
- Keep backend implementations separated by package:
  - `com.intellij.agent.workbench.codex.sessions.backend.rollout`
  - `com.intellij.agent.workbench.codex.sessions.backend.appserver`
- Backend selection must default to rollout and only switch to app-server when `agent.workbench.codex.sessions.backend=app-server` is explicitly set.
- Unknown backend override values must log a warning and fall back to rollout.
- Rollout backend must scan only `~/.codex/sessions/**/rollout-*.jsonl`.
- Rollout change detection must use `AgentWorkbenchDirectoryWatcher` (prop/io.methvin watcher stack); Java NIO `WatchService` must not be used in this backend.
- Rollout updates must be strictly event-driven and must not rely on periodic polling timers.
- Rollout backend must filter sessions by normalized `cwd` matching project/worktree path.
- Rollout backend must support multi-path prefetch and return per-path filtered thread lists from a shared scan.
- Rollout cache invalidation must be path-scoped for watcher-reported rollout file changes; full cache rescan is allowed only for overflow/ambiguous directory events.
- Path-scoped invalidation must force reparse of dirty rollout paths even when file size and mtime are unchanged.
- Non-rollout file events under `~/.codex/sessions` must still trigger event-driven refresh (without forced full reparse) so atomic temp/rename write patterns are observed without polling.
- Thread id must come from `session_meta.payload.id` (not rollout filename).
- Rollout backend must skip files missing `session_meta.payload.id` (no filename fallback).
- Title extraction must use the first qualifying `event_msg` with `payload.type=user_message`.
- `event_msg` with `payload.type=thread_name_updated` and non-blank `payload.thread_name` must override previously derived title.
- Title extraction must strip `## My request for Codex:` when present and use the text after the marker.
- Title extraction must ignore session-prefix user messages starting with `<environment_context>` or `<turn_aborted>` (case-insensitive, leading whitespace ignored).
- Title extraction must trim and whitespace-normalize text, then apply bounded title trim.
- If no qualifying title is found, title must fall back to `Thread <id-prefix>`.
- Thread activity precedence must be: `unread` > `reviewing` > `processing` > `ready`.
- Session tree indicator colors must match CodexMonitor classes:
  - `unread`: blue (`#4DA3FF`)
  - `reviewing`: teal (`#2FD1C4`)
  - `processing`: orange (`#FF9F43`)
  - `ready`: green (`#3FE47E`)
- New-session action semantics (including Codex `Codex (Full Auto)` parameters) are defined in `spec/actions/new-thread.spec.md` and are backend-invariant.
- Existing thread open behavior remains `codex resume <threadId>`.
- Codex archive action must use app-server `thread/archive` and pass `threadId`.
- Codex provider bridge must advertise archive capability and route archive calls through shared app-server service.
- Shared app-server client process must start lazily on first request.
- Shared app-server client process must stop after configurable idle timeout once no requests are in flight.
- Default app-server idle timeout must be 60 seconds; tests may set shorter values.

## Data & Backend
- `updatedAt` derives from latest event timestamp with file mtime fallback.
- `response_item` contributes to activity timing and pending user-input detection, but not title source extraction.
- Branch value comes from rollout session metadata when present; no branch fallback store is used.
- Listing stays app-server-backed; write operations (`thread/start`, `thread/archive`, `thread/unarchive`, persistence calls) remain app-server RPC.
- Refresh hints merge app-server and rollout signals after app-server raw status normalization, with rollout able to fill missing activity, raise activity to unread, or override stale non-response-required app-server hints with fresher `processing` or `reviewing` activity.

## Error Handling
- Invalid override values must not disable Codex listing; fallback to app-server must apply.
- Parse failures must isolate to failing files and preserve valid thread rows from other files.
- Watcher overflow events may trigger full rescan to recover correctness.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendFileWatchIntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionsWatcherTest'`

[@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
[@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt
[@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/actions/new-thread.spec.md`
