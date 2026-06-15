---
name: Agent Main Toolbar Activity
description: Requirements and UX rationale for global Agent activity counters in main toolbars.
targets:
  - ../../sessions-toolwindow/src/actions/AgentSessionsMainToolbarActivityGroup.kt
  - ../../sessions-toolwindow/src/ui/AgentSessionsTitleActions.kt
  - ../../sessions-toolwindow/src/ui/AgentSessionsActivityCounterComponent.kt
  - ../../sessions-toolwindow/resources/intellij.agent.workbench.sessions.toolwindow.xml
  - ../../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt
  - ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt
---

# Agent Main Toolbar Activity

Status: Draft
Date: 2026-05-29

## Summary
Source project frames can show a compact Agent activity group in the main toolbar when the user opts in. The dedicated Agent frame always shows the same compact group, backed by the Agent Threads chrome activity summary.

The group is a lightweight global Agent activity switcher: it helps users notice and reopen actionable Agent chats from any loaded project while they stay in source-code flow. The full cross-project task browser remains Agent Threads and the dedicated Agent frame.

## Goals
- Let users opt in to Agent task attention close to existing project workflow chrome.
- Let users jump to actionable Agent work from any source project frame.
- Keep Agent activity visible in the dedicated Agent frame main toolbar.
- Keep the main-toolbar signal compact while preserving detailed cross-project browsing in Agent Threads.

## Non-goals
- Filtering Agent activity by the current source project path.
- Changing Agent Threads title counters, collapsed stripe badge, or OS notification behavior.

## Requirements
- Sessions toolwindow plugin must register `AgentWorkbenchSessions.MainToolbar.Activity` in `MainToolbarLeft` after `MainToolbarVCSGroup`.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Main toolbar activity must expose exactly three stable counters: `Needs attention`, `Running`, and `Done`. The counters must reuse the Agent Threads title-bar counter action/component and bucket popup behavior.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Source project activity must be disabled by default and controlled by the Agent Workbench setting `Show Agent activity in main toolbar`.
  [@test] ../../sessions/testSrc/core/settings/AgentWorkbenchSettingsTest.kt
  [@test] ../../sessions/testSrc/settings/AgentWorkbenchSettingsConfigurableTest.kt

- Source project activity must be visible only for non-dedicated projects with an openable normalized source project path.
  Dedicated Agent frames must not exclude `AgentWorkbenchSessions.MainToolbar.Activity` from the main toolbar.
  They must show the group regardless of source-project path, source-toolbar setting, or current-run attention state.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Source-frame counts and popup rows must include fresh active rows from the current-run attention-gated summary for the selected bucket, regardless of the current source project path.
  Dedicated-frame counts and popup rows must include fresh active rows from the shared chrome activity summary for the selected bucket.
  Rows whose thread `updatedAt` is older than 3 days must not contribute to either main-toolbar activity group.
  Popup rows must keep project/worktree identity visible through the existing row label.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Even when the setting is enabled, the source project toolbar group must stay hidden until a fresh thread enters `Needs attention` after the initial loaded Agent activity baseline for the current IDE process. The group must hide again after all such attention rows leave the bucket or expire from the fresh chrome summary.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsActivitySummaryTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Zero-count counters must remain visible and disabled with their bucket tooltip while the group is visible, so counter positions are stable for muscle memory.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Clicking a non-empty counter must open the same bucket popup used by Agent Threads and let the user choose the chat tab to open or focus from the toolbar entry point.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

## User Experience
- The source project toolbar answers: did an Agent task become actionable during this IDE session?
- The same global counts may appear in multiple source project frames so users can switch to finished or waiting Agent work without first finding the dedicated frame or Agent Threads stripe.
- The dedicated Agent frame toolbar always shows the counters so users can inspect stripe activity without expanding Agent Threads.
- Activating a counter may open a chat from another project. The popup row must expose the destination project/worktree before the user chooses it.
- Users who need a full cross-project view use Agent Threads or the dedicated Agent frame.

## Data & Backend
- The group must use `normalizeOpenableSourceProjectPath` only to decide whether the current frame is a source project frame where the toolbar group can be shown.
- Activity rows must not be filtered by the current source project path.
- The source-frame toolbar consumes the current-run attention-gated summary, while the dedicated-frame toolbar consumes the shared chrome activity summary used by the Agent Threads stripe icon.
- Archived threads, stale rows older than 3 days, and `READY` activity are not surfaced in this toolbar group.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsMainToolbarActivityGroupTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest`

## References
- `../sessions/agent-sessions-tree.spec.md`
- `agent-dedicated-frame-project-switching.spec.md`
