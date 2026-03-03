---
name: Global Prompt Entry
description: Requirements for global prompt action behavior, target routing, keyboard semantics, validation, and launcher handoff.
targets:
  - ../../prompt/src/actions/AgentWorkbenchGlobalPromptAction.kt
  - ../../prompt/src/ui/AgentPromptPalettePopup.kt
  - ../../prompt/src/ui/AgentPromptPaletteView.kt
  - ../../prompt/src/ui/AgentPromptPaletteModels.kt
  - ../../prompt/src/ui/AgentPromptUiSessionStateService.kt
  - ../../prompt/resources/intellij.agent.workbench.prompt.xml
  - ../../prompt/resources/messages/AgentPromptBundle.properties
  - ../../sessions-core/src/prompt/AgentPromptLauncherBridge.kt
  - ../../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptFooterHintDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptEnterHandlersTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt
---

# Global Prompt Entry

Status: Draft
Date: 2026-03-03

## Summary
Define the global prompt entrypoint opened by `Cmd+\\` (macOS) / `Ctrl+\\` (Windows/Linux), including popup behavior, target mode switching, submit validation, and launch handoff.

Prompt-context collection and rendering contracts are specified separately in `spec/prompt-context/*.spec.md`.

## Goals
- Keep launch behavior consistent between `NEW_TASK` and `EXISTING_TASK` modes.
- Keep keyboard semantics explicit and testable.
- Keep submit validation deterministic before calling the launcher bridge.

## Non-goals
- Defining per-source prompt context payload/rendering details.
- Defining provider session discovery backend internals.
- Defining command composition semantics (covered by `spec/agent-core-contracts.spec.md`).

## Requirements
- Global action id `AgentWorkbenchPrompt.OpenGlobalPalette` must be available only when a project is open.

- Prompt target mode must support exactly:
  - `PromptTargetMode.NEW_TASK`,
  - `PromptTargetMode.EXISTING_TASK`.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt

- Prompt editor must be a shared component across both target modes; switching tabs must not recreate or hide the prompt viewport.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt

- Existing-task pane must stay bounded and must not starve prompt editor layout.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt

- Submit validation must block launch when any required precondition is missing:
  - empty prompt,
  - missing selected provider,
  - selected provider CLI unavailable,
  - missing project path,
  - missing prompt launcher bridge,
  - `EXISTING_TASK` mode without selected existing task id.
  [@test] ../../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt

- Keyboard behavior contract:
  - `Enter` runs submit action,
  - `Shift+Enter` inserts line break,
  - `Tab` submits only when tab-queue shortcut is enabled,
  - otherwise `Tab` / `Shift+Tab` perform forward/backward focus traversal.
  [@test] ../../prompt/testSrc/ui/AgentPromptEnterHandlersTest.kt

- Tab-queue shortcut must be enabled only when target mode is `EXISTING_TASK` and selected provider is `CODEX`.
  [@test] ../../prompt/testSrc/ui/AgentPromptFooterHintDecisionsTest.kt

- Footer hint contract:
  - existing-task Codex mode uses Codex-specific hint key,
  - all other states use default hint key,
  - explicit existing-task selection hint is shown only when mode is `EXISTING_TASK`, selection is empty, and provider is non-Codex.
  [@test] ../../prompt/testSrc/ui/AgentPromptFooterHintDecisionsTest.kt

- Submit flow must route through `AgentPromptLauncherBridge` using `AgentPromptLaunchRequest`; prompt popup must not directly call provider session sources.

- Context block soft-cap limit is `12_000` characters. When exceeded, user must explicitly choose send-full, auto-trim, or cancel before launch.

## User Experience
- Popup opens as a project-scoped launcher for both new and existing task targets.
- Existing-task mode exposes provider-scoped thread list with loading/empty/error states.
- Context chips are removable before submit.

## Data & Backend
- Existing-task list comes from launcher `observeExistingThreads(...)` stream with background refresh.
- On successful launch, popup closes and draft is cleared; otherwise popup remains and shows error feedback.

## Error Handling
- Validation errors are shown inline in footer using message keys.
- Existing-task loading failures degrade to empty/error list state without crashing popup.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptSubmitValidationDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptFooterHintDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptEnterHandlersTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewStructureTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewLayoutTest'`

## Open Questions / Risks
- Keymap conflict resolution for `Cmd/Ctrl+\\` still relies on duplicate keymap declarations and does not enforce a runtime winner.

## References
- `../prompt-context/prompt-context-contracts.spec.md`
- `../prompt-context/prompt-context-editor.spec.md`
- `../prompt-context/prompt-context-project-view.spec.md`
- `../prompt-context/prompt-context-vcs.spec.md`
- `../prompt-context/prompt-context-test-runner.spec.md`
- `../agent-core-contracts.spec.md`
