# Agent Workbench Plugin Local Notes

This file captures development references only. Product/design decisions live in the spec under `spec/`.

- `pluginId`: `com.intellij.agent.workbench`
- Tool window ID: `agent.workbench.sessions`
- Tool window title: "Agent Threads"
- Main module: `intellij.agent.workbench.plugin` (plugin.xml)
- Content module: `intellij.agent.workbench.sessions`
- Spec format (single source): `../../.ai/spec/SPEC_GUIDE.md` (specs live under `spec/`).
- Issue tracker: https://github.com/JetBrains/agent-workbench

## Spec Maintenance

Specs under `spec/` are part of the definition of done for Agent Workbench behavior changes.

- Before finishing any change under `community/plugins/agent-workbench/`, compare the touched files against the frontmatter `targets` in the owning `spec/*.spec.md` files.
- If a touched file is covered by an existing spec and the change affects behavior, contracts, UX, persistence, launch semantics, filtering, refresh logic, or test ownership, update that spec in the same changelist.
- If behavior changes and no existing spec owns it, add a new focused spec under `spec/` using `../../.ai/spec/SPEC_GUIDE.md`.
- If the change is an implementation-only refactor with no contract change, say explicitly in the final summary or review notes that no spec update was needed.
- Keep spec `targets`, `[@test]` links, and cross-references accurate when spec files change.
- Do not treat updated tests as a substitute for updating the owning spec. Specs remain the source of truth for product and behavior decisions.
Common ownership hints for this plugin:
- Launch cwd, resolved project directory, and `.agent-workbench.yaml` augmentation usually belong in `spec/agent-project-launch-config.spec.md`.
- Project catalog, worktree grouping, and new-thread source-target resolution usually belong in `spec/actions/new-thread.spec.md` and `spec/agent-sessions.spec.md`.
- Codex cwd filtering, refresh-hint consumption, and thread rebinding usually belong in `spec/agent-sessions-codex-rollout-source.spec.md`, `spec/agent-sessions-codex-rollout-hints.spec.md`, and `spec/actions/codex-thread-rebinding.spec.md`.

## Running Tests

Run commands from repository root.

Always pass fully-qualified test names (FQN). Simple class names do not match.

- Single test class:
  `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionCliTest`
- Plugin-wired editor/action test:
  `./tests.cmd --module intellij.agent.workbench.plugin.tests --test com.intellij.agent.workbench.chat.AgentChatEditorServiceTest`
- Whole sessions package:
  `./tests.cmd --module intellij.agent.workbench.sessions.tests --test 'com.intellij.agent.workbench.sessions.*'`
- Whole Agent Workbench test suite:
  `./tests.cmd --module intellij.agent.workbench.plugin.tests --test com.intellij.agent.workbench.plugin.AgentWorkbenchAllTestsSuite`

Important: keep the `--test` argument quoted (single quotes) so shells like `zsh` do not expand `*` before `tests.cmd` receives it.

## API Surface and Final Validation

Prefer the narrowest visibility for new Kotlin/Java declarations. Use `private` for implementation details and `internal` for module-local APIs by default. Make declarations public only when there is a concrete cross-module, extension point, serialization, or plugin wiring requirement.

If a declaration must stay public for technical reasons but is not supported public API, annotate it with `@ApiStatus.Internal` from `org.jetbrains.annotations.ApiStatus`. Prefer reducing visibility over adding the annotation when both are possible.

After `lint_files`, run the API check through `tests.cmd`:

`./tests.cmd --module intellij.projectStructureTests --test com.intellij.ideaProjectStructure.api.ApiCheckTest`
