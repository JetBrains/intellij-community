---
name: Global Prompt Suggestions
description: Requirements for context-based suggested prompts in the global prompt popup, including seed heuristics, async refresh, and hybrid Codex AI refinement.
targets:
  - ../../prompt/src/ui/AgentPromptDraftPersistenceDecisions.kt
  - ../../prompt/src/ui/AgentPromptPalettePopup.kt
  - ../../prompt/src/ui/AgentPromptPaletteView.kt
  - ../../prompt/src/ui/AgentPromptSuggestionController.kt
  - ../../prompt/src/ui/AgentPromptSuggestionsComponent.kt
  - ../../prompt/resources/messages/AgentPromptBundle.properties
  - ../../prompt/testSrc/suggestions/AgentPromptDefaultSuggestionGeneratorTest.kt
  - ../../prompt/testSrc/ui/AgentPromptDraftPersistenceDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptSuggestionControllerTest.kt
  - ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt
  - ../../prompt/testSrc/ui/AgentPromptSuggestionsComponentTest.kt
  - ../../sessions-core/src/prompt/AgentPromptSuggestionGenerator.kt
  - ../../sessions-core/src/prompt/AgentPromptSuggestionSeeds.kt
  - ../../sessions-core/src/prompt/AgentPromptSuggestionBundle.kt
  - ../../sessions-core/resources/intellij.agent.workbench.sessions.core.xml
  - ../../sessions-core/resources/messages/AgentPromptSuggestionsBundle.properties
  - ../../codex/common/src/CodexPromptSuggestions.kt
  - ../../codex/common/src/CodexAppServerClient.kt
  - ../../codex/common/src/CodexAppServerProtocol.kt
  - ../../codex/sessions/src/backend/appserver/CodexAppServerPromptSuggestionBackend.kt
  - ../../codex/sessions/src/backend/appserver/CodexPromptSuggestionModelSettings.kt
  - ../../codex/sessions/resources/intellij.agent.workbench.codex.sessions.xml
  - ../../codex/sessions/resources/messages/CodexSessionsBundle.properties
  - ../../codex/sessions/testSrc/backend/appserver/CodexAppServerPromptSuggestionGeneratorTest.kt
  - ../../codex/sessions/testSrc/backend/appserver/CodexPromptSuggestionModelSettingsTest.kt
  - ../../sessions/testSrc/CodexAppServerClientTest.kt
  - ../../prompt/testSrc/suggestions/AgentPromptSuggestionGeneratorTest.kt
---

# Global Prompt Suggestions

Status: Draft
Date: 2026-03-15

## Summary
Define context-based suggested prompts shown inside the global prompt popup. This spec owns fallback seed suggestion selection, prompt-panel rendering, async update semantics, the split between core fallback generation and optional Codex AI refinement, and prompt-suggestion model setting behavior.

Global prompt entry, target routing, submit validation, and manual context selection remain owned by `spec/actions/global-prompt-entry.spec.md`. Prompt-context contributor and renderer contracts remain owned by `spec/prompt-context/prompt-context-contracts.spec.md`.

## Goals
- Keep fallback prompt suggestions deterministic for the same visible context.
- Keep async refresh safe against stale or failed generator updates.
- Keep AI prompt refinement cheap, optional, and grounded in the visible context.
- Keep Codex transport and advanced-setting behavior explicit.

## Non-goals
- Defining prompt-context collection, rendering, or truncation rules.
- Defining provider selection, submit validation, or existing-task routing.
- Letting the local fallback generator invent context beyond the visible prompt context.

## Requirements
- Suggested prompts must render inside the prompt panel above the prompt editor, and never in the popup bottom panel.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewStructureTest.kt

- When no candidates are available, the suggestions block must remain hidden. When candidates are present, it must render as a captionless quick-action strip above the editor, show at most three visible suggestion pills in generator order, and route selection through the component callback.
  [@test] ../../prompt/testSrc/ui/AgentPromptSuggestionsComponentTest.kt

- Once suggestions are visible for the current popup state, entering prompt text must not hide the suggestion row or change prompt editor height.
  [@test] ../../prompt/testSrc/ui/AgentPromptPaletteViewLayoutTest.kt

- Suggestion refresh lifecycle:
  - clear current suggestions before loading a new request,
  - publish later updates from the same request,
  - ignore stale updates from older requests after context changes,
  - treat empty context as empty suggestions.
  [@test] ../../prompt/testSrc/ui/AgentPromptSuggestionControllerTest.kt

- Template seed selection must use the first matching visible context category in this precedence order: `testFailures`, `vcsCommits`, editor context (`snippet` / `file` / `symbol`), `paths`, else no suggestions.
  [@test] ../../prompt/testSrc/suggestions/AgentPromptDefaultSuggestionGeneratorTest.kt

- Test-runner seed contract:
  - any positive failed test count produces `tests.fix`, `tests.explain`, `tests.stabilize`,
  - otherwise selected tests produce `tests.coverage`, `tests.review`, `tests.extend`,
  - test context outranks VCS context when both are present.
  [@test] ../../prompt/testSrc/suggestions/AgentPromptDefaultSuggestionGeneratorTest.kt

- VCS, editor, and paths seed contracts:
  - VCS context produces `vcs.review`, `vcs.summary`, `vcs.trace`,
  - editor context produces `editor.explain`, `editor.refactor`, `editor.review`,
  - paths context produces `paths.plan`, `paths.summary`, `paths.impact`.
  [@test] ../../prompt/testSrc/suggestions/AgentPromptDefaultSuggestionGeneratorTest.kt

