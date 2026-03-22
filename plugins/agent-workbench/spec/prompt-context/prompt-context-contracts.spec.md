---
name: Prompt Context Contracts
description: Canonical contracts for prompt-context contributor, manual context source, and renderer extension points, resolver ordering, and envelope/chip formatting behavior.
targets:
  - ../../prompt/core/src/AgentPromptContextContributorBridge.kt
  - ../../prompt/core/src/AgentPromptContextRendererBridge.kt
  - ../../prompt/core/src/AgentPromptContextEnvelopeFormatter.kt
  - ../../prompt/core/src/AgentPromptBuiltinContextRenderers.kt
  - ../../prompt/core/src/AgentPromptContextPayloads.kt
  - ../../prompt/core/src/AgentPromptManualContextSourceBridge.kt
  - ../../prompt/core/src/AgentPromptModels.kt
  - ../../sessions-core/resources/intellij.agent.workbench.sessions.core.xml
  - ../../prompt/src/context/AgentPromptContextResolverService.kt
  - ../../prompt/src/ui/AgentPromptContextNormalizationDecisions.kt
  - ../../prompt/src/ui/AgentPromptPaletteModels.kt
  - ../../prompt/src/ui/AgentPromptUiSessionStateService.kt
  - ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextNormalizationDecisionsTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextSoftCapPolicyTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt
  - ../../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt
---

# Prompt Context Contracts

Status: Draft
Date: 2026-03-11

## Summary
Define the shared prompt-context contract used by global prompt entry: extension-point lifecycle, contributor resolution order, renderer lookup/fallback, envelope serialization, truncation metadata, and chip rendering behavior.

Provider/source-specific context rules are defined in separate specs under `spec/prompt-context/`.

## Goals
- Keep cross-source context contracts defined once.
- Prevent behavioral drift between context providers and renderer implementations.
- Keep default context resolution deterministic and failure-tolerant.

## Non-goals
- Defining source-specific payload schemas for editor/project-view/VCS/test-runner.
- Defining global prompt popup validation and mode switching UX.

## Requirements
- `com.intellij.agent.workbench.promptContextContributor` and `com.intellij.agent.workbench.promptContextRenderer` extension points are canonical integration points for automatically resolved prompt context.

- `com.intellij.agent.workbench.promptManualContextSource` is the canonical integration point for popup-invoked manual context pickers.
  - Manual sources expose stable `sourceId`, display name, availability gating, and a picker callback that returns a replacement context item for that source.
  - Manual sources are additive to auto context and do not change contributor resolution precedence.

- Resolver phase ordering must be deterministic:
  - evaluate `INVOCATION` contributors first,
  - return first non-empty contributor result,
  - evaluate `FALLBACK` contributors only when invocation phase yields no items.
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- Contributor exceptions must be isolated: a failing contributor is skipped and resolution continues.
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- Resolver must attach phase metadata to returned items when contributor did not set it explicitly.
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- `AgentPromptContextResolverService.collectDefaultContext(...)` returns auto context only; manual context items are merged later by popup UI state.

- In invocation phase, lower contributor order wins precedence for mutually exclusive sources (for example test-runner before VCS before project-view).
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- Envelope formatting contract:
  - Context block header is `### IDE Context`.
  - When soft cap is exceeded, envelope includes `soft-cap: limit=<n> auto-trim=<yes|no>` summary.
  - Each item is rendered by `rendererId` bridge when present.
  - Unknown or failing renderer falls back to generic `context: renderer=<id> title=<title>` + text code fence.

- Soft-cap policy contract:
  - Default soft cap is `12_000` chars.
  - Trimming starts from the last context item.
  - Trim mode uses partial truncation first, then omitted stub (`[omitted due to soft cap]`) when needed.
  - Items are retained with updated truncation metadata (not dropped outright).
  [@test] ../../prompt/testSrc/ui/AgentPromptContextSoftCapPolicyTest.kt

- Truncation metadata contract:
  - `AgentPromptContextTruncationReason` values are `NONE`, `SOURCE_LIMIT`, `SOFT_CAP_PARTIAL`, `SOFT_CAP_OMITTED`,
  - envelope and chip renderers must include truncation suffix only when reason is not `NONE`.

- Chip rendering contract:
  - use renderer `renderChip(...)` when available,
  - fallback chip text uses `title` + first line preview,
  - path-like previews must shorten to project-relative under project root, else user-home-relative under home root,
  - long path-like previews use filename-biased middle truncation after that normalization.
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

- Popup context normalization contract:
  - before chips are rendered or context is submitted, popup UI must remove path-like context items that resolve to the effective working project root and would otherwise render as `.`,
  - for `paths` items, remove only the redundant root entry and keep remaining entries when present,
  - for `file` items, remove the whole item when it resolves to the effective working project root.
  [@test] ../../prompt/testSrc/ui/AgentPromptContextNormalizationDecisionsTest.kt

- Hierarchical context item contract:
  - `itemId` and `parentItemId` are optional relation fields on `AgentPromptContextItem`,
  - `parentItemId` references another item's `itemId` within the same resolved context set,
  - removal consumers may remove descendants recursively when a parent item is removed,
  - missing or unknown parent links are treated as non-fatal and do not block context usage.

- Manual context persistence contract:
  - popup runtime state may store manual context items keyed by source id for same-session restore,
  - persisted `AgentPromptUiDraft` state must not serialize manual context items,
  - manual context restore is independent from auto-context fingerprint matching used for removed auto context.
  [@test] ../../prompt/testSrc/ui/AgentPromptUiSessionStateServiceTest.kt

- Canonical built-in renderer ids are:
  - `snippet`, `file`, `symbol`, `paths`, `vcsCommits`, `testFailures`.

## User Experience
- Context chips should stay concise and identifiable.
- Envelope output should prefer stable identifiers and compact descriptors over verbose dumps.

## Data & Backend
- Context item shape (`rendererId`, `title`, `body`, `payload`, `itemId`, `parentItemId`, `source`, `phase`, `truncation`) is the shared data contract across all contributors and renderers.
- Renderer registry is id-keyed; duplicate ids resolve to first registration.
- Manual source items reuse the same `AgentPromptContextItem` shape and renderer registry as auto-contributed items.

## Error Handling
- Contributor and renderer failures are logged and degraded gracefully.
- Missing renderer id support never blocks launch; generic envelope/chip rendering is used.
- Manual source picker failures degrade to inline popup error feedback and do not mutate existing context state.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextSoftCapPolicyTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextNormalizationDecisionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateServiceTest'`

## Open Questions / Risks
- Built-in renderer contracts currently rely mostly on integration-style tests rather than per-renderer unit tests.

## References
- `../actions/global-prompt-entry.spec.md`
- `prompt-context-editor.spec.md`
- `prompt-context-files.spec.md`
- `prompt-context-project-view.spec.md`
- `prompt-context-vcs.spec.md`
- `prompt-context-test-runner.spec.md`
