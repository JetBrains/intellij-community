---
name: Project Frame Type
description: Requirements for the projectFrameType extension point, the hidden-project reopen rules, and the project-window traversal exclusion.
style: plain-1
targets:
  - ../src/com/intellij/openapi/wm/ex/ProjectFrameType.kt
  - ../src/com/intellij/openapi/wm/ex/ProjectFrameCapabilities.kt
  - ../resources/intellij.platform.projectFrame.xml
  - ../../ide-core-impl/src/com/intellij/ide/impl/OpenProjectTask.kt
  - ../../platform-impl/src/com/intellij/ide/RecentProjectMetaInfo.kt
  - ../../platform-impl/src/com/intellij/ide/RecentProjectsManagerBase.kt
  - ../../platform-impl/src/com/intellij/ide/ActiveWindowsWatcher.kt
  - ../../platform-impl/src/com/intellij/openapi/project/impl/IdeProjectFrameAllocator.kt
  - ../../platform-impl/src/com/intellij/openapi/wm/impl/headertoolbar/MainToolbar.kt
  - ../../platform-impl/src/com/intellij/toolWindow/ToolWindowSetInitializer.kt
  - ../../platform-impl/src/com/intellij/openapi/wm/impl/ProjectWindowAction.kt
  - ../../lang-impl/src/com/intellij/openapi/wm/impl/ProjectWindowActionGroup.kt
---

# Project Frame Type

Status: Draft
Date: 2026-09-13

## Summary

A project frame type is a static policy that a plugin declares for a kind of project window. The
`projectFrameType` extension point in `intellij.platform.projectFrame` declares it. The policy decides
three things. It decides whether a hidden project reopens on IDE start. It names the tool-window layout
profile of the frame. It names the actions that leave the main toolbar.

A related frame capability, `EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER`, takes a project window out of
project-window traversal. It comes from `projectFrameCapabilitiesProvider`, because it needs a `Project`.
This spec owns both contracts, because the tests that guard them state examples and not the rules.

The IJPL UI group owns the module and this spec. No bundled plugin declares a frame type today. Air used
one for its dedicated frame and dropped it, as
[ADR 0116](../../../../plugins/air/docs/decisions/0116-the-welcome-project-hosts-the-agent-sessions.md)
records.

## Goals

- State the order in which a project open resolves its frame type.
- State when a hidden project reopens on IDE start.
- State what a frame type changes in a frame: the layout profile and the main toolbar.
- State how a project window leaves project-window traversal, and what stays.

## Non-goals

- Policy that needs a `Project`, such as the startup UI policy of `projectFrameCapabilitiesProvider`.
- The rules of the welcome project. `WelcomeScreenProjectProvider` owns them.
- The content of a `projectFrameToolWindowLayout` profile. The spec covers only how a frame type selects one.
- Any plugin-specific frame type.

## Requirements

### Declaration

- The `projectFrameType` extension point declares one frame type per `id`. The declaration carries
  `reopenWhenHidden`, `toolWindowLayoutProfile`, and nested `excludeAction` elements.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

- An `excludeAction` element names a `place` and an action `id`.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

- A declaration with the `id` alone is inert. It reopens no hidden project, names no layout profile, and
  excludes no action.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

- The platform resolves the policy from the frame type id alone. It needs no `Project`.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

- The platform trims a frame type id, a place, and an action id. It drops a blank action id.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

- A blank frame type id and an undeclared one resolve to no policy.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

- Where two declarations share one id, the platform keeps the first. It logs an error that names the id.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

### Resolution order

- A project open resolves its frame type from the first value in this order:
  - `OpenProjectTask.projectFrameTypeId`,
  - the `projectFrameTypeId` of the recent-project metadata passed with the open task,
  - the `projectFrameTypeId` of the stored recent-project metadata of the project path.

- An effective frame type id that no `projectFrameType` declares is a warning. The log names the id and
  the project path. The frame then opens with no frame-type policy. No test covers the warning.

- A startup reopen passes the stored `projectFrameTypeId` into the open task. A frame type therefore
  survives a restart.

### Reopen rules for a hidden project

- A hidden project reopens on IDE start under three conditions. It was open at the last exit. Its frame
  type declares `reopenWhenHidden="true"`. The platform reopen setting is on.
  [@test] ../../platform-tests/testSrc/com/intellij/ide/RecentProjectManagerTest.kt

- A hidden project whose frame type declares no reopen stays closed after a restart.
  [@test] ../../platform-tests/testSrc/com/intellij/ide/RecentProjectManagerTest.kt

- A hidden project that was closed at the last exit stays closed after a restart.
  [@test] ../../platform-tests/testSrc/com/intellij/ide/RecentProjectManagerTest.kt

- A hidden project stays out of the Recent Projects list in every case.
  [@test] ../../platform-tests/testSrc/com/intellij/ide/RecentProjectManagerTest.kt

