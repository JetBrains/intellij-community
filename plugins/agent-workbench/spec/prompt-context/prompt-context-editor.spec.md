---
name: "Prompt Context: Editor"
description: Requirements for editor invocation and selected-editor fallback context.
targets:
  - ../../prompt/context/src/AgentPromptEditorContextContributor.kt
  - ../../prompt/context/src/AgentPromptSelectedEditorFallbackContextContributor.kt
  - ../../prompt/context/src/AgentPromptEditorContextSupport.kt
  - ../../prompt/core/src/AgentPromptBuiltinContextRenderers.kt
  - ../../prompt/context/resources/intellij.agent.workbench.prompt.context.xml
  - ../../prompt/ui/testSrc/context/AgentPromptEditorContextContributorTest.kt
---

# Prompt Context: Editor

Status: Draft
Date: 2026-05-09

## Summary
Editor context contributes the current file, symbol, and snippet around the invocation point, with a selected-editor fallback for non-editor invocation places.

## Requirements
- Missing editor, PSI file, symbol, or file path degrades to the available subset of context rather than failing collection.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Explicit selection is preferred for snippet content; otherwise the contributor uses the caret-centered line window with truncation metadata when needed.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Symbol context prefers the reference/name segment at the caret and falls back to the enclosing named PSI element when no caret reference is available.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptEditorContextContributorTest.kt

- Symbol and file context must render through built-in renderers so envelope text and chips stay consistent with other prompt-context sources.
  [@test] ../../prompt/ui/testSrc/context/AgentPromptEditorContextContributorTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.context.AgentPromptEditorContextContributorTest`

## References
- `prompt-context-contracts.spec.md`
- `../actions/global-prompt-entry.spec.md`
