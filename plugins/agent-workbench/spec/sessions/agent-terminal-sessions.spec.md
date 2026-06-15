---
name: Agent Workbench Terminal Sessions
description: Requirements for managing regular IDE Terminal shells as Agent Workbench sessions.
targets:
  - ../../common/src/session/AgentSessionModels.kt
  - ../../terminal/sessions/src/**/*.kt
  - ../../terminal/sessions/testSrc/*.kt
  - ../../chat/src/AgentChatTerminalTabSupport.kt
  - ../../chat/testSrc/AgentChatTerminalTabBuilderConfigurationTest.kt
  - ../../sessions-core/src/providers/AgentSessionProviderDescriptor.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../sessions/src/AgentSessionProviderMenuActions.kt
  - ../../sessions-actions/src/actions/*NewThread*.kt
  - ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
---

# Agent Workbench Terminal Sessions

Status: Accepted
Date: 2026-05-24

## Summary
Agent Workbench can start a regular IDE Terminal shell from the same new-thread affordances used for agent providers. Terminal sessions are tracked as provider-backed sessions so users can reopen, rename, archive, and unarchive them from Agent Workbench.

## Requirements
- Terminal is a first-class `AgentSessionProvider.TERMINAL` provider shown after Codex, Claude, Junie, and Pi.
  [@test] ../../terminal/sessions/testSrc/TerminalAgentSessionProviderDescriptorTest.kt

- New terminal-session launches must use the IDE Terminal default shell. Agent Chat terminal setup must not force `TerminalProcessType.NON_SHELL` or pass an explicit shell command for these launches.
  [@test] ../../chat/testSrc/AgentChatTerminalTabBuilderConfigurationTest.kt

- New terminal sessions must use provider-allocated concrete session ids so they appear immediately in the sessions tree instead of remaining pending `new-*` identities.
  [@test] ../../terminal/sessions/testSrc/TerminalAgentSessionProviderDescriptorTest.kt
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- Terminal sessions must persist in app-level non-roamable state per normalized project path, with id, title, creation time, update time, and archived flag. Listed rows must use provider `TERMINAL`, activity `READY`, active/archived filtering, and newest-updated ordering.
  [@test] ../../terminal/sessions/testSrc/TerminalSessionSourceTest.kt

- Terminal sessions persist only the last working directory as restore context, captured periodically while the terminal tab is open and once more when the chat editor closes the tab. Recent commands and recent terminal output must not be persisted (security: they routinely contain secrets). Restored sessions reopen at the saved working directory.
  [@test] ../../terminal/sessions/testSrc/TerminalSessionSourceTest.kt
  [@test] ../../terminal/sessions/testSrc/TerminalAgentSessionProviderDescriptorTest.kt

- Rename, archive, unarchive, and record-new-session operations must emit scoped thread-change events for the affected project path and thread id.
  [@test] ../../terminal/sessions/testSrc/TerminalSessionSourceTest.kt
  [@test] ../../terminal/sessions/testSrc/TerminalAgentSessionProviderDescriptorTest.kt

- Terminal sessions must not dispatch an initial prompt message. Terminal availability is always true at the provider layer; terminal-creation failures are handled by the shared terminal launch path.
  [@test] ../../terminal/sessions/testSrc/TerminalAgentSessionProviderDescriptorTest.kt

- Terminal must not be offered as a global prompt provider because prompt submissions are not dispatched to terminal sessions.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt

- New terminal-session launches must not update the shared last-used provider or launch-mode preferences used by prompt-capable agent quick-start actions.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

- Closing the last open editor copy of a concrete terminal session must archive that terminal session. Pending terminal tabs, non-terminal providers, and project disposal must not be treated as terminal archive requests.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- New-thread UI and provider menus must remain provider-generic. Terminal-specific action text, tooltip text, menu description, and new-tab title must come from descriptor bundle keys.
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Per-provider FUS telemetry tags exist for Codex, Claude, Junie, and Terminal; the generic launch path emits provider-tagged events without per-provider code in each descriptor.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.terminal.sessions.tests --test "com.intellij.agent.workbench.terminal.sessions.*Test"`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatTerminalTabBuilderConfigurationTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test "com.intellij.agent.workbench.sessions.*ActionsTest"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLaunchServiceTest`

## References
- `agent-sessions.spec.md`
- `../actions/new-thread.spec.md`
- `../chat/agent-chat-editor.spec.md`
