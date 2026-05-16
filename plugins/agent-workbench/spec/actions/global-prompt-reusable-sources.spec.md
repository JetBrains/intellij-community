---
name: Global Prompt Reusable Sources
description: Requirements for prompt history and reusable prompt insertion in the global prompt popup.
targets:
  - ../../prompt/core/src/AgentPromptReusableSourceEntry.kt
  - ../../prompt/core/src/AgentPromptLauncherBridge.kt
  - ../../prompt/ui/src/AgentPromptLibraryModel.kt
  - ../../prompt/ui/src/AgentPromptPaletteSessionController.kt
  - ../../prompt/ui/src/AgentPromptClaudeSlashCompletionProvider.kt
  - ../../prompt/ui/src/AgentPromptReusableSourceCollector.kt
  - ../../prompt/ui/src/AgentPromptUiSessionStateService.kt
  - ../../prompt/ui/testSrc/AgentPrompt*Test.kt
  - ../../sessions-core/src/providers/AgentSessionProviderDescriptor.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../sessions/testSrc/CodexAppServerClientTest.kt
---

# Global Prompt Reusable Sources

Status: Draft
Date: 2026-05-16

## Summary
The global prompt popup lets users reuse saved prompts, recent submitted prompts, and project prompt files without leaving the prompt field. This spec owns local saved-prompt persistence, history persistence, prompt-file discovery, prompt-library preview behavior, and provider-specific command completion.

## Requirements
- Successful standard prompt submissions must be persisted to prompt history. Failed submissions, blank prompts, and extension-tab submits must not create history entries.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt

- Prompt history must normalize line endings to `\n`, trim outer whitespace, de-duplicate exact prompt text, move repeated prompts to the front, sort newest first, and retain at most 50 entries.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- Saved prompts must persist in Agent Workbench project state, normalize line endings to `\n`, trim outer whitespace, de-duplicate exact prompt text, move repeated saves to the front, and retain at most 50 entries. Saving a recent prompt must not create, edit, or delete repository files.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- Prompt history must be separate from prompt drafts. Clearing or restoring a draft must not clear history, and previewing a history entry must be cancellable without mutating the saved draft.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteDraftControllerTest.kt

- The popup header must expose a single prompt-library action for prompt-text reuse. The prompt-library chooser previews the selected recent prompt or prompt file in the shared prompt editor, accepts by replacing the prompt text, and restores the previous prompt snapshot when dismissed without accepting.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt

- Reusable prompt sources must include non-blank `.github/prompts/*.prompt.md` files discovered from the working project path and its ancestors. If a prompt file contains YAML-style frontmatter, `name`, `title`, and `description` may be used for chooser presentation while the body is inserted as prompt text.
  [@test] ../../prompt/ui/testSrc/AgentPromptReusableSourceCollectorTest.kt

- Recent prompt entries can be promoted to local saved prompts. Saved prompt entries can be removed from local saved prompts. Save/remove actions must refresh the chooser row state without accepting the prompt. Removing a saved prompt must not affect prompt files discovered from `.github/prompts`.
  [@test] ../../prompt/ui/testSrc/AgentPromptLibraryModelTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- Provider-specific commands and skills must not be mixed into the prompt-library chooser. They are completion entries in the prompt editor.
  [@test] ../../prompt/ui/testSrc/AgentPromptReusableSourceCollectorTest.kt

- Claude command completion must reuse existing slash-completion discovery for built-in menu commands, project commands, and skills. Accepted Claude entries insert their slash invocation text.
  [@test] ../../prompt/ui/testSrc/AgentPromptClaudeSlashCompletionProviderTest.kt

- Provider-specific completion entries must flow through `AgentPromptLauncherBridge` and `AgentSessionProviderDescriptor` so prompt UI does not call provider session internals directly.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Codex skill completion entries must come from the app-server `skills/list` method through `AgentPromptLauncherBridge`, include only enabled skills, and insert `$skillName ` for accepted entries.
  [@test] ../../prompt/ui/testSrc/AgentPromptClaudeSlashCompletionProviderTest.kt
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

## User Experience
- The prompt-library chooser is a lightweight popup flow attached to the global prompt, not a persistent management view.
- An empty prompt library shows a localized empty-state message and leaves the current prompt unchanged.
- Previewing an entry should be reversible until the user accepts it.
- The save action is available for unsaved recent-prompt entries and creates a local saved prompt. Saved local entries expose a remove action; prompt-file entries are read-only in this chooser. Saving or removing a local prompt keeps the chooser flow active and updates the visible row state.
- Claude commands use `/` completion; Codex skills use `$` completion.

## Data & Backend
- `AgentPromptReusableSourceEntry` is the shared UI-facing model for reusable entries. It carries stable id, label, insert text, kind, provider, optional description, and optional source path.
- Provider-specific entries are requested for the currently selected provider and resolved working project path when command completion needs them.
- Generic prompt-file entries are provider-agnostic and may appear for any selected provider.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.*"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.CodexAppServerClientTest`

## References
- `global-prompt-entry.spec.md`
- `global-prompt-suggestions.spec.md`
