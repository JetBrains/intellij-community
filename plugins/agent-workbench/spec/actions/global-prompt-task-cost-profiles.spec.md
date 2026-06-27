---
name: Global Prompt Task-Cost Profiles
description: Requirements for choosing task-cost launch profiles in the global prompt and main toolbar.
targets:
  - ../../prompt/ui/src/AgentPromptGenerationSettingsController.kt
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../ui/src/AgentWorkbenchPopup.kt
  - ../../prompt/ui/resources/messages/AgentPromptBundle.properties
  - ../../sessions-actions/src/actions/AgentSessionsMainToolbarNewThreadActions.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../sessions/src/state/AgentSessionLaunchProfileStateService.kt
  - ../../sessions/src/state/AgentSessionUiPreferencesStateService.kt
  - ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt
  - ../../ui/testSrc/AgentWorkbenchPopupTest.kt
  - ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt
  - ../../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt
---

# Global Prompt Task-Cost Profiles

Status: Draft
Date: 2026-06-27

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
- The prompt composer must render selected context as attachment cards above the editable prompt text, while the bottom tray places Add Context on the left as a prompt-composition action and exposes one right-side launch-settings affordance for both popup and inline prompt surfaces. Context cards, prompt text, and left tray actions must share one leading composer content lane, while launch settings are right-aligned in that same lane. The launch-settings affordance must show the selected provider icon, including the red YOLO/Brave badge for YOLO profiles, plus the compact selected profile name or generated profile name, and open one popup for profile choice, per-task tuning, and profile management. Default/save/update actions stay visible inline next to the launch-settings affordance. Built-in provider-backed profiles use compact mode labels, such as `Default`, `Full Auto`, `Skip Permissions`, or `Brave Mode`, to avoid repeating provider names. User profiles show their saved profile name, and unmatched edited controls show a generated compact name, such as `High` or `GPT-5 High`.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- The prompt must restore the stored default profile on open when that profile is still applicable. If the default is unavailable, the prompt may fall back to the provider-list default without writing a replacement default.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Choosing a built-in or user profile in the prompt profile popup must apply that profile to the current task only. It must not update the stored default profile id.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- The launch-settings popup must list profiles split into normal profiles and YOLO/Brave profiles, then expose reasoning-effort, Plan-effort, a nested model submenu, and profile management. The model row shows the current model display name or `Default Model` with a submenu chevron; model choices and model-loading status live in that nested submenu. Starting or completing model-catalog refresh from this popup must not recreate the parent popup. Rename, delete, duplicate, and full profile editing remain in profile management.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../ui/testSrc/AgentWorkbenchPopupTest.kt

- The prompt Plan checkbox must remain a separate header option. Selecting or restoring a profile must not change the current Plan checkbox state, and saving/updating user profiles must not store Plan state.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- When the current prompt uses an applicable saved or built-in profile that is not the stored default, the generation-control row must show an inline `Make Default` action. When the current provider, launch mode, model, effort, or Plan effort draft does not match any applicable profile but was edited from a selected saved user profile, the same location must show `Update Profile`; this updates the existing profile without creating a copy or changing the stored default id. Other unmatched drafts must show `Save as Default`; this creates a user profile with a generated name, makes it the stored default, and keeps it active in the prompt. The inline action is hidden when the current profile is already the stored default or generation controls are hidden.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- `Manage Launch Profiles` must open a single application-level standalone non-modal profile editor window with a profile list and a details pane for name, provider, launch mode, editable model, effort, and Plan effort when the selected provider supports dedicated Plan reasoning effort. The model field must allow catalog selection and manually typed provider model ids for providers that support model overrides, and valid custom ids persist automatically with other profile detail edits. Reopening the action while the window is open must focus the existing window instead of creating another one. When the action is invoked from the global prompt, the prompt popup must close while the editor is open and be restored after the editor closes. The editor must allow editing built-in and user profiles. Built-in edits are stored as user customizations for the built-in slot; saving a customized built-in back to the built-in values removes that customization. Selecting rows in the editor must only navigate the editor and must not apply that profile to the current prompt. Quick profile selection may omit unavailable profiles, but the management editor must still show stored unavailable user profiles so they can be deleted or switched to an available provider. `Set as Default` remains an explicit editor action for setting the default without changing the current prompt.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt

- `Copy` must persist the selected profile's current provider, launch mode, model, effort, and Plan effort as a new reusable user profile without also making it the default. Generated names must be compact profile labels suitable for the prompt header, such as `High`, `GPT-5 High`, or `Full Auto`, avoiding provider names when the provider is already identified by icon/details. Valid profile detail edits persist automatically; invalid detail edits remain transient and are discarded when another row is selected. The editor must not expose a separate blank create mode.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- The main toolbar primary click must launch with the stored default profile when one is applicable. When no explicit applicable default exists, it must open the profile picker without launching a fallback. Launching from the primary click must not rewrite the default id.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Choosing a profile from the main toolbar dropdown must launch once with that profile and must not rewrite the stored default id.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- The main toolbar profile dropdown must split normal and YOLO/Brave profiles with a separator instead of grouping by built-in versus user profile storage.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

## User Experience
- Profile labels should read as task presets or compact states, for example `Default`, `Custom`, `Careful`, or `Fast`, not as a raw settings list. The composer should not duplicate the provider name as both icon identity and adjacent text, and model/reasoning values should be exposed by the tuning affordance instead of repeated in the profile label.
- Built-in and user profiles should appear together in the quick chooser. The built-in/user distinction matters in the manage dialog, where built-in edits are stored as removable user customizations.
- The launch-settings popup should show profiles, tuning rows, and one `Manage Launch Profiles` action. Default/save/update actions should stay inline in the composer tray next to the launch-settings control. Model selection should be a nested submenu so the parent popup stays compact and stable while catalog rows load. The composer tray itself should keep exactly one visible launch-settings control.
- Existing-task mode and extension tabs should hide task-cost controls because they do not launch a new task with generation settings.

## Data & Backend
- The persisted `activeLaunchProfileId` value represents the explicit default profile id.
- User launch profiles and the explicit default profile id must be stored in the app-level roamable `AgentSessionLaunchProfileStateV2`, not in the non-roamable UI preferences state.
- Per-task prompt changes live in the prompt controller state and are sent with the launch request; they are not persisted unless the user chooses an explicit default/profile action such as `Save as Default`, `Update Profile`, or `Set as Default` in profile management.
- Profile UI may store Plan effort only for providers that support dedicated Plan reasoning effort, but must not store Plan checkbox state or create profiles that automatically start in Plan mode.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptProviderSelectorTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewStructureTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewLayoutTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsMainToolbarNewThreadActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionUiPreferencesStateServiceTest`

## References
- `global-prompt-entry.spec.md`
