---
name: Agent Threads Tree UI
description: Swing tree rendering, interaction, speed-search, and row affordance requirements for Agent Threads.
targets:
  - ../sessions-toolwindow/src/**/*.kt
  - ../sessions-toolwindow/testSrc/*.kt
  - ../sessions/src/state/AgentSessionTreeUiStateService.kt
  - ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
---

# Agent Threads Tree UI

Status: Draft
Date: 2026-05-09

## Summary
The Agent Threads tree follows IntelliJ tree conventions while adding provider-specific thread presentation, loading indicators, warnings, and new-thread row actions.

## Requirements
- Rendering precedence is strict: blocking path error suppresses provider warnings and empty rows; warnings suppress empty rows; `More` rows preserve exact and unknown-count semantics.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Thread rows must render provider icon, normalized activity badge, relative update time, and tooltip status. Raw provider statuses must not create extra visible activity states.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsCodexActivityRenderingIntegrationTest.kt

- The tool-window header must summarize known thread activity without adding tree levels: `NEEDS_INPUT` and `REVIEWING` appear under `Needs attention`,
  `PROCESSING` appears under `Running`, and passive `UNREAD`/`READY` threads keep the header visible through `Done`/`Idle` counters.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsActivitySummaryTest.kt

- Selection and activation must match platform tree behavior: single-click selects, Enter/double-click opens openable rows, and double-click on openable parent rows prefers open/focus over expansion.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Context menus must preserve multi-selection when invoked from an already selected row and retarget selection when invoked from an unselected row.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Project/worktree context menus must keep the platform `CopyReferencePopupGroup` for copy path/reference actions.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Tree speed search must use platform tree speed search, derive searchable text from rendered row labels, and match only at word starts or camel humps.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsTreeSnapshotTest.kt

- Speed search must reveal hidden matching project/thread rows behind `More` rows by using the shared visibility primitives.
  [@test] ../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

- Project/worktree rows expose hover-or-selection new-thread affordances and loading indicators without changing normal tree selection semantics.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Collapse/expand state must persist by normalized project path and must not store transient thread content.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsSwingTreeStatePersistenceTest.kt
  [@test] ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test "com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsSwing*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreeSnapshotTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshOnDemandIntegrationTest`

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-core-contracts.spec.md`
- `spec/actions/new-thread.spec.md`
