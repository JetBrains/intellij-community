---
name: Global Prompt Entry
description: Requirements for global prompt action behavior, target routing, manual context selection, keyboard semantics, validation, and launcher handoff.
targets:
  - ../../prompt/src/actions/AgentWorkbenchGlobalPromptAction.kt
  - ../../prompt/src/actions/AgentWorkbenchGlobalPromptAutoSelectAction.kt
  - ../../prompt/src/actions/AgentWorkbenchPromptShortcutActionPromoter.kt
  - ../../prompt/src/ui/AgentPromptDraftPersistenceDecisions.kt
  - ../../prompt/ui/src/AgentPromptClaudeSlashCompletionProvider.kt
  - ../../prompt/ui/src/AgentPromptAddContextModels.kt
  - ../../prompt/ui/src/AgentPromptAddToAgentContextActionService.kt
  - ../../prompt/ui/src/AgentPromptEnterHandlers.kt
  - ../../prompt/ui/src/AgentPromptPalettePopup.kt
  - ../../prompt/ui/src/AgentPromptPaletteSessionController.kt
  - ../../prompt/ui/src/AgentPromptPaletteSubmitController.kt
  - ../../prompt/ui/src/AgentPromptTextField.kt
  - ../../prompt/ui/src/actions/AgentWorkbenchAddToAgentContextAction.kt
  - ../../prompt/ui/src/actions/AgentWorkbenchAddToAgentContextIntention.kt
  - ../../prompt/src/ui/AgentPromptPalettePopup.kt
  - ../../prompt/src/ui/AgentPromptPaletteView.kt
  - ../../prompt/src/ui/AgentPromptPaletteModels.kt
  - ../../prompt/src/ui/AgentPromptUiSessionStateService.kt
  - ../../chat/src/AgentChatOpenTabsSnapshot.kt
  - ../../chat/src/AgentChatPendingContextPanel.kt
  - ../../prompt-vcs/src/context/AgentPromptVcsCommitManualContextSource.kt
  - ../../prompt/resources/intellij.agent.workbench.prompt.xml
  - ../../prompt/resources/messages/AgentPromptBundle.properties
  - ../../prompt/ui/resources/messages/AgentPromptBundle.properties
  - ../../prompt/core/src/AgentPromptLauncherBridge.kt
  - ../../prompt/core/src/AgentPromptPaletteExtension.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../prompt/testSrc/ui/AgentPromptProviderSelectionDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptSubmitValidationDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptFooterHintDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPlanModeDecisionsTest.kt
  - ../../prompt/ui/testSrc/AgentPromptExistingTaskControllerTest.kt
  - ../../prompt/ui/testSrc/AgentPromptClaudeSlashCompletionProviderTest.kt
  - ../../prompt/ui/testSrc/AgentPromptEnterHandlersTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  - ../../prompt/testSrc/ui/AgentPromptEnterHandlersTest.kt
  - ../../prompt/testSrc/ui/AgentPromptDraftPersistenceDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt
  - ../../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt
  - ../../prompt/testSrc/actions/AgentWorkbenchPromptActionPromoterTest.kt
  - ../../prompt/ui/testSrc/actions/AgentWorkbenchAddToAgentContextActionTest.kt
  - ../../plugin/testSrc/AgentWorkbenchAddToAgentContextActionRegistrationTest.kt
  - ../../prompt-vcs/testSrc/context/AgentPromptVcsCommitManualContextSourceTest.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
---

# Global Prompt Entry

Status: Draft
Date: 2026-03-14

## Summary
Define the global prompt entrypoint opened by `Cmd+\\` (macOS) / `Ctrl+\\` (Windows/Linux), including popup behavior, target mode switching, submit validation, and launch handoff.

Prompt-context collection and rendering contracts are specified separately in `spec/prompt-context/*.spec.md`.
Suggested prompt generation, rendering, and Codex polishing are specified separately in `spec/actions/global-prompt-suggestions.spec.md`.

## Goals
- Keep launch behavior consistent between `NEW_TASK` and `EXISTING_TASK` modes.
- Keep keyboard semantics explicit and testable.
- Keep submit validation deterministic before calling the launcher bridge.

## Non-goals
- Defining per-source prompt context payload/rendering details.
- Defining context-based suggested prompt generation or AI polishing behavior.
- Defining provider session discovery backend internals.
- Defining command composition semantics (covered by `spec/agent-core-contracts.spec.md`).

## Requirements
- Global action id `AgentWorkbenchPrompt.OpenGlobalPalette` must be available only when a project is open.

- When `AgentWorkbenchPrompt.OpenGlobalPalette` is invoked while the global prompt popup is already visible for the same project, the existing popup must be focused instead of creating a second popup instance.

- Re-focusing an already visible popup must preserve its live state, including prompt text, selected tab, provider selection, and context chips.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt

