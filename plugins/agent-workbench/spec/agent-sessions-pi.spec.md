---
name: Agent Workbench Pi Sessions
description: Requirements for first-class pi.dev session support in Agent Workbench.
targets:
  - ../common/src/session/AgentSessionModels.kt
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

- Pi launch support must use the shared Terminal agent resolver for the `pi` executable without registering Pi as a built-in Terminal agent. New Agent Workbench sessions must launch `pi --extension <themeExtension> --session-id <uuid>` with a provider-allocated session id, while resumed sessions must launch `pi --extension <themeExtension> --session <threadId>`. The launch environment must pass `AGENT_WORKBENCH_PI_THEME_STATE=<stateFile>` so the extension can apply the current IDE-compatible Pi theme without modifying Pi settings. If the Agent Workbench-managed Pi theme extension cannot be materialized, launch must continue without `--extension`.
  [@test] ../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt

- Agent Workbench must provide a Pi extension that lazily builds IntelliJ-compatible light/dark Pi `Theme` instances, applies only the currently requested theme, and watches the managed theme-state file for IDE theme changes. The extension resource is materialized into a managed IDE system-cache directory with a non-JSON sidecar manifest containing a format version and SHA-256 hash.
  [@test] ../pi/sessions/testSrc/PiThemeSupportTest.kt

- Initial prompts must be passed as a positional Pi CLI message argument. Pi must not expose built-in Agent Workbench plan mode or YOLO launch modes.
  [@test] ../pi/sessions/testSrc/PiAgentSessionProviderDescriptorTest.kt

- Pi session loading must read JSONL files from the effective Pi session directory: `PI_CODING_AGENT_SESSION_DIR`, then project/global Pi `settings.json` `sessionDir`, then the default `~/.pi/agent/sessions/<encoded-cwd>` directory.
  [@test] ../pi/sessions/testSrc/PiSessionSourceTest.kt

- Listed Pi threads must be filtered by the session header `cwd`, use the header `id`, use the latest `session_info.name` as the title, fall back to the first user message, and sort newest first by latest user/assistant activity timestamp, header timestamp, or file mtime.
  [@test] ../pi/sessions/testSrc/PiSessionSourceTest.kt

- Rename must append a Pi-compatible `session_info` entry to the session JSONL file. Archive and unarchive must persist Agent Workbench sidecar state keyed by normalized project path and session id.
  [@test] ../pi/sessions/testSrc/PiSessionSourceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.pi.sessions.tests --test 'com.intellij.agent.workbench.pi.sessions.*Test'`
- `./tests.cmd --module intellij.agent.workbench.plugin.tests --test com.intellij.agent.workbench.plugin.AgentWorkbenchProviderRegistrationTest`

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-terminal-sessions.spec.md`
- Pi local checkout: `/Users/develar/projects/pi-main/packages/coding-agent`
