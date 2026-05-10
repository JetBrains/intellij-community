---
name: "Prompt Context: VCS"
description: Requirements for VCS commit context collection, manual commit selection, and commit rendering.
targets:
  - ../../prompt/vcs/src/context/*.kt
  - ../../prompt/vcs/src/render/*.kt
  - ../../prompt/vcs/resources/**/*.xml
  - ../../prompt/vcs/resources/messages/*.properties
  - ../../prompt/vcs/testSrc/context/*.kt
  - ../../prompt/vcs/testSrc/render/*.kt
  - ../../prompt/ui/testSrc/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context: VCS

Status: Draft
Date: 2026-05-09

## Summary
VCS prompt context captures selected commits or manually chosen commits and renders concise commit metadata for prompt envelopes and chips.

## Requirements
- Empty or missing VCS selection produces no context; invalid/blank commit values are ignored during normalization.
  [@test] ../../prompt/vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- Manual commit source opens from loaded VCS log data and degrades to popup error feedback when the log is unavailable.
  [@test] ../../prompt/vcs/testSrc/context/AgentPromptVcsCommitManualContextSourceTest.kt

- Commit payloads preserve hash, subject, author/date metadata when available, root path metadata, and truncation state.
  [@test] ../../prompt/vcs/testSrc/context/AgentPromptVcsLogSelectionContextContributorTest.kt

- VCS commit renderer formats commit context without requiring full diff content and keeps path/chip rendering consistent with built-in context contracts.
  [@test] ../../prompt/vcs/testSrc/render/AgentPromptVcsCommitsContextRendererBridgeTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptContextEntryPathRenderingTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.vcs.tests --test "com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcs*Test"`
- `./tests.cmd --module intellij.agent.workbench.prompt.vcs.tests --test com.intellij.agent.workbench.prompt.vcs.render.AgentPromptVcsCommitsContextRendererBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest`

## References
- `prompt-context-contracts.spec.md`
- `../actions/add-to-agent-context.spec.md`
