---
name: Agent Main Toolbar Activity
description: Requirements and UX rationale for global Agent activity counters in source-project main toolbars.
targets:
  - ../sessions-toolwindow/src/actions/AgentSessionsMainToolbarActivityGroup.kt
  - ../sessions-toolwindow/src/ui/AgentSessionsTitleActions.kt
  - ../sessions-toolwindow/src/ui/AgentSessionsActivityCounterComponent.kt
  - ../sessions-toolwindow/resources/intellij.agent.workbench.sessions.toolwindow.xml
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt
  - ../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt
---

# Agent Main Toolbar Activity

Status: Draft
Date: 2026-05-26

## Summary
Source project frames show a compact Agent activity group in the main toolbar. The group is a lightweight global Agent activity switcher: it helps users notice and reopen actionable Agent chats from any loaded project while they stay in source-code flow.

The full cross-project task browser remains Agent Threads and the dedicated Agent frame. The toolbar group is only the small always-visible activity entry point.

## Goals
- Show Agent task attention close to existing project workflow chrome.
- Let users jump to actionable Agent work from any source project frame.
- Keep the main-toolbar signal compact while preserving detailed cross-project browsing in Agent Threads.

## Non-goals
- Filtering Agent activity by the current source project path.
- Showing the activity group in the dedicated Agent frame.
- Changing Agent Threads title counters, collapsed stripe badge, or OS notification behavior.

## Requirements
- Sessions toolwindow plugin must register `AgentWorkbenchSessions.MainToolbar.Activity` in `MainToolbarLeft` after `MainToolbarVCSGroup`.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Source project main toolbar must expose exactly three stable counters: `Needs attention`, `Running`, and `Done`. The counters must reuse the Agent Threads title-bar counter action/component and bucket popup behavior.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- The activity group must be visible only for non-dedicated projects with an openable normalized source project path. Dedicated Agent frames must exclude `AgentWorkbenchSessions.MainToolbar.Activity` from the main toolbar through `projectFrameActionExclusion`.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt
  [@test] ../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Counts and popup rows must include fresh active rows from the shared chrome activity summary for the selected bucket, regardless of the current source project path. Rows whose thread `updatedAt` is older than 3 days must not contribute to the source-frame toolbar. Popup rows must keep project/worktree identity visible through the existing row label.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Zero-count counters must remain visible and disabled with their bucket tooltip, so counter positions are stable for muscle memory.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

- Clicking a non-empty counter must open the same bucket popup used by Agent Threads and let the user choose the chat tab to open or focus from the toolbar entry point.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsMainToolbarActivityGroupTest.kt

## User Experience
- The source project toolbar answers: is any Agent task actionable now?
- The same global counts may appear in multiple source project frames so users can switch to finished or waiting Agent work without first finding the dedicated frame or Agent Threads stripe.
- Activating a counter may open a chat from another project. The popup row must expose the destination project/worktree before the user chooses it.
- Users who need a full cross-project view use Agent Threads or the dedicated Agent frame.

## Data & Backend
- The group must use `normalizeOpenableSourceProjectPath` only to decide whether the current frame is a source project frame where the toolbar group can be shown.
- Activity rows must not be filtered by the current source project path.
- The counters consume fresh active thread activity summaries. Archived threads, stale rows older than 3 days, and `READY` activity are not surfaced in this toolbar group.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsMainToolbarActivityGroupTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest`

## References
- `spec/agent-sessions-tree.spec.md`
- `spec/agent-dedicated-frame-project-switching.spec.md`
