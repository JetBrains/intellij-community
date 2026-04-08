---
name: Agent Chat Semantic Navigation
description: Requirements for semantic transcript regions and editor navigation affordances in Agent Workbench chat tabs.
targets:
  - ../chat/src/AgentChatFileEditor.kt
  - ../chat/src/AgentChatTerminalViewSupport.kt
  - ../chat/src/AgentChatEditorTabProposedPlanActions.kt
  - ../chat/src/CodexTuiPatchFoldController.kt
  - ../chat/resources/intellij.agent.workbench.chat.xml
  - ../chat/resources/messages/AgentChatBundle.properties
  - ../common/src/AgentWorkbenchActionIds.kt
  - ../plugin/resources/META-INF/plugin.xml
  - ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt
  - ../sessions-actions/testSrc/AgentWorkbenchPluginLoadingTest.kt
---

# Agent Chat Semantic Navigation

Status: Draft
Date: 2026-04-01

## Summary
Define how Agent Workbench chat tabs annotate semantically meaningful terminal-transcript regions and expose them through IntelliJ editor navigation affordances. V1 owns proposed-plan navigation in embedded Codex chats; chat tab lifecycle and persistence remain owned by `spec/agent-chat-editor.spec.md`.

## Goals
- Make important TUI transcript regions navigable through standard IntelliJ editor affordances.
- Reuse occurrence navigation and editor stripe markers instead of inventing chat-local navigation UI.
- Keep provider-specific transcript parsing isolated behind detector contracts so more providers and region kinds can be added without redesigning editor integration.

## Non-goals
- Inline accept/decline controls or any transcript-mutating UI.
- PSI-based gutters, structure view nodes, or Code Vision entries for chat transcript regions.
- Defining declined-plan, tool-call, patch, or error-region navigation in v1.
- Defining chat tab lifecycle, persistence, or thread-opening behavior.

## Requirements
- Agent Workbench chat must define semantic transcript navigation as provider-pluggable region detection over terminal transcript snapshots, with editor integration owned by shared chat-editor plumbing instead of provider-specific action logic.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- V1 semantic navigation must support only `PROPOSED_PLAN` regions for Codex chats; providers without a registered detector must expose no semantic transcript regions.
- V1 semantic navigation must also support `UPDATED_PLAN` regions for Codex chats to mark plan updates (accepted/modified plans) alongside proposed plans.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- Proposed-plan navigation must remain gated by registry key `agent.workbench.semantic.proposed.plan.navigation`, default `false`.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- Codex plan detection must use TUI header patterns emitted by the Codex CLI: `• Proposed Plan` for proposed plans and `• Updated Plan` for plan updates (U+2022 BULLET prefix). Header-only lines without subsequent content must not produce semantic regions.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- Proposed-plan summaries must derive from the first meaningful line inside the block, ignoring empty lines, code fences, and Markdown heading/list prefixes used only for formatting.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- Multiple proposed-plan regions in one transcript must preserve transcript order and assign deterministic same-content ordinals so repeated content remains independently navigable.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- Semantic-region state must materialize navigable editor markup for each active proposed-plan region and must support wrap-around previous/next navigation based on the editor caret position.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- Proposed plans and updated plans must use distinct error-stripe mark colors so users can visually distinguish plan states at a glance.
  [@test] ../chat/testSrc/AgentChatSemanticRegionControllerTest.kt

- Agent Workbench chat plugin must register editor-tab actions `Previous Proposed Plan` and `Next Proposed Plan` so navigation is discoverable from chat tabs and standard IntelliJ action lookup.
  [@test] ../sessions-actions/testSrc/AgentWorkbenchPluginLoadingTest.kt

## User Experience
- When the registry key is disabled, or the selected chat provider has no semantic detector, the chat editor shows no semantic-navigation actions or markers.
- In supported Codex chats, each detected proposed plan appears as a thin right-stripe marker with tooltip text derived from the region summary.
- Each detected plan update (accepted/modified plan) appears as a distinct-colored thin right-stripe marker with tooltip text derived from the region summary.
- Clicking a proposed-plan marker or invoking previous/next occurrence navigation moves the caret to the start of that region and centers it in the transcript editor.
- Proposed-plan navigation is read-only; users still accept, decline, or edit plans through the TUI itself.

## Data & Backend
- The source of truth for semantic-region offsets is the active terminal transcript shown in the embedded editor.
- Shared controller/state logic must react to transcript changes, active output-model switches, and session termination without leaving stale markers attached to the editor. Section boundaries are detected heuristically from TUI header patterns and indentation structure.
- Detector implementations may be provider-specific, but region kinds and navigation/controller abstractions must remain provider-agnostic so future semantic areas can reuse the same pipeline.

## Error Handling
- Unsupported providers and malformed transcript blocks must degrade silently to no semantic regions.
- Editor updates must verify that the active output model and snapshot still match before applying markers, so stale asynchronous detection results cannot annotate the wrong transcript.
- Session termination and editor disposal must clear semantic markers and related listeners.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatSemanticRegionControllerTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.CodexTuiPatchFoldControllerTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentWorkbenchPluginLoadingTest`

## Open Questions / Risks
- Additional transcript areas such as accepted plans, declined plans, approval prompts, tool calls, and patches will require an explicit region taxonomy and product decisions for colors, labels, and action naming.
- Other providers may need stable transcript delimiters before semantic navigation can be implemented without brittle heuristics.
- Long transcript truncation or alternate-screen transitions may need explicit product rules if navigation is expected to reach historical regions no longer present in the active editor buffer.

## References
- `spec/agent-chat-editor.spec.md`
- `spec/agent-core-contracts.spec.md`
