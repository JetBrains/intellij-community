---
name: "Prompt Context: Project View"
description: Requirements for project-view selection context and paths rendering.
targets:
  - ../../prompt/context/src/AgentPromptProjectViewSelectionContextContributor.kt
  - ../../prompt/core/src/AgentPromptBuiltinContextRenderers.kt
  - ../../prompt/context/resources/intellij.agent.workbench.prompt.context.xml
  - ../../prompt/ui/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt
  - ../../prompt/ui/testSrc/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context: Project View

Status: Draft
Date: 2026-05-09

## Summary
Project View context converts selected local files and directories into path context items rendered by the built-in paths renderer.

## Requirements
- Empty selection or non-local entries produce no context.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt

- Selected files/directories are normalized into path payload entries with stable display labels and source truncation metadata when needed.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptProjectViewSelectionContextContributorTest.kt

- The paths renderer preserves unresolved path markers, directory/file counts, and chip display behavior used by manual file context.
  [@test] ../../prompt/ui/testSrc/AgentPromptContextEntryPathRenderingTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.context.AgentPromptProjectViewSelectionContextContributorTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest`

## References
- `prompt-context-contracts.spec.md`
- `../actions/add-to-agent-context.spec.md`
