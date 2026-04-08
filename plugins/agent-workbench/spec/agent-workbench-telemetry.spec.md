---
name: Agent Workbench Telemetry
description: Requirements for custom IntelliJ Feature Usage Statistics collected by Agent Workbench.
targets:
  - ../sessions-core/src/statistics/AgentWorkbenchTelemetry.kt
  - ../prompt/core/src/AgentPromptModels.kt
  - ../sessions-core/resources/intellij.agent.workbench.sessions.core.xml
  - ../prompt/src/ui/AgentPromptPaletteDecisions.kt
  - ../prompt/src/ui/AgentPromptPalettePopup.kt
  - ../sessions/src/service/AgentSessionLaunchService.kt
  - ../sessions/src/service/AgentSessionArchiveService.kt
  - ../sessions-toolwindow/src/ui/AgentSessionsToolWindow.kt
  - ../sessions-actions/src/actions/*.kt
  - ../sessions-toolwindow/src/actions/*.kt
  - ../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt
  - ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  - ../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionIntegrationTestSupport.kt
  - ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  - ../sessions-actions/testSrc/AgentSessionsGoToSourceProjectFromToolbarActionTest.kt
  - ../sessions-actions/testSrc/AgentSessionsOpenDedicatedFrameActionTest.kt
  - ../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  - ../sessions-toolwindow/testSrc/AgentSessionsToolWindowFactorySwingTest.kt
  - ../../../../build/events/FUS.properties
---

# Agent Workbench Telemetry

Status: Draft
Date: 2026-03-11

## Summary
Define the custom Agent Workbench usage telemetry collected through IntelliJ Feature Usage Statistics (FUS).

This spec covers only Agent Workbench-specific semantic telemetry. Generic action-click accounting remains owned by the platform `actions` FUS group.

## Goals
- Measure prompt launch outcomes and core session workflows without collecting user content.
- Keep the logging schema small, stable, and allowlisted.
- Centralize semantic telemetry in shared service/facade code instead of scattering direct FUS calls across UI actions.

## Non-goals
- Counting ordinary `AnAction` invocations already covered by platform telemetry.
- OpenTelemetry, application state collectors, or project state collectors.
- Logging prompt text, project paths, thread ids, session ids, clipboard contents, VCS hashes, or prompt context payloads.

## Requirements
- Agent Workbench custom telemetry must use exactly one event-only `CounterUsagesCollector` registered from `sessions-core` under FUS group `agent.workbench` version `1`.

- Agent Workbench custom telemetry must not duplicate platform action usage counting.
  Ordinary `AnAction` invocations such as prompt-open, refresh, toggle actions, copy-thread-id, and popup display/open with no semantic operation must rely on the platform `actions` group instead of custom Agent Workbench events.

- The custom schema must use only stable enum or allowlisted fields:
  - `entry_point`
  - `provider`
  - `launch_mode`
  - `target_kind`
  - `blocked_reason`
  - `launch_result`

- Provider reporting must be coarse and stable.
  Raw provider ids must not be logged; custom telemetry must normalize provider values to `CODEX`, `CLAUDE`, or `OTHER`.
  [@test] ../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- The collector must expose only these custom event ids:
  - `prompt.submit_blocked`
  - `prompt.launch_resolved`
  - `thread.create_requested`
  - `thread.open_requested`
  - `thread.archive_requested`
  - `project.focus_requested`
  - `dedicated_frame.focus_requested`

- `prompt.submit_blocked` must log only unresolved local submit validation failures.
  If submit enters the working-project-path selection retry path and the user elects to retry, no blocked event may be emitted for that attempt.
  [@test] ../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt

- `prompt.launch_resolved` must be the custom telemetry source of truth for prompt launch outcome.
  It must encode the final resolved outcome of the prompt launch workflow rather than the immediate return value of launcher admission.

- `prompt.launch_resolved.launch_result` must distinguish at least:
  - `SUCCESS`
  - `PROVIDER_UNAVAILABLE`
  - `UNSUPPORTED_LAUNCH_MODE`
  - `TARGET_THREAD_NOT_FOUND`
  - `TARGET_THREAD_BUSY_FOR_PLAN_MODE`
  - `CANCELLED`
  - `DROPPED_DUPLICATE`
  - `INTERNAL_ERROR`
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Semantic session events must be emitted from service-layer source-of-truth code after single-flight admission.
  Dropped duplicate requests must not emit `thread.create_requested`, `thread.open_requested`, `project.focus_requested`, `dedicated_frame.focus_requested`, or `thread.archive_requested`.
  Prompt-launch duplicate drops may instead resolve through `prompt.launch_resolved = DROPPED_DUPLICATE`.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt

- UI-layer actions and tree interactions must propagate only semantic context such as `entryPoint` into the service layer; they must not become independent sources of custom workflow logging.
  [@test] ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions-actions/testSrc/AgentSessionsGoToSourceProjectFromToolbarActionTest.kt
  [@test] ../sessions-actions/testSrc/AgentSessionsOpenDedicatedFrameActionTest.kt
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Entry-point taxonomy is fixed to the following values:
  - `PROMPT`
  - `TREE_ROW`
  - `TREE_ROW_OVERLAY`
  - `TREE_POPUP`
  - `EDITOR_TAB_QUICK`
  - `EDITOR_TAB_POPUP`
  - `TOOLBAR`
  - `WINDOW_MENU`

- The non-`AnAction` inline quick-create affordance in the sessions tree must emit semantic create telemetry with `entry_point = TREE_ROW_OVERLAY`.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsToolWindowFactorySwingTest.kt

- `thread.archive_requested.provider` should be set only when the archived target selection is single-provider; mixed-provider archive requests should omit provider instead of inventing a synthetic value.
  [@test] ../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt

## Data & Backend
- The shared telemetry facade in `sessions-core` is the only custom logging API other Agent Workbench modules may call directly.
- `prompt.submit_blocked` records validation metadata only; it must not include prompt text or selected task ids.
- `prompt.launch_resolved` records provider, launch mode, target kind, and resolved outcome only.
- `thread.create_requested` records new-thread intent only after the create flow is accepted.
- `thread.open_requested` records thread/sub-agent open intent only after the open flow is accepted.
- `project.focus_requested` and `dedicated_frame.focus_requested` are semantic navigation events and do not carry provider information.
- FUS metadata descriptions for these events must be documented in `build/events/FUS.properties`.

## Error Handling
- Validation failures must stay local to the prompt popup and degrade to inline error messaging; telemetry for those failures must use `prompt.submit_blocked` only.
- Prompt-launch cancellation and duplicate-drop outcomes must remain first-class resolved telemetry values, not be folded into `INTERNAL_ERROR`.
- Test-only telemetry capture must be possible without hitting the real FUS backend so semantic event tests remain deterministic.
  [@test] ../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptSubmitValidationDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionArchiveServiceIntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsGoToSourceProjectFromToolbarActionTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsOpenDedicatedFrameActionTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreePopupActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsToolWindowFactorySwingTest'`

## Open Questions / Risks
- Cross-group analysis that combines platform `actions` telemetry with Agent Workbench semantic telemetry is intentionally outside this spec.
- Any future addition of fields or event ids must be treated as a schema change and may require a group-version bump.

## References
- `actions/global-prompt-entry.spec.md`
- `actions/new-thread.spec.md`
- `agent-sessions.spec.md`
- `agent-dedicated-frame.spec.md`
