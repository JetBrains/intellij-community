---
name: "Prompt Context: VCS"
description: Requirements for VCS selection context contributor and VCS revisions envelope/chip rendering.
targets:
  - ../../prompt-vcs/src/context/AgentPromptVcsLogSelectionContextContributor.kt
  - ../../prompt-vcs/src/render/AgentPromptVcsRevisionsContextRendererBridge.kt
  - ../../prompt-vcs/resources/intellij.agent.workbench.prompt.vcs.xml
  - ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt
  - ../../prompt-vcs/testSrc/render/AgentPromptVcsRevisionsContextRendererBridgeTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context: VCS

Status: Draft
Date: 2026-03-03

## Summary
Define VCS-driven prompt context behavior for selected revisions, including revision extraction priority, hash payload schema, truncation limits, and VCS renderer output.

## Goals
- Keep VCS context hash-first and non-verbose.
- Keep revision rendering stable between payload and body fallback paths.

## Non-goals
- Defining commit metadata/diff expansion behavior.
- Defining non-VCS prompt context sources.

## Requirements
- Contributor registration contract:
  - phase is `INVOCATION`,
  - order is `50`.

- VCS contributor extraction priority must be:
  1. `VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION`,
  2. `VcsDataKeys.VCS_REVISION_NUMBERS`,
  3. `VcsDataKeys.VCS_REVISION_NUMBER`.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Contributor output must produce one context item with:
  - `rendererId = vcsRevisions`,
  - `source = vcsLog`,
  - body lines containing revision hashes only.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Revision normalization contract:
  - trim hash values,
  - remove empty hashes,
  - deduplicate by hash,
  - keep root-path metadata when available.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Source limit contract:
  - include at most 20 revisions,
  - expose `selectedCount` and `includedCount`,
  - set truncation reason to `SOURCE_LIMIT` when capped.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Payload contract for VCS item:
  - `entries[]` with required `hash` and optional `rootPath`.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- VCS renderer contract:
  - envelope starts with `vcs revisions:`,
  - hashes come from payload `entries[].hash` when present,
  - otherwise fall back to non-empty lines from `body`.
  [@test] ../../prompt-vcs/testSrc/render/AgentPromptVcsRevisionsContextRendererBridgeTest.kt

- VCS chip contract:
  - preview uses first available revision hash,
  - chip text is title + first revision preview.
  [@test] ../../prompt-vcs/testSrc/render/AgentPromptVcsRevisionsContextRendererBridgeTest.kt
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

## User Experience
- VCS context should quickly anchor the prompt to selected revisions without noisy metadata.
- Chip preview should expose the first selected hash for immediate confirmation.

## Data & Backend
- Commit selection may provide root path metadata, but envelope content remains hash-only by default.
- Revision selection from non-log sources is normalized into the same payload schema.

## Error Handling
- Missing data context or missing revision selection yields empty context.
- Invalid/blank revision values are ignored during normalization.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsLogSelectionContextContributorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.vcs.render.AgentPromptVcsRevisionsContextRendererBridgeTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest'`

## Open Questions / Risks
- Multi-root VCS selections currently preserve root paths only in payload metadata, not envelope lines.

## References
- `prompt-context-contracts.spec.md`
- `../actions/global-prompt-entry.spec.md`
