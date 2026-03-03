---
name: Prompt Context Contracts
description: Canonical contracts for prompt-context contributor and renderer extension points, resolver ordering, and envelope/chip formatting behavior.
targets:
  - ../../sessions-core/src/prompt/AgentPromptContextContributorBridge.kt
  - ../../sessions-core/src/prompt/AgentPromptContextRendererBridge.kt
  - ../../sessions-core/src/prompt/AgentPromptContextEnvelopeFormatter.kt
  - ../../sessions-core/src/prompt/AgentPromptBuiltinContextRenderers.kt
  - ../../sessions-core/src/prompt/AgentPromptContextPayloads.kt
  - ../../sessions-core/src/prompt/AgentPromptModels.kt
  - ../../sessions-core/resources/intellij.agent.workbench.sessions.core.xml
  - ../../prompt/src/context/AgentPromptContextResolverService.kt
  - ../../prompt/src/ui/AgentPromptPaletteModels.kt
  - ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextSoftCapPolicyTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context Contracts

Status: Draft
Date: 2026-03-03

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
- `com.intellij.agent.workbench.promptContextContributor` and `com.intellij.agent.workbench.promptContextRenderer` extension points are canonical integration points for prompt context.

- Resolver phase ordering must be deterministic:
  - evaluate `INVOCATION` contributors first,
  - return first non-empty contributor result,
  - evaluate `FALLBACK` contributors only when invocation phase yields no items.
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- Contributor exceptions must be isolated: a failing contributor is skipped and resolution continues.
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- Resolver must attach phase metadata to returned items when contributor did not set it explicitly.
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- In invocation phase, lower contributor order wins precedence for mutually exclusive sources (for example test-runner before VCS before project-view).
  [@test] ../../prompt/testSrc/context/AgentPromptContextResolverServiceTest.kt

- Envelope formatting contract:
  - context block header is `### IDE Context`,
  - when soft cap is exceeded, envelope includes `soft-cap: limit=<n> auto-trim=<yes|no>` summary,
  - each item is rendered by `rendererId` bridge when present,
  - unknown or failing renderer falls back to generic `context: renderer=<id> title=<title>` + text code fence.

- Soft-cap policy contract:
  - default soft cap is `12_000` chars,
  - trimming starts from the last context item,
  - trim mode uses partial truncation first, then omitted stub (`[omitted due to soft cap]`) when needed,
  - items are retained with updated truncation metadata (not dropped outright).
  [@test] ../../prompt/testSrc/ui/AgentPromptContextSoftCapPolicyTest.kt

- Truncation metadata contract:
  - `AgentPromptContextTruncationReason` values are `NONE`, `SOURCE_LIMIT`, `SOFT_CAP_PARTIAL`, `SOFT_CAP_OMITTED`,
  - envelope and chip renderers must include truncation suffix only when reason is not `NONE`.

- Chip rendering contract:
  - use renderer `renderChip(...)` when available,
  - fallback chip text uses `title` + first line preview,
  - path-like previews must shorten to project-relative under project root, else user-home-relative under home root.
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

- Canonical built-in renderer ids are:
  - `snippet`, `file`, `symbol`, `paths`, `vcsRevisions`, `testFailures`.

## User Experience
- Context chips should stay concise and identifiable.
- Envelope output should prefer stable identifiers and compact descriptors over verbose dumps.

## Data & Backend
- Context item shape (`rendererId`, `title`, `body`, `payload`, `source`, `phase`, `truncation`) is the shared data contract across all contributors and renderers.
- Renderer registry is id-keyed; duplicate ids resolve to first registration.

## Error Handling
- Contributor and renderer failures are logged and degraded gracefully.
- Missing renderer id support never blocks launch; generic envelope/chip rendering is used.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.context.AgentPromptContextResolverServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextSoftCapPolicyTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest'`

## Open Questions / Risks
- Built-in renderer contracts currently rely mostly on integration-style tests rather than per-renderer unit tests.

## References
- `../actions/global-prompt-entry.spec.md`
- `prompt-context-editor.spec.md`
- `prompt-context-project-view.spec.md`
- `prompt-context-vcs.spec.md`
- `prompt-context-test-runner.spec.md`
