---
name: Agent Chat Terminal API Spike
description: Exploratory evaluation of low-level terminal API alternatives for Agent Chat editor tabs.
targets:
  - ../chat/src/AgentChatFileEditor.kt
  - ../../terminal/frontend/src/com/intellij/terminal/frontend/toolwindow/*.kt
  - ../../terminal/src/org/jetbrains/plugins/terminal/*.kt
---

# Agent Chat Terminal API Spike

Status: Informational
Date: 2026-02-28

## Question
Can Agent Chat switch from `TerminalToolWindowTabsManager` to a lower-level terminal builder and avoid toolwindow content abstractions entirely?

## Options Evaluated
- Keep `TerminalToolWindowTabsManager` with detached tab creation (`shouldAddToToolWindow(false)`).
- Use classic/deprecated `TerminalView` / `createLocalShellWidget` APIs.
- Use internal reworked terminal implementation classes directly (for example `TerminalViewImpl` and related internals).

## Findings
- The classic `TerminalView` and `createLocalShellWidget` path is deprecated and marked for removal, and documentation in `TerminalToolWindowManager` recommends migrating to `TerminalToolWindowTabsManager`.
- Reworked low-level classes are internal (`@ApiStatus.Internal`) and are not stable integration points for plugin code.
- Public reworked APIs still model terminal tabs via `TerminalToolWindowTab` and `Content`; there is no stable public API to create/manage a reworked terminal session that bypasses this lifecycle model.
- Detached tabs created with `shouldAddToToolWindow(false)` are supported by the API, but Agent Chat must handle disposal correctly when content is detached.

## Recommendation
- Keep Agent Chat on `TerminalToolWindowTabsManager`.
- Do not migrate to classic/deprecated or internal low-level terminal APIs.
- Maintain explicit disposal handling for detached tab content in Agent Chat lifecycle code.

## Revisit Trigger
- Re-evaluate only if terminal platform exposes a stable public API for editor-scoped reworked terminal lifecycle without content-manager coupling.
