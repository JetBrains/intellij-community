---
name: Agent Workbench Pi Sessions
description: Requirements for first-class pi.dev session support in Agent Workbench.
targets:
  - ../common/src/session/AgentSessionModels.kt
  - ../pi/sessions/resources/intellij.agent.workbench.pi.sessions.xml
  - ../pi/sessions/resources/pi-extension/agent-workbench-theme.ts
  - ../pi/sessions/src/**/*.kt
  - ../pi/sessions/testSrc/*.kt
  - ../plugin/resources/META-INF/plugin.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
---

# Agent Workbench Pi Sessions

Status: Accepted
Date: 2026-06-05

## Summary
Agent Workbench treats Pi as a first-class terminal-backed provider. Pi sessions are discovered from Pi JSONL session files, can be launched and resumed from Agent Workbench, and support rename/archive state without requiring a Pi-specific backend API.

## Requirements
- Pi must be exposed as `AgentSessionProvider.PI`, registered after Junie and before Terminal, and shown in provider menus with a Pi icon and localized labels.
  [@test] ../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt
  [@test] ../plugin/testSrc/AgentWorkbenchProviderRegistrationTest.kt

- Pi launch support must use the shared Terminal agent resolver for the `pi` executable without registering Pi as a built-in Terminal agent. New Agent Workbench sessions must launch `pi --extension <themeExtension> --session-id <uuid>` with a provider-allocated session id, while resumed sessions must launch `pi --extension <themeExtension> --session <threadId>`. The launch environment must pass `AGENT_WORKBENCH_PI_THEME_STATE=<stateFile>` so the extension can read the current IDE-compatible Pi theme snapshot without modifying Pi settings. If the Agent Workbench-managed Pi theme extension cannot be materialized, launch must continue without `--extension`.
  [@test] ../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt

- Agent Workbench must provide a Pi extension that reads a managed JSON theme snapshot, validates all required Pi foreground/background theme keys, applies a full Pi `Theme`, and watches the managed theme-state file for IDE theme changes. The snapshot must be built from the active IDE look-and-feel, Swing UI defaults, and editor color scheme so Islands Dark, Islands Light, Islands Darcula, High Contrast, and customized user colors are reflected at runtime. Theme state must be refreshed on both IDE look-and-feel and editor color scheme changes. The extension resource is materialized into a managed IDE system-cache directory with a non-JSON sidecar manifest containing a format version and SHA-256 hash.
  [@test] ../pi/sessions/testSrc/PiThemeSupportTest.kt
  [@test] ../pi/sessions/testSrc/PiThemeSnapshotBuilderTest.kt

- Initial prompts must be passed as a positional Pi CLI message argument. Pi must not expose built-in Agent Workbench plan mode or YOLO launch modes.
  [@test] ../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt

- Pi session loading must read JSONL files from the effective Pi session directory: `PI_CODING_AGENT_SESSION_DIR`, then project/global Pi `settings.json` `sessionDir`, then the default `~/.pi/agent/sessions/<encoded-cwd>` directory.
  [@test] ../pi/sessions/testSrc/PiSessionSourceTest.kt

- Listed Pi threads must be filtered by the session header `cwd`, use the header `id`, use the latest `session_info.name` as the title, fall back to the first user message, and sort newest first by latest user/assistant activity timestamp, header timestamp, or file mtime.
  [@test] ../pi/sessions/testSrc/PiSessionSourceTest.kt

- Pi thread activity must be inferred from the persisted JSONL leaf. User, tool-result, custom, and assistant `toolUse` leaves are shown
  as `PROCESSING`; completed assistant leaves use the shared Done/read semantics when observed work completes. Pi does not report
  `NEEDS_INPUT` from session JSONL until an explicit persisted status signal exists.
  [@test] ../pi/sessions/testSrc/PiSessionSourceTest.kt

- Rename must append a Pi-compatible `session_info` entry to the session JSONL file. Archive and unarchive must persist Agent Workbench sidecar state keyed by normalized project path and session id.
  [@test] ../pi/sessions/testSrc/PiSessionSourceTest.kt

## Theme Mapping Notes
- Runtime IDE/editor colors are the preferred source for every Pi theme key. Fallback palettes are allowed only when a corresponding UI default or editor color is unavailable, when the IDE theme cannot be resolved, or when the Pi extension starts without a readable state file.
- Semantic foregrounds come from `Label.successForeground`/`Label.errorForeground`/`Label.warningForeground`; secondary/tertiary text from `Component.infoForeground` (muted) and `Label.disabledForeground` (dim); feedback backgrounds from `Banner.successBackground`/`Banner.errorBackground`/`Banner.aiBackground`.
- Pi renders inside the IDE terminal, so the terminal default colors (console scheme `NORMAL_OUTPUT`/`CONSOLE_BACKGROUND_KEY`, falling back to editor defaults) are the base for default text, alpha blending, and darkness detection. The editor caret-row color backs user messages so they stay distinguishable from the terminal background; the Pi extension delegates theme tokens unknown to the snapshot to the built-in Pi theme for forward compatibility.
- The maintained fallback variants are Islands Dark, Islands Light, Islands Darcula, and High Contrast. Unknown custom themes fall back by darkness while still using the runtime colors that are available.
- When syncing with Pi theme API changes, update the Kotlin snapshot builder, TypeScript snapshot validation, extension fallback snapshot, and tests in the same change. The required key lists in the Pi extension and `PiThemeSnapshotBuilderTest` must stay aligned with Pi `ThemeColor` and `ThemeBg` definitions.
- Any hardcoded or blended color should be treated as a documented fallback, not as the primary mapping. Prefer replacing such values with stable IDE/editor color keys when a suitable source becomes available.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.pi.sessions.tests --test 'com.intellij.agent.workbench.pi.sessions.*Test'`
- `./tests.cmd --module intellij.agent.workbench.plugin.tests --test com.intellij.agent.workbench.plugin.AgentWorkbenchProviderRegistrationTest`

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-terminal-sessions.spec.md`
- Pi local checkout: `/Users/develar/projects/pi-main/packages/coding-agent`
