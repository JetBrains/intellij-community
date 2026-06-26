---
name: Codex IDE Theme Support
description: Requirements for Agent Workbench-managed Codex theme materialization.
targets:
  - ../../lib-agent/providers/codex/sessions/src/**/*.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/**/*.kt
  - agent-sessions-codex-theme.spec.md
---

# Codex IDE Theme Support

Status: Draft
Date: 2026-06-26

## Summary
Agent Workbench provides a generated Codex TextMate theme derived from the active IDE/editor colors. The generated theme is materialized in the IDE system directory and applied to Codex launches through `tui.theme`.

## Requirements
- Agent Workbench must generate a valid `.tmTheme` from current IDE/editor colors and materialize it under the IDE system directory, not under Codex home.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeSupportTest.kt

- The generated theme must include base foreground/background/selection colors, common syntax scopes, Codex status-line scopes, and diff backgrounds for `markup.inserted` and `markup.deleted`.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeSupportTest.kt

- Codex launches must pass `-c tui.theme="<absolute generated theme path without .tmTheme>"`. Agent Workbench must not set `CODEX_HOME` and must not write generated files to `CODEX_HOME/themes`.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeSupportTest.kt

- Theme materialization failures must not block Codex launch; launches continue without the theme config.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeSupportTest.kt

- Existing running Codex sessions are not required to live-refresh. New and resumed launches should use the current generated theme.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

## Notes
This implementation relies on current Codex path resolution accepting absolute `tui.theme` values. If Codex later rejects absolute theme names, Agent Workbench should switch to an upstream-supported `tui.theme_path` or `tui.theme_dir` API.
