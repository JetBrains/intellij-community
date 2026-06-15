---
name: Agent Threads Tree UI
description: Swing tree rendering, interaction, speed-search, and row affordance requirements for Agent Threads.
targets:
  - ../../sessions-toolwindow/src/**/*.kt
  - ../../sessions-toolwindow/testSrc/*.kt
  - ../../sessions/src/state/AgentSessionTreeUiStateService.kt
  - ../../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
---

# Agent Threads Tree UI

Status: Draft
Date: 2026-05-26

## Summary
The Agent Threads tree follows IntelliJ tree conventions while adding provider-specific thread presentation, loading indicators, provider-source warnings, and new-thread row actions.

## Requirements
- Rendering precedence is strict: blocking path error suppresses provider warnings and empty rows; warnings suppress empty rows; `More` rows preserve exact and unknown-count semantics.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Thread rows must render provider icon, normalized activity badge, relative update time, and tooltip status. Raw provider statuses must not create extra visible activity states.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt

- Long thread titles must clip within the visible tool window and must not create horizontal tree scrolling. Full clipped content remains available through the row tooltip.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt

- The tool window surfaces thread activity through chrome rather than a body strip:
  the title bar carries one counter action per active bucket (`Needs attention` covers `NEEDS_INPUT`/`REVIEWING`,
  `Running` covers `PROCESSING`, `Done` covers `UNREAD`). `READY` threads are not surfaced in title chrome.
  Thread rows render their actual activity badge, but title counters, stripe badges, and OS notifications use summary activity;
  sub-agent-only activity does not contribute to those global signals. Title counters and stripe badges ignore activity rows
  whose thread `updatedAt` is older than 3 days; the tree and OS notification transition tracking still use the full active
  thread state.
  All three counters are always visible so that bucket positions remain stable for muscle memory.
  Counters render as quiet inline title-bar signals: a small marker plus count, without chip fill or border.
  `Needs attention` uses the strongest treatment with a bold count; `Running` and `Done` use normal foreground counts;
  zero-count buckets render disabled rather than disappearing.
  Clicking a counter opens a popup listing only that bucket's threads.
  The stripe button icon uses collapsed notification precedence: it carries a `Needs attention` badge whenever any
  thread is in `NEEDS_INPUT` or `REVIEWING`, otherwise a `Done` badge whenever any thread is `UNREAD`, otherwise no badge.
  Stripe badges use the Agent Workbench activity colors: `Needs attention` uses the `NEEDS_INPUT` color and `Done` uses the
  `UNREAD` color. `PROCESSING` threads never badge the collapsed stripe button.
  When the IDE is not active, a thread's first transition into `Needs attention` or `Done` after the initial loaded
  baseline emits a per-thread OS system notification through platform system notifications. On platform implementations
  that support exact native activation callbacks, clicking the OS notification opens/focuses the matching chat tab by
  stable path/provider/thread id. Initial loaded rows and `Running` transitions do not notify.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsActivitySummaryTest.kt

- Source-frame main-toolbar Agent activity reuses these counter visuals and popup primitives, but its global scope and placement are owned by `../frame/agent-main-toolbar-activity.spec.md`.

- Selection and activation must match platform tree behavior: single-click selects, Enter/double-click opens openable rows, and double-click on openable parent rows prefers open/focus over expansion.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Chat tab selection controls when `Done` is acknowledged. Selecting an already `Done` thread marks it read immediately.
  If a selected thread transitions to `Done`, the tree keeps that state visible until selection leaves that chat tab,
  including switching to another editor or closing the selected tab.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreeStateControllerTest.kt

- Context menus must preserve multi-selection when invoked from an already selected row and retarget selection when invoked from an unselected row.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Project/worktree context menus must keep the platform `CopyReferencePopupGroup` for copy path/reference actions.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Tree speed search must use platform tree speed search, derive searchable text from rendered row labels, and match only at word starts or camel humps.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreeSnapshotTest.kt

- Speed search must reveal hidden matching project/thread rows behind `More` rows by using the shared visibility primitives.
  [@test] ../../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

- Project/worktree rows expose hover-or-selection new-thread affordances and loading indicators without changing normal tree selection semantics.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- The active tree view overlays currently open pending Agent Chat tabs as ephemeral placeholders. The archived view ignores them, and placeholders are not archive, cost, or row-action targets.

- Collapse/expand state must persist by normalized project path and must not store transient thread content.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeStatePersistenceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test "com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsSwing*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreeSnapshotTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshOnDemandIntegrationTest`

## References
- `agent-sessions.spec.md`
- `../core/agent-core-contracts.spec.md`
- `../actions/new-thread.spec.md`
- `../frame/agent-main-toolbar-activity.spec.md`
