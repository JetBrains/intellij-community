---
name: Agent Workbench Core Contracts
description: Cross-cutting contracts shared by Sessions, Chat, provider bridges, editor-tab actions, and prompt launch.
targets:
  - ../../common/src/**/*.kt
  - ../../sessions-core/src/**/*.kt
  - ../../sessions/src/**/*.kt
  - ../../sessions-actions/src/**/*.kt
  - ../../chat/src/**/*.kt
  - ../../claude/sessions/src/**/*.kt
  - ../../codex/sessions/src/**/*.kt
  - ../../junie/sessions/src/**/*.kt
  - ../../sessions/testSrc/*.kt
  - ../../sessions-actions/testSrc/*.kt
  - ../../chat/testSrc/*.kt
  - ../../claude/sessions/testSrc/*.kt
  - ../../codex/sessions/testSrc/*.kt
  - ../../junie/sessions/testSrc/*.kt
---

# Agent Workbench Core Contracts

Status: Draft
Date: 2026-05-09

## Summary
These contracts keep shared identity, command mapping, provider capabilities, prompt handoff, and cross-surface actions consistent across Agent Threads and Agent Chat.

## Requirements
- Canonical thread identity is `provider:threadId`. Stable provider ids are lowercase (`codex`, `claude`, `junie`); unknown ids remain valid identities and use fallback UI presentation.
  [@test] ../../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Project/worktree keys used for visibility, warm snapshots, and deduplication must use normalized paths.
  [@test] ../../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

- Standard resume command mapping after executable token is canonical: Codex `-c check_for_update_on_startup=false -c tui.terminal_title=["thread-id","thread"] resume <id>`, Claude `--resume <id>`, Junie `--skip-update-check --session-id <id>`. Codex `thread-id` is supported by stable CLI `0.131.0+`; stable `0.130.0` and older ignore it and fall back to `thread`. YOLO resume uses provider-specific YOLO flags only when explicitly requested by prompt launch or restored from stored chat tab metadata; ordinary thread open must not infer YOLO from global last-used UI preferences.
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- New-thread command mapping after executable token is canonical: Codex standard/YOLO include `-c check_for_update_on_startup=false -c tui.terminal_title=["thread-id","thread"]`, Claude standard/YOLO with a preallocated `--session-id <uuid>`, and Junie standard/YOLO are defined by provider descriptors and tested there. Codex `thread-id` has the same `0.131.0+` stable compatibility boundary as resume launches.
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt

- Provider bridges resolve executables through the shared `TerminalAgentResolver` at launch time. `AgentSessionProviderDescriptor.isCliAvailable` is a single suspending contract backed by the resolver; synchronous UI surfaces consult the project-level provider availability cache and request background refreshes instead of blocking UI code. Launch-spec construction is suspending.
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt

- Prompt launch handoff carries one optional startup launch override plus ordered post-start dispatch steps and token. Dispatch steps carry an explicit action; legacy text-only steps are interpreted as text dispatch. Startup prompt commands are transient and must not replace persisted chat resume commands.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Prompt plan mode is requested only through provider option ids. A user-typed `/plan` prefix is ordinary prompt text owned by the provider CLI, not Agent Workbench syntax, and must not be stripped or converted into plan mode.
  [@test] ../../codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../../junie/sessions/testSrc/JunieAgentSessionProviderDescriptorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Post-start prompt dispatch is terminal-readiness-gated. Terminal plan-mode dispatch first ensures the TUI is visibly in Plan mode via the BackTab terminal sequence, then sends the plain prompt body; if Plan mode cannot be confirmed, the prompt body is not submitted and dispatch must not fall back to standard-mode execution.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../../chat/testSrc/AgentChatInitialMessageDispatcherTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Claude plan-mode prompt launch uses `--permission-mode plan` in startup commands for new sessions and resumed threads when Agent Workbench opens the process. Plain `claude --resume <id>` must not be treated as preserving plan mode by Agent Workbench, and already-open editor tabs are not mutated into plan mode by prompt launch.
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeNewThreadPromptLaunchIntegrationTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeExistingThreadPromptLaunchIntegrationTest.kt

- Claude recognized menu commands remain post-start dispatch and are sent as executable terminal input without prompt context packaging.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeAgentSessionProviderDescriptorTest.kt

- Editor-tab popup actions for a selected Agent Chat tab expose archive, optional rename, copy thread id, and select-in-threads in stable placement relative to built-in close/copy actions.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Sessions-tree popup thread actions keep open/new actions separated from archive/rename/copy actions and gate archive/rename visibility by provider capability.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt

- `Archive Thread` shortcuts and archive/rename enablement must stay consistent across tree-row and editor-tab entry points.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt

- Claude archive, unarchive, and rename are provider-backed. Archive and unarchive use the non-interactive resumed Claude transport; rename reuses a matching top-level Claude chat tab when one is open and otherwise uses the non-interactive resumed Claude transport.
  [@test] ../../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeThreadRenameEngineTest.kt

- `Select in Agent Threads` calls `ensureThreadVisible(path, provider, threadId)` before activating the tool window. Visibility primitives increment visible counts in +3 steps.
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../../sessions-toolwindow/testSrc/SessionTreeSelectionSyncTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.agent.workbench.claude.sessions.tests --test com.intellij.agent.workbench.claude.sessions.ClaudeAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.agent.workbench.junie.sessions.tests --test com.intellij.agent.workbench.junie.sessions.JunieAgentSessionProviderDescriptorTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsEditorTabActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.SessionTreeSelectionSyncTest`

## References
- `../sessions/agent-sessions.spec.md`
- `../chat/agent-chat-editor.spec.md`
- `../actions/new-thread.spec.md`
- `../actions/global-prompt-entry.spec.md`
