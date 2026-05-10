---
name: "Prompt Context: Manual Files"
description: Requirements for manual file/folder context selection and paths payload output.
targets:
  - ../../prompt/ui/src/context/AgentPromptProjectPathsManualContextSource.kt
  - ../../prompt/ui/src/context/AgentPromptProjectPathsChooserPopup.kt
  - ../../prompt/ui/resources/messages/AgentPromptBundle.properties
  - ../../prompt/ui/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt
  - ../../prompt/ui/testSrc/AgentPromptContextEntryPathRenderingTest.kt
---

# Prompt Context: Manual Files

Status: Draft
Date: 2026-05-09

## Summary
Manual file context lets users add chosen files or directories to the prompt as one paths context item.

## Requirements
- Manual file source is available only for a resolved project with local content roots.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt

- Selected paths are normalized, de-duplicated, ordered by chooser result, and rendered through the same paths renderer as Project View context.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptContextEntryPathRenderingTest.kt

- Empty or cancelled selection must not mutate existing manual context state.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptProjectPathsManualContextSourceTest.kt

- Manual file context remains runtime-only and is excluded from persisted prompt draft state.
  [@test] ../../prompt/ui/testSrc/AgentPromptUiSessionStateServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.context.AgentPromptProjectPathsManualContextSourceTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptContextEntryPathRenderingTest`

## References
- `prompt-context-contracts.spec.md`
- `prompt-context-project-view.spec.md`
- `../actions/add-to-agent-context.spec.md`