- While one hidden project is eligible for the reopen, the IDE reports that it will reopen a project. The
  start therefore shows no Welcome Screen.
  [@test] ../../platform-tests/testSrc/com/intellij/ide/RecentProjectManagerTest.kt

### Tool-window layout profile

- `toolWindowLayoutProfile` names a `projectFrameToolWindowLayout` profile by id.
  [@test] ../../platform-impl/testSrc/com/intellij/toolWindow/ProjectFrameToolWindowLayoutServiceTest.kt

- A tool window that the profile marks `register="false"` is not registered for that frame type. The
  platform does not create its factory.
  [@test] ../../platform-impl/testSrc/com/intellij/toolWindow/ProjectFrameToolWindowLayoutServiceTest.kt
  [@test] ../../lang-impl/testSources/com/intellij/toolWindow/ProjectFrameToolWindowLayoutServiceTest.kt

- A frame type with no declaration keeps every tool-window registration. A profile with a matching name
  changes nothing without the declaration.
  [@test] ../../platform-impl/testSrc/com/intellij/toolWindow/ProjectFrameToolWindowLayoutServiceTest.kt

### Main toolbar exclusion

- An `excludeAction` entry with `place="MainToolbar"` removes that action from the main toolbar of the
  frame. The removal applies when the platform builds the toolbar.

- The removal applies to the top-level children of the main toolbar groups only. A nested action stays.

- An `excludeAction` entry for another place changes nothing in the main toolbar.
  [@test] ../../platform-tests/testSrc/com/intellij/openapi/wm/ex/ProjectFrameTypeServiceTest.kt

### Project-window traversal exclusion

- `Next Project Window` and `Previous Project Window` skip a window that carries
  `EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER`.
  [@test] ../../lang-impl/testSources/com/intellij/openapi/wm/impl/ProjectWindowActionGroupTest.kt

- The visible entries of the `OpenProjectWindows` group omit such a window. The internal children keep
  it.
  [@test] ../../lang-impl/testSources/com/intellij/openapi/wm/impl/ProjectWindowActionGroupTest.kt

- Such a window stays a valid traversal anchor. From it, both actions reach the neighbour windows.
  [@test] ../../lang-impl/testSources/com/intellij/openapi/wm/impl/ProjectWindowActionGroupTest.kt

- Global window traversal keeps such a window. Only `EXCLUDE_FROM_WINDOW_SWITCH_ORDER` removes a window
  from it, and the current window always stays. No test covers global traversal.

## User Experience

- A hidden project of an opted-in frame type comes back after a restart. The Recent Projects list never
  shows it.
- `Next Project Window` and `Previous Project Window` skip an excluded window. Both still work from that
  window.
- The Window menu lists no excluded window.
- The main toolbar of a frame shows none of the actions its frame type excludes.

## Data & Backend

- `OpenProjectTask.projectFrameTypeId` carries the frame type into a project open.
- `RecentProjectMetaInfo.projectFrameTypeId` persists the frame type of a recent project.
- `RecentProjectMetaInfo.hidden` marks a hidden project, and `RecentProjectMetaInfo.opened` records
  whether the project was open at exit.
- `ProjectFrameTypeService` answers every policy question from the declared descriptors. The frame
  allocator, the startup reopen, the tool-window initializer, and the main toolbar read it.
- `ProjectFrameCapabilitiesService` answers a capability question for a `Project`. The project-window
  group and the active-windows watcher read it.

## Error Handling

- Two declarations of one frame type id log an error. The first declaration stays in force.
- An effective frame type id with no declaration logs a warning. The frame opens with no policy.
- A capabilities provider that throws is logged and skipped. The other providers still contribute.

## Testing / Local Run

The `[@test]` links name the tests. Their modules are:

| Test | Module |
| --- | --- |
| `ProjectFrameTypeServiceTest`, `RecentProjectManagerTest` | `intellij.platform.tests` |
| `ProjectWindowActionGroupTest`, the lang-impl layout test | `intellij.platform.lang.tests` |
| the platform-impl `ProjectFrameToolWindowLayoutServiceTest` | `intellij.platform.ide.impl.tests` |

The lang-impl layout test exists because only that test application has a base layout with a Project View.
There a suppressed registration is asserted against a non-empty layout.

## Open Questions / Risks

- No gate checks the references of a spec under `community/platform`. A moved file leaves a stale path
  here until a reader notices.
- The undeclared-id warning, the toolbar filtering, and global window traversal have no test.

## References

- `../../../../plugins/air/spec/frame/agent-sessions-window-project-switching.spec.md`
- [ADR 0116](../../../../plugins/air/docs/decisions/0116-the-welcome-project-hosts-the-agent-sessions.md)
- `../../welcome-screen/src/com/intellij/openapi/wm/ex/WelcomeScreenProjectProvider.kt`
- `../../../.ai/spec/SPEC_GUIDE.md`
