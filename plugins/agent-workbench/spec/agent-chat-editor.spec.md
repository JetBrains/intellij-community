---
name: Agent Chat Editor
description: Requirements for opening Agent chat tabs from Sessions in dedicated-frame and current-project modes.
targets:
  - ../chat/src/*.kt
  - ../common/src/icons/*.kt
  - ../chat/resources/intellij.agent.workbench.chat.xml
  - ../chat/resources/messages/AgentChatBundle.properties
  - ../plugin/resources/META-INF/plugin.xml
  - ../plugin-content.yaml
  - ../sessions/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../sessions/testSrc/*.kt
---

# Agent Chat Editor

Status: Draft
Date: 2026-02-19

## Summary
Define how thread/sub-agent selections open chat editor tabs. Routing honors dedicated-frame mode, reuses tabs by session identity, persists/restores chat tabs through a protocol-backed virtual file system, and lazily initializes heavy terminal content on first explicit tab selection.

## Goals
- Open chat editor tabs reliably from Session tree interactions.
- Reuse existing tabs for the same provider/session/sub-agent identity.
- Keep routing behavior consistent with dedicated-frame setting.
- Restore previously opened chat tabs across IDE restart without `mock:///` file resolution failures.
- Keep startup responsive by deferring terminal initialization until a tab is explicitly shown.

## Non-goals
- Implementing non-terminal chat UI.
- Defining provider discovery or session list loading.

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
- Successful thread archive from Agent Threads must close all matching open chat tabs and delete matching metadata files for the same normalized project path + thread identity.
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
- Editor tab icon must be provider-specific for Agent chat tabs:
  - `codex:*` thread identity uses Codex icon,
  - `claude:*` thread identity uses Claude icon,
  - unknown/invalid provider identity falls back to default Agent chat icon.
- Editor tab icon must include a thread activity badge (`READY`, `PROCESSING`, `REVIEWING`, `UNREAD`) using Session activity semantics.
- Unknown activity state for chat tabs defaults to `READY`.
- Editor-tab provider icons must be resolved via shared typed icon holder (`AgentWorkbenchCommonIcons`) instead of inline path-based icon loading in editor-tab provider code.
- Codex/Claude editor-tab provider icons currently use 14x14 SVG assets; separate dark/size variants are out of scope for this spec revision.
- Editor-tab popup actions for the selected Agent chat tab must include:
  - `Select in Agent Threads`;
  - `Archive Thread` (enabled only when provider supports archive);
  - `Copy Thread ID`.
- `Select in Agent Threads` from editor-tab popup must ensure thread visibility (`ensureThreadVisible`) before activating Agent Threads tool window.
- `Archive Thread` from editor-tab popup must delegate to the same archive service flow used by Agent Threads tree actions.
- Session entity labels must use `Thread`; `Chat` naming is limited to editor-tab/file surface.
- The shell command used to start chat sessions is provider-specific:
  - Codex: `codex resume <threadId>`
  - Claude: `claude --resume <threadId>`
- Codex fresh-thread opens (project-row `New Thread`) use `codex` without `resume`.

[@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt
[@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt
[@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
[@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt
[@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
[@test] ../chat/testSrc/AgentChatFileEditorProviderTest.kt
[@test] ../chat/testSrc/AgentChatTabSelectionServiceTest.kt

## User Experience
- Single click on a thread row opens the chat editor.
- Single click on a sub-agent row opens a separate chat editor tab for that sub-agent.
- Editor tab name is the thread title; editor icon reflects provider identity with current 14x14 SVG assets.
- Editor tab icon reflects provider identity (Codex/Claude) with fallback to default Agent chat icon for unknown providers.
- Editor-tab popup exposes thread lifecycle/navigation actions (`Select in Agent Threads`, `Archive Thread`, `Copy Thread ID`) for selected chat tabs.
- By default, chat editor opens in a dedicated frame.
- Users can disable dedicated-frame mode from Advanced Settings to restore current-project-frame behavior.
- After restart, all previously open chat tabs are restored in their prior project frame context.
- Restored tabs appear immediately; terminal session creation is deferred until the user activates a tab.

## Data & Backend
- Chat terminal sessions start in the source project/worktree working directory.
- Sessions service passes provider-aware session identity and command into the chat open flow.
- Chat file URLs persist only short stable tab identity (`tabKey`).
- Full restore metadata is read from tab metadata files and not encoded in virtual file URL path.

## Error Handling
- If the project path is invalid or project opening fails, do not open a chat editor tab.
- If provider/session identity cannot be resolved, fail safely without crashing the UI.
- If a restored tab metadata file is missing/corrupt/invalid (or has missing path/identity/command), skip and close that tab and show a non-blocking warning notification.
- Restore validation failures must delete the tab metadata file immediately.
- Restore warnings for the same tab/reason must be deduplicated per IDE session to avoid startup notification spam.
- If terminal initialization fails on first tab activation, close the tab and show a warning notification.
- Terminal initialization failures must delete the tab metadata file immediately.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsOpenModeRoutingTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatFileEditorProviderTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatTabSelectionServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.chat.AgentChatEditorServiceTest'`

## Open Questions / Risks
- Future product policy may intentionally limit restart restore to one chat tab; if adopted, this spec should be revised and implemented via explicit tab-closing policy before workspace save.

## References
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-sessions.spec.md`
