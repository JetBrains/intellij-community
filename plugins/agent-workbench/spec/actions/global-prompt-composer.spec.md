---
name: Global Prompt Composer
description: Mental model, UI ownership, and layout contract for the Agent Workbench global prompt composer.
targets:
  - ../../prompt/ui/src/AgentPromptPaletteView.kt
  - ../../prompt/ui/src/AgentPromptTextField.kt
  - ../../prompt/ui/src/AgentPromptContextChipsComponent.kt
  - ../../prompt/ui/src/AgentPromptPaletteContextController.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt
  - ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt
---

# Global Prompt Composer

Status: Draft
Date: 2026-06-27

## Summary
The global prompt composer is the task-preparation surface for Agent Workbench. It combines user instruction text, selected task context, and launch configuration before a prompt is submitted to a new agent task or an existing loaded task. The composer is not a plain text box: it separates what the user asks from what context is attached and how the agent task should run.

## Goals
- Give implementers and agents a single mental model for prompt UI ownership before editing layout, context chips, or launch controls.
- Keep task input, selected context, and execution settings visually connected while preserving their separate meanings.
- Make layout alignment explicit so editor internals, context rendering, and tray controls cannot drift independently.

## Non-goals
- This spec does not define context collection sources, resolver ordering, or prompt-context payload formats; those are owned by `../prompt-context/prompt-context-contracts.spec.md` and source-specific prompt-context specs.
- This spec does not define provider model catalogs, profile persistence, or launch-profile editing; those are owned by `global-prompt-generation-controls.spec.md` and `global-prompt-task-cost-profiles.spec.md`.
- This spec does not define popup lifecycle, keyboard submit behavior, validation, or launcher handoff; those are owned by `global-prompt-entry.spec.md`.

## Requirements
- The composer must treat editable prompt text as the user's instruction. Selected context must render as attachment cards outside the editable text and must not be inserted into the visible prompt message.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- Context attachment cards must appear above the editable prompt text. They represent task input context already selected for the launch, not suggestions, history, generation settings, or sent thread view content.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt

- `Add Context` must live in the bottom tray as the left prompt-composition action. It belongs to the task input lane because it changes attached context, not launch configuration.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- Launch settings must live in the bottom tray as the right launch-configuration affordance. They configure how a new task starts and must not be duplicated in the header or inside editable prompt text.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- Header controls must be limited to prompt-surface tools such as the compact Plan mode icon toggle, Run in container, prompt library, and related surface actions. Header controls must not contain selected context cards, `Add Context`, or the primary launch-settings affordance.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- Context cards, editable prompt text, `Add Context`, and the right edge of launch settings must share one composer-owned content lane in popup and inline prompt surfaces. The embedded editor must not add hidden horizontal insets that define or compensate for this lane.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt

- The composer must remain borderless. Prompt spacing may use composer-owned padding and vertical gaps, but not nested framed cards or editor borders that visually split context from the instruction.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt

- Inline empty-state and popup composers must use the same composition model. Inline mode may use compact typography, focus forwarding, and smaller vertical gaps, but it must keep the same context/text/tray ownership.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewStructureTest.kt

- Inline composers must keep a compact initial height, grow vertically as editable prompt text needs more lines, and cap text-driven growth before falling back to the prompt editor's vertical scrolling. Popup composers remain manually resizable and must not auto-grow from prompt text changes.
  [@test] ../../prompt/ui/testSrc/AgentPromptPaletteViewLayoutTest.kt

## User Experience
- Users should read the composer from top to bottom as: selected context, instruction, composition and launch controls.
- Context cards should be inspectable and removable, but visually secondary to the instruction and launch action. Removing a card should return the user to the editable instruction flow.
- `Add Context` is intentionally placed at the start of the input lane because users commonly decide what to include before or while describing the task.
- Launch settings are intentionally right-aligned because they tune execution rather than add content to the prompt.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewLayoutTest`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteViewStructureTest`

## References
- `global-prompt-entry.spec.md`
- `global-prompt-generation-controls.spec.md`
- `global-prompt-task-cost-profiles.spec.md`
- `../prompt-context/prompt-context-contracts.spec.md`
