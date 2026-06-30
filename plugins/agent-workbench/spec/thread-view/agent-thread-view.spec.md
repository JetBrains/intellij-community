---
name: Agent Thread View Editor
description: Requirements for Agent Thread View editor tab identity, restore, lifecycle, terminal integration, and pending context send behavior.
targets:
  - ../../thread-view/src/**/*.kt
  - ../../thread-view/resources/**/*.xml
  - ../../thread-view/resources/messages/AgentThreadViewBundle.properties
  - ../../thread-view/testSrc/*.kt
  - ../../sessions/src/service/*.kt
  - ../../sessions/testSrc/*.kt
---

# Agent Thread View Editor

Status: Draft
Date: 2026-05-09

## Summary
Agent Thread View tabs are protocol-backed editor tabs around terminal-backed agent sessions. This spec owns tab identity, restore, terminal lifetime, editor presentation, file drop, and pending-context send behavior.

## Requirements
- Agent Thread View registers an async file editor provider, virtual file system key `agent-thread-view`, and editor-tab title/icon providers.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorProviderTest.kt

- Thread View virtual files use v2 URLs `agent-thread-view://2/<tabKey>`, where `tabKey` is a lowercase base36 SHA-256 digest over full tab identity. The URL carries tab identity only; editor-provider state stores the restore metadata for open tabs.
  [@test] ../../thread-view/testSrc/AgentThreadViewEditorServiceTest.kt

- Opening a thread view must reuse an existing tab for the same canonical thread identity and sub-agent id. Already-open top-level concrete tabs are addressable by normalized path, provider, and thread id for ordered post-start dispatch.
  [@test] ../../thread-view/testSrc/AgentThreadViewEditorServiceTest.kt
  [@test] ../../thread-view/testSrc/AgentThreadViewOpenTopLevelDispatchTest.kt

- New Agent Thread View editor tabs must follow platform editor-tab placement: after the selected tab by default, or at the end when the IDE's `Open new tabs at the end` setting is enabled. Opening, pending-thread rebinding, and concrete-thread rebinding must reuse and update matching existing tabs in place without moving them or passing a custom `FileEditorOpenOptions.index`.
  [@test] ../../thread-view/testSrc/AgentThreadViewEditorServiceTest.kt

- Restore must restore all previously open Agent Thread Views from editor state, tolerate unresolved URL materialization before provider state is applied, reconstruct pending new-session startup from provider/mode metadata instead of persisted shell commands, restore the stored resume launch mode and generation settings for concrete tabs, and use persisted title/activity only as bootstrap fallback until live shared thread presentation is available. Restored terminal commands must be rebuilt through the shared session launch planner so provider-specific generation settings, model catalogs, launch augmenters, and launch contributors are applied consistently for new and resumed tabs.
  [@test] ../../thread-view/testSrc/AgentThreadViewEditorServiceTest.kt
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorProviderTest.kt

- Restore of a concrete tab whose provider now reports the backing thread or sub-agent as archived must close and forget that persisted tab without starting a terminal or showing a restore-failure warning. Explicitly opening an archived row remains owned by the sessions archived view and must unarchive before opening.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorLifecycleTest.kt

- Open pending Agent Thread View editor tabs are tracked by the pending-tabs state/lifecycle service. Closing a pending tab removes its matching ephemeral projection.

- Terminal content initialization is lazy: the lightweight editor shell appears immediately, and the terminal starts only after explicit tab selection/focus.
  [@test] ../../thread-view/testSrc/AgentThreadViewTabSelectionServiceTest.kt

- The default deferred-start waiting shell is centered, uses provider-neutral regular-weight progress copy for generic new-thread starts, renders optional detail text as secondary copy, and delays the spinner briefly to avoid flicker. Custom deferred-start content continues to replace the default shell until the tab is ready to start.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorLifecycleTest.kt

- The live terminal belongs to the logical open Thread View (`tabKey`), not a transient `FileEditor` instance. Reordering, splitter movement, detach/reattach, or editor recreation must not restart the live terminal.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorLifecycleTest.kt
  [@test] ../../thread-view/testSrc/AgentThreadViewScopedTerminalRefreshControllerTest.kt

- Agent Thread View files are unsplittable. Closing the final thread view file releases terminal resources; disposing a transient editor instance releases only editor-local controllers.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorProviderTest.kt
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorLifecycleTest.kt

- Dropping files onto the terminal area pastes ordered file paths without executing; dropping files onto the pending context panel creates a pending `Files` context chip.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileDropSupportTest.kt

- `Add to Agent Context` into an open top-level concrete thread view adds de-duplicated pending context and does not mutate terminal input until the user submits.
  [@test] ../../thread-view/testSrc/AgentThreadViewOpenTopLevelDispatchTest.kt

- Plain Enter with pending context sends the current terminal prompt plus one `### IDE Context` envelope, then clears pending context only after the terminal accepts the send. Context over the soft cap requires explicit send-full, auto-trim, or cancel.
  [@test] ../../thread-view/testSrc/AgentThreadViewOpenTopLevelDispatchTest.kt

- Concrete tab title/icon presentation resolves live data from shared thread presentation. Tab icon badges are chrome signals and
  use shared chrome activity, while tab row/session activity remains separate. Sub-agent tabs keep their own stored title while inheriting
  parent chrome activity.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorProviderTest.kt
  [@test] ../../thread-view/testSrc/AgentThreadViewEditorServiceTest.kt

- Pending-thread and concrete `/new` rebinding follow `../actions/codex-thread-rebinding.spec.md`.

- Initial prompt delivery is readiness-gated for terminal dispatch and follows shared live prompt-record/startup-command contracts in `../core/agent-core-contracts.spec.md`. Prompt text, tokens, delivery state, and terminal dispatch queues are live-session-only metadata and must not be persisted in editor state or tab cache restore data.
  [@test] ../../thread-view/testSrc/AgentThreadViewFileEditorLifecycleTest.kt
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Agent Thread View scoped refresh from active session file changes remains enabled for providers that emit scoped refresh signals.
  [@test] ../../thread-view/testSrc/AgentThreadViewScopedTerminalRefreshControllerTest.kt

- Archive of a matching top-level thread closes the open Agent Thread View and removes matching persisted state when present.
  [@test] ../../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt
  [@test] ../../thread-view/testSrc/AgentThreadViewEditorServiceTest.kt

- Restore validation and terminal initialization failures close the Agent Thread View, forget matching persisted state when present, and emit de-duplicated warning notifications. Command lookup failures include the attempted command and startup `PATH` snapshot when available from the terminal-start failure.
  [@test] ../../thread-view/testSrc/AgentThreadViewRestoreNotificationServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.thread.view.tests --test "com.intellij.agent.workbench.thread.view.AgentThreadView*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionArchiveServiceIntegrationTest`

## References
- `../core/agent-core-contracts.spec.md`
- `../actions/codex-thread-rebinding.spec.md`
- `../actions/add-to-agent-context.spec.md`
- `../sessions/agent-sessions.spec.md`
