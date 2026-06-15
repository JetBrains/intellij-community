---
name: Global Prompt Entry
description: Requirements for opening, focusing, submitting, and routing the Agent Workbench global prompt.
targets:
  - ../../prompt/ui/src/actions/AgentWorkbenchGlobalPromptAction.kt
  - ../../prompt/ui/src/AgentPromptPalettePopup.kt
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../prompt/ui/src/AgentPromptPaletteSubmitController.kt
  - ../../prompt/ui/src/AgentPromptEnterHandlers.kt
  - ../../prompt/ui/src/AgentPromptExistingTaskController.kt
  - ../../prompt/ui/src/AgentPromptProviderSelector.kt
  - ../../prompt/core/src/AgentPromptLauncherBridge.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../prompt/ui/testSrc/*.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
---

# Global Prompt Entry

Status: Draft
Date: 2026-05-16

## Summary
The global prompt opens a project-scoped popup for starting a new task or sending a prompt to an existing loaded task. This spec owns popup lifecycle, target mode, validation, keyboard behavior, provider selection, and launcher handoff. Context collection and Add-to-Agent-Context routing are specified separately.

## Requirements
- `AgentWorkbenchPrompt.OpenGlobalPalette` and `AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect` are available only with an open project. Invoking either action while the popup is already visible for the same project focuses the existing popup and preserves live state.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt

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

- Working project path resolution must never use the dedicated-frame project path. In a dedicated Agent frame, it resolves from selected Sessions context, selected chat tab source path, then most recent non-dedicated project; unresolved submits prompt for a source project and keep the popup open on cancel.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Keyboard behavior is: Enter submits, Shift+Enter inserts a line break, Tab/Shift+Tab switch prompt tabs unless completion or Codex tab-queue handling consumes the key.
  [@test] ../../prompt/ui/testSrc/AgentPromptEnterHandlersTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptFooterHintDecisionsTest.kt

- Claude slash completion is available only for Claude provider prompts and only for explicit slash-token completion; it merges built-in Claude menu commands with project `.claude/commands` and `.claude/skills` definitions.
  [@test] ../../prompt/ui/testSrc/AgentPromptClaudeSlashCompletionProviderTest.kt

- Provider restore order is shared provider preferences, persisted prompt draft provider, then provider-list default order.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectionDecisionsTest.kt

- Submit flow must route through `AgentPromptLauncherBridge` using `AgentPromptLaunchRequest`; the prompt UI must not call provider session sources directly.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Plan mode is available only when the selected provider exposes the plan-mode option, persists in project prompt draft state, and is forced off/rejected for busy existing tasks. A typed `/plan` prefix remains prompt text and does not toggle the option.
  [@test] ../../prompt/ui/testSrc/AgentPromptPlanModeDecisionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- `NEW_TASK` exposes provider-backed model and reasoning-effort controls as specified by `global-prompt-generation-controls.spec.md`. Header actions stay limited to prompt-surface tools such as provider selection, Plan mode, Run in container, preview, and prompt library.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Generation settings are applied only to `NEW_TASK` launches; `EXISTING_TASK` must not expose editable model or reasoning-effort controls.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Extension tab auto-selection is opt-in through `AgentPromptPaletteExtension.shouldAutoSelect(contextItems)` and applies only to the auto-select action. Active extension tabs own their submit action and bypass provider/options routing, except that an extension may opt back into the provider selector through `AgentPromptPaletteExtension.showsProviderSelector()` and into the per-task model/reasoning controls through `AgentPromptPaletteExtension.showsGenerationControls()`. The chosen provider, generation settings, and model catalog are forwarded to the submit action through the data context.
  [@test] ../../prompt/ui/testSrc/AgentPromptExtensionActionDataContextTest.kt

- Prompt draft persistence must not serialize manual context items. Successful submit or explicit draft clear clears removed-auto-context and manual-context runtime state.
  [@test] ../../prompt/ui/testSrc/AgentPromptDraftPersistenceDecisionsTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

## User Experience
- The popup is a focused launcher, not a persistent tool window.
- Validation errors appear inline and keep the popup open.
- Successful launches close the popup and clear the submitted draft.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.AgentPrompt*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`

## References
- `add-to-agent-context.spec.md`
- `global-prompt-generation-controls.spec.md`
- `global-prompt-suggestions.spec.md`
- `../prompt-context/prompt-context-contracts.spec.md`
- `../core/agent-core-contracts.spec.md`
