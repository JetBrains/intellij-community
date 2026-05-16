---
name: Global Prompt Reusable Sources
description: Requirements for prompt history and reusable prompt insertion in the global prompt popup.
targets:
  - ../../prompt/core/src/AgentPromptReusableSourceEntry.kt
  - ../../prompt/core/src/AgentPromptLauncherBridge.kt
  - ../../prompt/ui/src/AgentPromptPaletteSessionController.kt
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
The global prompt popup lets users reuse recent submitted prompts and project/provider prompt sources without leaving the prompt field. This spec owns history persistence, reusable-source discovery, chooser preview behavior, and provider handoff for reusable prompt entries.

## Requirements
- Successful standard prompt submissions must be persisted to prompt history. Failed submissions, blank prompts, and extension-tab submits must not create history entries.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteSubmitControllerTest.kt

- Prompt history must normalize line endings to `\n`, trim outer whitespace, de-duplicate exact prompt text, move repeated prompts to the front, sort newest first, and retain at most 50 entries.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

- Prompt history must be separate from prompt drafts. Clearing or restoring a draft must not clear history, and previewing a history entry must be cancellable without mutating the saved draft.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteDraftControllerTest.kt

- The popup header must expose prompt history and reusable prompt actions. Each chooser previews the selected entry in the shared prompt editor, accepts by replacing the prompt text, and restores the previous prompt snapshot when dismissed without accepting.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt

- Reusable prompt sources must include non-blank `.github/prompts/*.prompt.md` files discovered from the working project path and its ancestors. If a prompt file contains YAML-style frontmatter, `title` and `description` may be used for chooser presentation while the body is inserted as prompt text.
  [@test] ../../prompt/ui/testSrc/AgentPromptReusableSourceCollectorTest.kt

- Claude reusable sources must reuse existing Claude slash-completion discovery for project commands and skills, excluding built-in menu entries. Accepted Claude entries insert their slash invocation text.
  [@test] ../../prompt/ui/testSrc/AgentPromptClaudeSlashCompletionProviderTest.kt

- Provider-specific reusable sources must flow through `AgentPromptLauncherBridge` and `AgentSessionProviderDescriptor` so prompt UI does not call provider session internals directly.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Codex reusable skill entries must come from the app-server `skills/list` method, include only enabled skills, and insert `$skillName ` for accepted entries.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

## User Experience
- History and reusable-source choosers are lightweight popup flows attached to the global prompt, not persistent management views.
- Empty history or source lists show a localized empty-state message and leave the current prompt unchanged.
- Previewing an entry should be reversible until the user accepts it.

## Data & Backend
- `AgentPromptReusableSourceEntry` is the shared UI-facing model for reusable entries. It carries stable id, label, insert text, kind, provider, optional description, and optional source path.
- Provider-specific entries are requested for the currently selected provider and resolved working project path.
- Generic prompt-file entries are provider-agnostic and may appear for any selected provider.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.*"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.CodexAppServerClientTest`

## References
- `global-prompt-entry.spec.md`
- `global-prompt-suggestions.spec.md`
