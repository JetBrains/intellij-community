---
name: Global Prompt Entry
description: Requirements for opening, focusing, submitting, and routing the Agent Workbench global prompt.
targets:
  - ../../prompt/ui/src/actions/AgentWorkbenchGlobalPromptAction.kt
  - ../../prompt/ui/src/emptyState/AgentWorkbenchInlinePromptEmptyStateProvider.kt
  - ../../prompt/ui/src/actions/AgentWorkbenchGlobalPromptEmptyTextProvider.kt
  - ../../prompt/ui/src/AgentPromptPalettePopupService.kt
  - ../../prompt/ui/src/AgentPromptPalettePopup.kt
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../prompt/ui/src/AgentPromptPaletteSubmitController.kt
  - ../../prompt/ui/src/AgentPromptEnterHandlers.kt
  - ../../prompt/ui/src/AgentPromptExistingTaskController.kt
  - ../../prompt/ui/src/AgentPromptProviderSelector.kt
  - ../../prompt/core/src/AgentPromptLauncherBridge.kt
  - ../../sessions-actions/src/actions/NewThreadMenuActions.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../prompt/ui/testSrc/*.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
---

# Global Prompt Entry

Status: Draft
Date: 2026-05-27

## Summary
The global prompt opens a project-scoped prompt surface for starting a new task or sending a prompt to an existing loaded task. The surface is normally a popup, but the empty editor state may host a compact inline prompt composer instead of standard empty-state hints. This spec owns popup and inline lifecycle, target mode, validation, keyboard behavior, provider selection, and launcher handoff. Context collection and Add-to-Agent-Context routing are specified separately.

## Requirements
- `AgentWorkbenchPrompt.OpenGlobalPalette` and `AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect` are available only with an open project. Invoking either action while the popup is already visible for the same project focuses the existing popup and preserves live state.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt

- When the main editor area is empty, Agent Workbench contributes a compact inline composer to the platform empty editor state. The composer owns the rich empty-state surface, uses the shared prompt content/session machinery, and suppresses the painted empty editor action hints while it is visible. Standard empty-state hints remain a fallback when no rich composer is available.
  [@test] ../../prompt/ui/testSrc/emptyState/AgentWorkbenchInlinePromptEmptyStateProviderTest.kt
  [@test] ../../../../platform/platform-impl/testSrc/com/intellij/openapi/fileEditor/impl/EditorEmptyTextPainterTest.kt

- The inline empty-state composer is gated by the `agent.workbench.inline.empty.state.prompt` system property, enabled by default. When enabled, the inline provider creates the composer and the redundant `AgentWorkbenchPrompt.OpenGlobalPalette` promoted-text hint is suppressed. When disabled, the inline provider creates no component and the promoted-text hint is shown as the empty-editor affordance instead.
  [@test] ../../prompt/ui/testSrc/emptyState/AgentWorkbenchInlinePromptEmptyStateProviderTest.kt
  [@test] ../../prompt/ui/testSrc/actions/AgentWorkbenchGlobalPromptEmptyTextProviderTest.kt

- Showing the empty editor state must not auto-focus the inline composer. Invoking `AgentWorkbenchPrompt.OpenGlobalPalette` opens the ordinary global prompt popup even when the inline composer is visible in the empty editor state.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt
  [@test] ../../prompt/ui/testSrc/emptyState/AgentWorkbenchInlinePromptEmptyStateProviderTest.kt

- `AgentWorkbenchPrompt.OpenGlobalPalette` is invoked by pressing `Ctrl` twice. `AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect` is invoked by holding `Alt`/`Option` while pressing `Ctrl` twice. While Agent Workbench is installed, it displaces Run Anything from bare `Ctrl Ctrl` and must not install a replacement Run Anything keymap shortcut.
  [@test] ../../prompt/ui/testSrc/actions/AgentWorkbenchGlobalPromptDoubleCtrlShortcutTest.kt

- The main prompt popup remains open when application focus moves to another app or IDE frame, and it refocuses when the originating project frame becomes active again. Popup dismissal is limited to explicit dismissal or clicks outside the popup inside the originating frame.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupActivationDecisionsTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupDismissalDecisionsTest.kt

- The prompt supports exactly `NEW_TASK` and `EXISTING_TASK` target modes. Opening the standard prompt defaults to `NEW_TASK`; switching modes must not recreate the shared prompt editor.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt

- Existing-task mode observes loaded threads through `AgentPromptLauncherBridge`, scoped to the resolved working project path, and may preselect the focused open chat thread or a single loaded thread without automatically switching modes.
  [@test] ../../prompt/ui/testSrc/AgentPromptExistingTaskControllerTest.kt

- Submit validation must block empty prompts, missing provider, unavailable provider CLI, missing project path, missing launcher bridge, and existing-task submits without a selected task.
  [@test] ../../prompt/ui/testSrc/AgentPromptSubmitValidationDecisionsTest.kt

- Working project path resolution must never use the dedicated-frame project path. In non-dedicated frames it resolves from the current open project's identity path, so Bazel projects contribute their `.bazelproject` identity instead of raw `project.basePath`. In a dedicated Agent frame, it resolves from selected Sessions context, selected chat tab source path, then most recent non-dedicated project; unresolved submits prompt for a source project and keep the popup open on cancel.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Keyboard behavior is: Enter submits, Shift+Enter inserts a line break, Tab/Shift+Tab switch prompt tabs unless completion or Codex tab-queue handling consumes the key.
  [@test] ../../prompt/ui/testSrc/AgentPromptEnterHandlersTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptFooterHintDecisionsTest.kt

- Claude slash completion is available only for Claude provider prompts and only for explicit slash-token completion; it merges built-in Claude menu commands with project `.claude/commands` and `.claude/skills` definitions.
  [@test] ../../prompt/ui/testSrc/AgentPromptClaudeSlashCompletionProviderTest.kt

- New-task provider, mode, and generation defaults restore through launch profiles. Prompt drafts may persist provider options and container mode, but they must not persist a separate provider id default.
  [@test] ../../prompt/ui/testSrc/AgentPromptLaunchProfileStateTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- Submit flow must route through `AgentPromptLauncherBridge` using `AgentPromptLaunchRequest`; the prompt UI must not call provider session sources directly.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Inline empty-state submit uses the same validation and launch path as the popup prompt. Successful inline submit clears the submitted draft and resets the inline session without closing editor tabs or creating a popup.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../prompt/ui/testSrc/emptyState/AgentWorkbenchInlinePromptEmptyStateProviderTest.kt

- Inline empty-state mode is always a `NEW_TASK` prompt. It must not restore `EXISTING_TASK` draft mode or extension-tab auto-selection because those controls are hidden in the compact empty-state surface.
  [@test] ../../prompt/ui/testSrc/emptyState/AgentWorkbenchInlinePromptEmptyStateProviderTest.kt

- Inline new-thread mode is also always a `NEW_TASK` prompt. It is hosted inside a deferred chat tab, starts from the selected launch profile, skips extension-tab auto-selection, and keeps the inline prompt visible when `AgentPromptLauncherBridge.launch(...)` returns a failure so the same pending tab can be retried.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSessionControllerTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- New-task launches accepted from the global prompt use the shared generic new-thread deferred tab: provider-neutral centered copy appears immediately, and the spinner appears only after a short delay.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- Plan mode is available only when the selected provider exposes the plan-mode option, persists in project prompt draft state, and is forced off/rejected for busy existing tasks. A typed `/plan` prefix remains prompt text and does not toggle the option.
  [@test] ../../prompt/ui/testSrc/AgentPromptPlanModeDecisionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Codex `NEW_TASK` prompts start through the Codex app-server remote-resume path. Standard prompts start plain app-server turns; Plan prompts do not type `/plan`, and the resumed TUI must visibly enter Plan mode before the prompt starts. An acknowledged no-op `thread/settings/update` without `thread/settings/updated` must not be treated as prompt-send failure.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexPlanPromptRealAppServerIntegrationTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexNewThreadPromptLaunchIntegrationTest.kt

- `NEW_TASK` and `EXISTING_TASK` expose the provider selector. Changing provider in `EXISTING_TASK` reloads the selectable task list for that provider. Provider-backed model and reasoning-effort controls are exposed for `NEW_TASK` through the unified launch-settings control specified by `global-prompt-generation-controls.spec.md`. The prompt composer mental model, context/text/tray ownership, and content-lane layout contract are owned by `global-prompt-composer.spec.md`.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSessionControllerTest.kt

- Generation settings are applied only to `NEW_TASK` launches; `EXISTING_TASK` must not expose editable model or reasoning-effort controls.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Extension tab auto-selection is opt-in through `AgentPromptPaletteExtension.shouldAutoSelect(contextItems)` and applies only to the auto-select action. Active extension tabs own their submit action and bypass provider/options routing, except that an extension may opt back into the provider selector through `AgentPromptPaletteExtension.showsProviderSelector()` and into the per-task model/reasoning controls through `AgentPromptPaletteExtension.showsGenerationControls()`. The chosen provider, generation settings, and model catalog are forwarded to the submit action through the data context.
  [@test] ../../prompt/ui/testSrc/AgentPromptExtensionActionDataContextTest.kt

- Prompt draft persistence must not serialize manual context items. Successful submit or explicit draft clear clears removed-auto-context and manual-context runtime state.
  [@test] ../../prompt/ui/testSrc/AgentPromptDraftPersistenceDecisionsTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- Pasting a clipboard image into the prompt adds it as a screenshot context item. If the clipboard contains copied image files, those files are decoded and preferred over generic `imageFlavor` icon data; non-image copied files must not be consumed by the image paste provider.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptImagePasteProviderTest.kt

## User Experience
- The popup is a focused launcher, not a persistent tool window.
- The empty editor inline composer is a compact persistent empty-state affordance, not a full embedded popup.
- Empty editor rendering must not steal focus; users focus the inline composer by clicking it or invoking the global prompt action while it is visible.
- The inline prompt editor exposes localized accessible name and description metadata, while validation and status feedback continue to use the shared prompt status strip behavior.
- The popup keep-open toggle is a secondary footer control, not part of the primary header action cluster.
- Validation errors appear inline and keep the popup open.
- Successful launches close the popup and clear the submitted draft.
- Successful inline new-thread launches clear the submitted draft and replace the prompt surface by starting the deferred chat tab.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.AgentPrompt*Test"`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.emptyState.AgentWorkbenchInlinePromptEmptyStateProviderTest;com.intellij.agent.workbench.prompt.ui.AgentPromptPalettePopupServiceTest"`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteSessionControllerTest;com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteSubmitControllerTest"`
- `./tests.cmd --module intellij.platform.ide.impl.tests --test com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainterTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLaunchServiceTest`

## References
- `global-prompt-composer.spec.md`
- `add-to-agent-context.spec.md`
- `global-prompt-generation-controls.spec.md`
- `global-prompt-suggestions.spec.md`
- `../prompt-context/prompt-context-contracts.spec.md`
- `../core/agent-core-contracts.spec.md`
