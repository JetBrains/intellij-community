---
name: Codex Sessions Rollout Source
description: Codex thread list source and activity indicators for Agent Threads.
targets:
  - ../sessions/src/providers/codex/*.kt
  - ../codex/sessions/src/*.kt
  - ../sessions/src/SessionTreeStyle.kt
  - ../sessions/src/AgentSessionModels.kt
  - ../sessions/src/AgentSessionsService.kt
  - ../sessions/testSrc/*.kt
  - ../codex/sessions/testSrc/*.kt
---

# Codex Sessions Rollout Source

Status: Draft
Date: 2026-02-13

## Summary
Codex thread discovery for Agent Threads defaults to rollout files under `~/.codex/sessions` instead of app-server `thread/list`. The source computes thread activity (`unread`, `reviewing`, `processing`, `ready`) and maps it to indicator colors. Existing app-server loading remains available behind an alternative `SessionBackend` implementation.

## Goals
- Make Codex thread indicators reflect real activity based on rollout data.
- Keep app-server implementation in code as an alternate backend.
- Avoid archived-session handling in this iteration.
- Keep rollout backend changes independent from new-session action semantics.

## Non-goals
- Archived session browsing or unarchive actions.
- Claude behavior changes.
- Realtime push status updates from app-server notifications.

## Requirements
- Introduce `CodexSessionBackend` interface (singular naming) for Codex thread loading.
- Provide `CodexRolloutSessionBackend` as default backend and keep `CodexAppServerSessionBackend` as alternate.
- Rollout backend must scan only `~/.codex/sessions/**/rollout-*.jsonl`.
- Rollout backend must filter sessions by normalized `cwd` matching project/worktree path.
- Thread id must come from `session_meta.payload.id` (not rollout filename).
- Rollout backend must skip files missing `session_meta.payload.id` (no filename fallback).
- Thread activity precedence must be: `unread` > `reviewing` > `processing` > `ready`.
- Session tree indicator colors must match CodexMonitor classes:
  - `unread`: blue (`#4DA3FF`)
  - `reviewing`: teal (`#2FD1C4`)
  - `processing`: orange (`#FF9F43`)
  - `ready`: green (`#3FE47E`)
- New-session action semantics (including Codex `Codex (Full Auto)` parameters) are defined in `spec/actions/new-thread.spec.md` and are backend-invariant.
- Existing thread open behavior remains `codex resume <threadId>`.

## Data & Backend
- Rollout backend computes `updatedAt` from latest event timestamp with file mtime fallback.
- Rollout backend derives title from user-message content with bounded trim and fallback `Thread <id-prefix>`.
- Rollout backend carries branch from session meta when present; no branch fallback source is used.
- `CodexSessionSource` maps rollout backend data directly and does not use `CodexSessionBranchStore` fallback.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionCliTest'`

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/actions/new-thread.spec.md`
