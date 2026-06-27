---
name: Codex Session Activity
description: Requirements for mapping Codex app-server and rollout status signals to Workbench thread activity.
targets:
  - ../../sessions/src/service/*.kt
  - ../../sessions/testSrc/*.kt
  - ../../lib-agent/core/src/*.kt
  - ../../lib-agent/common/testSrc/*.kt
  - ../../codex/common/src/*.kt
  - ../../lib-agent/providers/codex/sessions/src/backend/**/*.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/backend/*.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/backend/appserver/*.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/*.kt
  - ../../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt
---

# Codex Session Activity

Status: Draft
Date: 2026-05-09

## Summary
Workbench shows normalized activity (`NEEDS_INPUT`, `UNREAD`, `REVIEWING`, `PROCESSING`, `READY`) rather than raw Codex status kinds. App-server snapshots and app-server notifications are the authority for Workbench Codex status; rollout/filewatch signals may request discovery refreshes or project-file refreshes, but they must not supply thread activity or presentation updates to `CodexSessionSource`.

## Requirements
- `CodexThreadStatusKind` is raw provider state only. It must not create extra Agent Threads activity states.
  [@test] ../../lib-agent/providers/codex/common/testSrc/CodexAppServerProtocolTest.kt

- `CodexThreadActiveFlag.WAITING_ON_APPROVAL` and `WAITING_ON_USER_INPUT` are response-required signals and map to needs input when no higher-priority internal rule applies.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt

- `REVIEWING` is a derived Workbench activity from app-server snapshot or rollout review-mode signals, never a raw Codex status kind.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt

- Activity precedence is needs input, reviewing, processing, passive unread assistant output, then ready. Structured Codex plan items are needs-input attention, not passive unread output. `SYSTEM_ERROR`, `NOT_LOADED`, and `UNKNOWN` normalize to ready unless a higher-priority signal is present.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- App-server snapshots that include turn details must derive work-in-progress from the turn list. A stale raw `ACTIVE` thread status must not mask a turn-derived unread or ready state when no turn is still in progress.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/CodexSessionActivityResolverTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- App-server `thread/read includeTurns` detects pending plans from structured `plan`/`Plan` items. Rollout parsing may detect plans from structured `item_completed` events whose nested item type is `Plan` for rollout backend coverage, but rollout-derived plan state must not drive Workbench source status. Assistant text tags are not parsed for activity. Structured plan attention remains needs-input after matching or legacy no-id completed/aborted rollout turns because Codex's post-plan implementation choice is local TUI state, not a persisted `request_user_input` signal. A later user message clears pending plan attention.
  [@test] ../../lib-agent/providers/codex/common/testSrc/CodexAppServerProtocolTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Codex activity projection is shared between app-server turn snapshots and rollout-backend parsing. Completion is turn-aware when `turn_id` is present: a stale completion from an earlier turn must not clear a newer processing turn or pending plan, while a matching or legacy completed turn closes earlier incomplete work such as processing/tool-call attention without clearing structured plan attention.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/CodexThreadActivityProjectionTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- App-server `thread/started` and `thread/status/changed` notifications may seed raw status/flag hints. Notification hints are timestamped, short-lived, path-normalized, and must not override a newer known thread seed. Snapshot-only promotions such as unread assistant output and review mode require app-server `thread/read`.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/backend/appserver/CodexAppServerRefreshHintsProviderTest.kt

- `CodexSessionSource` must prefetch activity/rebind refresh hints from the app-server provider only. Rollout discovery events forwarded by the source are discovery-only `THREADS_CHANGED` events with scoped paths or project-file-change evidence; source-level forwarding must drop rollout thread ids, activity updates, and presentation updates.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexSessionSourceRefreshHintsTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt

- Thread-scoped app-server refresh for a grouped sub-agent child must return the folded parent thread. Partial parent updates merge returned sub-agents by id with existing siblings so one child status update cannot drop other children.
  Folded sub-agent activity is rendered only on the sub-agent tree row. It must not contribute to parent row activity, parent summary activity, tool-window counters, stripe badges, activity menu rows, or OS notifications.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Session-tree Codex badge colors use platform `IconBadge` semantics: processing uses `IconBadge.successBackground`
  (`#55A76A` light/`#5FAD65` dark), needs input and reviewing use `IconBadge.warningBackground`
  (`#FFAF0F` light/`#F2C55C` dark), and done/unread uses `IconBadge.infoBackground`
  (`#588CF3` light/`#548AF7` dark); ready threads show the plain provider icon without a badge.
  [@test] ../../lib-agent/common/testSrc/AgentThreadActivityPresentationTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.backend.CodexSessionActivityResolverTest`
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProviderTest`
- `./tests.cmd --module intellij.platform.ai.agent.codex.sessions.tests --test com.intellij.platform.ai.agent.codex.sessions.CodexSessionSourceRefreshHintsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshCoordinatorTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsCodexActivityRenderingIntegrationTest`

## References
- `agent-sessions-codex-rollout-source.spec.md`
- `agent-sessions-codex-rollout-discovery.spec.md`
- `agent-sessions-tree.spec.md`
