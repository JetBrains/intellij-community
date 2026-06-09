---
name: Global Prompt Generation Controls
description: Requirements for Ask Agent model and reasoning-effort controls in the global prompt.
targets:
  - ../../prompt/ui/src/AgentPromptGenerationSettingsController.kt
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../prompt/ui/src/AgentPromptPaletteSubmitController.kt
  - ../../prompt/core/src/AgentPromptModels.kt
  - ../../prompt/core/src/AgentPromptLauncherBridge.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
---

# Global Prompt Generation Controls

Status: Draft
Date: 2026-06-09

## Summary
Ask Agent generation controls let users choose the model and reasoning effort for a new task without changing provider selection or prompt options. The controls are scoped to `NEW_TASK` launches, use provider defaults unless explicitly overridden, and persist Ask Agent defaults only through explicit popup actions.

## Requirements
- Provider-backed model selection, when available, and reasoning-effort selection render as low-emphasis floating controls inside the prompt input surface, with no separate editor border, background strip, or divider. Header actions stay limited to prompt-surface tools such as provider selection, Plan mode, Run in container, preview, and prompt library.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Model and reasoning effort default to `Default`, which means provider-auto behavior and sends no CLI override. Codex model selection is populated from the shared Codex app-server `model/list` catalog; Claude Code model selection remains hidden until a reliable dynamic model catalog is available.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- Provider model catalogs load on demand when the model selector is opened. After a catalog has loaded once, reopening the selector within 30 seconds must show the cached catalog immediately without provider I/O. Older cached catalogs must still render immediately and refresh in the background. The background refresh status appears after 3 seconds to avoid flicker for fast refreshes; if refresh fails after cached data exists, keep the cached choices visible and show the refresh failure inline.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Quick changes in the composer stripe are transient for the current launch. They must not be saved as last-used preferences on submit or popup close.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Persisted Ask Agent generation preferences are per-provider overrides layered above provider defaults. They require the explicit `Save for Ask Agent` action from a generation selector and can be removed with `Clear Ask Agent Default`.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Generation selector footer actions expose only meaningful commands: hide the footer when provider defaults are active and no Ask Agent override exists; show `Save for Ask Agent` only when it would persist a new or different non-provider-default override; show `Clear Ask Agent Default` when a saved Ask Agent override exists and the current effective settings are provider-default or already match the saved override. Do not show disabled no-op save actions.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- Generation settings are applied only to `NEW_TASK` launches. `EXISTING_TASK` must not expose editable model or reasoning-effort controls.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

## User Experience
- `Default` belongs to the provider/model/effort selector state; `Ask Agent Default` belongs to the IDE-side saved override.
- Disabled popup actions are reserved for genuinely unavailable commands, not already-satisfied saved states.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptProviderSelectorTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteSubmitControllerTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`

## References
- `global-prompt-entry.spec.md`
- `../agent-core-contracts.spec.md`
