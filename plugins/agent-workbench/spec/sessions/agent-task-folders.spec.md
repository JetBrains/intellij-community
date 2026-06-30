---
name: Agent Task Folders
description: Requirements for task folders that group Agent Threads work without changing the one-thread-one-session model.
targets:
  - ../../sessions-task-folders/src/AgentTaskFolders.kt
  - ../../sessions/src/service/AgentSessionArchiveService.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../sessions-toolwindow/src/tree/SessionTree.kt
  - ../../sessions-toolwindow/src/tree/SessionTreeSearch.kt
  - ../../sessions-toolwindow/src/ui/AgentSessionsTree*.kt
  - ../../sessions-toolwindow/src/actions/AgentSessionsTreePopupActions.kt
  - ../../sessions-toolwindow/src/actions/*TaskFolder*.kt
  - ../../sessions-toolwindow/src/actions/SessionTreeActionTargets.kt
  - ../../sessions-toolwindow/resources/intellij.agent.workbench.sessions.toolwindow.xml
  - ../../sessions-task-folders/testSrc/AgentTaskFolderServiceTest.kt
  - ../../sessions-toolwindow/testSrc/AgentSessionsTreeSnapshotTest.kt
  - ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
---

# Agent Task Folders

Status: Draft
Date: 2026-06-28

## Summary

Agent Task Folders are lightweight persisted groups inside Agent Threads history. A folder has an opaque global KSUID identity, can be
associated with normalized project or worktree paths, can hold explicit thread assignments, and has a simple `In Progress` -> `Done` lifecycle. Folders organize existing threads; they do not
create a new top-level Agent Workbench entity and do not inject context into prompts automatically.

## Requirements

- The folder service must persist app-level, non-roamable folder state keyed by global folder id. New folder ids must be generated as
  platform KSUIDs; persisted folder ids must be trimmed and accepted only if they match KSUID shape. Folder path associations, names,
  assignment thread ids, and metadata keys must be trimmed or normalized; invalid or orphaned persisted records must be dropped on load.
  [@test] ../../sessions-task-folders/testSrc/AgentTaskFolderServiceTest.kt

- A thread may be assigned to at most one in-progress task folder per normalized path/provider/thread id. Assigning a thread to another
  folder must move it from the previous folder. Folder rename, metadata, delete, and status mutations must address the global folder id,
  not the render path. Done folders must be hidden from the active tree and reject new assignments.
  [@test] ../../sessions-task-folders/testSrc/AgentTaskFolderServiceTest.kt

- The active Agent Threads tree must render in-progress task folders under their owning project or worktree. Loaded assigned threads must
  appear as children of the folder and must be removed from the normal ungrouped thread rows and `More` counts. Assigned threads that are
  not loaded are not represented by synthetic rows.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreeSnapshotTest.kt

- In current-project-only scope, project-level task folders must flatten with the project rows while keeping assigned thread rows under the
  folder. Folder rows are searchable by folder name and copy/prompt path context resolves to the folder's owning path.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreeSnapshotTest.kt

- Project and worktree rows must offer `New Task Folder`. The Agent Threads title toolbar must also offer `New Task Folder` for the resolved
  current source project path so users can create a task folder when no project/worktree row is available. Thread rows must offer
  `Move to Task Folder` for in-progress folders on the same path and `Remove from Task Folder` for assigned threads. Folder rows must offer
  rename, delete, explicit metadata set/delete, and mark done actions. The metadata set dialog must expose an editable metadata key combo
  with `issue` and `review` key presets while persisting ordinary string key/value metadata. Custom typed keys are stored as entered after trimming.
  User-visible action text and dialog text must live in `AgentSessionsBundle.properties`.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Agent/tool integrations that expose task-folder move/remove operations for arbitrary thread ids must validate against loaded active threads
  in the current project/worktree path and must not create synthetic assignments for unloaded, archived, pending, or cross-path threads.

- Dragging active thread rows onto an in-progress task folder on the same path must perform the same move as `Move to Task Folder`. If the
  dragged row is selected, all selected active same-path thread rows move together; if it is not selected, only the dragged row moves.
  Mixed-path selections and drops onto other paths or done folders must be rejected.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Marking a folder done must archive all assigned threads through the normal archive service, including assignments whose rows are not
  currently loaded. The folder status may become `Done` only after the archive service reports that every requested target was archived.
  Empty folders may be marked done immediately.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

## Non-goals

- Task folders must not auto-add previous threads, commits, issue data, or folder metadata to new prompts.
- Task folders must not introduce a new top-level project/task entity outside Agent Threads history.
- Provider-specific issue integrations are out of scope; metadata is explicit string key/value data.

## Testing / Local Run

- `./tests.cmd --module intellij.agent.workbench.sessions.task.folders.tests --test com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderServiceTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreeSnapshotTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreePopupActionsTest`
