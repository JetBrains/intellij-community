---
name: Dedicated Frame Project Switching
description: Requirements for dedicated-frame switching UX and init-time main-toolbar pruning.
targets:
  - ../../../../platform/project-frame/src/com/intellij/openapi/wm/ex/ProjectFrameCapabilities.kt
  - ../../../../platform/project-frame/src/com/intellij/openapi/wm/ex/ProjectFrameActionExclusions.kt
  - ../../../../platform/project-frame/resources/intellij.platform.projectFrame.xml
  - ../../../../platform/ide-core-impl/src/com/intellij/ide/impl/OpenProjectTask.kt
  - ../../../../platform/platform-impl/src/com/intellij/ide/RecentProjectMetaInfo.kt
  - ../../../../platform/platform-impl/src/com/intellij/ide/RecentProjectsManagerBase.kt
  - ../../../../platform/platform-impl/src/com/intellij/ide/ProjectWindowCustomizerService.kt
  - ../../../../platform/platform-impl/src/com/intellij/ide/ActiveWindowsWatcher.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/project/impl/IdeProjectFrameAllocator.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/wm/impl/ProjectFrameHelper.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/wm/impl/IdeRootPane.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/wm/impl/headertoolbar/MainToolbar.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/wm/impl/ProjectFrameCustomHeaderHelper.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/wm/impl/customFrameDecorations/header/MacToolbarFrameHeader.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/wm/impl/customFrameDecorations/header/MenuFrameHeader.kt
  - ../../../../platform/platform-impl/src/com/intellij/openapi/wm/impl/customFrameDecorations/header/toolbar/ToolbarFrameHeader.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../sessions/src/frame/AgentWorkbenchDedicatedFrameProjectManager.kt
  - ../../sessions/src/frame/AgentWorkbenchProjectFrameCapabilitiesProvider.kt
  - ../../sessions/src/service/AgentSourceChatSwitching.kt
  - ../../sessions-actions/src/actions/AgentSessionsGoToSourceProjectFromToolbarAction.kt
  - ../../sessions-actions/src/actions/AgentSessionsSwitchSourceAndChatAction.kt
  - ../../chat/src/AgentChatFocusService.kt
  - ../../sessions-actions/src/actions/AgentSessionsGoToSourceProjectFromEditorTabAction.kt
  - ../../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../../sessions/testSrc/*.kt
  - ../../chat/src/AgentChatEditorTabColorProvider.kt
  - ../../chat/resources/intellij.agent.workbench.chat.xml
  - ../../chat/testSrc/AgentChatEditorTabColorProviderTest.kt
---

# Dedicated Frame Project Switching

Status: Draft
Date: 2026-03-01

## Summary
Define how Agent Workbench dedicated-frame mode supports cross-project navigation with dedicated-frame participation in global window traversal while keeping project-window traversal explicit and dedicated-aware.

This spec owns:
- dedicated-frame exclusion from `Next/Previous Project Window` traversal and `OpenProjectWindows` entries,
- dedicated-frame frame-type propagation/persistence,
- init-time main-toolbar pruning by `(frameType, place, id)`,
- dedicated-frame main-toolbar source-project one-click affordance,
- dedicated-frame explicit open/focus affordance,
- source-frame active-chat focus affordance,
- bidirectional source/chat focus action for user keymap binding,
- editor-tab popup affordance to jump to source project.

Terminal hyperlink click routing in dedicated frame is owned by `agent-dedicated-frame-terminal-hyperlink-routing.spec.md`.
Source-frame main-toolbar Agent activity is owned by `agent-main-toolbar-activity.spec.md`.

## Goals
- Keep dedicated frame out of project-window cycling while preserving it in global window cycling.
- Remove unneeded dedicated-frame toolbar widgets at init time (do not create then hide).
- Keep source-project switching explicit and discoverable from dedicated frame.
- Let users explicitly switch between source code and the active Agent chat without relying on OS window traversal.

## Non-goals
- Source-aware `Cmd+\`` routing from dedicated frame.
- Opening multiple dedicated frames for parallel source projects.
- Runtime replacement of `NewUiRunWidget` or toolbar actions via `ActionConfigurationCustomizer`.
- A bundled default shortcut for source/chat switching.

## Requirements
- Dedicated frame capability set must include `ProjectFrameCapability.EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER`.
  [@test] ../../sessions/testSrc/AgentWorkbenchProjectFrameCapabilitiesProviderTest.kt

- `ProjectWindowActionGroup` traversal (`Next/Previous Project Window`) and `OpenProjectWindows` visible entries must honor `EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER` while keeping the currently focused window eligible as traversal anchor.
  [@test] ../../../../platform/lang-impl/testSources/com/intellij/openapi/wm/impl/ProjectWindowActionGroupTest.kt

- Platform global window traversal (`Cmd+\``) must keep dedicated frames in traversal order by not assigning `EXCLUDE_FROM_WINDOW_SWITCH_ORDER` to dedicated projects.
  [@test] ../../sessions/testSrc/AgentWorkbenchProjectFrameCapabilitiesProviderTest.kt

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
  - `AIAssistantHubPopupAction`
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions plugin must not register `AgentSessionsToolbarActionConfigurationCustomizer`.
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- In dedicated frame, editor-tab popup must expose `AgentWorkbenchSessions.GoToSourceProjectFromEditorTab`, and the action must stay hidden outside dedicated projects.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- In dedicated frame, Agent chat editor tabs must reuse the source project's stored project color when `UISettings.differentiateProjects` is enabled:
  - use the platform recent-project soft background palette keyed by the stored source-project color index,
  - derive a muted tab color from stored custom project colors,
  - do not generate or persist project color metadata during tab color resolution,
  - expose the source project path in the tab tooltip so color is not the only project-identification cue.
  [@test] ../../chat/testSrc/AgentChatEditorTabColorProviderTest.kt
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Agent chat editor tabs can be dragged from the dedicated frame to the matching already-open source project frame:
  - the matching source project frame accepts the drop,
  - other project frames reject the drop,
  - closed source projects are not opened during drag-and-drop,
  - the running terminal-backed agent session is preserved and must not restart.
  [@test] ../../chat/testSrc/AgentChatCrossProjectDockTargetRegistrarTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- In dedicated frame, main toolbar must expose `AgentWorkbenchSessions.GoToSourceProjectFromToolbar`:
  - visible only in dedicated projects,
  - shows active chat tab source project name and opens/focuses source project in one click,
  - shows disabled `No source project` placeholder when no valid active source project path is available.
  [@test] ../../sessions-actions/testSrc/AgentSessionsGoToSourceProjectFromToolbarActionTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions plugin must register `AgentWorkbenchSessions.OpenDedicatedFrame` and expose it from both:
  - sessions toolwindow header title actions,
  - `OpenProjectWindows` group (Window menu project windows section).
  [@test] ../../sessions-actions/testSrc/AgentSessionsOpenDedicatedFrameActionTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions plugin must register `AgentWorkbenchSessions.SwitchSourceAndChat` in `OpenProjectWindows` after
  `AgentWorkbenchSessions.OpenDedicatedFrame`, without any bundled default keyboard shortcut:
  - from a source project frame, it focuses the most recently focused Agent chat tab for that source project in the dedicated frame,
    falling back to the first open chat tab for that source project and then to opening/focusing the dedicated frame without selecting an unrelated chat,
  - from the dedicated frame, it opens/focuses the source project for the selected Agent chat tab,
  - in the dedicated frame, it stays disabled when the selected chat tab has no openable source project.
  [@test] ../../sessions-actions/testSrc/AgentSessionsSwitchSourceAndChatActionTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

## User Experience
- `Cmd+\`` in normal project frames includes dedicated frame windows.
- `Next/Previous Project Window` skips dedicated frame windows as targets but still works when invoked from dedicated frame (switch anchor stays valid).
- Dedicated frame does not show VCS quick actions or run-target/run-widget controls.
- Users can explicitly open/focus dedicated frame via `Open Agent Dedicated Frame` action.
- Users can bind `Switch Between Source and Agent Chat` in Keymap to toggle focus between code and the active Agent chat.
- Dedicated frame main toolbar shows active source project (from selected chat tab) and allows one-click open/focus of that source project.
- From dedicated frame, users can jump to source project via editor-tab popup action `Go to Source Project`.

## Data & Backend
- Dedicated frame type id is `AGENT_DEDICATED`.
- `AgentSessionLaunchService` sets `OpenProjectTask.projectFrameTypeId` when opening dedicated frame project.
- `AgentWorkbenchDedicatedFrameProjectManager.configureProject` persists `projectFrameTypeId` into recent metadata.
- Source project open/focus behavior uses `AgentSessionLaunchService.openOrFocusProject(path)`.
- Source/chat switching behavior is centralized in `AgentSourceChatSwitching`.
- Source-frame active-chat focus uses `AgentChatFocusService` to select the most recently focused Agent chat tab for the source project
  from platform editor history, falling back to the first open Agent chat tab for that source project.
- Agent chat tab colors read only stored recent-project color metadata for the source project. They must not call
  path-based platform project color helpers because those helpers can generate and persist a missing color index for
  a project path; dedicated-frame tabs can refer to many closed source projects.
- Toolbar source-project action resolves active chat-tab source path via `AgentChatTabSelectionService.selectedChatTab`.
- Dedicated frame open/focus behavior uses `AgentSessionLaunchService.openOrFocusDedicatedFrame(currentProject)`.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentWorkbenchProjectFrameCapabilitiesProviderTest`
- `./tests.cmd --module intellij.platform.lang.tests --test com.intellij.openapi.wm.impl.ProjectWindowActionGroupTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsGoToSourceProjectFromToolbarActionTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsSwitchSourceAndChatActionTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFocusServiceTest`

## Open Questions / Risks
- If source-aware `Cmd+\`` behavior becomes required later, this spec should evolve to define platform-level semantics explicitly.

## References
- `agent-dedicated-frame.spec.md`
- `../sessions/agent-sessions.spec.md`
- `agent-main-toolbar-activity.spec.md`
- `agent-dedicated-frame-terminal-hyperlink-routing.spec.md`