- When the IDE loses application focus because the user switches to another app, the main global prompt popup must remain open and preserve its live state until it is explicitly dismissed.

- When the same project frame becomes active again while the main global prompt popup remains visible, whether after switching to another app or after switching to another IDE project frame, the popup must request focus again.
  [@test] ../../prompt/testSrc/ui/AgentPromptPalettePopupActivationDecisionsTest.kt

- When the main global prompt popup remains visible, clicking in another IDE frame, including Agent Workbench dedicated frame, must not dismiss it.
- Switching between IDE frames or apps via keyboard window-switch shortcuts must also not dismiss the main global prompt popup.
- Clicking outside the popup inside the originating IDE frame dismisses the popup, unless the click is the same one that activates the IDE frame from an inactive state (user was in another app or another IDE frame) — that activation click must not dismiss the popup. Clicks inside child windows owned by the originating IDE frame (for example, floating tool windows, detached editor windows, or owned dialogs) count as clicks inside the originating IDE frame for dismissal purposes.
  [@test] ../../prompt/testSrc/ui/AgentPromptPalettePopupDismissalDecisionsTest.kt

- Chooser popups opened from the main global prompt remain transient and may still close on application deactivation.

- When both `AgentWorkbenchPrompt.OpenGlobalPalette` and `AIAssistant.Editor.AskAiAssistantInEditor` are applicable for `Cmd+\\` / `Ctrl+\\` in an editor context, `AgentWorkbenchPrompt.OpenGlobalPalette` must be executed first.

- Global action id `AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect` must be available only when a project is open. It opens the same popup but with EP-driven extension tab auto-selection (see below).

- When `AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect` is invoked while the global prompt popup is already visible for the same project, it must behave the same as the standard action and only focus the existing popup.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt

- `AgentWorkbenchPrompt.AddToAgentContext` and the corresponding editor intention must collect default prompt context from the invocation place and route it into the global prompt popup. If no context is available, they must show the `popup.status.context.empty` status and must not open a popup.

- `AgentWorkbenchPrompt.AddToAgentContext` must be registered into the following invocation popups so the action surfaces wherever a `prompt.context` contributor can produce context:
  - `EditorPopupMenu` (anchored inside the cut/copy/paste cluster, after `Copy.Paste.Special`),
  - `ProjectViewPopupMenu` (anchored inside the cut/copy/paste/edit cluster, after `ProjectViewEditSource`),
  - `EditorTabPopupMenu` (anchored inside the copy cluster, after `CopyPaths`),
  - `ConsoleEditorPopupMenu` (anchored inside the second cluster, after `$SearchWeb`),
  - `TestTreePopupMenu` (anchored last),
  - `ChangesViewPopupMenu`, `Vcs.Log.ContextMenu`, `Vcs.Log.ChangesBrowser.Popup` (provided by the `prompt.vcs.ui` sub-module).
  [@test] ../../plugin/testSrc/AgentWorkbenchAddToAgentContextActionRegistrationTest.kt

- Visibility gating (`update()`) for `AgentWorkbenchPrompt.AddToAgentContext` must be place-specific without introducing module dependencies:
  - in `ActionPlaces.EDITOR_POPUP`, the action is visible only when the editor has a non-empty document and a backing virtual or PSI file,
  - in `ActionPlaces.PROJECT_VIEW_POPUP`, the action is visible only when the selection contains at least one local-filesystem file (`.` is not considered a local context file),
  - in `ActionPlaces.EDITOR_TAB_POPUP`, the action is visible only when the tab is backed by a local-filesystem virtual file,
  - in all other registered places (console, test tree, VCS popups), visibility falls back to the project-presence check and the contributor empty-fallback path emits `popup.status.context.empty` when no context is available.

  [@test] ../../prompt/ui/testSrc/actions/AgentWorkbenchAddToAgentContextActionTest.kt
  [@test] ../../plugin/testSrc/AgentWorkbenchAddToAgentContextActionRegistrationTest.kt

- When `AgentWorkbenchPrompt.AddToAgentContext` is invoked while the global prompt popup is already visible for the same project, it must append de-duplicated context to the visible popup, request composer focus, and preserve the popup's current tab, provider selection, and existing-task selection.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt

- When `AgentWorkbenchPrompt.AddToAgentContext` is invoked without a visible prompt popup, existing-chat auto-routing must use only open top-level concrete agent chat tabs for the same normalized project path. Pending chat tabs, sub-agent tabs, and non-open session catalog threads must not be target candidates.

