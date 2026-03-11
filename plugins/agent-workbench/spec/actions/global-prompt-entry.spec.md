---
name: Global Prompt Entry
description: Requirements for global prompt action behavior, target routing, manual context selection, keyboard semantics, validation, and launcher handoff.
targets:
  - ../../prompt/src/actions/AgentWorkbenchGlobalPromptAction.kt
  - ../../prompt/src/actions/AgentWorkbenchGlobalPromptAutoSelectAction.kt
  - ../../prompt/src/ui/AgentPromptPalettePopup.kt
  - ../../prompt/src/ui/AgentPromptPaletteView.kt
  - ../../prompt/src/ui/AgentPromptPaletteModels.kt
  - ../../prompt/src/ui/AgentPromptUiSessionStateService.kt
  - ../../prompt-vcs/src/context/AgentPromptVcsCommitManualContextSource.kt
  - ../../prompt/resources/intellij.agent.workbench.prompt.xml
  - ../../prompt/resources/messages/AgentPromptBundle.properties
  - ../../sessions-core/src/prompt/AgentPromptLauncherBridge.kt
  - ../../sessions-core/src/prompt/AgentPromptPaletteExtension.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../prompt/testSrc/ui/AgentPromptProviderSelectionDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptFooterHintDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPlanModeDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptEnterHandlersTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt
  - ../../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt
  - ../../prompt-vcs/testSrc/context/AgentPromptVcsCommitManualContextSourceTest.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
---

# Global Prompt Entry

Status: Draft
Date: 2026-03-11

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

- Global action id `AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect` must be available only when a project is open. It opens the same popup but with EP-driven extension tab auto-selection (see below).

- Prompt target mode must support exactly:
  - `PromptTargetMode.NEW_TASK`,
  - `PromptTargetMode.EXISTING_TASK`.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt

- Prompt editor must be a shared component across both target modes; switching tabs must not recreate or hide the prompt viewport.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt

- Context row must show an `Add Context` affordance only when at least one manual context source is available for the current project.
- When shown, `Add Context` must be rendered once, after existing context chips, in the context row rather than the header.
- `Add Context` must expose inline mnemonic activation consistent with other prompt controls.
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

- Working project path resolution for submit and existing-task loading must never use dedicated-frame project path.

- Working project path resolution order in dedicated-frame project must be:
  - selected Sessions tree context path (project/thread/worktree),
  - selected chat tab source path,
  - most recent non-dedicated project path.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- If no working project path can be resolved automatically, submit must prompt user to choose from available non-dedicated project candidates; cancel keeps popup open and shows project-path validation error.

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

- Provider restore order for opening the prompt must be:
  - prompt draft `providerId`,
  - shared preferred provider exposed by `AgentPromptLauncherBridge.preferredProvider()`,
  - provider-list default ordering.
  [@test] ../../prompt/testSrc/ui/AgentPromptProviderSelectionDecisionsTest.kt

- Submit flow must route through `AgentPromptLauncherBridge` using `AgentPromptLaunchRequest`; prompt popup must not directly call provider session sources.

- Successful prompt launch must update the shared preferred provider used by future prompt openings and new-thread affordances.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Plan mode toggle contract:
  - Toggle is visible when selected provider's bridge has `supportsPlanMode == true`.
  - Toggle default is enabled and is persisted in per-project prompt draft state.
  - When effective plan mode is enabled, submit must set `initialMessageRequest.planModeEnabled = true`.
  - Effective plan mode must be forced off for `EXISTING_TASK` target when selected thread activity is `PROCESSING` or `REVIEWING`.
  - Providers without `supportsPlanMode` must always submit with plan mode disabled.
  [@test] ../../prompt/testSrc/ui/AgentPromptPlanModeDecisionsTest.kt

- Context block soft-cap limit is `12_000` characters. When exceeded, user must explicitly choose send-full, auto-trim, or cancel before launch.

- Extension tab auto-selection (`Alt+Cmd+\` / `Alt+Ctrl+\`):
  - When the popup is opened via `AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect`, the `AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY` attribute is set to `true`.
  - When `preferExtensions` is true, the popup calls `AgentPromptPaletteExtension.shouldAutoSelect(contextItems)` on each active extension tab in order.
  - The first extension that returns `true` from `shouldAutoSelect` has its tab auto-selected.
  - If no extension returns `true`, the default "New Task" tab remains selected.
  - `shouldAutoSelect` defaults to `false` in the EP interface; each extension opts in independently.
  - When the popup is opened via the standard `AgentWorkbenchPrompt.OpenGlobalPalette` (`Cmd+\` / `Ctrl+\`), no auto-selection occurs regardless of extension tab state.

- Extension tab submit flow:
  - When an extension tab is active, submit delegates to the action returned by `AgentPromptPaletteExtension.getSubmitActionId()`.
  - Provider options, plan mode, and existing-task routing are bypassed for extension tabs.

- Manual context contract:
  - manual context is additive to automatically resolved prompt context,
  - manual context removal affects only the selected manual source item,
  - submit uses the combined auto + manual context list through `AgentPromptLaunchRequest.initialMessageRequest.contextItems`,
  - re-running the same manual source replaces that source's previous manual item instead of appending duplicates.

- Prompt draft persistence contract:
  - persisted `AgentPromptUiDraft` must not serialize manual context items,
  - runtime-only restore snapshot may keep manual context items keyed by source id for the current IDE session,
  - successful submit or explicit draft clear must clear both removed-auto context state and manual context state.
    [@test] ../../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt

## User Experience
- Popup opens as a project-scoped launcher for both new and existing task targets.
- Existing-task mode exposes provider-scoped thread list with loading/empty/error states.
- Context chips are removable before submit.
- Context row is shown when at least one context chip is present or when `Add Context` is available; it collapses only when both chips and the `Add Context` affordance are absent.
- Chip-removal hierarchy is provider-defined via context item relations (`itemId`/`parentItemId`); removing a parent chip may remove all descendant chips recursively.
- When manual context sources are available, the context row exposes `Add Context` as the single entry point for adding source-specific context.
- Manual context chips share the same row and submit path as auto context chips.

## Data & Backend
- Existing-task list comes from launcher `observeExistingThreads(...)` stream with background refresh.
- Existing-task list must be scoped to resolved working project path.
- On successful launch, popup closes and draft is cleared; otherwise popup remains and shows error feedback.
- `AgentPromptContextResolverService.collectDefaultContext(...)` remains the auto-context source of truth; manual context is merged later in popup state.
- Manual context sources are discovered via `com.intellij.agent.workbench.promptManualContextSource` and invoked from popup-owned UI state.

## Error Handling
- Validation errors are shown inline in footer using message keys.
- Existing-task loading failures degrade to empty/error list state without crashing popup.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptSubmitValidationDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptFooterHintDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPlanModeDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptEnterHandlersTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewStructureTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewLayoutTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsCommitManualContextSourceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest'`

## Open Questions / Risks
- Keymap conflict resolution for `Cmd/Ctrl+\\` still relies on duplicate keymap declarations and does not enforce a runtime winner.

## References
- `../prompt-context/prompt-context-contracts.spec.md`
- `../prompt-context/prompt-context-editor.spec.md`
- `../prompt-context/prompt-context-project-view.spec.md`
- `../prompt-context/prompt-context-vcs.spec.md`
- `../prompt-context/prompt-context-test-runner.spec.md`
- `../agent-core-contracts.spec.md`
