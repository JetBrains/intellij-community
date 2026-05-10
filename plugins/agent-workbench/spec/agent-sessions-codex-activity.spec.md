---
name: Codex Session Activity
description: Requirements for mapping Codex app-server and rollout status signals to Workbench thread activity.
targets:
  - ../codex/common/src/*.kt
  - ../codex/sessions/src/backend/**/*.kt
  - ../codex/sessions/testSrc/backend/*.kt
  - ../codex/sessions/testSrc/backend/appserver/*.kt
  - ../codex/sessions/testSrc/*.kt
  - ../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt
---

# Codex Session Activity

Status: Draft
Date: 2026-05-09

## Summary
Workbench shows normalized activity (`UNREAD`, `REVIEWING`, `PROCESSING`, `READY`) rather than raw Codex status kinds. App-server status is primary; rollout may fill gaps or uplift stale non-response-required activity when allowed by refresh-hint rules.

## Requirements
- `CodexThreadStatusKind` is raw provider state only. It must not create extra Agent Threads activity states.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- `CodexThreadActiveFlag.WAITING_ON_APPROVAL` and `WAITING_ON_USER_INPUT` are response-required signals and map to unread when no higher-priority internal rule applies.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt

- `REVIEWING` is a derived Workbench activity from app-server snapshot or rollout review-mode signals, never a raw Codex status kind.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt

- Activity precedence is response-required unread, reviewing, processing, passive unread assistant output, then ready. `SYSTEM_ERROR`, `NOT_LOADED`, and `UNKNOWN` normalize to ready unless a higher-priority signal is present.
  [@test] ../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- App-server `thread/started` and `thread/status/changed` notifications may seed raw status/flag hints. Snapshot-only promotions such as unread assistant output and review mode require `thread/read` or rollout fallback.
  [@test] ../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- App-server activity remains primary for overlapping thread ids, except rollout may provide missing activity, raise activity to unread, or override stale non-response-required app-server activity with fresher processing/reviewing.
  [@test] ../codex/sessions/testSrc/CodexSessionSourceRefreshHintsTest.kt
  [@test] ../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt

- Session-tree Codex badge colors are unread `#4DA3FF`, reviewing `#2FD1C4`, processing `#FF9F43`, and ready `#3FE47E`.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivityResolverTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProviderTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRefreshHintsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsCodexActivityRenderingIntegrationTest`

## References
- `spec/agent-sessions-codex-rollout-source.spec.md`
- `spec/agent-sessions-codex-rollout-hints.spec.md`
- `spec/agent-sessions-tree.spec.md`
