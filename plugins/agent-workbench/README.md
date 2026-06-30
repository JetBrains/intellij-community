# Agent Workbench Plugin

## Vision: AI-First Workflow UX

The Agent Workbench plugin reimagines the IDE experience around AI-assisted development. Rather than treating AI as an add-on feature, the plugin creates a seamless workflow where developers can:

- **Start threads naturally** - Begin agent tasks from any context in the IDE
- **Maintain persistent threads** - Keep thread history organized and accessible across sessions
- **Navigate AI interactions** - Browse, search, and resume previous threads efficiently
- **Integrate with development flow** - Connect AI assistance directly to code navigation, editing, and debugging

The goal is to make AI assistance feel like a native part of the development environment, reducing context switching and keeping developers in flow.

## Global Prompt Palette

Use `Cmd+\` (macOS) or `Ctrl+\` (Windows/Linux) to open a centered prompt palette from anywhere in the IDE.

The palette:

- Defaults to Codex (provider-extensible).
- Captures invocation context (selection/caret snippet, file, symbol, project).
- Falls back to last selected editor context when invoked outside editors.
- Uses invocation-derived context chips; add extra details directly in prompt text.
- Sends the composed first prompt into a newly opened Agent Thread View.

## Architecture

The plugin provides two complementary views for working with AI-assisted development:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              IntelliJ IDEA                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────────────────────┐   ┌─────────────────────────────────┐ │
│   │      PROJECT FRAME              │   │       AGENT THREAD VIEW          │ │
│   │  (traditional development)      │   │   (task orchestration)          │ │
│   │                                 │   │                                 │ │
│   │  ┌────────┐ ┌────────────────┐  │   │  ┌────────┐ ┌────────────────┐  │ │
│   │  │ Agent  │ │                │  │   │  │ Agent  │ │                │  │ │
│   │  │Sessions│ │     Editor     │  │◄─►│  │Sessions│ │ Agent Thread  │  │ │
│   │  │  Tool  │ │                │  │   │  │  Tool  │ │      View      │  │ │
│   │  │ Window │ │                │  │   │  │ Window │ │  • Status      │  │ │
│   │  │        │ │  • Navigate    │  │   │  │        │ │  • Input       │  │ │
│   │  │Projects│ │  • Edit        │  │   │  │Projects│ │  • History     │  │ │
│   │  │  └─Th. │ │  • Debug       │  │   │  │  └─Th. │ │                │  │ │
│   │  │    └─… │ │  • Review VCS  │  │   │  │    └─… │ │                │  │ │
│   │  └────────┘ └────────────────┘  │   │  └────────┘ └────────────────┘  │ │
│   └─────────────────────────────────┘   └─────────────────────────────────┘ │
│                                                                             │
│   Why dual views?                                                           │
│   • Work on multiple tasks in parallel — see status of each                 │
│   • AI isn't "there" yet — you still review, read, understand code          │
│   • Not vibe-coding — we use AI in production, need to know how/why         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

The Agent Threads Tool Window organizes threads by project:

```
Projects
├── project-a
│   ├── Thread: "Add caching layer"     [done]
│   └── Thread: "Fix auth bug"          [needs input]
│       └── sub-agent: "research"
└── project-b (not open)                [Connect]
    └── Thread: "Refactor API"          [inactive]
