---
name: Agent Chat Editor
description: Requirements for Agent chat editor tab lifecycle, persistence/restore, and terminal-backed initialization.
targets:
  - ../chat/src/*.kt
  - ../chat/resources/intellij.agent.workbench.chat.xml
  - ../chat/resources/messages/AgentChatBundle.properties
  - ../common/src/icons/*.java
  - ../sessions/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../plugin/resources/META-INF/plugin.xml
  - ../plugin-content.yaml
  - ../chat/testSrc/*.kt
  - ../sessions/testSrc/*.kt
---

# Agent Chat Editor

Status: Draft
Date: 2026-02-22

## Summary
Define how Agent chat tabs are opened, restored, reused, and rendered in editor tabs. This spec owns tab lifecycle and persistence behavior. Shared command mapping and shared editor-tab popup action semantics are owned by `spec/agent-core-contracts.spec.md`.

## Goals
- Open and reuse chat tabs deterministically from thread/sub-agent selection.
- Restore all previously open Agent chat tabs across restart using protocol-backed virtual files.
- Keep startup responsive through lazy terminal-session initialization.
- Keep tab titles/icons stable and refreshable as session data evolves.

## Non-goals
- Defining sessions aggregation and tree-state behavior.
- Defining dedicated-frame routing policy details.
- Defining provider command mapping and shared popup-action contracts.

## Requirements
- Agent Workbench modules must register:
  - `fileEditorProvider` for Agent chat files,
  - `virtualFileSystem` key `agent-chat`,
  - `editorTabTitleProvider` for Agent chat tabs.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Chat editor opening must use `AsyncFileEditorProvider` and terminal reworked frontend integration (`TerminalToolWindowTabsManager`) with `shouldAddToToolWindow(false)`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Chat tabs must reuse an existing tab for the same canonical thread identity (`provider:threadId`) and `subAgentId` when present.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Agent chat virtual files must use protocol-backed v2 URLs: `agent-chat://2/<tabKey>`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- `tabKey` must be lowercase base36 (`0-9a-z`) encoding of SHA-256 digest over full tab identity.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Restore metadata must be persisted in app-level cache-file-backed `AgentChatTabsStateService` keyed by `tabKey`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Persisted tab-state payload must include project hash/path, thread identity/sub-agent, thread id, shell command, title, activity, pending Codex metadata (`pendingCreatedAtMs`, `pendingFirstInputAtMs`, `pendingLaunchMode`), and updated timestamp.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Chat restore must restore all previously open Agent chat tabs, not only the selected one.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Persisted tab-state entries are canonical restore source; legacy descriptor URL format and legacy `<config>/agent-workbench-chat-frame/tabs/*.awchat.json` metadata are out of compatibility scope and may be removed best-effort.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Stale or invalid tab-state entries must be pruned periodically.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Terminal content initialization must be lazy:
  - lightweight tab/editor shell is created immediately,
  - terminal session starts only on first explicit tab selection/focus.
  [@test] ../chat/testSrc/AgentChatTabSelectionServiceTest.kt

- Editor tab title must come from thread title with fallback `Agent Chat`, via `EditorTabTitleProvider` (no virtual-file-name mutation dependency), and must be middle-truncated to 50 characters for presentation while tooltip keeps full title.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Reopening an already-open tab with a newer thread title must update existing tab presentation.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Pending Codex tabs must capture first user-input timestamp once (on first terminal key event) and persist it for later rebind matching.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Editor tab icon must be provider-specific using canonical identity; `READY` is unbadged, non-`READY` activities use the activity badge mapping, unknown provider uses the default chat icon, and unknown activity defaults to `READY`.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Provider icon lookup in chat/editor tab providers must use shared typed icon holder (`AgentWorkbenchCommonIcons`), not inline path-based icon loading.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Successful archive must close matching open chat tabs for the same normalized path + canonical thread identity and delete corresponding persisted tab-state entries.
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Restore validation failures and terminal initialization failures must close the tab, delete the corresponding tab-state entry immediately, and surface deduplicated non-blocking warning notifications.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Terminal initialization failures caused by command lookup must include actionable warning text with attempted command and startup `PATH` snapshot when available.
  [@test] ../chat/testSrc/AgentChatRestoreNotificationServiceTest.kt

- Dedicated-frame vs current-project target frame selection must follow `spec/agent-dedicated-frame.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Shared command mapping and editor-tab popup action contract must follow `spec/agent-core-contracts.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Editor tab actions must include `Bind Pending Codex Thread` for pending Codex tabs, invoking targeted rebind for the active pending tab only.
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

## User Experience
- Clicking a thread opens its chat tab.
- Clicking a sub-agent opens a separate tab scoped to that sub-agent.
- Restored tabs appear immediately, while terminal startup is deferred until first explicit selection.

## Data & Backend
- Chat terminal sessions use source project/worktree `cwd`.
- Sessions service provides identity and command inputs to chat open flow.
- URL path carries only short stable `tabKey`; full restore payload is read from state service.

## Error Handling
- Invalid project path or project-open failure must not crash UI or open a broken tab.
- Missing/invalid identity context must fail safely without editor-tab corruption.
- Restore/initialization warning notifications must be deduplicated per tab+reason for the IDE session.
- Command lookup failures should expose actionable diagnostics (command + startup `PATH`) without adding fallback launch behavior.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatEditorServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatFileEditorProviderTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatTabSelectionServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatRestoreNotificationServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsOpenModeRoutingTest'`

## Open Questions / Risks
- If product policy changes restart-restore scope (for example single-tab restore), this spec requires explicit revision and migration behavior.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-sessions.spec.md`
