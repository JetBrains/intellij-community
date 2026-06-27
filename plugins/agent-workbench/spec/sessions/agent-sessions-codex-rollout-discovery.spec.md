---
name: Codex Rollout Discovery
description: Requirements for Codex rollout parsing, watching, discovery, and project-file evidence.
targets:
  - ../../lib-agent/providers/codex/sessions/src/backend/rollout/**/*.kt
  - ../../lib-agent/filewatch/src/**/*.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/*Rollout*.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/backend/rollout/*.kt
  - ../../lib-agent/filewatch/testSrc/**/*.kt
---

# Codex Rollout Discovery

Status: Draft
Date: 2026-05-09

## Summary
Codex rollout files are parsed for discovery, project-file-change evidence, cost recovery, and local integration coverage. They do not create the primary thread list and do not drive Workbench source status.

## Requirements
- Rollout scan scope is limited to `~/.codex/sessions/**/rollout-*.jsonl`; sessions are filtered by normalized `cwd` matching the requested project/worktree path.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Thread id source is `session_meta.payload.id`; filename fallback is forbidden and files without that id are skipped.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Title extraction uses the first qualifying user message, with explicit thread-name updates overriding derived titles. If no title is found, fallback is `Thread <id-prefix>`.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Rollout review-mode and response-required needs-input hints must use current Codex rollout event shapes, including `entered_review_mode`, `exited_review_mode`, `request_user_input`, persisted `response_item` tool calls named `request_user_input`, approval events (`exec_approval_request`, `apply_patch_approval_request`, `request_permissions`, `elicitation_request`), and escalated tool-call arguments (`sandbox_permissions: require_escalated`).
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Rollout update events consumed by `CodexSessionSource` are discovery-only: they may trigger app-server thread refreshes and project-file refreshes, but rollout-discovered ids, activity hints, and presentation hints must not create persisted thread rows or status updates. Rollout discovery must not expose activity or presentation updates for already-known thread ids.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/rollout/CodexRolloutDiscoveryProviderTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexSessionSourceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Rebind candidates from rollout discovery are top-level CLI sessions only; parsed sub-agent sessions must not become automatic rebind targets.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/rollout/CodexRolloutDiscoveryProviderTest.kt

- Rollout watching uses `AgentWorkbenchDirectoryWatcher`; Java NIO `WatchService` must not be used directly. Refresh remains event-driven, not periodic polling.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt
  [@test] ../../lib-agent/filewatch/testSrc/AgentWorkbenchDirectoryWatcherTest.kt

- Active chat terminal refresh may watch concrete rollout files for live outline invalidation and project-file-change evidence. Activity-only or title-only rollout appends may emit neutral scoped active-thread invalidation events, but must not carry thread ids, activity updates, or presentation updates. Workbench uses Codex app-server `fs/watch`/`fs/changed` for app-server-backed filesystem notifications such as replace or rename updates.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexSessionSourceRealAppServerIntegrationTest.kt

- Codex app-server `fs/watch` is not treated as sufficient for rollout appends written through a long-lived file descriptor. On macOS, Workbench must keep the immediate file watcher fallback so project-file evidence can invalidate the UI before the Codex writer closes the rollout file.
  [@test] ../../lib-agent/filewatch/testSrc/impl/watchservice/MacOSXListeningWatchServiceTest.kt

- Path-scoped invalidation reparses dirty rollout paths even when file size and mtime are unchanged; overflow or ambiguous directory events may trigger a full rescan.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt

- Path-scoped rollout updates must carry project-file-change evidence when they observe a newly completed native mutating Codex tool call (`exec_command` or `apply_patch`); later status-only appends after the same completed tool must not repeat that evidence. JetBrains MCP tool calls observed as rollout `mcp_tool_call_end` events are not Codex mutation evidence: MCP mutations own their IDE/VFS refresh behavior through the tool implementation, and rollout parsing must not add a second session-source VFS refresh for them.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Local-gated real TUI integration verifies production rollout ingestion when a real `codex` CLI and PTY support are available; deterministic parser/backend tests remain the CI owner for event-shape matrices.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexSessionSourceRealTuiIntegrationTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test "com.intellij.platform.ai.agent.codex.sessions.CodexRollout*Test"`
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexRolloutDiscoveryProviderTest`
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.CodexSessionSourceRealTuiIntegrationTest`
- `./tests.cmd --module intellij.platform.ai.agent.filewatch.tests --test com.intellij.platform.ai.agent.filewatch.AgentWorkbenchDirectoryWatcherTest`

## References
- `agent-sessions-codex-rollout-source.spec.md`
- `agent-sessions-codex-activity.spec.md`
- `../actions/codex-thread-rebinding.spec.md`
