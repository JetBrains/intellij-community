---
name: Dedicated Frame Project Switching
description: Requirements for dedicated-frame switching UX and init-time main-toolbar pruning.
targets:
  - ../../platform/project-frame/src/com/intellij/openapi/wm/ex/ProjectFrameCapabilities.kt
  - ../../platform/project-frame/src/com/intellij/openapi/wm/ex/ProjectFrameActionExclusions.kt
  - ../../platform/project-frame/resources/intellij.platform.projectFrame.xml
  - ../../platform/ide-core-impl/src/com/intellij/ide/impl/OpenProjectTask.kt
  - ../../platform/platform-impl/src/com/intellij/ide/RecentProjectMetaInfo.kt
  - ../../platform/platform-impl/src/com/intellij/ide/RecentProjectsManagerBase.kt
  - ../../platform/platform-impl/src/com/intellij/ide/ActiveWindowsWatcher.java
  - ../../platform/platform-impl/src/com/intellij/openapi/project/impl/IdeProjectFrameAllocator.kt
  - ../../platform/platform-impl/src/com/intellij/openapi/wm/impl/ProjectFrameHelper.kt
  - ../../platform/platform-impl/src/com/intellij/openapi/wm/impl/IdeRootPane.kt
  - ../../platform/platform-impl/src/com/intellij/openapi/wm/impl/headertoolbar/MainToolbar.kt
  - ../../platform/platform-impl/src/com/intellij/openapi/wm/impl/ProjectFrameCustomHeaderHelper.kt
  - ../../platform/platform-impl/src/com/intellij/openapi/wm/impl/customFrameDecorations/header/MacToolbarFrameHeader.kt
  - ../../platform/platform-impl/src/com/intellij/openapi/wm/impl/customFrameDecorations/header/MenuFrameHeader.kt
  - ../../platform/platform-impl/src/com/intellij/openapi/wm/impl/customFrameDecorations/header/toolbar/ToolbarFrameHeader.kt
  - ../sessions/src/AgentSessionsService.kt
  - ../sessions/src/AgentWorkbenchDedicatedFrameProjectManager.kt
  - ../sessions/src/AgentWorkbenchProjectFrameCapabilitiesProvider.kt
  - ../sessions/src/AgentSessionsGoToSourceProjectFromEditorTabAction.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/testSrc/*.kt
---

# Dedicated Frame Project Switching

Status: Draft
Date: 2026-02-26

## Summary
Define how Agent Workbench dedicated-frame mode supports cross-project navigation with dedicated-frame participation in global window traversal while keeping project-window traversal explicit and dedicated-aware.

This spec owns:
- dedicated-frame exclusion from `Next/Previous Project Window` traversal and `OpenProjectWindows` entries,
- dedicated-frame frame-type propagation/persistence,
- init-time main-toolbar pruning by `(frameType, place, id)`,
- dedicated-frame explicit open/focus affordance,
- editor-tab popup affordance to jump to source project.

## Goals
- Keep dedicated frame out of project-window cycling while preserving it in global window cycling.
- Remove unneeded dedicated-frame toolbar widgets at init time (do not create then hide).
- Keep source-project switching explicit and discoverable from dedicated frame.

## Non-goals
- Source-aware `Cmd+\`` routing from dedicated frame.
- Opening multiple dedicated frames for parallel source projects.
- Runtime replacement of `NewUiRunWidget` or toolbar actions via `ActionConfigurationCustomizer`.

## Requirements
- Dedicated frame capability set must include `ProjectFrameCapability.EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER`.
  [@test] ../sessions/testSrc/AgentWorkbenchProjectFrameCapabilitiesProviderTest.kt

- `ProjectWindowActionGroup` traversal (`Next/Previous Project Window`) and `OpenProjectWindows` visible entries must honor `EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER` while keeping the currently focused window eligible as traversal anchor.
  [@test] ../../platform/lang-impl/testSources/com/intellij/openapi/wm/impl/ProjectWindowActionGroupTest.kt

- Platform global window traversal (`Cmd+\``) must keep dedicated frames in traversal order by not assigning `EXCLUDE_FROM_WINDOW_SWITCH_ORDER` to dedicated projects.
  [@test] ../sessions/testSrc/AgentWorkbenchProjectFrameCapabilitiesProviderTest.kt

- `OpenProjectTask` and recent-project metadata must carry `projectFrameTypeId`, and allocator must resolve frame type by priority:
  - `OpenProjectTask.projectFrameTypeId`, then
  - recent metadata `RecentProjectMetaInfo.projectFrameTypeId`.

- Project-frame module must provide static extension mapping for action exclusions by frame type and place:
  - extension point: `projectFrameActionExclusion`
  - bean fields: `frameType`, `place`, `id` (action id).

- Main toolbar action-group computation must apply exclusion filtering at top-level children only.

- Sessions plugin must register dedicated-frame exclusions for `frameType="AGENT_DEDICATED"`, `place="MainToolbar"` and action IDs:
  - `MainToolbarVCSGroup`
  - `ExecutionTargetsToolbarGroup`
  - `NewUiRunWidget`
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions plugin must not register `AgentSessionsToolbarActionConfigurationCustomizer`.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- In dedicated frame, editor-tab popup must expose `AgentWorkbenchSessions.GoToSourceProjectFromEditorTab`, and the action must stay hidden outside dedicated projects.
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions plugin must register `AgentWorkbenchSessions.OpenDedicatedFrame` and expose it from both:
  - sessions toolwindow header title actions,
  - `OpenProjectWindows` group (Window menu project windows section).
  [@test] ../sessions/testSrc/AgentSessionsOpenDedicatedFrameActionTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

## User Experience
- `Cmd+\`` in normal project frames includes dedicated frame windows.
- `Next/Previous Project Window` skips dedicated frame windows as targets but still works when invoked from dedicated frame (switch anchor stays valid).
- Dedicated frame does not show VCS quick actions or run-target/run-widget controls.
- Users can explicitly open/focus dedicated frame via `Open Agent Dedicated Frame` action.
- From dedicated frame, users can jump to source project via editor-tab popup action `Go to Source Project`.

## Data & Backend
- Dedicated frame type id is `AGENT_DEDICATED`.
- `AgentSessionsService` sets `OpenProjectTask.projectFrameTypeId` when opening dedicated frame project.
- `AgentWorkbenchDedicatedFrameProjectManager.configureProject` persists `projectFrameTypeId` into recent metadata.
- Source project open/focus behavior uses `AgentSessionsService.openOrFocusProject(path)`.
- Dedicated frame open/focus behavior uses `AgentSessionsService.openOrFocusDedicatedFrame(currentProject)`.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentWorkbenchProjectFrameCapabilitiesProviderTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.openapi.wm.impl.ProjectWindowActionGroupTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest'`

## Open Questions / Risks
- If source-aware `Cmd+\`` behavior becomes required later, this spec should evolve to define platform-level semantics explicitly.

## References
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-sessions.spec.md`
