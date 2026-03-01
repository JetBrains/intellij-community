# Agent Workbench Plugin

## Vision: AI-First Workflow UX

The Agent Workbench plugin reimagines the IDE experience around AI-assisted development. Rather than treating AI as an add-on feature, the plugin creates a seamless workflow where developers can:

- **Start conversations naturally** - Begin coding discussions from any context in the IDE
- **Maintain persistent threads** - Keep conversation history organized and accessible across sessions
- **Navigate AI interactions** - Browse, search, and resume previous conversations efficiently
- **Integrate with development flow** - Connect AI assistance directly to code navigation, editing, and debugging

The goal is to make AI assistance feel like a native part of the development environment, reducing context switching and keeping developers in flow.

## Architecture

The plugin provides two complementary views for working with AI-assisted development:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              IntelliJ IDEA                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────────────────────┐   ┌─────────────────────────────────┐ │
│   │      PROJECT FRAME              │   │    AI-CHAT DEDICATED VIEW       │ │
│   │  (traditional development)      │   │   (task orchestration)          │ │
│   │                                 │   │                                 │ │
│   │  ┌────────┐ ┌────────────────┐  │   │  ┌────────┐ ┌────────────────┐  │ │
│   │  │ Agent  │ │                │  │   │  │ Agent  │ │                │  │ │
│   │  │Sessions│ │     Editor     │  │◄─►│  │Sessions│ │   Chat Panel   │  │ │
│   │  │  Tool  │ │                │  │   │  │  Tool  │ │                │  │ │
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

The Agent Threads Tool Window organizes conversations by project:

```
Projects
├── project-a
│   ├── Thread: "Add caching layer"     [done]
│   └── Thread: "Fix auth bug"          [needs input]
│       └── sub-agent: "research"
└── project-b (not open)                [Connect]
    └── Thread: "Refactor API"          [inactive]
```

## Specifications

Detailed requirements and testing contracts are documented in `spec/`.

- [Core Contracts](spec/agent-core-contracts.spec.md) - Canonical cross-feature contracts: identity, command mapping, shared editor-tab actions, and shared visibility primitives.
- [Agent Threads Tool Window](spec/agent-sessions.spec.md) - Provider aggregation, load/refresh lifecycle, deduplication, and project/worktree tree behavior.
- [Agent Threads Visibility and More Row](spec/agent-sessions-thread-visibility.spec.md) - Deterministic visibility rendering and More-row precedence rules.
- [Agent Chat Editor](spec/agent-chat-editor.spec.md) - Chat tab lifecycle, persistence/restore, lazy terminal initialization, titles/icons.
- [Agent Chat Dedicated Frame](spec/agent-dedicated-frame.spec.md) - Dedicated-frame mode routing, lifecycle, shortcut semantics, and filtering.
- [Codex Sessions Rollout Source](spec/agent-sessions-codex-rollout-source.spec.md) - Rollout-default Codex discovery, watcher semantics, backend selector, and app-server write interoperability.
- [Agent Sessions New-Session Actions](spec/actions/new-thread.spec.md) - New-thread UX, provider/YOLO selection, creation dedup, pending-thread rebinding.
- [Testing Contract](spec/agent-sessions-testing.spec.md) - Coverage ownership matrix and required contract test suites.

## Test All

Run all Agent Workbench tests with:

```bash
./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.*'
```
