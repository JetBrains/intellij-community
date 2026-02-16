---
name: Codex Sessions Rollout Source
description: Codex thread list source and activity indicators for Agent Threads.
targets:
  - ../sessions/src/providers/codex/*.kt
  - ../codex/common/src/CodexAppServerClient.kt
  - ../codex/sessions/src/**/*.kt
  - ../sessions/src/SessionTreeStyle.kt
  - ../sessions/src/AgentSessionModels.kt
  - ../sessions/src/AgentSessionsService.kt
  - ../sessions/testSrc/*.kt
  - ../codex/sessions/testSrc/*.kt
---

# Codex Sessions Rollout Source

Status: Draft
Date: 2026-02-16

## Summary
Codex thread discovery for Agent Threads defaults to rollout files under `~/.codex/sessions` instead of app-server `thread/list`. The source computes thread activity (`unread`, `reviewing`, `processing`, `ready`) and maps it to indicator colors. Existing app-server loading remains available behind an alternative `SessionBackend` implementation. Title extraction semantics are aligned with Codex rollout parsing behavior. Archive operations route through app-server `thread/archive`, and the shared app-server process is started lazily and stopped on idle timeout.

## Goals
- Make Codex thread indicators reflect real activity based on rollout data.
- Keep app-server implementation in code as an alternate backend.
- Support archive action for rollout-discovered threads without changing list backend default.
- Keep rollout backend changes independent from new-session action semantics.

## Non-goals
- Archived session browsing or unarchive actions.
- Claude behavior changes.
- Realtime push status updates from app-server notifications.

## Requirements
- Introduce `CodexSessionBackend` interface (singular naming) for Codex thread loading.
- Provide `CodexRolloutSessionBackend` as default backend and keep `CodexAppServerSessionBackend` as alternate.
- Keep backend implementations separated by package:
  - `com.intellij.agent.workbench.codex.sessions.backend.rollout`
  - `com.intellij.agent.workbench.codex.sessions.backend.appserver`
- Backend selection must default to rollout and only switch to app-server when `agent.workbench.codex.sessions.backend=app-server` is explicitly set.
- Unknown backend override values must log a warning and fall back to rollout.
- Rollout backend must scan only `~/.codex/sessions/**/rollout-*.jsonl`.
- Rollout backend must filter sessions by normalized `cwd` matching project/worktree path.
- Rollout backend must support multi-path prefetch and return per-path filtered thread lists from a shared scan.
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
- Rollout backend computes `updatedAt` from latest event timestamp with file mtime fallback.
- Rollout backend derives title from the first qualifying `event_msg.user_message`, using Codex marker stripping and session-prefix filtering rules.
- `response_item` entries contribute to unread/activity timing but do not provide title source data.
- Rollout backend prefetch for multiple paths uses one filesystem scan and groups parsed threads by normalized `cwd`.
- Rollout backend carries branch from session meta when present; no branch fallback source is used.
- `CodexSessionSource` maps rollout backend data directly and does not use `CodexSessionBranchStore` fallback.
- Codex thread listing remains rollout-backed by default; write operations (`thread/start`, `thread/archive`, and persistence calls) use app-server RPC.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionCliTest'`

[@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/actions/new-thread.spec.md`
