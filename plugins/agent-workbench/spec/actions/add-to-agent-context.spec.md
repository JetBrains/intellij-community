---
name: Add to Agent Context
description: Requirements for adding editor, project, test, console, and VCS context to the prompt or an open Agent Chat tab.
targets:
  - ../../prompt/ui/src/actions/AgentWorkbenchAddToAgentContextAction.kt
  - ../../prompt/ui/src/actions/AgentWorkbenchAddToAgentContextIntention.kt
  - ../../prompt/ui/src/AgentPromptAddToAgentContextActionService.kt
  - ../../prompt/ui/src/AgentPromptAddContext*.kt
  - ../../prompt/ui/src/AgentPromptContext*.kt
  - ../../chat/src/AgentChatPendingContextPanel.kt
  - ../../chat/src/AgentChatOpenTabsSnapshot.kt
  - ../../prompt/vcs-ui/resources/intellij.agent.workbench.prompt.vcs.ui.xml
  - ../../prompt/ui/testSrc/actions/*.kt
  - ../../prompt/ui/testSrc/context/*.kt
  - ../../plugin/testSrc/AgentWorkbenchAddToAgentContextActionRegistrationTest.kt
---

# Add to Agent Context

Status: Draft
Date: 2026-05-09

## Summary
`Add to Agent Context` collects context from the invocation place and routes it either into the visible prompt popup or into an already-open top-level concrete Agent Chat tab for the same workspace.

## Requirements
- The action/intention must collect default prompt context from the invocation place. If no context is available, it shows the empty-context status and does not open a popup.
  [@test] ../../prompt/ui/testSrc/actions/AgentWorkbenchAddToAgentContextActionTest.kt

- The action is registered in editor, project view, editor tab, console, test tree, Changes View, and VCS log popups where prompt-context contributors can produce context.
  [@test] ../../plugin/testSrc/AgentWorkbenchAddToAgentContextActionRegistrationTest.kt

- Place-specific visibility must avoid module-specific dependencies: editor/editor-tab places require a local backing file, project view requires at least one local filesystem selection, and other places fall back to project presence plus contributor availability.
  [@test] ../../prompt/ui/testSrc/actions/AgentWorkbenchAddToAgentContextActionTest.kt

- If the prompt popup is visible for the project, invocation appends de-duplicated context to that popup, preserves selected tab/provider/task state, and focuses the composer.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt

- If the prompt popup is not visible, routing considers only open top-level concrete Agent Chat tabs for the same normalized project path. Pending tabs, sub-agent tabs, and catalog-only threads are not candidates.
  [@test] ../../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt

- Fresh routing is deterministic: no candidate opens the prompt with context, one candidate adds pending context to that chat, selected candidate wins when multiple exist, and otherwise the user chooses a target; chooser cancellation opens nothing.
  [@test] ../../prompt/ui/testSrc/AgentPromptPalettePopupServiceTest.kt
  [@test] ../../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt

- Context added to an open chat is buffered as pending editor-local context and must not mutate terminal input until the user submits.
  [@test] ../../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt

- Manual context is additive to auto context, source reruns replace the previous item from that source, and removing a parent context chip may remove descendant chips recursively.
  [@test] ../../prompt/ui/testSrc/AgentPromptContextRemovalDecisionsTest.kt
  [@test] ../../prompt/ui/testSrc/AgentPromptContextPersistenceDecisionsTest.kt

- Context block soft cap is 12,000 characters. Exceeding it requires explicit send-full, auto-trim, or cancel before launch.
  [@test] ../../prompt/ui/testSrc/AgentPromptContextSoftCapPolicyTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.actions.*"`
- `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test "com.intellij.agent.workbench.prompt.ui.AgentPromptContext*Test"`
- `./tests.cmd --module intellij.agent.workbench.plugin.tests --test com.intellij.agent.workbench.plugin.AgentWorkbenchAddToAgentContextActionRegistrationTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatOpenTopLevelDispatchTest`

## References
- `global-prompt-entry.spec.md`
- `../prompt-context/prompt-context-contracts.spec.md`
- `../chat/agent-chat-editor.spec.md`
