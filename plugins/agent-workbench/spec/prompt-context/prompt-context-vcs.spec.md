---
name: "Prompt Context: VCS"
description: Requirements for VCS selection context contributor, manual VCS commit picker, and VCS commits envelope/chip rendering.
targets:
  - ../../prompt-vcs/src/context/AgentPromptVcsLogSelectionContextContributor.kt
  - ../../prompt-vcs/src/context/AgentPromptVcsCommitManualContextSource.kt
  - ../../prompt-vcs/src/render/AgentPromptVcsCommitsContextRendererBridge.kt
  - ../../prompt-vcs/resources/intellij.agent.workbench.prompt.vcs.xml
  - ../../prompt-vcs/resources/messages/AgentPromptVcsBundle.properties
  - ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt
  - ../../prompt-vcs/testSrc/context/AgentPromptVcsCommitManualContextSourceTest.kt
  - ../../prompt-vcs/testSrc/render/AgentPromptVcsCommitsContextRendererBridgeTest.kt
  - ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context: VCS

Status: Draft
Date: 2026-03-11

## Summary
Define VCS-driven prompt context behavior for selected commits, including auto-collected commit extraction, manual commit picking, shared payload schema, truncation limits, and VCS renderer output.

## Goals
- Keep VCS context hash-first and non-verbose.
- Keep commit rendering stable between payload and body fallback paths.

## Non-goals
- Defining commit metadata/diff expansion behavior.
- Defining non-VCS prompt context sources.
- Defining full-history pagination for manual commit picking.

## Requirements
- Contributor registration contract:
  - phase is `INVOCATION`,
  - order is `50`.

- Manual VCS source registration contract:
  - registered through `com.intellij.agent.workbench.promptManualContextSource`,
  - `sourceId = manual.vcs.commits`,
  - display name is `Commits…`,
  - manual source output uses the same `vcsCommits` renderer and payload schema as auto VCS context.

- VCS contributor extraction priority must be:
  1. `VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION`,
  2. `VcsDataKeys.VCS_REVISION_NUMBERS`,
  3. `VcsDataKeys.VCS_REVISION_NUMBER`.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Contributor output must produce one context item with:
  - `rendererId = vcsCommits`,
  - `source = vcsLog`,
  - body lines containing commit hashes only.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Commit normalization contract:
  - trim hash values,
  - remove empty hashes,
  - deduplicate by hash,
  - keep root-path metadata when available.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Source limit contract:
  - include at most 20 commits,
  - expose `selectedCount` and `includedCount`,
  - set truncation reason to `SOURCE_LIMIT` when capped.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Payload contract for VCS item:
  - `entries[]` with required `hash` and optional `rootPath`.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Manual VCS picker sourcing contract:
  - waits for VCS log readiness before populating candidates,
  - prefers VCS roots containing the resolved working project path when at least one such root exists,
  - otherwise falls back to all VCS log roots,
  - loads at most 200 recent commit candidates from the current permanent graph.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsCommitManualContextSourceTest.kt

- Manual VCS picker presentation contract:
  - chooser rows show short hash + commit subject,
  - root label is appended only when multiple roots are present in the candidate set,
  - chooser filtering matches hash, subject, and root label text,
  - previously attached commits are preselected from existing payload hashes when reopening the picker.

- Manual VCS output contract:
  - produces one context item with `rendererId = vcsCommits`,
  - `title = Picked Commits`,
  - `itemId = manual.vcs.commits`,
  - `source = manualVcs`,
  - body lines contain included commit hashes only,
  - payload shape matches the auto VCS contributor contract.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsCommitManualContextSourceTest.kt

- Manual VCS normalization and source-limit contract:
  - trim hash values,
  - remove empty hashes,
  - deduplicate by hash,
  - preserve root-path metadata on retained entries,
  - include at most 20 commits in the context item,
  - expose `selectedCount` and `includedCount`,
  - set truncation reason to `SOURCE_LIMIT` when capped.
  [@test] ../../prompt-vcs/testSrc/context/AgentPromptVcsCommitManualContextSourceTest.kt

- VCS renderer contract:
  - envelope starts with `commits:`,
  - hashes come from payload `entries[].hash` when present,
  - otherwise fall back to non-empty lines from `body`.
  [@test] ../../prompt-vcs/testSrc/render/AgentPromptVcsCommitsContextRendererBridgeTest.kt

- VCS chip contract:
  - preview uses first available commit hash,
  - chip text is title + first commit preview.
  [@test] ../../prompt-vcs/testSrc/render/AgentPromptVcsCommitsContextRendererBridgeTest.kt
  [@test] ../../prompt/testSrc/ui/AgentPromptContextEntryPathRenderingTest.kt

## User Experience
- VCS context should quickly anchor the prompt to selected commits without noisy metadata.
- Chip preview should expose the first selected hash for immediate confirmation.
- Manual commit picking is recent-history based and optimized for quick attachment from the global prompt popup rather than full log exploration.

## Data & Backend
- Commit selection may provide root path metadata, but envelope content remains hash-only by default.
- Commit selection from non-log sources is normalized into the same payload schema.
- Manual VCS context preserves the same renderer and envelope behavior as auto VCS context, so downstream prompt serialization remains uniform.

## Error Handling
- Missing data context or missing commit selection yields empty context.
- Invalid/blank commit values are ignored during normalization.
- Unavailable or unready VCS log for manual picking degrades to popup error feedback instead of creating partial context.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsLogSelectionContextContributorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsCommitManualContextSourceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.vcs.render.AgentPromptVcsCommitsContextRendererBridgeTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest'`

## Open Questions / Risks
- Multi-root VCS selections currently preserve root paths only in payload metadata, not envelope lines.
- Manual VCS picker currently limits discovery to recent history from the loaded permanent graph and does not provide full-history pagination.

## References
- `prompt-context-contracts.spec.md`
- `../actions/global-prompt-entry.spec.md`
