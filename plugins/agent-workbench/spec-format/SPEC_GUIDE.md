# Agent Workbench Spec Format

This is the single spec format for this plugin. Specs live in `spec/` and must be Markdown files ending in `.spec.md`.

## Required Structure
- YAML frontmatter with `name`, `description`, and `targets` (at least one file path or glob).
- An H1 title matching the frontmatter `name`.
- A metadata block with `Status` and `Date` (ISO-8601).
- A concise summary of the behavior and requirements being specified.
- `[@test]` links placed adjacent to the requirements they verify.

## Template

```markdown
---
name: Agent Threads Tool Window
description: Requirements for the Agent Threads tool window and app-server integration.
targets:
  - ../sessions/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
---

# Agent Threads Tool Window

Status: Draft
Date: 2026-02-03

## Summary
Provide a concise description of the feature, scope, and intent.

## Goals
- Primary outcomes the feature must deliver.

## Non-goals
- Explicit exclusions to avoid scope creep.

## Requirements
- Each requirement must be testable and specific.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

## User Experience
- Describe UI states and interactions.
- Keep user-visible strings in `.properties`.

## Data & Backend
- Protocols, payloads, ordering, paging, and error behavior.

## Error Handling
- Failure modes and user-facing recovery actions.

## Testing / Local Run
- List non-standard commands or environment setup.

## Open Questions / Risks
- Decisions pending or known risks.
```

## Guidance
- Use must/should/may language; avoid ambiguous phrasing.
- Keep specs small; split by feature or subsystem to stay within context limits.
- Include concrete examples for data shapes, UI states, or error copy when needed.
- Keep `targets` and `[@test]` paths accurate and up to date.
- Treat the spec as the source of truth during review and implementation.

## References
- Adapted from the Tessl spec-driven development tile (see `LICENSE`).
- Informed by Addy Osmani's "How to write a good spec for AI agents".
