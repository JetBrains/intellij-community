---
name: Codex Session Activity
description: Requirements for mapping Codex app-server and rollout status signals to Workbench thread activity.
targets:
  - ../../sessions/src/service/*.kt
  - ../../sessions/testSrc/*.kt
  - ../../common/src/*.kt
  - ../../common/testSrc/*.kt
  - ../../codex/common/src/*.kt
  - ../../codex/sessions/src/backend/**/*.kt
  - ../../codex/sessions/testSrc/backend/*.kt
  - ../../codex/sessions/testSrc/backend/appserver/*.kt
  - ../../codex/sessions/testSrc/*.kt
  - ../../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt
---

# Codex Session Activity

Status: Draft
Date: 2026-05-09

## Summary
Workbench shows normalized activity (`NEEDS_INPUT`, `UNREAD`, `REVIEWING`, `PROCESSING`, `READY`) rather than raw Codex status kinds. App-server status is primary when it is current; rollout, notifications, and scoped refreshes may fill gaps or replace stale activity when allowed by refresh-hint rules.

## Requirements
- `CodexThreadStatusKind` is raw provider state only. It must not create extra Agent Threads activity states.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- `CodexThreadActiveFlag.WAITING_ON_APPROVAL` and `WAITING_ON_USER_INPUT` are response-required signals and map to needs input when no higher-priority internal rule applies.
  [@test] ../../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt

- `REVIEWING` is a derived Workbench activity from app-server snapshot or rollout review-mode signals, never a raw Codex status kind.
  [@test] ../../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt

- Activity precedence is needs input, reviewing, processing, passive unread assistant output, then ready. Structured Codex plan items are needs-input attention, not passive unread output. `SYSTEM_ERROR`, `NOT_LOADED`, and `UNKNOWN` normalize to ready unless a higher-priority signal is present.
  [@test] ../../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- App-server snapshots that include turn details must derive work-in-progress from the turn list. A stale raw `ACTIVE` thread status must not mask a turn-derived unread or ready state when no turn is still in progress.
  [@test] ../../codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- App-server `thread/read includeTurns` detects pending plans from structured `plan`/`Plan` items. Rollout fallback detects plans from structured `item_completed` events whose nested item type is `Plan`; assistant text tags are not parsed for activity. A matching or legacy no-id completed/aborted rollout turn clears pending plan attention.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Codex activity projection is shared between app-server turn snapshots and rollout fallback. Completion is turn-aware when `turn_id` is present: a stale completion from an earlier turn must not clear a newer processing turn or pending plan, while a matching or legacy completed turn closes earlier incomplete work.
  [@test] ../../codex/sessions/testSrc/backend/CodexThreadActivityProjectionTest.kt
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- App-server `thread/started` and `thread/status/changed` notifications may seed raw status/flag hints. Notification hints are timestamped, short-lived, path-normalized, and must not override a newer known thread seed. Snapshot-only promotions such as unread assistant output and review mode require `thread/read` or rollout fallback.
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- App-server activity remains primary for overlapping thread ids when it is current, except rollout may provide missing activity, raise stale or missing activity to needs input, or override stale app-server activity with fresher processing/reviewing/done-output. Response-required app-server attention remains primary when it is current.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRefreshHintsTest.kt
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt

- Thread-scoped app-server refresh for a grouped sub-agent child must return the folded parent thread. Partial parent updates merge returned sub-agents by id with existing siblings so one child status update cannot drop other children.
  Folded sub-agent activity is rendered only on the sub-agent tree row. It must not contribute to parent row activity, parent summary activity, tool-window counters, stripe badges, activity menu rows, or OS notifications.
  [@test] ../../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Session-tree Codex badge fallback colors are needs input `#588CF3` light/`#548AF7` dark, done/unread `#55A76A` light/`#5FAD65` dark, reviewing `#8F5AE5`, and processing `#FFAF0F` light/`#F2C55C` dark; ready threads show the plain provider icon without a badge.
  [@test] ../../common/testSrc/AgentThreadActivityPresentationTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivityResolverTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProviderTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRefreshHintsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshCoordinatorTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsCodexActivityRenderingIntegrationTest`

## References
- `agent-sessions-codex-rollout-source.spec.md`
- `agent-sessions-codex-rollout-hints.spec.md`
- `agent-sessions-tree.spec.md`
