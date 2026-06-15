---
name: Global Prompt Task-Cost Profiles
description: Requirements for choosing task-cost launch profiles in the global prompt and main toolbar.
targets:
  - ../../prompt/ui/src/AgentPromptGenerationSettingsController.kt
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../prompt/ui/resources/messages/AgentPromptBundle.properties
  - ../../sessions-actions/src/actions/AgentSessionsMainToolbarNewThreadActions.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../sessions/src/state/AgentSessionLaunchProfileStateService.kt
  - ../../sessions/src/state/AgentSessionUiPreferencesStateService.kt
  - ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt
  - ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt
  - ../../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt
---

# Global Prompt Task-Cost Profiles

Status: Draft
Date: 2026-06-13

## Summary
Task-cost profiles make the global prompt ready to send while still letting users choose a cheaper, faster, or more careful agent setup per task. Profiles are the primary user-facing abstraction for provider, launch mode, model, and reasoning effort. Plan mode remains a separate prompt option; changing profiles must not silently change it.

## Goals
- Make the first prompt screen ready to send with the user's explicit default profile.
- Let users choose a profile for one task without accidental persistence.
- Keep persistent profile/default changes behind explicit actions.

## Non-goals
- This spec does not define provider setup, CLI availability checks, pricing display, or session-cost accounting.
- This spec does not require renaming the existing persisted `activeLaunchProfileId` field; it may remain the stored default profile id for compatibility.

## Requirements
- The prompt header must expose a single task-cost profile control in the same top-right header location as the previous provider selector. The control must show the selected provider icon, including the red YOLO/Brave badge for YOLO profiles, and open the profile chooser on click. Built-in provider-backed profiles use compact mode labels in the header, such as `Standard`, `Full Auto`, `Skip Permissions`, or `Brave Mode`, to avoid repeating provider names. User profiles show their saved profile name, and unmatched edited controls show `Custom`.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- The prompt must restore the stored default profile on open when that profile is still applicable. If the default is unavailable, the prompt may fall back to the provider-list default without writing a replacement default.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Choosing a built-in or user profile in the prompt profile popup must apply that profile to the current task only. It must not update the stored default profile id.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- The prompt profile popup must list profiles only, split into normal profiles and YOLO/Brave profiles with a separator. Raw model, reasoning-effort, Plan-effort, rename, delete, update, default, and duplicate controls must not appear in this quick popup.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- The prompt Plan checkbox must remain a separate header option. Selecting or restoring a profile must not change the current Plan checkbox state, and saving/updating user profiles must not store Plan state.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- When the current prompt uses an applicable saved or built-in profile that is not the stored default, the generation-control row must show an inline `Make Default` action. When the current provider, launch mode, model, effort, or Plan effort draft does not match any applicable profile, the same location must show `Save as Default`; this creates a user profile with a generated name, makes it the stored default, and keeps it active in the prompt. The inline action is hidden when the current profile is already the stored default or generation controls are hidden.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- `Manage Launch Profiles` must open a single application-level standalone non-modal profile editor window with a profile list and a details pane for name, provider, launch mode, model, effort, and Plan effort when the selected provider supports dedicated Plan reasoning effort. Reopening the action while the window is open must focus the existing window instead of creating another one. When the action is invoked from the global prompt, the prompt popup must close while the editor is open and be restored after the editor closes. The editor must allow editing built-in and user profiles. Built-in edits are stored as user customizations for the built-in slot; saving a customized built-in back to the built-in values removes that customization. Selecting rows in the editor must only navigate the editor and must not apply that profile to the current prompt. Quick profile selection may omit unavailable profiles, but the management editor must still show stored unavailable user profiles so they can be deleted or switched to an available provider. `Set as Default` remains an explicit editor action for setting the default without changing the current prompt.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt

- `Copy` must persist the selected profile's current provider, launch mode, model, effort, and Plan effort as a new reusable user profile without also making it the default. Generated names must be compact profile labels suitable for the prompt header, such as `High`, `GPT-5 High`, or `Full Auto`, avoiding provider names when the provider is already identified by icon/details. Valid profile detail edits persist automatically; invalid detail edits remain transient and are discarded when another row is selected. The editor must not expose a separate blank create mode.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- The main toolbar primary click must launch with the stored default profile when one is applicable, falling back to the first available built-in profile. Launching from the primary click must not rewrite the default id.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Choosing a profile from the main toolbar dropdown must launch once with that profile and must not rewrite the stored default id.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- The main toolbar profile dropdown must split normal and YOLO/Brave profiles with a separator instead of grouping by built-in versus user profile storage.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

## User Experience
- Profile labels should read as task presets or compact states, for example `Standard`, `Custom`, `Careful`, or `Fast`, not as a raw settings list. The header should not duplicate the provider name as both icon identity and adjacent text, and `default` wording should be reserved for persisted future-run selection.
- Built-in and user profiles should appear together in the quick chooser. The built-in/user distinction matters in the manage dialog, where built-in edits are stored as removable user customizations.
- The profile popup should show profiles and one `Manage Launch Profiles` action. Defaulting and saving the current draft are exposed as a compact inline action in the generation-control row, outside the popup.
- Existing-task mode and extension tabs should hide task-cost controls because they do not launch a new task with generation settings.

## Data & Backend
- The persisted `activeLaunchProfileId` value represents the explicit default profile id.
- User launch profiles and the explicit default profile id must be stored in the app-level roamable `AgentSessionLaunchProfileStateV2`, not in the non-roamable UI preferences state.
- Per-task prompt changes live in the prompt controller state and are sent with the launch request; they are not persisted unless the user chooses `Set as Default` or `Save Current` in profile management.
- Profile UI may store Plan effort only for providers that support dedicated Plan reasoning effort, but must not store Plan checkbox state or create profiles that automatically start in Plan mode.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptProviderSelectorTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewStructureTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewLayoutTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsMainToolbarNewThreadActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionUiPreferencesStateServiceTest`

## References
- `global-prompt-entry.spec.md`
