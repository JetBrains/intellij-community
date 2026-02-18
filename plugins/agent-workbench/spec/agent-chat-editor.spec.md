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
Date: 2026-02-18

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
- The `intellij.agent.workbench` content module must register a `fileEditorProvider` for Agent chat editors.
- The `intellij.agent.workbench` content module must register a non-physical `virtualFileSystem` with key `agent-chat` for chat editor files.
- The `intellij.agent.workbench` content module must register an `editorTabTitleProvider` for Agent chat files.
- The chat editor must be opened via `AsyncFileEditorProvider` and use the reworked terminal frontend (`TerminalToolWindowTabsManager`) with `shouldAddToToolWindow(false)`.
- Chat editors reuse an existing editor tab for the same provider/session identity (`provider:threadId`) and `subAgentId` when present.
- Agent chat virtual files must be protocol-backed (`agent-chat://`) and restorable by `VirtualFileManager.findFileByUrl`.
- Chat tab persistence must restore all previously open Agent chat tabs (not just the currently selected tab).
- Agent chat virtual file path must use short stable v2 format `agent-chat://2/<tabKey>`.
- `tabKey` must use lowercase Base36 (`0-9a-z`) encoding of full SHA-256 identity digest.
- Chat tab metadata must be persisted in disk files under `<config>/agent-workbench-chat-frame/tabs/<tabKey>.awchat.json`.
- Metadata file payload must include identity and runtime fields: project hash/path, thread identity/sub-agent, thread id, shell command, title, and updated timestamp.
- Metadata files are canonical restore state for Agent chat tabs; URL compatibility with previous descriptor-encoded format is not required.
- Stale or invalid metadata files must be pruned periodically.
- Chat editor creation must be lazy for heavy terminal content:
  - tab/editor shell is created immediately,
  - terminal session is created only after first explicit selection/focus of that tab.
- Advanced setting `agent.workbench.chat.open.in.dedicated.frame` controls target frame selection (default `true`).
- When the setting is enabled, chat opens in a dedicated frame project and the source project remains closed if it is currently closed.
- When the setting is disabled, chat opens in the source project frame and closed source projects are opened first.
- Clicking a thread row opens its chat editor. Clicking a sub-agent row opens a separate chat editor tab scoped to that sub-agent.
- The editor tab title must use the thread title (fallback to `Agent Chat` when blank).
- Editor tab title must be provided via `EditorTabTitleProvider` and must not depend on virtual file name mutations.
- Reopening an already open chat tab for the same identity with a newer thread title must update the existing tab title.
- The shell command used to start chat sessions is provider-specific:
  - Codex: `codex resume <threadId>`
  - Claude: `claude --resume <threadId>`
- Codex fresh-thread opens (project-row `New Thread`) use `codex` without `resume`.

- Chat editor opening must use `AsyncFileEditorProvider`.
- Terminal integration must use reworked frontend (`TerminalToolWindowTabsManager`) with `shouldAddToToolWindow(false)`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Chat tabs must reuse an existing tab for the same canonical thread identity (`provider:threadId`) and `subAgentId` when present.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Agent chat virtual files must use protocol-backed v2 URLs: `agent-chat://2/<tabKey>`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- `tabKey` must be lowercase base36 (`0-9a-z`) encoding of SHA-256 digest over full tab identity.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Restore metadata must be persisted in app-level cache-file-backed `AgentChatTabsStateService` keyed by `tabKey`.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Persisted tab-state payload must include:
  - project hash/path,
  - thread identity/sub-agent and thread id,
  - shell command, title, activity,
  - pending Codex metadata (`pendingCreatedAtMs`, `pendingFirstInputAtMs`, `pendingLaunchMode`),
  - concrete Codex `/new` rebinding metadata (`newThreadRebindRequestedAtMs`),
  - initial prompt metadata (`initialComposedMessage`, `initialMessageToken`, `initialMessageSent`, `initialMessageTimeoutPolicy`),
  - updated timestamp.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Chat restore must restore all previously open Agent chat tabs, not only the selected one.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Persisted tab-state entries are canonical restore source. Legacy descriptor URL format and `*.awchat.json` metadata are unsupported and may be removed.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Stale or invalid tab-state entries must be pruned periodically.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Terminal content initialization must be lazy:
  - lightweight tab/editor shell is created immediately,
  - terminal session starts only on first explicit tab selection/focus.
  [@test] ../chat/testSrc/AgentChatTabSelectionServiceTest.kt

- Disposing an initialized chat editor must always release terminal tab resources:
  - manager-backed tab content must close through `TerminalToolWindowTabsManager.closeTab`,
  - detached tab content (no content manager) must still be released.
  [@test] ../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../chat/testSrc/AgentChatTerminalTabCloseTest.kt

