---
name: Agent Workbench Telemetry
description: Requirements for Agent Workbench custom IntelliJ Feature Usage Statistics events.
targets:
  - ../../sessions-core/src/statistics/AgentWorkbenchTelemetry.kt
  - ../../sessions-core/resources/intellij.agent.workbench.sessions.core.xml
  - ../../prompt/core/src/AgentPromptModels.kt
  - ../../prompt/ui/src/AgentPromptPaletteSubmitController.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../sessions/src/service/AgentSessionArchiveService.kt
  - ../../sessions-actions/src/actions/*.kt
  - ../../sessions-toolwindow/src/**/*.kt
  - ../../../../../build/events/FUS.properties
---

# Agent Workbench Telemetry

Status: Draft
Date: 2026-05-09

## Summary
Agent Workbench custom telemetry records semantic workflow outcomes that platform action telemetry cannot infer. It must stay content-free, allowlisted, and centralized behind the shared telemetry facade.

## Requirements
- Custom telemetry uses one event-only `CounterUsagesCollector` in `sessions-core` with FUS group `agent.workbench` version `5`.

- Custom events must not duplicate ordinary `AnAction` invocation counting already covered by the platform `actions` group.

- The schema uses only stable enum or allowlisted fields: `entry_point`, `provider`, `launch_mode`, `target_kind`, `blocked_reason`, and `launch_result`.

- Provider values are normalized to `CODEX`, `CLAUDE`, `JUNIE`, `PI`, `TERMINAL`, or `OTHER`; raw provider ids, prompt text, paths, thread ids, session ids, clipboard contents, VCS hashes, and context payloads must not be logged.
  [@test] ../../prompt/ui/testSrc/AgentPromptSubmitValidationDecisionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Custom event ids are limited to prompt submit/launch outcomes and accepted semantic thread/project actions: `prompt.submit_blocked`, `prompt.launch_resolved`, `thread.create_requested`, `thread.open_requested`, `thread.archive_requested`, `project.focus_requested`, and `dedicated_frame.focus_requested`.

- `prompt.launch_resolved` records the final resolved launch outcome, including cancellation, duplicate drop, unsupported mode, unavailable provider, missing target, busy plan-mode target, success, and internal error.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Semantic session events are emitted from service-layer source-of-truth code after single-flight admission; dropped duplicates do not emit create/open/focus/archive events.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt

- UI actions pass semantic context such as entry point into services; they must not become independent custom telemetry sources.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- FUS metadata descriptions for custom events must be documented in `build/events/FUS.properties`.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptSubmitValidationDecisionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionArchiveServiceIntegrationTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreePopupActionsTest`

## References
- `../actions/global-prompt-entry.spec.md`
- `../actions/new-thread.spec.md`
- `../sessions/agent-sessions.spec.md`
