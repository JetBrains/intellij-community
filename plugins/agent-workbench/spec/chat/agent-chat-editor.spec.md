---
name: Agent Chat Editor
description: Requirements for Agent Chat editor tab identity, restore, lifecycle, terminal integration, and pending context send behavior.
targets:
  - ../../chat/src/**/*.kt
  - ../../chat/resources/**/*.xml
  - ../../chat/resources/messages/AgentChatBundle.properties
  - ../../chat/testSrc/*.kt
  - ../../sessions/src/service/*.kt
  - ../../sessions/testSrc/*.kt
---

# Agent Chat Editor

Status: Draft
Date: 2026-05-09

## Summary
Agent Chat tabs are protocol-backed editor tabs around terminal-backed agent sessions. This spec owns tab identity, restore, terminal lifetime, editor presentation, file drop, and pending-context send behavior.

## Requirements
- Agent Chat registers an async file editor provider, virtual file system key `agent-chat`, and editor-tab title/icon providers.
  [@test] ../../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Chat virtual files use v2 URLs `agent-chat://2/<tabKey>`, where `tabKey` is a lowercase base36 SHA-256 digest over full tab identity. The URL carries tab identity only; editor-provider state stores the restore metadata for open tabs. The legacy app-level tab cache may be read for old sessions, but is not the canonical restore store.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Opening a chat must reuse an existing tab for the same canonical thread identity and sub-agent id. Already-open top-level concrete tabs are addressable by normalized path, provider, and thread id for ordered post-start dispatch.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt

- Restore must restore all previously open Agent Chat tabs from editor state, tolerate unresolved URL materialization before provider state is applied, reconstruct pending new-session startup from provider/mode metadata instead of persisted shell commands, restore the stored resume launch mode and generation settings for concrete tabs, prune stale/invalid legacy tab state, and use persisted title/activity only as bootstrap fallback until live shared thread presentation is available. Restored terminal commands must be rebuilt through the shared session launch planner so provider-specific generation settings, model catalogs, launch augmenters, and launch contributors are applied consistently for new and resumed tabs.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorProviderTest.kt

- Open pending Agent Chat editor tabs are tracked by the pending-tabs state/lifecycle service. Closing a pending tab removes its matching ephemeral projection.

- Terminal content initialization is lazy: the lightweight editor shell appears immediately, and the terminal starts only after explicit tab selection/focus.
  [@test] ../../chat/testSrc/AgentChatTabSelectionServiceTest.kt

- The live terminal belongs to the logical open chat tab (`tabKey`), not a transient `FileEditor` instance. Reordering, splitter movement, detach/reattach, or editor recreation must not restart the live terminal.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../chat/testSrc/AgentChatScopedTerminalRefreshControllerTest.kt

- Agent Chat files are unsplittable. Closing the final chat file releases terminal resources; disposing a transient editor instance releases only editor-local controllers.
  [@test] ../../chat/testSrc/AgentChatFileEditorProviderTest.kt
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt

- Dropping files onto the terminal area pastes ordered file paths without executing; dropping files onto the pending context panel creates a pending `Files` context chip.
  [@test] ../../chat/testSrc/AgentChatFileDropSupportTest.kt

- `Add to Agent Context` into an open top-level concrete chat adds de-duplicated pending context and does not mutate terminal input until the user submits.
  [@test] ../../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt

- Plain Enter with pending context sends the current terminal prompt plus one `### IDE Context` envelope, then clears pending context only after the terminal accepts the send. Context over the soft cap requires explicit send-full, auto-trim, or cancel.
  [@test] ../../chat/testSrc/AgentChatOpenTopLevelDispatchTest.kt

- Concrete tab title/icon presentation resolves live data from shared thread presentation; sub-agent tabs keep their own stored title while inheriting parent activity.
  [@test] ../../chat/testSrc/AgentChatFileEditorProviderTest.kt
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Pending-thread and concrete `/new` rebinding follow `../actions/codex-thread-rebinding.spec.md`.

- Initial prompt dispatch is readiness-gated and follows shared command/dispatch contracts in `../core/agent-core-contracts.spec.md`.
  [@test] ../../chat/testSrc/AgentChatFileEditorLifecycleTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Agent Chat scoped refresh from active session file changes remains enabled for providers that emit scoped refresh signals.
  [@test] ../../chat/testSrc/AgentChatScopedTerminalRefreshControllerTest.kt

- Archive of a matching top-level thread closes the open chat tab and removes matching legacy tab state when present.
  [@test] ../../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Restore validation and terminal initialization failures close the tab, forget matching legacy tab state when present, and emit de-duplicated warning notifications. Command lookup failures include the attempted command and startup `PATH` snapshot when available from the terminal-start failure.
  [@test] ../../chat/testSrc/AgentChatRestoreNotificationServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test "com.intellij.agent.workbench.chat.AgentChat*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionArchiveServiceIntegrationTest`

## References
- `../core/agent-core-contracts.spec.md`
- `../actions/codex-thread-rebinding.spec.md`
- `../actions/add-to-agent-context.spec.md`
- `../sessions/agent-sessions.spec.md`
