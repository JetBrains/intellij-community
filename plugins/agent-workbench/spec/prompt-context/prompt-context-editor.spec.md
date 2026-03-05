---
name: "Prompt Context: Editor"
description: Requirements for editor invocation and fallback context contributors, including file/symbol/snippet item composition and rendering.
targets:
  - ../../prompt/src/context/AgentPromptEditorContextContributor.kt
  - ../../prompt/src/context/AgentPromptSelectedEditorFallbackContextContributor.kt
  - ../../prompt/src/context/AgentPromptEditorContextSupport.kt
  - ../../sessions-core/src/prompt/AgentPromptBuiltinContextRenderers.kt
  - ../../prompt/resources/intellij.agent.workbench.prompt.xml
  - ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt
---

# Prompt Context: Editor

Status: Draft
Date: 2026-03-03

## Summary
Define editor-driven prompt context behavior for both direct invocation and fallback selection, including canonical item ordering and snippet metadata.

## Goals
- Keep editor context deterministic and concise.
- Keep snippet/file/symbol composition stable for launch and rendering.
- Keep fallback behavior explicit when invocation context is unavailable.

## Non-goals
- Defining non-editor source behavior (project-view, VCS, test-runner).
- Defining global prompt popup validation and routing.

## Requirements
- Contributor registration contract:
  - `AgentPromptEditorContextContributor` runs in `INVOCATION` phase with order `0`,
  - `AgentPromptSelectedEditorFallbackContextContributor` runs in `FALLBACK` phase with order `0`.

- Editor invocation contributor must return no items when invocation has no editor in data context.
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Editor context item composition must follow canonical order:
  - `file` item first when file path exists,
  - `symbol` item second when concrete symbol name exists,
  - `snippet` item last.
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Editor item relationship contract:
  - `file` item uses `itemId=editor.file`,
  - `symbol` item uses `itemId=editor.symbol` and `parentItemId=editor.file` when file item is present,
  - `snippet` item uses `itemId=editor.snippet` and `parentItemId=editor.file` when file item is present.
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Symbol filtering contract:
  - placeholder-like symbol names wrapped in angle brackets (for example `<anonymous>`, `<lambda>`) are not emitted as `symbol` context items,
  - symbol resolution continues PSI parent traversal until first concrete symbol name or root.
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Snippet payload contract must include:
  - `startLine`,
  - `endLine`,
  - `selection`,
  - optional `language` (when PSI language id is available).
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Snippet language metadata is stored only on the snippet payload; file payload must not carry language metadata.
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Snippet source must be `editor`; non-truncated snippet must keep truncation reason `NONE` with `originalChars == includedChars == body.length`.
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Envelope composition must preserve item order (`file`, then `symbol`, then `snippet`) when those items are present.
  [@test] ../../prompt/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Renderer format contract for editor item types:
  - `file` renders as `file: <absolute-path>`,
  - `symbol` renders as `symbol: <symbol-name>`,
  - `snippet` renders as descriptor line + fenced code block with optional language label.

- Chip rendering contract for editor item types:
  - `file` chip shows shortened path (project-relative when under project root),
  - `symbol` chip shows symbol name,
  - `snippet` chip shows title only (line range), no code preview.
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

- Fallback contributor must collect from currently selected editor in project and participate only in `FALLBACK` phase.

## User Experience
- From editor invocation, users get minimal but anchor-rich context (file/symbol/snippet) without manual selection.
- If invocation has no explicit editor context, selected editor can still provide fallback context.

## Data & Backend
- Snippet text source:
  - selected range when editor has selection,
  - otherwise caret-centered line window.
- Snippet body may be source-limited and marked by truncation metadata.

## Error Handling
- Missing editor, PSI file, symbol, or file path degrades to available subset of context items.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.context.AgentPromptEditorContextContributorTest'`

## Open Questions / Risks
- No dedicated test currently asserts selected-editor fallback item content end-to-end.

## References
- `prompt-context-contracts.spec.md`
- `../actions/global-prompt-entry.spec.md`
