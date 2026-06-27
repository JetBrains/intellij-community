---
name: Global Prompt Generation Controls
description: Requirements for Ask Agent launch profile, model, and reasoning-effort controls in the global prompt.
targets:
  - ../../prompt/ui/src/AgentPromptGenerationSettingsController.kt
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../prompt/core/src/AgentPromptModels.kt
  - ../../prompt/core/src/AgentPromptLauncherBridge.kt
  - ../../lib-agent/sessions-core/src/providers/AgentSessionLaunchProfiles.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../sessions/src/state/AgentSessionUiPreferencesStateService.kt
  - ../../sessions-actions/src/actions/AgentSessionsMainToolbarNewThreadActions.kt
  - ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  - ../../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt
  - ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt
---

# Global Prompt Generation Controls

Status: Draft
Date: 2026-06-09

## Summary
Ask Agent launch controls let users choose a provider, launch mode, model, and normal reasoning effort through task-cost profiles, while Plan mode remains an independent prompt option. The controls are scoped to `NEW_TASK` launches, use built-in provider/mode profiles unless explicitly changed, and persist custom profiles only through explicit profile management actions.

## Requirements
- The prompt composer places one launch-settings affordance in the bottom tray location specified by `global-prompt-composer.spec.md`. The launch-settings affordance shows the selected provider icon plus a compact profile/model/reasoning summary and opens one popup containing profile choices, provider-backed model selection when available, normal reasoning-effort selection, Plan-mode reasoning-effort selection when available, and profile management. Profile default/save/update actions stay visible inline next to the launch-settings affordance.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Built-in launch profiles are derived from the currently available provider/mode menu. A profile captures provider, launch mode, and generation settings. Unavailable provider/mode combinations are omitted instead of remaining selectable.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- The model selector displays `Default Model` for provider-auto model behavior, while normal reasoning effort displays `Default`; both send no CLI override. Codex model selection is populated from the shared Codex app-server `model/list` catalog. Claude Code model selection uses a hardcoded alias catalog because Claude Code does not expose a reliable dynamic model catalog; explicit Claude selections must launch with `--model <id>` before the `--` prompt separator.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- Plan-mode reasoning effort is distinct from normal effort and is exposed only for providers that support a dedicated Plan reasoning effort transport. The Plan effort control is visible for such providers, supports `Same as Effort`, `Provider Default`, and explicit efforts, is enabled and applied only while Plan mode is selected, and clears the Plan-only override when Plan mode is not selected.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Codex Plan mode applies the selected model through the normal Codex model override because the Plan collaboration mask inherits the active model. A selected Plan-mode reasoning effort must also be passed through Codex's Plan-only `plan_mode_reasoning_effort` config so Plan turns do not fall back to the Codex Plan preset effort.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Provider model catalogs load on demand when either the global prompt model selector or the launch profile editor model combo is opened. After a catalog has loaded once, reopening either control within 30 seconds must show the cached catalog immediately without provider I/O. Older cached catalogs must still render immediately and refresh in the background. The background refresh status appears after 3 seconds to avoid flicker for fast refreshes; if refresh fails after cached data exists, keep the cached choices visible and show the refresh failure inline. Saved model ids that are absent from the current catalog must remain visible as custom choices instead of being dropped.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Model selector rows must keep `Default Model` first, then group explicit models with separators in this order: local models, OpenAI/Codex models, Claude Code models, and other models. Claude Code explicit rows must follow Claude Code menu order: `Opus`, `Sonnet`, `Sonnet (1M context)`, `Haiku`; additional supported aliases such as `Fable` follow those menu-derived rows. The same ordering, loading, retry, cached refresh, and custom-id preservation behavior applies in the launch profile editor's model combo.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Quick profile selection is transient for the current launch. It must not be saved on submit or popup close. The active profile label shows a modified state when the draft differs from the selected profile's provider, launch mode, model, or effort.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Persisted launch preferences are user profiles layered beside built-in profiles. The prompt generation-control row may explicitly save or default the current draft inline; broader management actions such as rename, delete, and built-in customization remain in `Manage Launch Profiles`.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionUiPreferencesStateServiceTest.kt

- Launch profile popup actions expose only the quick choice: applicable normal profiles, a separator, applicable YOLO/Brave profiles, and `Manage Launch Profiles`. Unavailable user profiles are omitted instead of being selected silently.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Generation settings are applied only to `NEW_TASK` launches. `EXISTING_TASK` must not expose editable model or reasoning-effort controls.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- The main toolbar New Thread split button uses the stored default launch profile for quick launch. Its picker lists normal profiles, then YOLO/Brave profiles, launches once with the chosen profile without changing the stored default, and launches using the profile's stored provider, mode, model, and normal effort settings.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

## User Experience
- `Default` belongs to provider/model/effort selector state and to the compact built-in standard profile label. The profile control uses compact state labels: `Default` for the built-in standard profile, saved profile names for exact user profiles, and `Custom` when current controls do not match an applicable profile. Model and reasoning details belong to the tuning affordance tooltip/accessibility text, not to the profile label. Plan mode belongs to the separate Plan checkbox, not to the selected profile.
- Popup tray controls use normal label weight, inline tray controls stay compact, and the launch-settings affordance must not visually dominate context attachments or prompt text.
- Built-in profiles are safe fallbacks and should not require users to create a profile before the toolbar quick launch works.
- Disabled popup actions are reserved for genuinely unavailable commands, not already-satisfied saved states.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptProviderSelectorTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteSubmitControllerTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionUiPreferencesStateServiceTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsMainToolbarNewThreadActionsTest`

## References
- `global-prompt-composer.spec.md`
- `global-prompt-entry.spec.md`
- `../core/agent-core-contracts.spec.md`
