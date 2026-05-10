---
name: Global Prompt Suggestions
description: Requirements for fallback and Codex-refined prompt suggestions in the global prompt popup.
targets:
  - ../../prompt/core/src/AgentPromptSuggestion*.kt
  - ../../prompt/ui/src/AgentPromptSuggestionController.kt
  - ../../prompt/ui/src/AgentPromptSuggestionsComponent.kt
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../prompt/ui/resources/messages/AgentPromptBundle.properties
  - ../../prompt/core/testSrc/AgentPrompt*Suggestion*Test.kt
  - ../../prompt/ui/testSrc/AgentPrompt*Suggestion*Test.kt
  - ../../codex/sessions/src/backend/appserver/CodexAppServerPromptSuggestionBackend.kt
  - ../../codex/sessions/testSrc/backend/appserver/Codex*Suggestion*Test.kt
  - ../../sessions/testSrc/CodexAppServerClientTest.kt
---

# Global Prompt Suggestions

Status: Draft
Date: 2026-05-09

## Summary
The prompt popup can show context-aware suggestions before the user types. Suggestions start from deterministic fallback seeds and may be refined by Codex app-server when available.

## Requirements
- Empty or unsupported context produces no suggestion UI and must not block prompt entry.
  [@test] ../../prompt/core/testSrc/AgentPromptDefaultSuggestionGeneratorTest.kt

- Fallback suggestions are deterministic, context-derived, and preserved when AI generation is unavailable or returns no valid candidates.
  [@test] ../../prompt/core/testSrc/AgentPromptSuggestionGeneratorTest.kt

- Suggestion refresh is asynchronous and scoped to the active popup/session; stale refreshes must not overwrite newer user state.
  [@test] ../../prompt/ui/testSrc/AgentPromptSuggestionControllerTest.kt

- Accepting a suggestion updates the prompt draft only as suggested text. Closing/reopening restores the last user-authored draft unless the user explicitly accepted/edited/submitted the suggestion.
  [@test] ../../prompt/ui/testSrc/AgentPromptDraftPersistenceDecisionsTest.kt

- Codex-refined suggestions use app-server prompt-suggestion transport and map generated/refined candidates to the expected UI suggestion kind.
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexAppServerPromptSuggestionGeneratorTest.kt
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- Invalid AI responses are filtered candidate-by-candidate; duplicate/empty candidates are ignored without hiding valid fallback suggestions.
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexAppServerPromptSuggestionGeneratorTest.kt

- Abandoned Codex suggestion turns are interrupted or the dedicated suggestion client is reset before reuse; unrelated notifications must not confirm cleanup for the active turn.
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexPromptSuggestionAppServerServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.core.tests --test "com.intellij.agent.workbench.prompt.core.AgentPrompt*Suggestion*Test"`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.AgentPrompt*Suggestion*Test"`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test "com.intellij.agent.workbench.codex.sessions.backend.appserver.Codex*Suggestion*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.CodexAppServerClientTest`

## References
- `global-prompt-entry.spec.md`
- `../prompt-context/prompt-context-contracts.spec.md`
