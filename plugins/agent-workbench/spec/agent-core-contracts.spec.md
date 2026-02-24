---
name: Agent Workbench Core Contracts
description: Canonical cross-cutting contracts shared by Sessions, Chat Editor, Dedicated Frame routing, and new-thread actions.
targets:
  - ../common/src/*.kt
  - ../common/src/icons/*.java
  - ../sessions/src/AgentSessionCli.kt
  - ../sessions/src/AgentSessionModels.kt
  - ../sessions/src/AgentSessionsEditorTabActionContext.kt
  - ../sessions/src/AgentSessionsService.kt
  - ../sessions/src/AgentSessionsToolWindow.kt
  - ../sessions/src/SessionTree.kt
  - ../chat/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../chat/resources/messages/AgentChatBundle.properties
  - ../sessions/testSrc/*.kt
  - ../chat/testSrc/*.kt
  - ../codex/sessions/testSrc/*.kt
---

# Agent Workbench Core Contracts

Status: Draft
Date: 2026-02-24

## Summary
Define the single source of truth for cross-feature behavior that must stay consistent across Agent Threads, Agent Chat editor tabs, dedicated-frame routing, and provider-specific session actions.

## Goals
- Keep shared behavior defined exactly once.
- Prevent drift in command mapping, identity keys, and editor-tab action semantics.
- Make cross-module refactors safer by pinning contracts to one canonical spec.

## Non-goals
- Defining sessions aggregation/tree rendering details.
- Defining Codex rollout parsing and watcher semantics.
- Defining dedicated-frame lifecycle details.

## Requirements
- Thread identity used by Sessions, Chat tabs, and persisted tab metadata must use canonical key format `provider:threadId`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt

- Provider ids in canonical thread identities must use lowercase stable values (`codex`, `claude`). Unknown provider ids are valid identities but must use fallback icon behavior on editor tabs.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Project/worktree state keys (visibility, open-preview cache, dedup gates) must use normalized paths so `/path` and `/path/` resolve to the same entry.
  [@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

- Resume command mapping is canonical:
  - Codex: `codex resume <threadId>`
  - Claude: `claude --resume <threadId>`
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt

- New-thread command mapping is canonical:
  - Codex default: `codex`
  - Codex YOLO: `codex --full-auto`
  - Claude default: `claude`
  - Claude YOLO: `claude --dangerously-skip-permissions`
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt

- Canonical provider command mapping must keep bare executable names; executable lookup is resolved by terminal startup environment and provider mapping must not pre-resolve absolute executable paths.
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt

- Editor-tab popup contract for a selected Agent chat tab must expose exactly these actions with this placement:
  - `Archive Thread` appears before built-in close actions.
  - `Select in Agent Threads` appears after `CopyPaths`.
  - `Copy Thread ID` appears after `Select in Agent Threads`.
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- `Archive Thread` default shortcuts must be:
  - Win/Linux keymaps: `Ctrl+Alt+F4`
  - macOS (`Mac OS X 10.5+`) keymap: `Cmd+Alt+W`
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- `Archive Thread` visibility/enablement must be gated by provider archive capability consistently for both tree-row and editor-tab entry points.
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

- Provider bridge unarchive capability is optional; unsupported providers must keep archive flow functional and must not block supported-provider unarchive restores.
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt

- `Select in Agent Threads` must call `ensureThreadVisible(path, provider, threadId)` before activating the Agent Threads tool window.
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/SessionTreeSelectionSyncTest.kt

- Shared visibility primitives are canonical:
  - `showMoreThreads(path)` increments visible count by +3 in runtime state for the normalized path.
  - `ensureThreadVisible(path, provider, threadId)` increments runtime visibility in +3 steps until target thread is visible.
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

## User Experience
- Users should see identical behavior regardless of whether actions are triggered from the tree or editor tabs.
- Provider icons and titles should remain stable when tabs are restored or reselected.

## Data & Backend
- Session identity and command mapping are contract-level inputs consumed by both Sessions and Chat modules.
- Provider-specific backends may vary, but they must not override canonical command mapping defined here.

## Error Handling
- Invalid provider ids must degrade gracefully using fallback icon behavior.
- Cross-surface action contracts must fail safely when required context is missing.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionCliTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.SessionTreeSelectionSyncTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatFileEditorProviderTest'`

## Open Questions / Risks
- If additional providers are added, provider-id validation and command mapping require explicit contract extension in this file.

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-sessions-thread-visibility.spec.md`
- `spec/actions/new-thread.spec.md`
