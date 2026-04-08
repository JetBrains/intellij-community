---
name: Agent Workbench Core Contracts
description: Canonical cross-cutting contracts shared by Sessions, Chat Editor, Dedicated Frame routing, and new-thread actions.
targets:
  - ../common/src/*.kt
  - ../common/src/session/ClaudeMenuCommands.kt
  - ../common/src/icons/*.java
  - ../sessions/src/AgentSessionCli.kt
  - ../sessions/src/AgentSessionModels.kt
  - ../chat/src/AgentChatEditorTabActionContext.kt
  - ../claude/sessions/src/ClaudeAgentSessionProviderDescriptor.kt
  - ../sessions/src/service/AgentSessionLaunchService.kt
  - ../sessions/src/AgentSessionsToolWindow.kt
  - ../sessions/src/SessionTree.kt
  - ../chat/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../chat/resources/messages/AgentChatBundle.properties
  - ../sessions/testSrc/*.kt
  - ../chat/testSrc/*.kt
  - ../claude/sessions/testSrc/*.kt
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

- Project/worktree state keys (visibility, warm snapshot, dedup gates) must use normalized paths so `/path` and `/path/` resolve to the same entry.
  [@test] ../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

- Resume command mapping is canonical:
  - Codex: `codex resume <threadId>`
  - Claude: `claude --resume <threadId>`
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

- New-thread command mapping is canonical:
  - Codex default: `codex`
  - Codex YOLO: `codex --full-auto`
  - Claude default: `claude`
  - Claude YOLO: `claude --dangerously-skip-permissions`
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

- Canonical provider command mapping must keep bare executable names; executable lookup is resolved by terminal startup environment and provider mapping must not pre-resolve absolute executable paths.
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

- New-thread prompt bootstrap — startup command format: provider bridges may build one-shot startup commands by appending `-- <prompt>` to canonical command.
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- Provider bridge policy may explicitly disable startup prompt command usage for a launch request (for example Codex Plan mode fallback cases), forcing post-start dispatch.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Initial prompt handoff from Sessions to Chat must use one dispatch plan carrying:
  - optional startup launch-spec override,
  - optional ordered post-start dispatch steps (`postStartDispatchSteps`) plus `initialMessageToken`.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Startup prompt command is transient and must not be persisted into chat tab runtime `shellCommand`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- If startup command support is unavailable, inapplicable for the open target (for example existing-tab reuse), or exceeds command-size guard, post-start dispatch steps must be used.
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Post-start initial message fallback must be terminal-readiness-gated for all providers: dispatch only after terminal session reaches `Running` and startup output readiness heuristic, never before process readiness.
- If readiness signal is missing, timeout fallback dispatch may proceed after bounded timeout except for Codex `/plan` dispatch steps, which must continue waiting for explicit readiness.
- Codex new-thread `/plan <prompt>` with a non-empty stripped body must use post-start dispatch on the normal pending PTY session: first `/plan`, then the stripped prompt body.
- Existing-thread opens and bare `/plan` must use the same sequenced Codex post-start dispatch behavior.
- The Codex `/plan` step must retry when post-send terminal output contains `'/plan' is disabled while a task is in progress.` and must not advance to the prompt-body step until that retry condition clears.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
- Recognized Claude menu commands from the canonical Claude menu-command set must not use startup prompt injection. They must remain post-start dispatch so the Claude TUI receives them as slash commands after the session is running.
- Claude menu-command post-start dispatch must send the raw command text as typed terminal input with execute semantics and without bracketed paste mode, so commands such as `/mcp`, `/model`, or `/memory` execute immediately instead of remaining as pasted prompt text.
  [@test] ../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- Editor-tab popup contract for a selected Agent chat tab must expose exactly these actions with this placement:
  - `Archive Thread` appears before built-in close actions.
  - `Rename Thread` appears after built-in close actions and before `Copy Thread ID` when the selected tab targets a concrete top-level thread for a provider that supports rename.
  - `Copy Thread ID` appears after `Rename Thread` and before `Select in Agent Threads`.
  - `Select in Agent Threads` appears before `CopyPaths`.
  [@test] ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions-tree popup contract for thread rows must keep the single divider between `Open`/`New Thread` actions and thread actions, place `Archive Thread` immediately before `Rename Thread`, and keep `Rename Thread` before `CopyReferencePopupGroup` without another divider.
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsToolWindowFactorySwingTest.kt

- `Archive Thread` default shortcuts must be:
  - Windows (`$default`) keymap: `Ctrl+Alt+Delete`
  - Linux (`Default for XWin`, `Default for GNOME`, `Default for KDE`) keymaps: `Alt+Shift+F4`
  - macOS (`Mac OS X 10.5+`) keymap: `Cmd+Alt+Delete`
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- `Archive Thread` visibility/enablement must be gated by provider archive capability consistently for both tree-row and editor-tab entry points.
  [@test] ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt

- `Rename Thread` visibility/enablement must be gated by provider rename capability consistently for both tree-row and editor-tab entry points, and it must remain hidden for pending tabs and sub-agent targets.
  [@test] ../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- Provider bridge unarchive capability is optional; unsupported providers must keep archive flow functional and must not block supported-provider unarchive restores.
  [@test] ../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

- `Select in Agent Threads` must call `ensureThreadVisible(path, provider, threadId)` before activating the Agent Threads tool window.
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/SessionTreeSelectionSyncTest.kt

- Shared visibility primitives are canonical:
  - `showMoreThreads(path)` increments visible count by +3 in runtime state for the normalized path.
  - `ensureThreadVisible(path, provider, threadId)` increments runtime visibility in +3 steps until target thread is visible.
  [@test] ../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

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
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.claude.sessions.ClaudeAgentSessionProviderDescriptorTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexAgentSessionProviderDescriptorTest'`
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
