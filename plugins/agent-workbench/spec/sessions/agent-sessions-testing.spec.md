---
name: Agent Threads Testing
description: Coverage ownership matrix for Agent Threads, Agent Chat, provider backends, and prompt-launch contracts.
targets:
  - ../../sessions/testSrc/*.kt
  - ../../sessions-toolwindow/testSrc/*.kt
  - ../../sessions-actions/testSrc/*.kt
  - ../../chat/testSrc/*.kt
  - ../../claude/sessions/testSrc/**/*.kt
  - ../../codex/sessions/testSrc/**/*.kt
  - ../../filewatch/testSrc/**/*.kt
---

# Agent Threads Testing

Status: Draft
Date: 2026-05-09

## Summary
This spec maps Agent Workbench behavior specs to their primary module tests. It does not define runtime behavior.

## Coverage Owners
- Core identity, command mapping, prompt handoff, and editor-tab action contracts: provider descriptor tests, `AgentSessionPromptLauncherBridgeTest`, and `AgentSessionsEditorTabActionsTest`.
- Sessions loading and refresh: `AgentSessionLoadAggregationTest`, `AgentSessionRefreshServiceIntegrationTest`, `AgentSessionRefreshOnDemandIntegrationTest`, `AgentSessionRefreshConcurrencyIntegrationTest`, and `AgentSessionRefreshCoordinatorTest`.
- Swing tree UI: `AgentSessionsSwingTreeRenderingTest`, `AgentSessionsSwingTreeCellRendererTest`, `AgentSessionsSwingTreeInteractionTest`, `AgentSessionsSwingTreeStatePersistenceTest`, and `AgentSessionsTreeSnapshotTest`.
- New-thread actions: `AgentSessionsSwingNewSessionActionsTest`, `AgentSessionsTreePopupActionsTest`, `AgentSessionsEditorTabActionsTest`, and `AgentSessionsMainToolbarNewThreadActionsTest`.
- Chat tab lifecycle and dispatch: `AgentChatEditorServiceTest`, `AgentChatFileEditorProviderTest`, `AgentChatFileEditorLifecycleTest`, `AgentChatTabSelectionServiceTest`, and `AgentChatOpenTopLevelDispatchTest`.
- Archive, rename, and provider capability behavior: `AgentSessionArchiveServiceIntegrationTest`, `AgentSessionRenameServiceTest`, provider descriptor tests, and Claude rename/store tests.
- Dedicated frame behavior: `AgentSessionsGearActionsTest`, `AgentSessionPromptLauncherBridgeTest`, project frame capability tests, and terminal hyperlink routing tests.
- Codex source/backend behavior: `CodexAppServerClientTest`, `CodexAppServerSessionBackendTest`, `CodexSessionBackendSelectorTest`, rollout parser/watcher tests, refresh-hints tests, and activity resolver tests.
- File watching: `AgentWorkbenchDirectoryWatcherTest`, `DirectoryWatcherImplTest`, and provider watcher tests.

## Integration Gating
- Mock-backed Codex app-server contract coverage is mandatory in CI.
- Real Codex backend and real TUI rollout tests are local-gated by resolvable `codex` CLI; the TUI test also requires PTY support.
- Real-backend tests assert invariants, not user-specific thread ids.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test "com.intellij.agent.workbench.sessions.AgentSessionRefresh*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test "com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsSwing*Test"`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test "com.intellij.agent.workbench.chat.AgentChat*Test"`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test "com.intellij.agent.workbench.codex.sessions.CodexRollout*Test"`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProviderTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.CodexAppServerClientTest`

## References
- `../core/agent-core-contracts.spec.md`
- `agent-sessions.spec.md`
- `agent-sessions-tree.spec.md`
- `agent-sessions-refresh.spec.md`
- `../chat/agent-chat-editor.spec.md`
- `../actions/new-thread.spec.md`
- `agent-sessions-codex-rollout-source.spec.md`
