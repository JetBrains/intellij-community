---
name: Agent Chat Dedicated Frame
description: Requirements for dedicated-frame routing, lifecycle, and project filtering used by Agent Workbench chat opens.
targets:
  - ../sessions/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../chat/src/*.kt
  - ../sessions/testSrc/*.kt
---

# Agent Chat Dedicated Frame

Status: Draft
Date: 2026-03-01

## Summary
Define dedicated-frame mode behavior for Agent chat routing. This spec owns frame policy, frame lifecycle, filtering, and shortcut semantics. Shared command mapping and action contracts are owned by `spec/agent-core-contracts.spec.md`.

## Goals
- Default to one reusable dedicated frame per IDE instance.
- Allow explicit user mode switch between dedicated frame and source-project frame.
- Keep dedicated-frame project hidden from session registry and recent-project UX.
- Keep dedicated-frame policy centralized and independent from welcome-project provider internals.

## Non-goals
- Multiple dedicated frames per project/thread/sub-agent.
- Replacing terminal-backed editor with non-terminal chat UI.
- Defining shared command mapping or popup action contracts.

## Requirements
- Advanced setting key `agent.workbench.chat.open.in.dedicated.frame` exists, defaults to `true`, and is exposed in Advanced Settings.
- Sessions gear menu includes toggle action `AgentWorkbenchSessions.ToggleDedicatedFrame` that updates the same setting.
- Dedicated mode (`true`):
  - Thread/sub-agent click opens chat in the dedicated frame project.
  - If the dedicated frame project is not open, it is opened in a new frame and reused afterwards.
  - Source project is not opened automatically when closed.
  - Project View tool window is suppressed by frame capability and is not initialized for the dedicated frame project.
  - Agent Workbench registers `AgentWorkbenchSessions.ActivateWithProjectShortcut` with `use-shortcut-of="ActivateProjectToolWindow"`.
  - Shortcut routing for Cmd+1 is dedicated-frame-only: the custom action is enabled only for dedicated projects and activates Agent Sessions tool window.
  - Platform action id `ActivateProjectToolWindow` is not redefined by Agent Workbench.
- Current-project mode (`false`):
  - Preserve legacy behavior: open chat in source project frame.
  - If source project is closed, open it first and then open chat.
- Dedicated frame project is hidden from recent projects metadata.
- Dedicated frame project is excluded from Sessions project registry (both open and recent enumerations).
- Chat terminal working directory remains the source project path regardless of frame mode.
- Chat editor persistence/restore behavior is defined by `spec/agent-chat-editor.spec.md` and applies equally in both modes:
  - protocol-backed restore via `agent-chat://2/<tabKey>`,
  - restore of all previously open chat tabs,
  - restore metadata persisted in `<config>/agent-workbench-chat-frame/tabs/*.awchat.json`,
  - lazy terminal initialization on first explicit tab activation.
- Implementation must stay independent from `welcomeScreenProjectProvider` because that provider model is singleton-like across products.
- Chat resume command remains provider-specific in dedicated mode as in current-project mode:
  - Codex: `codex resume <threadId>`
  - Claude: `claude --resume <threadId>`
- New-session action semantics (including Codex `Codex (Full Auto)` mapping) are defined in `spec/actions/new-thread.spec.md` and do not change between dedicated/current-project modes.

- Sessions gear menu must expose `AgentWorkbenchSessions.ToggleDedicatedFrame` and update the same advanced setting.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions plugin must expose `AgentWorkbenchSessions.OpenDedicatedFrame` so users can explicitly reopen/focus dedicated frame.
  [@test] ../sessions/testSrc/AgentSessionsOpenDedicatedFrameActionTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- In dedicated mode (`true`):
  - thread/sub-agent open requests must route to dedicated frame project,
  - dedicated frame project must be created/opened on demand and then reused,
  - closed source project must not be auto-opened.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Dedicated frame project must suppress Project View capability and must not initialize Project View for dedicated projects.
  [@test] ../sessions/testSrc/AgentWorkbenchToolWindowLayoutProfileProviderTest.kt

- Plugin descriptor must register `AgentWorkbenchSessions.ActivateWithProjectShortcut` with `use-shortcut-of="ActivateProjectToolWindow"`.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Cmd+1 shortcut routing via `AgentWorkbenchSessions.ActivateWithProjectShortcut` must be dedicated-project-only; platform action id `ActivateProjectToolWindow` must not be redefined.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- In current-project mode (`false`), chat opens must use source project frame; closed source projects must open first.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Dedicated-frame project must be hidden from recent-project metadata.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Dedicated-frame project must be excluded from Sessions project registry for both open and recent project enumeration.
  [@test] ../sessions/testSrc/AgentSessionProjectCatalogTest.kt

- Dedicated-frame project switching and header navigation affordances must follow `spec/agent-dedicated-frame-project-switching.spec.md`.
  [@test] ../sessions/testSrc/AgentWorkbenchProjectFrameCapabilitiesProviderTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Dedicated-frame terminal hyperlink routing to source projects must follow `spec/agent-dedicated-frame-terminal-hyperlink-routing.spec.md`.
  [@test] ../sessions/testSrc/AgentWorkbenchTerminalHyperlinkNavigationInterceptorTest.kt
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt

- Chat terminal `cwd` must remain source project path regardless of frame mode.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Chat persistence and restore behavior must follow `spec/agent-chat-editor.spec.md` in both modes.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

## Architecture Decision — Welcome Screen Independence
- Implementation must stay independent from `welcomeScreenProjectProvider` singleton model. Dedicated-frame lifecycle is self-contained and must not couple to welcome-screen project provider internals.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

## User Experience
- Default click behavior opens chat in dedicated frame.
- Toggling dedicated-frame setting affects subsequent opens immediately.
- Dedicated frame receives focus when chat opens there.
- Dedicated frame can be reopened from explicit action entry points.
- Sessions tree never shows dedicated frame as a project node.
- In Rider with per-app toolwindows enabled, dedicated-frame toolwindow layout customization remains independent from normal Rider project frames.

## Data & Backend
- Mode state is stored via Advanced Settings.
- Dedicated-frame project path/lifecycle is managed by `AgentWorkbenchDedicatedFrameProjectManager`.
- Frame mode does not alter canonical identity or command contracts.

## Error Handling
- If dedicated frame cannot be prepared/opened, routing must fail safely and log warning.
- Invalid source project path must not crash routing.
- Dedicated-mode Project View API access may fail in unsupported contexts; this is accepted behavior.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsOpenModeRoutingTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwingNewSessionActionsTest'`

## Open Questions / Risks
- Dedicated-frame storage-path policy may later align with broader welcome-project storage conventions.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/agent-dedicated-frame-project-switching.spec.md`
- `spec/agent-dedicated-frame-terminal-hyperlink-routing.spec.md`
- `spec/actions/new-thread.spec.md`
- `community/platform/platform-impl/src/com/intellij/openapi/wm/ex/WelcomeScreenProjectProvider.kt`
