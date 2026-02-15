---
name: Agent Chat Dedicated Frame
description: Requirements for the dedicated AI-chat frame used by Agent Workbench chat routing.
targets:
  - ../sessions/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../chat/src/*.kt
  - ../sessions/testSrc/*.kt
---

# Agent Chat Dedicated Frame

Status: Draft
Date: 2026-02-13

## Summary
Define dedicated-frame behavior for Agent chat opening. By default, chat opens in a dedicated frame backed by a hidden internal project. Users can switch to current-project-frame mode via Advanced Settings and a Sessions gear toggle.

## Rationale
- A dedicated frame supports AI-task orchestration separately from regular project editing.
- It avoids forcing source projects to open when users only need chat context.
- It keeps chat frame policy centralized instead of duplicating logic across Sessions and Chat specs.

## Goals
- Default chat routing to a single reusable dedicated frame per IDE instance.
- User-configurable mode switching between dedicated frame and current project frame.
- Stable dedicated-frame lifecycle with predictable reuse and focus behavior.
- Dedicated frame project must not appear in the Sessions tree.

## Non-goals
- Implementing `welcomeScreenProjectProvider` integration.
- Creating multiple dedicated frames per project/thread/sub-agent.
- Replacing terminal-backed chat editor with Compose app-server UI.

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
- Implementation must stay independent from `welcomeScreenProjectProvider` because that provider model is singleton-like across products.
- Chat resume command remains provider-specific in dedicated mode as in current-project mode:
  - Codex: `codex resume <threadId>`
  - Claude: `claude --resume <threadId>`
- New-session action semantics (including Codex `Codex (Full Auto)` mapping) are defined in `spec/actions/new-thread.spec.md` and do not change between dedicated/current-project modes.

[@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt
[@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt
[@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

## User Experience
- Default click on thread opens dedicated chat frame.
- Toggling `Open Chat in Dedicated Frame` immediately affects subsequent opens.
- Dedicated frame window receives focus after opening chat.
- Sessions list never shows the dedicated frame as a project node.

## Data & Backend
- Mode state is stored via Advanced Settings.
- Dedicated frame project path is managed by `AgentWorkbenchDedicatedFrameProjectManager`.
- Chat command remains provider-specific with source-project `cwd`.

## Error Handling
- If dedicated frame project path cannot be prepared/opened, log warning and do not open chat tab.
- Invalid source project path should not crash routing logic.
- Project View API calls in dedicated frame context are unsupported; `ProjectView` consumers may fail with NPE and this is accepted for dedicated mode.

## Testing / Local Run
- Verify gear actions include toggle + refresh + open.
- Verify toggle flips advanced setting value.
- Verify dedicated mode opens/reuses dedicated frame project.
- Verify current-project mode preserves legacy flow.
- Verify dedicated frame project is filtered from Sessions list.
- Verify plugin descriptor provides `AgentWorkbenchSessions.ActivateWithProjectShortcut` with `use-shortcut-of="ActivateProjectToolWindow"` and does not redefine `ActivateProjectToolWindow`.
- Run tests using:
  - `./tests.cmd -Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest`
  - `./tests.cmd -Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest`

## Open Questions / Risks
- Dedicated frame project path base policy can be revised later to align with welcome-project storage conventions.
- Additional service-level tests for dedicated routing behavior may be needed.

## References
- `spec/agent-chat-editor.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/actions/new-thread.spec.md`
- `community/platform/platform-impl/src/com/intellij/openapi/wm/ex/WelcomeScreenProjectProvider.kt`
