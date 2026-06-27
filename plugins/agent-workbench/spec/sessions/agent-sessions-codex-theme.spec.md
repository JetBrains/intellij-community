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

- The generated theme must cover Codex TUI surfaces that are actually consumed from `tui.theme`: syntax foreground scopes, explicit foreground-scope lookups used by Codex UI accents, and diff background scopes. At minimum this includes `comment`, `keyword`, `keyword.control`, `storage.type`, `storage.modifier`, `entity.name.function`, `entity.name.tag`, `variable`, `string`, `constant`, `constant.numeric`, `constant.language`, `constant.other`, `entity.name.type`, `support.type`, `keyword.operator`, `punctuation`, `markup.underline.link`, `markup.heading`, `entity.name.section`, `markup.inserted`, `diff.inserted`, `markup.deleted`, and `diff.deleted`.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeSupportTest.kt

- Agent Workbench must not treat the generated Codex theme as a full TUI skin. Codex currently consumes theme foregrounds and diff-scope backgrounds, but ordinary syntax-span backgrounds, root background, selection, italic, underline, and unrelated hardcoded TUI chrome are not expected to be styled by the generated `.tmTheme`.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeRealTuiIntegrationTest.kt

- Codex launches must pass `-c tui.theme="<absolute generated theme path without .tmTheme>"`. Agent Workbench must not set `CODEX_HOME` and must not write generated files to `CODEX_HOME/themes`.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeSupportTest.kt

- Theme materialization failures must not block Codex launch; launches continue without the theme config.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeSupportTest.kt

- Existing running Codex sessions are not required to live-refresh. New and resumed launches should use the current generated theme.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexAgentSessionProviderDescriptorTest.kt

- Real TUI verification must follow the production launch shape: start the Codex app server, create/materialize the thread, resume the TUI through `codex resume --remote`, then start the turn through the app server. Theme assertions must use distinctive generated RGB escape sequences, not visual similarity; Codex has hardcoded diff fallback backgrounds that can make a diff look themed even when the generated theme is not driving it.
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexThemeRealTuiIntegrationTest.kt

## Notes
This implementation relies on current Codex path resolution accepting absolute `tui.theme` values. If Codex later rejects absolute theme names, Agent Workbench should switch to an upstream-supported `tui.theme_path` or `tui.theme_dir` API.