- Editor tab title must come from thread title with fallback `Agent Chat`, via `EditorTabTitleProvider` (no virtual-file-name mutation dependency).
- Tab title must be middle-truncated to 50 characters for presentation; tooltip keeps full title.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Reopening an already-open tab with a newer thread title must update existing tab presentation.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Pending Codex tabs must capture first user-input timestamp once (on first terminal key event) and persist it for later rebind matching.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Concrete top-level Codex tabs must detect execution of exact terminal command `/new`, persist a single rebind anchor timestamp (`newThreadRebindRequestedAtMs`), and request scoped refresh for the tab path.
- `/new` detection must track the typed command line, handle backspace/delete and escape reset, and must not arm on partial commands or incidental `/new` substrings.
  [@test] ../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Concrete top-level Codex tabs armed by `/new` may be rebound to a newly discovered concrete thread for the same normalized path; rebinding must update tab identity, resume command, title, activity, and persisted snapshot, then clear the `/new` anchor.
- Concrete `/new` rebinding must validate the persisted anchor timestamp before applying so stale refresh work cannot rebind after a newer `/new` request.
- Concrete `/new` rebinding must skip if the target identity is already open, require a unique in-window target candidate, and clear stale anchors without rebinding.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Chat open requests may carry a single initial-message dispatch plan that includes both optional startup launch override and optional post-start metadata.
- On new-tab opens, startup launch override (when present) is transient and must suppress immediate post-start metadata persistence on creation.
- On existing-tab opens, startup launch override is not applicable and post-start metadata must update the existing tab state for readiness-gated dispatch.
- Post-start initial prompt metadata (`initialComposedMessage`, `initialMessageToken`) must be dispatched only after terminal session state reaches `Running` and terminal output indicates startup readiness (first meaningful output plus idle stabilization window), not eagerly at editor initialization.
- Readiness stabilization defaults: 250ms output-idle window after first meaningful output; if no readiness signal appears within 2s after `Running`, timeout fallback dispatch is allowed for non-plan messages.
- Codex plan-mode command messages (`/plan` command form) must not dispatch on readiness timeout and must continue waiting for explicit readiness until session termination/editor disposal.
- If terminal session reaches `Terminated` before `Running`, or the editor is disposed before `Running`, pending initial prompt metadata must remain unsent.
- If initial prompt metadata is updated while waiting for `Running`, dispatch must use the latest metadata and stale in-flight dispatch attempts must not mark metadata as sent.
  [@test] ../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Editor tab icon must be provider-specific using canonical identity; every normalized `AgentThreadActivity` state is represented by an activity badge, unknown provider uses the default chat icon as the base icon, and unknown activity defaults to `READY`.
- Pending YOLO-mode tabs must overlay a red error-dot badge on the provider icon (via `withYoloModeBadge`) to visually distinguish YOLO sessions from standard ones.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Provider icon lookup in chat/editor tab providers must use shared typed icon holder (`AgentWorkbenchCommonIcons`), not inline path-based icon loading.
  [@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Successful archive must close matching open chat tabs for the same normalized path + canonical thread identity and delete corresponding persisted tab-state entries.
  [@test] ../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Restore validation failures and terminal initialization failures must close the tab, delete the corresponding tab-state entry immediately, and surface deduplicated non-blocking warning notifications.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Terminal initialization failures caused by command lookup must include actionable warning text with attempted command and startup `PATH` snapshot when available.
  [@test] ../chat/testSrc/AgentChatRestoreNotificationServiceTest.kt

- Editor tab actions must include `Bind Pending Codex Thread` for pending Codex tabs, invoking targeted rebind for the active pending tab only.
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

## User Experience
- Clicking a thread opens its chat tab.
- Clicking a sub-agent opens a separate tab scoped to that sub-agent.
- Restored tabs appear immediately, while terminal startup is deferred until first explicit selection.

## Data & Backend
- Chat terminal sessions start in the source project/worktree working directory.
- Sessions service passes provider-aware session identity and command into the chat open flow.
- Chat file URLs persist only short stable tab identity (`tabKey`).
- Full restore metadata is read from tab metadata files and not encoded in virtual file URL path.

## Error Handling
- If the project path is invalid or project opening fails, do not open a chat editor tab.
- If provider/session identity cannot be resolved, fail safely without crashing the UI.
- If a restored tab metadata file is missing/corrupt/invalid (or has missing path/identity/command), skip and close that tab and show a non-blocking warning notification.
- Restore warnings for the same tab/reason must be deduplicated per IDE session to avoid startup notification spam.
- If terminal initialization fails on first tab activation, close the tab and show a warning notification.

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
- `spec/agent-dedicated-frame-project-switching.spec.md`
