---
name: "Prompt Context: Test Runner"
description: Requirements for test-selection prompt context contributor and test-failures renderer/chip behavior.
targets:
  - ../../prompt-testrunner/src/context/AgentPromptTestSelectionContextContributor.kt
  - ../../prompt-testrunner/src/render/AgentPromptTestFailuresContextRendererBridge.kt
  - ../../prompt-testrunner/resources/intellij.agent.workbench.prompt.testrunner.xml
  - ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt
  - ../../prompt-testrunner/testSrc/render/AgentPromptTestFailuresContextRendererBridgeTest.kt
---

# Prompt Context: Test Runner

Status: Draft
Date: 2026-03-03

## Summary
Define test-runner prompt context behavior for selected tests, including status/assertion normalization, payload schema, truncation limits, and envelope/chip rendering.

## Goals
- Keep test context focused on actionable failure anchors.
- Keep assertion hints concise and bounded.
- Include the failure/output text from the active console when invocation comes from the test failure pane.
- Keep rendering resilient across new payload and legacy body formats.

## Non-goals
- Including unbounded test output by default.
- Defining non-test prompt context behavior.

## Requirements
- Contributor registration contract:
  - phase is `INVOCATION`,
  - registration is ordered before the editor contributor via extension ordering.

- Test contributor selection source priority:
  - `AbstractTestProxy.DATA_KEYS` array,
  - fallback to `AbstractTestProxy.DATA_KEY` single item.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- Applicability contract:
  - contributor returns empty when selected tests are present but the focused invocation editor is a non-console editor,
  - contributor remains eligible when no editor is present,
  - contributor remains eligible when the active console editor is a console editor.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- Contributor output must produce one context item with:
  - `rendererId = testFailures`,
  - `source = testRunner`,
  - line format `<status>: <reference>[ | assertion: <hint>]`.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- Console output contract:
  - when the active console editor is a console editor, contributor captures failure text from that editor,
  - selected console text wins over full console document text,
  - outer blank lines are trimmed while internal formatting is preserved,
  - console output is stored as optional payload fields `consoleOutput` and `consoleOutputFromSelection`,
  - console output is source-truncated to a fixed char cap.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- Status and assertion contract:
  - status normalization supports `failed`, `passed`, `ignored`, `inProgress`, `unknown`,
  - assertion hint prefers error message, then first non-empty stacktrace line,
  - assertion hint is whitespace-normalized and capped to 180 chars.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- Source limit contract:
  - include at most 5 tests,
  - cap console output to the contributor source limit,
  - set truncation reason `SOURCE_LIMIT` when capped.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- Payload contract for test item:
  - `entries[]` with `name`, `status`, `reference`, optional `locationUrl`, optional `assertionMessage`,
  - optional `consoleOutput`, optional `consoleOutputFromSelection`,
  - `selectedCount`, `candidateCount`, `includedCount`,
  - `statusCounts` map.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- If no failing tests are selected, contributor must still include selected tests with normalized statuses.
  [@test] ../../prompt-testrunner/testSrc/context/AgentPromptTestSelectionContextContributorTest.kt

- Test renderer contract:
  - envelope label is derived from status counts,
  - when payload entries exist, renderer uses payload,
  - when `consoleOutput` is present, renderer appends a `failure console output:` text code block after the test entries,
  - when payload missing, renderer parses legacy body lines,
  - legacy `java:test://Suite.testA` anchor must render as `Suite#testA`.
  [@test] ../../prompt-testrunner/testSrc/render/AgentPromptTestFailuresContextRendererBridgeTest.kt

- Test chip contract:
  - chip preview prefers first failed entry, else first available entry,
  - console output does not change chip preview selection,
  - chip text combines group label + preview.
  [@test] ../../prompt-testrunner/testSrc/render/AgentPromptTestFailuresContextRendererBridgeTest.kt

## User Experience
- Selected tests should appear as concise references with optional short assertion hints.
- Failure-pane invocations should also include the console output excerpt as a code block.
- Group label should summarize status mix (for example failed/passed counts).

## Data & Backend
- Contributor normalizes and de-duplicates selected tests before rendering payload/body.
- Renderer can operate from payload-first contract while remaining backward-compatible with body-only format.

## Error Handling
- Missing data context or no selected tests yields empty context.
- Invalid payload entries are ignored during renderer extraction.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.testrunner.context.AgentPromptTestSelectionContextContributorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.testrunner.render.AgentPromptTestFailuresContextRendererBridgeTest'`

## Open Questions / Risks
- Replacement preference in duplicate-test normalization is implicit in code and not yet separately tested as a standalone contract.

## References
- `prompt-context-contracts.spec.md`
- `../actions/global-prompt-entry.spec.md`
