---
name: Codex Rollout Refresh Hints
description: Requirements for Codex rollout parsing, watching, and refresh-hint consumption.
targets:
  - ../../codex/sessions/src/backend/rollout/**/*.kt
  - ../../filewatch/src/**/*.kt
  - ../../codex/sessions/testSrc/*Rollout*.kt
  - ../../codex/sessions/testSrc/backend/rollout/*.kt
  - ../../filewatch/testSrc/**/*.kt
---

# Codex Rollout Refresh Hints

Status: Draft
Date: 2026-05-09

## Summary
Codex rollout files are parsed for refresh hints only: pending/concrete tab rebinding, needs-input/activity uplift, unread done-output hints, and local integration coverage. They do not create the primary thread list.

## Requirements
- Rollout scan scope is limited to `~/.codex/sessions/**/rollout-*.jsonl`; sessions are filtered by normalized `cwd` matching the requested project/worktree path.
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Thread id source is `session_meta.payload.id`; filename fallback is forbidden and files without that id are skipped.
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Title extraction uses the first qualifying user message, with explicit thread-name updates overriding derived titles. If no title is found, fallback is `Thread <id-prefix>`.
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Rollout review-mode and response-required needs-input hints must use current Codex rollout event shapes, including `entered_review_mode`, `exited_review_mode`, `request_user_input`, persisted `response_item` tool calls named `request_user_input`, approval events (`exec_approval_request`, `apply_patch_approval_request`, `request_permissions`, `elicitation_request`), and escalated tool-call arguments (`sandbox_permissions: require_escalated`).
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Rollout hints may be consumed for pending-tab rebinding, concrete `/new` rebinding, and Codex activity projection. Rollout-discovered ids must not create persisted thread rows.
  [@test] ../../codex/sessions/testSrc/backend/rollout/CodexRolloutRefreshHintsProviderTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Rebind candidates from rollout hints are top-level CLI sessions only; parsed sub-agent sessions may contribute hierarchy/activity data but must not become automatic rebind targets.
  [@test] ../../codex/sessions/testSrc/backend/rollout/CodexRolloutRefreshHintsProviderTest.kt

- Rollout watching uses `AgentWorkbenchDirectoryWatcher`; Java NIO `WatchService` must not be used directly. Refresh remains event-driven, not periodic polling.
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt
  [@test] ../../filewatch/testSrc/AgentWorkbenchDirectoryWatcherTest.kt

- Path-scoped invalidation reparses dirty rollout paths even when file size and mtime are unchanged; overflow or ambiguous directory events may trigger a full rescan.
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt

- Path-scoped rollout updates must carry project-file-change evidence when they observe a newly completed native mutating Codex tool call (`exec_command` or `apply_patch`); later status-only appends after the same completed tool must not repeat that evidence. JetBrains MCP tool calls observed as rollout `mcp_tool_call_end` events are not Codex mutation evidence: MCP mutations own their IDE/VFS refresh behavior through the tool implementation, and rollout parsing must not add a second session-source VFS refresh for them.
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Local-gated real TUI integration verifies production rollout ingestion when a real `codex` CLI and PTY support are available; deterministic parser/backend tests remain the CI owner for event-shape matrices.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRealTuiIntegrationTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test "com.intellij.agent.workbench.codex.sessions.CodexRollout*Test"`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProviderTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRealTuiIntegrationTest`
- `./tests.cmd --module intellij.agent.workbench.filewatch.tests --test com.intellij.agent.workbench.filewatch.AgentWorkbenchDirectoryWatcherTest`

## References
- `agent-sessions-codex-rollout-source.spec.md`
- `agent-sessions-codex-activity.spec.md`
- `../actions/codex-thread-rebinding.spec.md`
