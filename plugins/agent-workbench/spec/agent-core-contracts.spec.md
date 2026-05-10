---
name: Agent Workbench Core Contracts
description: Cross-cutting contracts shared by Sessions, Chat, provider bridges, editor-tab actions, and prompt launch.
targets:
  - ../common/src/**/*.kt
  - ../sessions-core/src/**/*.kt
  - ../sessions/src/**/*.kt
  - ../sessions-actions/src/**/*.kt
  - ../chat/src/**/*.kt
  - ../claude/sessions/src/**/*.kt
  - ../codex/sessions/src/**/*.kt
  - ../junie/sessions/src/**/*.kt
  - ../sessions/testSrc/*.kt
  - ../sessions-actions/testSrc/*.kt
  - ../chat/testSrc/*.kt
  - ../claude/sessions/testSrc/*.kt
  - ../codex/sessions/testSrc/*.kt
  - ../junie/sessions/testSrc/*.kt
---

# Agent Workbench Core Contracts

Status: Draft
Date: 2026-05-09

## Summary
These contracts keep shared identity, command mapping, provider capabilities, prompt handoff, and cross-surface actions consistent across Agent Threads and Agent Chat.

## Requirements
- Canonical thread identity is `provider:threadId`. Stable provider ids are lowercase (`codex`, `claude`, `junie`); unknown ids remain valid identities and use fallback UI presentation.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Project/worktree keys used for visibility, warm snapshots, and deduplication must use normalized paths.
  [@test] ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

- Resume command mapping after executable token is canonical: Codex `-c check_for_update_on_startup=false resume <id>`, Claude `--resume <id>`, Junie `--skip-update-check --session-id <id>`.
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt

- New-thread command mapping after executable token is canonical: Codex standard/YOLO, Claude standard/YOLO, and Junie standard/YOLO are defined by provider descriptors and tested there.
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt

- Provider bridges resolve executables through the shared terminal agent resolver at launch time. Launch-spec construction is suspending; synchronous CLI availability remains available for UI painting.
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt

- Prompt launch handoff carries one optional startup launch override plus ordered post-start dispatch steps and token. Startup prompt commands are transient and must not replace persisted chat resume commands.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Post-start prompt dispatch is terminal-readiness-gated. Codex plan-mode dispatch sends `/plan` before the prompt body and retries the `/plan` step while Codex reports busy.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Claude recognized menu commands remain post-start dispatch and are sent as executable terminal input without prompt context packaging.
  [@test] ../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- Editor-tab popup actions for a selected Agent Chat tab expose archive, optional rename, copy thread id, and select-in-threads in stable placement relative to built-in close/copy actions.
  [@test] ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Sessions-tree popup thread actions keep open/new actions separated from archive/rename/copy actions and gate archive/rename visibility by provider capability.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- `Archive Thread` shortcuts and archive/rename enablement must stay consistent across tree-row and editor-tab entry points.
  [@test] ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt

- Claude archive, unarchive, and rename are provider-backed. When a matching top-level Claude chat tab is open, operations reuse that tab; otherwise they use the non-interactive resumed Claude transport.
  [@test] ../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt
  [@test] ../claude/sessions/testSrc/ClaudeThreadRenameEngineTest.kt

- `Select in Agent Threads` calls `ensureThreadVisible(path, provider, threadId)` before activating the tool window. Visibility primitives increment visible counts in +3 steps.
  [@test] ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions-toolwindow/testSrc/SessionTreeSelectionSyncTest.kt
  [@test] ../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.agent.workbench.claude.sessions.tests --test com.intellij.agent.workbench.claude.sessions.ClaudeAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.agent.workbench.junie.sessions.tests --test com.intellij.agent.workbench.junie.sessions.JunieAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.SessionTreeSelectionSyncTest`

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/actions/new-thread.spec.md`
- `spec/actions/global-prompt-entry.spec.md`