- Fresh `AddToAgentContext` routing must be deterministic:
  - no open chat target candidate opens the popup on `PromptTargetMode.NEW_TASK`,
  - one open chat target candidate adds context to that Agent Chat tab's pending context buffer,
  - a selected open chat target candidate adds context to that Agent Chat tab's pending context buffer,
  - multiple open chat target candidates without a selected candidate show the `popup.add.context.target.chooser.title` chooser and add context to the chosen Agent Chat tab,
  - chooser cancellation must not open a popup.

- Add-context target candidates marked as selected must be ordered before other candidates. A selected candidate represents the currently selected top-level concrete chat tab for the same normalized project path.

- Prompt target mode must support exactly:
  - `PromptTargetMode.NEW_TASK`,
  - `PromptTargetMode.EXISTING_TASK`.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt

- Prompt editor must be a shared component across both target modes; switching tabs must not recreate or hide the prompt viewport.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt

- Prompt composer must show an `Add Context` affordance only when at least one manual context source is available for the current project.
- When shown, `Add Context` must be rendered once as a fixed control inside the prompt composer. Existing context chips must render in the same composer-integrated context cluster without repositioning that control.
- `Add Context` must expose inline mnemonic activation consistent with other prompt controls.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt

- Existing-task pane must stay bounded and must not starve prompt editor layout.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt

- Opening the standard global prompt must default to `PromptTargetMode.NEW_TASK`. Switching to `PromptTargetMode.EXISTING_TASK` may preselect the focused open chat-tab thread for the selected provider, or the lone loaded thread when exactly one existing task is loaded, but it must not switch the tab automatically.
  [@test] ../../prompt/ui/testSrc/AgentPromptExistingTaskControllerTest.kt

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
  - `Enter` runs submit action from the prompt editor and from the existing-task selector when it owns focus,
  - `Shift+Enter` inserts line break,
  - `Tab` submits only when tab-queue shortcut is enabled; otherwise it selects the next available prompt tab and wraps around,
  - `Shift+Tab` selects the previous available prompt tab and wraps around,
  - when a prompt-editor completion lookup is open, `Enter` must accept the selected lookup item instead of submitting,
  - when a prompt-editor completion lookup is open, `Tab` must replace-complete the selected lookup item instead of triggering prompt tab navigation or submit behavior.
  [@test] ../../prompt/testSrc/ui/AgentPromptEnterHandlersTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptEnterHandlersTest.kt

- Claude-only slash completion contract:
  - slash completion is available only when the selected provider is `CLAUDE`,
  - slash completion operates only on the current whitespace-delimited token when that token starts with `/`,
  - ordinary non-slash tokens and path fragments such as `/path/to/file.txt` must not trigger Claude menu completion unless the token is being completed explicitly,
  - auto-popup is allowed only when `/` is typed as the first prompt character,
  - completion entries merge three sources: a curated built-in Claude menu-command set, `.claude/commands/*.md` command names, and `.claude/skills/*/SKILL.md` skill names discovered from the effective working project path and its ancestors,
  - completion entries must show source-kind type labels and any available argument hint; custom command and skill argument hints come from `argument-hint` frontmatter, and built-in menu commands may provide documented optional argument hints,
  - for duplicate slash names from the same source kind, the nearest ancestor definition wins,
  - when the same slash name exists as both a command and a skill, both lookup items must remain visible with type labels.
  [@test] ../../prompt/ui/testSrc/AgentPromptClaudeSlashCompletionProviderTest.kt

- Tab-queue shortcut must be enabled only when target mode is `EXISTING_TASK`, selected provider is `CODEX`, and there is no next prompt tab to select.
  [@test] ../../prompt/testSrc/ui/AgentPromptFooterHintDecisionsTest.kt

- Footer hint contract:
  - existing-task Codex mode uses Codex-specific hint key only when the tab-queue shortcut is enabled,
  - all other states use default hint key,
  - explicit existing-task selection hint is shown only when mode is `EXISTING_TASK`, selection is empty, and provider is non-Codex.
  [@test] ../../prompt/testSrc/ui/AgentPromptFooterHintDecisionsTest.kt

- Provider restore order for opening the prompt must be:
  - shared provider preferences from `AgentPromptLauncherBridge.loadProviderPreferences()` (authoritative, updated by all launch surfaces),
  - prompt draft `providerId` (fallback for when shared preferences have no provider),
  - provider-list default ordering.
  [@test] ../../prompt/testSrc/ui/AgentPromptProviderSelectionDecisionsTest.kt

- Submit flow must route through `AgentPromptLauncherBridge` using `AgentPromptLaunchRequest`; prompt popup must not directly call provider session sources.

- When the selected provider is `CLAUDE` and the submitted prompt starts with a recognized Claude menu command, the prompt popup must submit only the raw slash command text and must omit prompt context packaging (`initialMessageRequest.contextItems` and any context summary envelope) for that launch request.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt

