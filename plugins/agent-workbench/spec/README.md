# Agent Workbench Specs

Agent Workbench specs follow `community/.ai/spec/SPEC_GUIDE.md`. Each spec must keep frontmatter `targets` and adjacent `[@test]` links current with the implementation it describes.

For an overview of Agent Workbench content modules and dependency layers, see [Module Architecture](../README.md#module-architecture).

## Directory Layout

- `core/` - shared provider contracts, state storage, and telemetry.
- `sessions/` - Agent Threads session discovery, rendering, refresh, providers, and session-local UX.
- `frame/` - dedicated-frame behavior, project switching, hyperlink routing, and main-toolbar activity.
- `chat/` - chat editor behavior, semantic navigation, and Codex IDE context.
- `actions/` - user actions that launch or modify Agent Workbench flows.
- `prompt-context/` - prompt context collection, rendering, and picker behavior.
- `launch/` - project-local launch config.
- `vcs/` - VCS integration specs.
- `review/` - AI review specs.

## Path Conventions

- Paths in `targets` and `[@test]` are relative to the spec file that contains them.
- Specs one level below `spec/` use `../../<module>/...` for Agent Workbench modules.
- Cross-spec links should be relative Markdown links, for example `../sessions/agent-sessions.spec.md`.
- Code backlinks use repository-root paths, for example `// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions.spec.md`.
