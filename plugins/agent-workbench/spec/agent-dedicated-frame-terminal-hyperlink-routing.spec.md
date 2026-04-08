---
name: Dedicated Frame Terminal Hyperlink Routing
description: Requirements for opening terminal file hyperlinks in source projects when clicked from Agent Workbench dedicated frame.
targets:
  - ../chat/src/AgentChatTerminalTabSupport.kt
  - ../chat/testSrc/AgentChatTerminalTabBuilderConfigurationTest.kt
  - ../../terminal/frontend/src/com/intellij/terminal/frontend/toolwindow/TerminalToolWindowTabBuilder.kt
  - ../../terminal/frontend/src/com/intellij/terminal/frontend/toolwindow/impl/TerminalToolWindowTabsManagerImpl.kt
  - ../../terminal/frontend/src/com/intellij/terminal/frontend/view/impl/TerminalViewImpl.kt
  - ../../terminal/src/org/jetbrains/plugins/terminal/hyperlinks/TerminalAsyncHyperlinkInfo.kt
  - ../../terminal/src/org/jetbrains/plugins/terminal/hyperlinks/TerminalCrossProjectFileHyperlinkNavigator.kt
  - ../../terminal/resources/META-INF/terminal.xml
  - ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt
  - ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/TerminalCrossProjectFileHyperlinkNavigatorTest.kt
---

# Dedicated Frame Terminal Hyperlink Routing

Status: Draft
Date: 2026-04-05

## Summary
Define how terminal file hyperlinks behave when clicked from Agent Workbench dedicated frame.

This spec owns:
- terminal tab metadata for alternate source-project navigation,
- terminal-owned cross-project execution of `FileHyperlinkInfo`,
- dedicated-frame integration that sets the source project path when the tab is created,
- fallback behavior for directories and non-openable files.

## Goals
- Open terminal file hyperlinks in the source project associated with the Agent Workbench chat tab.
- Keep terminal behavior unchanged for tabs that do not declare alternate source-navigation metadata.
- Keep terminal hyperlink models storing the original hyperlinks; no wrapper hyperlink types are introduced for dedicated frame routing.

## Non-goals
- Intercepting non-file terminal hyperlinks.
- Adding a new platform-wide hyperlink API for cross-project navigation.
- Persisting alternate source-navigation metadata across terminal restoration in this pass.

## Requirements
- Terminal tool window tabs may declare an optional `sourceNavigationProjectPath`.
  [@test] ../chat/testSrc/AgentChatTerminalTabBuilderConfigurationTest.kt

- Terminal view must stamp that path onto the live editors so click handling can resolve it from the active tab context.

- Terminal hyperlink click handling must keep stored hyperlinks unchanged and must consult alternate source-navigation metadata only at click time.
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt

- If the clicked hyperlink is not `FileHyperlinkInfo`, or the tab has no alternate source-navigation path, terminal must keep its normal navigation behavior.
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/BackendTerminalHyperlinkHighlighterTest.kt

- If the tab declares an alternate source-navigation path and the clicked hyperlink is `FileHyperlinkInfo`, terminal must open or reuse that project and delegate navigation through the standard file hyperlink flow in that project.
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/TerminalCrossProjectFileHyperlinkNavigatorTest.kt

- Cross-project file navigation must preserve exact offsets when the descriptor already has an offset, and otherwise preserve line and column when rebuilding the target-project descriptor.
  [@test] ../../terminal/tests/src/com/intellij/terminal/tests/reworked/backend/TerminalCrossProjectFileHyperlinkNavigatorTest.kt

- For directory hyperlinks, terminal must navigate to project view for in-project directories and reveal the directory in the system file manager for external directories.
  This behavior is provided by the existing file hyperlink implementation once the target-project descriptor is rebuilt.

- For file hyperlinks without editor providers, terminal must fall back to browser navigation.
  This behavior is provided by the existing file hyperlink implementation once the target-project descriptor is rebuilt.

- Agent Workbench must contribute only the source-navigation project path when creating the detached chat terminal tab. It must not register a terminal hyperlink extension for this behavior.
  [@test] ../chat/testSrc/AgentChatTerminalTabBuilderConfigurationTest.kt

## User Experience
- Clicking a file-path hyperlink inside a dedicated-frame chat terminal opens the file in the source project.
- If the source project is closed, it opens before navigation.
- If the hyperlink targets an external directory, the system file manager opens that directory.
- In ordinary terminal tabs, hyperlink behavior remains unchanged.

## Data & Backend
- Agent Workbench passes the normalized source project path into the terminal tab builder.
- Terminal resolves alternate source-navigation metadata from editor user data attached to the tab’s live editors.
- Terminal rebuilds an `OpenFileDescriptor` for the target project and delegates to the standard file hyperlink navigation path there.

## Error Handling
- Invalid or missing source project paths must not crash hyperlink handling.
- If source project opening fails or the file hyperlink descriptor is invalid, terminal falls back to its default hyperlink behavior.
- Browser and external-directory fallbacks stay owned by the existing file hyperlink implementation.

## Testing / Local Run
- `./tests.cmd --module intellij.terminal.tests --test com.intellij.terminal.tests.reworked.backend.BackendTerminalHyperlinkHighlighterTest`
- `./tests.cmd --module intellij.terminal.tests --test com.intellij.terminal.tests.reworked.backend.TerminalCrossProjectFileHyperlinkNavigatorTest`
- `./tests.cmd --module intellij.agent.workbench.chat.tests --test com.intellij.agent.workbench.chat.AgentChatTerminalTabBuilderConfigurationTest`

## References
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-dedicated-frame-project-switching.spec.md`
