# Agent Workbench Plugin Local Notes

This file captures development references only. Product/design decisions live in the spec under `spec/`.

- `pluginId`: `com.intellij.agent.workbench`
- Tool window ID: `agent.workbench.sessions`
- Tool window title: "Agent Threads"
- Main module: `intellij.agent.workbench.plugin` (plugin.xml)
- Content module: `intellij.agent.workbench.sessions`
- Spec format (single source): `spec-format/SPEC_GUIDE.md` (specs live under `spec/`).
- Issue tracker: https://github.com/JetBrains/agent-workbench

## Running Tests

Run commands from repository root (`/Users/develar/projects/idea-4`).

Always pass fully-qualified test names (FQN). Simple class names do not match.

- Single test class:
  `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionCliTest'`
- Whole sessions package:
  `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.*'`
- Whole Agent Workbench test suite:
  `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.plugin.AgentWorkbenchAllTestsSuite'`

Important: keep the `-Dintellij.build.test.patterns=...` argument quoted (single quotes) so shells like `zsh` do not expand `*` before `tests.cmd` receives it.