- Successful prompt launch must update the shared preferred provider used by future prompt openings and new-thread affordances.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Plan mode toggle contract:
  - Toggle is visible when selected provider's bridge exposes the `AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE` prompt option.
  - Toggle default is enabled and is persisted in per-project prompt draft state.
  - When effective plan mode is enabled, submit must include `AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE` in `initialMessageRequest.providerOptionIds`.
  - Effective plan mode must be forced off for `EXISTING_TASK` target when selected thread activity is `PROCESSING` or `REVIEWING`.
  - Manual prompts that resolve to effective plan mode (for example `/plan ...`) must obey the same `EXISTING_TASK` busy-task restriction as the toggle path.
  - Existing-task prompt launch must reject effective plan-mode requests when the selected thread activity is `PROCESSING` or `REVIEWING`.
  - Providers without the plan-mode prompt option must always submit with plan mode disabled.
  [@test] ../../prompt/testSrc/ui/AgentPromptPlanModeDecisionsTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

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
  - applying a suggested prompt without later manual edits must not replace the persisted per-tab user draft; closing and reopening restores the last user-authored text for that tab, or empty text when none existed,
    [@test] ../../prompt/testSrc/ui/AgentPromptDraftPersistenceDecisionsTest.kt
  - successful submit or explicit draft clear must clear both removed-auto context state and manual context state.
    [@test] ../../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt

## User Experience
- Popup opens as a project-scoped launcher for both new and existing task targets.
- Existing-task mode exposes provider-scoped thread list with loading/empty/error states.
- Context chips are removable before submit.
- Composer context cluster is shown when at least one context chip is present or when `Add Context` is available; it collapses only when both chips and the `Add Context` affordance are absent.
- Chip-removal hierarchy is provider-defined via context item relations (`itemId`/`parentItemId`); removing a parent chip may remove all descendant chips recursively.
- The composer-integrated context cluster exposes `Add Context` as the single entry point for adding source-specific context.
- `Files and Folders…` is the default manual context source for any resolved project and may be accompanied by source-specific additions such as VCS commits.
- Manual context chips share the same composer-integrated context cluster and submit path as auto context chips.
- `Add to Agent Context` first applies to a visible prompt composer. If no prompt composer is visible and a single open Agent Chat target, or one selected open Agent Chat target, is available for the workspace, it adds de-duplicated context to that chat's pending context buffer without mutating terminal input.
- If no open Agent Chat target is available, `Add to Agent Context` opens the prompt composer with removable context chips instead of opening a chat thread.
- If multiple open Agent Chat targets are available and no target is selected, `Add to Agent Context` asks for the target, then uses the same pending-context path with prompt-composer fallback.

## Data & Backend
- Existing-task list comes from launcher `observeExistingThreads(...)` stream with background refresh.
- Existing-task list must be scoped to resolved working project path.
- `AgentPromptLauncherBridge.listAddContextTargetCandidates(projectPath)` is the shared signal for fresh add-context routing and existing-task row preselection. It must describe open chat targets only, not the full session catalog.
- `AgentPromptLauncherBridge.addContextToOpenChatTarget(...)` is the open-chat pending-context hook. `ADDED_TO_CHAT` means the context was accepted into an existing chat's pending context buffer; `UNAVAILABLE` must fall back to opening the prompt composer with the same context.
- On successful launch, popup closes and draft is cleared; otherwise popup remains and shows error feedback.
- `AgentPromptContextResolverService.collectDefaultContext(...)` remains the auto-context source of truth; manual context is merged later in popup state.
- Manual context sources are discovered via `com.intellij.agent.workbench.promptManualContextSource` and invoked from popup-owned UI state.

## Error Handling
- Validation errors are shown inline in footer using message keys.
- Existing-task loading failures degrade to empty/error list state without crashing popup.
- Add-context target-candidate loading failures degrade to `NEW_TASK` routing without crashing popup.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptSubmitValidationDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptFooterHintDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPlanModeDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptEnterHandlersTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.actions.AgentWorkbenchPromptActionPromoterTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewStructureTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewLayoutTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPalettePopupServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptExistingTaskControllerTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.actions.AgentWorkbenchAddToAgentContextActionTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.plugin.AgentWorkbenchAddToAgentContextActionRegistrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.context.AgentPromptProjectPathsManualContextSourceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsCommitManualContextSourceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatOpenTopLevelDispatchTest'`

## References
- `global-prompt-suggestions.spec.md`
- `../prompt-context/prompt-context-contracts.spec.md`
- `../prompt-context/prompt-context-editor.spec.md`
- `../prompt-context/prompt-context-files.spec.md`
- `../prompt-context/prompt-context-project-view.spec.md`
- `../prompt-context/prompt-context-vcs.spec.md`
- `../prompt-context/prompt-context-test-runner.spec.md`
- `../agent-core-contracts.spec.md`
