---
name: "Prompt Context: Project View"
description: Requirements for project-view selection context contributor and `paths` rendering/chip behavior.
targets:
  - ../../prompt/src/context/AgentPromptProjectViewSelectionContextContributor.kt
  - ../../prompt/core/src/AgentPromptBuiltinContextRenderers.kt
  - ../../prompt/resources/intellij.agent.workbench.prompt.xml
  - ../../prompt/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context: Project View

Status: Draft
Date: 2026-03-03

## Summary
Define project-view prompt context collection from selected files/directories, including payload schema, source truncation behavior, and `paths` renderer/chip formatting.

## Goals
- Keep project-view context pointer-first and compact.
- Keep path context compact in envelope output; preserve file/directory semantics in contributor body.

## Non-goals
- Defining editor, VCS, or test-runner context behavior.
- Defining global prompt popup routing and submit flow.

## Requirements
- Contributor registration contract:
  - phase is `INVOCATION`,
  - registration is ordered after the VCS contributor and before the tree-selection contributor via extension ordering.

- Project-view contributor must collect selection from:
  - `CommonDataKeys.VIRTUAL_FILE_ARRAY` when present,
  - otherwise `CommonDataKeys.VIRTUAL_FILE`.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt

- Contributor output must produce exactly one context item with:
  - `rendererId = paths`,
  - `source = projectView`,
  - body lines prefixed as `file: <path>` or `dir: <path>`.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt

- Source selection contract:
  - deduplicate by path while preserving encounter order,
  - include at most five paths,
  - mark truncation reason as `SOURCE_LIMIT` when selection is capped.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt

- Payload contract for project-view item:
  - `entries[]` with `{ kind, path }`,
  - `selectedCount`,
  - `includedCount`,
  - `directoryCount`,
  - `fileCount`.
  [@test] ../../prompt/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt

- `paths` envelope rendering:
  - singular entry renders as `path: <absolute-path>`,
  - multiple entries render as `paths:\n<absolute-path-1>\n<absolute-path-2>` (no per-line prefix).
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

- `paths` chip rendering shortens path previews (project-relative when under project root); `file:`/`dir:` prefix is stripped by the renderer.
- Long `paths` chip previews are filename-biased middle-truncated after path normalization.
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

## User Experience
- Users should see selected project-view paths as concise file/directory anchors.
- Chips should remain readable for deeply nested absolute paths.

## Data & Backend
- Envelope rendering resolves relative paths against current project path when possible.
- Non-resolvable paths are preserved with unresolved marker semantics from built-in renderer contract.

## Error Handling
- Missing or empty project-view selection returns no context item.
- Blank/invalid paths are filtered from payload entries.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.context.AgentPromptProjectViewSelectionContextContributorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest'`

## Open Questions / Risks
- No dedicated test currently validates directory/file mix counting beyond basic payload assertions.

## References
- `prompt-context-contracts.spec.md`
- `../actions/global-prompt-entry.spec.md`
