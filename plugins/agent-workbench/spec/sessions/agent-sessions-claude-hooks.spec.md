---
name: Agent Sessions Claude Hooks
description: Requirements for Claude Code hook wiring used by Agent Workbench sessions.
targets:
  - ../../claude/sessions/src/ClaudeAgentSessionProviderDescriptor.kt
  - ../../claude/sessions/src/ClaudeHookBridge.kt
  - ../../claude/sessions/src/ClaudeHookHttpRequestHandler.kt
  - ../../claude/sessions/src/backend/store/ClaudeStoreSessionBackend.kt
  - ../../claude/sessions/resources/intellij.agent.workbench.claude.sessions.xml
---

# Agent Sessions Claude Hooks

Status: Draft
Date: 2026-06-17

## Summary

Agent Workbench must use Claude Code hooks for immediate Claude session status and project-file refresh signals that are not reliably available through transcript file watching. Hooks are installed only for Claude sessions launched by Agent Workbench and are passed with the Claude CLI `--settings` flag.

## Goals

- Detect Claude user-interaction tools before the transcript watcher catches up, so the thread can show `NEEDS_INPUT` promptly.
- Detect Claude project-mutating tools after they run, so Agent Workbench can schedule exact VFS/project-file refreshes.
- Keep existing Claude JSONL/session-index watching for thread list, title, activity reconciliation, cost, archive state, and fallback refresh.

## Non-goals

- Do not write or mutate `.claude/settings.local.json`.
- Do not install hooks for Claude CLI invocations that are not launched through Agent Workbench session launch specs.
- Do not use hooks to replace transcript parsing as the authoritative session state source.

## Requirements

- Agent Workbench-launched Claude sessions must pass a generated hook settings file through `claude --settings <file>`.
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- The generated settings must include a `PreToolUse` hook with matcher `AskUserQuestion|ExitPlanMode` and a `PostToolUse` hook with matcher `Write|Edit|MultiEdit|NotebookEdit`.
  [@test] ../../claude/sessions/testSrc/ClaudeHookBridgeTest.kt

- Hook settings must be per-launch and authenticated with a bearer token bound to the expected Claude session id.
  [@test] ../../claude/sessions/testSrc/ClaudeHookBridgeTest.kt

- Hook bearer tokens must be invalidated when Agent Workbench observes the launched terminal session closing.
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- `--settings` must be inserted before Claude's `--` prompt separator. Commands that contain `--bare` or `--safe-mode` must not receive hook settings because those modes disable hooks.
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- `PreToolUse` events for `AskUserQuestion` or `ExitPlanMode` must emit a path-scoped `HINTS_CHANGED` update with `NEEDS_INPUT` activity for the hook session id and must not force a provider refresh by setting `threadIds`.
  [@test] ../../claude/sessions/testSrc/ClaudeHookBridgeTest.kt

- `PostToolUse` events for `Write`, `Edit`, `MultiEdit`, or `NotebookEdit` must emit a path-scoped `HINTS_CHANGED` update with `mayHaveChangedProjectFiles=true`. When Claude provides a relative `file_path` or `notebook_path`, Agent Workbench must resolve it against the hook `cwd` and pass the exact changed project file path.
  [@test] ../../claude/sessions/testSrc/ClaudeHookBridgeTest.kt

- Unsupported or incomplete hook events that pass authentication must be accepted without emitting an update. Debug logging must include the ignored reason, hook event name, tool name, and `cwd`.
  [@test] ../../claude/sessions/testSrc/ClaudeHookBridgeTest.kt

- The Claude store backend must merge hook update events into normal source updates, while the active-thread immediate file watch path remains disabled for Claude.
  [@test] ../../claude/sessions/testSrc/ClaudeStoreSessionBackendTest.kt

## Data & Backend

- Hook payloads are accepted through a localhost built-in-server HTTP endpoint under `/agent-workbench/claude/hook`.
- The endpoint accepts only POST requests with an `Authorization: Bearer <token>` header whose token is registered for the payload session id.
- The hook parser must tolerate Claude payload field variants used by current and nearby CLI versions: `hook_event_name`/`hookEventName`, `session_id`/`sessionId`, `tool_name`/`toolName`, and `tool_input`/`toolInput`.

## Error Handling

- Missing or invalid bearer tokens must be rejected with an unauthorized hook result.
- Malformed JSON or missing session id must be rejected as bad requests. Authenticated but unsupported or incomplete hook payloads must be accepted without emitting updates.
- Hook delivery failures must not block Claude Code; the generated HTTP hook has a short timeout and the JSONL watcher remains the fallback.

## Testing / Local Run

- Focused tests: `./tests.cmd --module intellij.agent.workbench.claude.sessions.tests --test com.intellij.agent.workbench.claude.sessions.ClaudeAgentSessionProviderDescriptorTest;com.intellij.agent.workbench.claude.sessions.ClaudeHookBridgeTest;com.intellij.agent.workbench.claude.sessions.ClaudeStoreSessionBackendTest`

## Open Questions / Risks

- Claude Code does not hook arbitrary assistant text questions; only tool calls such as `AskUserQuestion` and `ExitPlanMode` are covered.
- If the IDE built-in server is unavailable or the HTTP hook times out, the JSONL watcher remains the fallback.
