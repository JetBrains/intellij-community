---
name: Agent Chat Structure View
description: Requirements for the Agent Chat editor Structure View and provider-backed session history outline.
targets:
  - ../../common/src/session/AgentSessionModels.kt
  - ../../chat/src/AgentChatStructureView.kt
  - ../../chat/src/AgentChatFileEditor.kt
  - ../../chat/src/AgentChatFileEditorProvider.kt
  - ../../chat/resources/messages/AgentChatBundle.properties
  - ../../chat/testSrc/AgentChatFileEditorProviderTest.kt
  - ../../sessions-core/src/providers/AgentSessionOutline.kt
  - ../../sessions-core/src/providers/AgentSessionSource.kt
  - ../../codex/sessions/src/CodexSessionSource.kt
  - ../../codex/sessions/src/backend/rollout/CodexRolloutParser.kt
  - ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
  - ../../claude/common/src/ClaudeSessionsStore.kt
  - ../../claude/sessions/src/ClaudeSessionBackend.kt
  - ../../claude/sessions/src/ClaudeSessionSource.kt
  - ../../claude/sessions/src/backend/store/ClaudeStoreSessionBackend.kt
  - ../../claude/sessions/testSrc/ClaudeSessionsStoreTest.kt
  - ../../pi/sessions/src/PiSessionSource.kt
  - ../../pi/sessions/testSrc/PiSessionSourceTest.kt
---

# Agent Chat Structure View

Status: Draft
Date: 2026-06-16

## Summary
Agent Chat Structure View exposes a browse-only outline of persisted provider session history for concrete chat tabs. The outline is built from provider session files and must not start or restore the terminal just to populate Structure View.

## Requirements
- Agent Chat registers Structure View through the file editor provider for valid concrete chat files only. Pending tabs, invalid restored files, and files without provider/thread identity must not expose a Structure View builder.
  [@test] ../../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Structure View loading is asynchronous and provider-backed. Opening Structure View must use `AgentSessionSource.loadThreadOutline(path, threadId, subAgentId)` and must not initialize the chat terminal, send input, restore live terminal state, or mutate the selected thread.
  [@test] ../../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Provider outline loading is optional. `null` means the provider cannot produce an outline for the requested thread, and the UI must show an unavailable fallback instead of failing editor creation.

- The visible Structure tree starts with provider outline items directly; the UI must not add a duplicate session/thread title wrapper above the provider timeline. Loading, unavailable, and empty states render as a single top-level fallback row with localized Agent Chat bundle strings.

- The hidden Structure View root may auto-expand to reveal provider outline items. If a provider outline has one visible top-level root item, that visible root may auto-expand too, but nested provider group rows such as agent work phases must stay collapsed by default. Users can expand nested rows explicitly to inspect tool calls, exits, and other run details.

- The first version is browse-only. Structure View elements are not source navigatable and must not perform rollback, fork, active-leaf selection, terminal focus, or provider-side state changes.

- Codex outline items must not navigate to rollout JSONL records as a substitute for chat navigation. The outline is parsed from persisted history, while the editor renders a live terminal TUI that can clear, redraw, switch buffers, or trim scrollback. TUI navigation may be added only if the IDE can resolve a stable live TUI position for the item or Codex exposes a stable jump/anchor API.

- Outline items preserve provider order and hierarchy. The shared model supports user prompts, assistant responses, agent work groups, tool calls, tool results, plans, approval requests, input requests, summaries, and metadata; unknown provider records should be skipped or mapped to metadata rather than exposed as raw JSON.

- Codex outlines are parsed from rollout JSONL data and should group inferred agent work so tool-call and tool-result activity remains readable as a block-oriented history browser.
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Claude outlines are parsed from transcript JSONL data and should group assistant/tool activity into readable blocks while preserving prompt and summary records.
  [@test] ../../claude/sessions/testSrc/ClaudeSessionsStoreTest.kt

- Pi outlines are parsed from persisted Pi JSONL session entries using `/tree` semantics: `id`/`parentId` hierarchy, missing or self-parent roots, timestamp-ordered siblings, hidden bookkeeping nodes with visible descendants promoted, and leaf/bookkeeping records kept out of the visible Structure View. The implementation must not launch Pi or scrape the interactive `/tree` TUI.
  [@test] ../../pi/sessions/testSrc/PiSessionSourceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatFileEditorProviderTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendTest`
- `./tests.cmd --module intellij.agent.workbench.claude.sessions.tests --test com.intellij.agent.workbench.claude.sessions.ClaudeSessionsStoreTest`
- `./tests.cmd --module intellij.agent.workbench.pi.sessions.tests --test com.intellij.agent.workbench.pi.sessions.PiSessionSourceTest`

## References
- `agent-chat-editor.spec.md`
- `../sessions/agent-sessions.spec.md`
- `../sessions/agent-sessions-tree.spec.md`
