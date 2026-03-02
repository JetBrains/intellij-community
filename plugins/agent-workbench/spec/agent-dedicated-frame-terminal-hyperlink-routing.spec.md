---
name: Dedicated Frame Terminal Hyperlink Routing
description: Requirements for opening terminal file hyperlinks in source projects when clicked from Agent Workbench dedicated frame.
targets:
  - ../sessions/src/service/AgentWorkbenchTerminalHyperlinkNavigationInterceptor.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/intellij.agent.workbench.sessions.iml
  - ../sessions/testSrc/AgentWorkbenchTerminalHyperlinkNavigationInterceptorTest.kt
  - ../../terminal/src/org/jetbrains/plugins/terminal/hyperlinks/TerminalHyperlinkNavigationInterceptor.kt
  - ../../terminal/resources/META-INF/terminal.xml
  - ../../terminal/backend/src/com/intellij/terminal/backend/hyperlinks/BackendTerminalHyperlinkFacade.kt
  - ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt
---

# Dedicated Frame Terminal Hyperlink Routing

Status: Draft
Date: 2026-03-01

## Summary
Define how terminal file hyperlinks behave when clicked from Agent Workbench dedicated frame.

This spec owns:
- terminal hyperlink interception extension point contract,
- dedicated-frame-only routing from clicked hyperlink to source project,
- fallback behavior to terminal default navigation when not handled.

## Goals
- Open terminal file hyperlinks in the source project associated with the selected chat tab when click originates in dedicated frame.
- Keep terminal plugin behavior unchanged for all non-dedicated contexts.
- Keep interception low-level and minimal to avoid reimplementing terminal hyperlink pipeline in sessions plugin.

## Non-goals
- Intercepting non-file terminal hyperlinks.
- Global override of hyperlink behavior outside dedicated-frame context.
- Defining terminal text parsing/highlighting rules.

## Requirements
- Terminal plugin must define dynamic extension point `org.jetbrains.plugins.terminal.hyperlinkNavigationInterceptor` with `TerminalHyperlinkNavigationInterceptor` interface.
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt

- Terminal backend hyperlink click flow must consult interceptors before default `hyperlink.navigate(...)` behavior.
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt

- If any interceptor returns handled (`true`), terminal must skip default hyperlink navigation and still log hyperlink-followed usage event.
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt

- Agent Workbench sessions plugin must register dedicated-frame interceptor in terminal extension namespace.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Agent Workbench interceptor must handle only when all conditions are true:
  - current project is dedicated frame,
  - selected chat tab has non-empty source path,
  - source path is not dedicated-frame project path,
  - hyperlink is `FileHyperlinkInfo` with non-null descriptor.
  [@test] ../sessions/testSrc/AgentWorkbenchTerminalHyperlinkNavigationInterceptorTest.kt

- Agent Workbench interceptor must open or reuse source project by selected source path, focus its window, and navigate using descriptor reconstructed for target project.
  [@test] ../sessions/testSrc/AgentWorkbenchTerminalHyperlinkNavigationInterceptorTest.kt

- If source project cannot be resolved/opened or descriptor is invalid/non-navigable, interceptor must fail safely (`false`) and let terminal fallback run.
  [@test] ../sessions/testSrc/AgentWorkbenchTerminalHyperlinkNavigationInterceptorTest.kt

## User Experience
- Clicking file path hyperlink in dedicated-frame terminal opens file in corresponding source project.
- If source project is closed, it opens and receives focus before navigation.
- In non-dedicated frames, terminal hyperlink behavior remains unchanged.

## Data & Backend
- Source-project resolution uses active chat-tab source path (`AgentChatTabSelectionService.selectedChatTab`).
- Path normalization/parsing uses Agent Workbench path helpers.
- Navigation executes on EDT via `OpenFileHyperlinkInfo` built from project-bound `OpenFileDescriptor`.

## Error Handling
- Interceptor exceptions must be logged and ignored (except cancellation), preserving terminal fallback behavior.
- Invalid source paths must not crash hyperlink handling.
- Missing or invalid descriptors must result in no-op interceptor handling.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.terminal.tests.reworked.backend.BackendTerminalHyperlinkHighlighterTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentWorkbenchTerminalHyperlinkNavigationInterceptorTest'`

## Open Questions / Risks
- Current implementation handles only `FileHyperlinkInfo`; future hyperlink types may require explicit dedicated-frame semantics.

## References
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-dedicated-frame-project-switching.spec.md`