- Prompt suggestion generation architecture:
  - `AgentPromptSuggestionGenerators.find()` must always resolve to a built-in generator that computes local template suggestions from visible context,
  - the built-in generator must emit template suggestions immediately,
  - optional AI refinement is provided through a separate `promptSuggestionAiBackend` EP,
  - the built-in generator must publish at most one later AI update for the same request.
  [@test] ../../prompt/testSrc/suggestions/AgentPromptSuggestionGeneratorTest.kt

- Codex prompt suggestion request contract:
  - prompt suggestions must use a dedicated Codex app-server client/service path isolated from shared session notification handling,
  - the shared session Codex client must route public notifications only, and the dedicated prompt-suggestion Codex client must route parsed prompt-turn notifications only,
  - prompt suggestions start an ephemeral `thread/start` request with trimmed normalized `cwd`, `approvalPolicy = never`, and `sandbox = read-only`,
  - prompt suggestions run a single `turn/start` on that thread,
  - `targetModeId` maps `NEW_TASK` to `new_task` and `EXISTING_TASK` to `existing_task` inside the turn input text,
  - the turn input text includes visible context payloads, `source`, `itemId`, `parentItemId`, truncation metadata, and fallback seed candidates even when the seed list is empty,
  - `turn/start` includes `model = gpt-5.4` by default, `effort = low`, and a strict `outputSchema`,
  - streamed prompt-turn lifecycle notifications such as `turn/started`, `item/started`, and other non-terminal events may arrive before completion and must be ignored unless they match the active suggestion `threadId` and `turnId`,
  - the structured suggestion result is read from the final matching `item/completed` assistant message text and finalized by the matching `turn/completed`,
  - timeout, coroutine cancellation, or early failure before terminal `turn/completed` must trigger best-effort `turn/interrupt` for that suggestion turn,
  - if cleanup cannot confirm terminal completion for the interrupted suggestion turn within `500 ms`, the dedicated prompt-suggestion client must be shut down and discarded before reuse,
  - model comes from `agent.workbench.codex.prompt.suggestion.model`.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexAppServerPromptSuggestionGeneratorTest.kt

- Codex AI refinement contract:
  - Codex may return either `polishedSeeds` or `generatedCandidates`,
  - `polishedSeeds` must match the fallback slot ids exactly in count and order to be accepted,
  - accepted polished seeds must preserve existing ids and use provenance `AI_POLISHED`,
  - `generatedCandidates` may replace fallback suggestions when at least one non-blank unique candidate survives validation,
  - the client must accept at most three generated candidates, synthesize local ids, and use provenance `AI_GENERATED`,
  - notifications for other threads or turns must never affect the active popup suggestion update,
  - timeout, interrupt, cleanup failure, CLI unavailability, protocol mismatch, app-server failure, and fully invalid AI responses must leave the local template suggestions visible.
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexAppServerPromptSuggestionGeneratorTest.kt

- Advanced setting `agent.workbench.codex.prompt.suggestion.model` contract:
  - blank value falls back to `gpt-5.4`,
  - `off` disables polishing case-insensitively,
  - any other trimmed value is passed through as the model name.
  [@test] ../../codex/sessions/testSrc/backend/appserver/CodexPromptSuggestionModelSettingsTest.kt

## User Experience
- Suggested prompts are optional, context-derived starting points rather than required submit input.
- Suggested prompts render as a single-row quick-action strip above the prompt editor with no section title, no wrapping, truncated labels when needed, and a visual treatment that stays distinct from removable context chips.
- The prompt popup applies a chosen suggestion by replacing the composer text with the candidate prompt text.
- Applying a chosen suggestion must only change the live popup composer state until the user edits or submits; closing and reopening the popup must not treat an untouched inserted suggestion as stored user text.
  [@test] ../../prompt/testSrc/ui/AgentPromptDraftPersistenceDecisionsTest.kt
- Visible suggestions remain available while the user continues composing so the prompt area does not jump vertically after the first keystroke.
- AI may either polish the fallback wording in place or replace the row with up to three context-generated suggestions, but it must remain grounded in the provided context.

## Data & Backend
- Suggestion requests use the popup's visible context list after auto/manual context merging and popup visibility filtering.
- `AgentPromptSuggestionRequest` and `AgentPromptSuggestionUpdate` are the shared prompt-suggestion transport within Agent Workbench.
- Fallback seed generation lives in `sessions-core` and is independent of the selected session provider.
- Codex app-server prompt suggestion payloads carry prompt context payload objects, prompt-context metadata, and fallback seed candidates without prompt-context-specific reinterpretation in the transport layer.
- Prompt-suggestion transport must remain isolated from shared session-list notification processing so streamed AI refinement traffic cannot pollute long-lived session clients, and prompt-suggestion traffic must not enqueue public session notifications.

## Error Handling
- Empty context degrades to no suggestion UI.
- Generator exceptions, Codex CLI absence, app-server errors, and timeouts must be logged and degraded to fallback suggestions or empty UI without blocking prompt entry.
- Invalid AI responses must be filtered candidate-by-candidate; if no valid candidates remain, fallback suggestions stay visible.
- Abandoned prompt-suggestion turns must be interrupted or the dedicated prompt-suggestion client must be reset before reuse; a reused client must not keep running stale suggestion turns in the background, and notifications from other threads or turns must never be treated as cleanup confirmation for the active abandoned turn.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.suggestions.AgentPromptSuggestionSeedsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.suggestions.AgentPromptSuggestionGeneratorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptSuggestionControllerTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewStructureTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptSuggestionsComponentTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerPromptSuggestionBackendTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexPromptSuggestionModelSettingsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.CodexAppServerClientTest'`

## References
- `global-prompt-entry.spec.md`
- `../prompt-context/prompt-context-contracts.spec.md`
