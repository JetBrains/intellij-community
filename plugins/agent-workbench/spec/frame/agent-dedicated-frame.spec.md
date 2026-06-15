---
name: Agent Chat Dedicated Frame
description: Requirements for dedicated-frame routing, lifecycle, and project filtering used by Agent Workbench chat opens.
targets:
  - ../../sessions/src/*.kt
  - ../../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../chat/src/*.kt
  - ../../sessions/testSrc/*.kt
---

# Agent Chat Dedicated Frame

Status: Draft
Date: 2026-03-01

## Summary
Define dedicated-frame mode behavior for Agent chat routing. This spec owns frame policy, frame lifecycle, filtering, and shortcut semantics. Shared command mapping and action contracts are owned by `../core/agent-core-contracts.spec.md`.

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
- Dedicated-frame mode must be stored in Agent Workbench settings and exposed in the Agent Workbench Chat settings group.
  The legacy Advanced Settings key `agent.workbench.chat.open.in.dedicated.frame` must stay registered with default `true` and `visible="false"` as a migration source.
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions gear menu must expose `AgentWorkbenchSessions.ToggleDedicatedFrame` and update the Agent Workbench settings value.
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions plugin must expose `AgentWorkbenchSessions.OpenDedicatedFrame` so users can explicitly reopen/focus dedicated frame.
  [@test] ../../sessions-actions/testSrc/AgentSessionsOpenDedicatedFrameActionTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- In dedicated mode (`true`):
  - thread/sub-agent open requests must route to dedicated frame project,
  - dedicated frame project must be created/opened on demand and then reused,
  - closed source project must not be auto-opened.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Dedicated frame project must suppress Project View capability and must not initialize Project View for dedicated projects.
  [@test] ../../sessions/testSrc/AgentWorkbenchProjectFrameCapabilitiesProviderTest.kt
  [@test] ../../../../platform/platform-impl/testSrc/com/intellij/toolWindow/ProjectFrameToolWindowLayoutServiceTest.kt

- Plugin descriptor must register `AgentWorkbenchSessions.ActivateWithProjectShortcut` with `use-shortcut-of="ActivateProjectToolWindow"`.
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- Cmd+1 shortcut routing via `AgentWorkbenchSessions.ActivateWithProjectShortcut` must be dedicated-project-only; platform action id `ActivateProjectToolWindow` must not be redefined.
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt

- In current-project mode (`false`), chat opens must use source project frame; closed source projects must open first.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Dedicated-frame project must be hidden from recent-project metadata.

- Dedicated-frame project must be excluded from Sessions project registry for both open and recent project enumeration.
  [@test] ../../sessions/testSrc/AgentSessionProjectCatalogTest.kt

- Dedicated-frame project switching and header navigation affordances must follow `agent-dedicated-frame-project-switching.spec.md`.
  [@test] ../../sessions/testSrc/AgentWorkbenchProjectFrameCapabilitiesProviderTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsGearActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Dedicated-frame terminal hyperlink routing to source projects must follow `agent-dedicated-frame-terminal-hyperlink-routing.spec.md`.
  [@test] ../../chat/testSrc/AgentChatTerminalTabBuilderConfigurationTest.kt
  [@test] ../../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/TerminalCrossProjectFileHyperlinkNavigatorTest.kt
  [@test] ../../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/TerminalCrossProjectFileHyperlinkNavigatorTest.kt

- Chat terminal `cwd` must remain source project path regardless of frame mode.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- Chat persistence and restore behavior must follow `../chat/agent-chat-editor.spec.md` in both modes.
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

## Architecture Decision — Welcome Screen Independence
- Implementation must stay independent from `welcomeScreenProjectProvider` singleton model. Dedicated-frame lifecycle is self-contained and must not couple to welcome-screen project provider internals.

## User Experience
- Default click behavior opens chat in dedicated frame.
- Toggling dedicated-frame setting affects subsequent opens immediately.
- Dedicated frame receives focus when chat opens there.
- Dedicated frame can be reopened from explicit action entry points.
- Sessions tree never shows dedicated frame as a project node.
- In Rider with per-app toolwindows enabled, dedicated-frame toolwindow layout customization remains independent from normal Rider project frames.

## Data & Backend
- Mode state is stored via `AgentWorkbenchSettings` only when it differs from the default; the hidden legacy Advanced Settings key is read only to migrate previous user choices.
- Dedicated-frame project path/lifecycle is managed by `AgentWorkbenchDedicatedFrameProjectManager`.
- Frame mode does not alter canonical identity or command contracts.

## Error Handling
- If dedicated frame cannot be prepared/opened, routing must fail safely and log warning.
- Invalid source project path must not crash routing.
- Dedicated-mode Project View API access may fail in unsupported contexts; this is accepted behavior.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreePopupActionsTest`

## Open Questions / Risks
- Dedicated-frame storage-path policy may later align with broader welcome-project storage conventions.

## References
- `../core/agent-core-contracts.spec.md`
- `../chat/agent-chat-editor.spec.md`
- `../sessions/agent-sessions.spec.md`
- `agent-dedicated-frame-project-switching.spec.md`
- `agent-dedicated-frame-terminal-hyperlink-routing.spec.md`
- `../actions/new-thread.spec.md`
- `community/platform/platform-impl/src/com/intellij/openapi/wm/ex/WelcomeScreenProjectProvider.kt`