```

## Module Architecture

Agent Workbench is one plugin assembled from many content modules. Use this map when choosing spec targets,
checking dependency direction, or deciding whether code belongs in provider logic, shared session services, or UI.

Primary sources of truth:

- [`plugin/resources/META-INF/plugin.xml`](plugin/resources/META-INF/plugin.xml) declares the plugin id and content modules.
- [`plugin/plugin-content.yaml`](plugin/plugin-content.yaml) declares packaged content-module jars.
- `intellij.agent.workbench.*.iml` files declare module dependencies and generate `BUILD.bazel` files.
- [`spec/README.md`](spec/README.md) and [`community/.ai/spec/SPEC_GUIDE.md`](../../.ai/spec/SPEC_GUIDE.md) define spec placement and metadata rules.
- [`docs/agent-session-provider-guide.md`](docs/agent-session-provider-guide.md) explains how to implement or review a provider-backed session source.

```text
com.intellij.agent.workbench
`- intellij.air.plugin          plugin wrapper; owns plugin.xml
   `- runtime content modules
      |- lib-agent/*                       provider/runtime contracts and implementations
       |- sessions, thread-view, prompt/*          app services and user workflows
      |- sessions-toolwindow, actions      visible Agent Threads surfaces
      |- ui, settings                      shared UI helpers and persisted preferences
      |- codex/*, claude/awb, pi/awb       provider-specific IDE integrations
      `- vcs-merge, ai-review, container   adjacent feature areas
```

Dependency direction is mostly layered, with feature modules depending down on shared services and provider
contracts. IntelliJ Platform dependencies are omitted here.

```text
feature and UI modules
  sessions-toolwindow
  sessions-actions
  prompt/ui
  thread-view
  vcs-merge / ai-review
          |
          v
shared app layer
  sessions
  settings
  ui
  prompt/context
  prompt/vcs
          |
          v
lib-agent layer
  sessions-core       provider/session contracts and launch models
  providers/*         provider descriptors, sources, CLI adapters
  cli / filewatch
  json
  common / core       shared ids, state primitives, icons, activity presentation
```

Provider modules are split by provider family. The `sessions` modules implement `AgentSessionProviderDescriptor`
and register provider-backed session behavior through the shared sessions contracts.

```text
lib-agent/providers
|- claude
|  |- common
|  `- sessions
|- codex
|  |- common
|  `- sessions
|- junie
|  |- common
|  `- sessions
|- opencode
|  `- sessions
|- pi
|  |- sessions
|  `- sessions-filewatch
`- terminal
   `- sessions
```

`lib-agent` is not currently a UI-free library. This is intentional enough that moving a single icon class only
relocates coupling; it does not remove it.

```text
lib-agent/common
|- AgentThreadActivityPresentation        uses UI color presentation
|- AgentThreadActivityIcons               badges Swing icons
`- icons/AgentWorkbenchCommonIcons        shared provider logos
      |
      `- used by provider descriptors, session/thread view tests, and provider-specific UI

lib-agent/sessions-core
`- AgentSessionProviderDescriptor         exposes provider Icon and optional JComponent hooks
```

If Agent Workbench ever needs a UI-free `lib-agent`, that should be a contract split: provider descriptors would
carry stable provider ids and capability data, while UI modules would resolve icons and components from a separate
presentation layer. Moving `AgentWorkbenchCommonIcons` within `lib-agent` is not enough.

## Specifications

Detailed requirements and testing contracts are documented in `spec/`.

- [Core Contracts](spec/core/agent-core-contracts.spec.md) - Canonical cross-feature contracts: identity, command mapping, shared editor-tab actions, and shared visibility primitives.
- [Agent Threads Tool Window](spec/sessions/agent-sessions.spec.md) - Provider aggregation, load/refresh lifecycle, deduplication, and project/worktree tree behavior.
- [Agent Threads Visibility and More Row](spec/sessions/agent-sessions-thread-visibility.spec.md) - Deterministic visibility rendering and More-row precedence rules.
- [Agent Thread View Editor](spec/thread-view/agent-thread-view.spec.md) - Agent Thread View lifecycle, persistence/restore, lazy terminal initialization, titles/icons.
- [Agent Thread View Dedicated Frame](spec/frame/agent-dedicated-frame.spec.md) - Dedicated-frame mode routing, lifecycle, shortcut semantics, and filtering.
- [Agent Main Toolbar Activity](spec/frame/agent-main-toolbar-activity.spec.md) - Global Agent activity counters shown in source-project main toolbars.
- [Codex Sessions Rollout Source](spec/sessions/agent-sessions-codex-rollout-source.spec.md) - Rollout-default Codex discovery, watcher semantics, backend selector, and app-server write interoperability.
- [Agent Sessions New-Session Actions](spec/actions/new-thread.spec.md) - New-thread UX, provider/YOLO selection, creation dedup, pending-thread rebinding.
- [Global Prompt Composer](spec/actions/global-prompt-composer.spec.md) - Prompt-as-task-composer mental model, context/text/tray ownership, and layout lane contract.
- [Global Prompt Entry](spec/actions/global-prompt-entry.spec.md) - Global shortcut entrypoint, centered popup UX, context capture, and launch bridge flow.
- [Global Prompt Suggestions](spec/actions/global-prompt-suggestions.spec.md) - Context-derived seed prompts, prompt-panel suggestion UI, async refresh semantics, and Codex polishing.
- [Testing Contract](spec/sessions/agent-sessions-testing.spec.md) - Coverage ownership matrix and required contract test suites.

## Test All

Run all Agent Workbench tests with:

```bash
./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.*'
```

## Troubleshooting Codex + ijproxy Launch

When diagnosing a Codex thread started from `Cmd+\` or `Ctrl+\` that falls back to shell tools instead of `ijproxy`, collect a fresh `idea.log` from IDE startup through one repro and enable these categories in `Help | Diagnostic Tools | Debug Log Settings`:

- `#com.intellij.agent.workbench.sessions.launch.config.backend.AgentWorkbenchProjectLaunchConfigLogCategory` for `.agent-workbench.yaml` discovery, parsed config summaries, shim preparation, and provider launch augmentation.
- `#com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService` for sanitized new-thread launch handoff summaries.
- `#com.intellij.platform.ai.agent.codex.common.CodexAppServerClient` for Codex app-server startup summaries and forwarded stderr.
- `#com.intellij.mcpserver.impl.McpServerService:trace` for IDE MCP server and session startup.
- `#com.intellij.mcpserver.impl.McpSessionHandler:trace` for MCP tool list updates and actual tool calls.
- Optional: `#com.intellij.mcpserver.impl.util.network.RoutingContext:trace` for stdio and session transport issues.
- Optional: `#com.intellij.mcpserver.ToolCallListener:trace` for extra per-tool activity.
- Optional: `#com.intellij.mcpserver.impl.ReflectionToolsProvider` for MCP tool discovery and loading issues.

Interpret the logs like this:

- If the launch-config category never reports a resolved config for `codex`, agent-workbench did not apply `.agent-workbench.yaml` augmentation.
- If the launch-config logs look correct but `CodexAppServerClient` shows startup or stderr failures, the problem is before MCP tool calls.
- If `McpSessionHandler` shows session traffic and tool calls, `ijproxy` was available and shell-only behavior is model-side rather than missing transport.
