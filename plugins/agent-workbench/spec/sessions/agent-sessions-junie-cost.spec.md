---
name: Agent Sessions Junie Cost
description: Requirements for exact Junie session cost aggregation from root session events.
targets:
  - ../../junie/sessions/src/**/*.kt
  - ../../junie/sessions/testSrc/**/*.kt
  - ../../sessions/src/service/*.kt
  - ../../sessions/testSrc/*.kt
---

# Agent Sessions Junie Cost

Status: Approved
Date: 2026-05-27

## Summary

Agent Workbench already knows how to list Junie threads from `~/.junie/sessions/index.jsonl`, but it does not surface per-session cost. Real Junie session logs already contain provider-reported exact spend in the root session event stream, so the Junie design should reuse that exact data instead of re-estimating cost from OpenRouter prices.

## Goals

- Show Junie session cost in Agent Threads when the root session log contains provider-reported cost.
- Sum cost across the whole root Junie session event stream, including sessions that used multiple models.
- Reuse the existing visible-thread and archived-thread cost hydration flow.
- Recompute Junie cost only when the thread's `updatedAt` changes.

## Non-goals

- Repricing Junie sessions through OpenRouter or any other catalog.
- Scanning `task-*` subdirectories or `.matterhorn` payloads for Junie cost.
- Background polling beyond the existing Agent Threads refresh flow.
- Backfilling a complete total when some Junie usage records omit cost.

## Observed Data Source

Real local Junie sessions are stored under `~/.junie/sessions/` with this layout:

- `index.jsonl` contains one record per session with `sessionId`, `projectDir`, and `updatedAt`.
- `sessions/<sessionId>/events.jsonl` contains the root session event stream.
- `sessions/<sessionId>/state.json` contains the latest saved agent state and is not needed for cost.

Within `events.jsonl`, relevant records have the form `SessionA2uxEvent -> event.agentEvent.kind == LlmResponseMetadataEvent -> modelUsage[]`. Real `modelUsage[]` entries contain:

- `model`
- `cost`
- `inputTokens`
- `cacheInputTokens`
- `cacheCreateTokens`
- `outputTokens`

The current change uses only `cost` for display, but the token fields are present in case the shared usage model needs them later.

## Requirements

- For Junie, session cost must be computed as the sum of all `modelUsage[*].cost` values across every `LlmResponseMetadataEvent` in the root `events.jsonl` file for that session.
- One Junie session may include multiple models, so aggregation must sum all reported `cost` values rather than trying to keep only the last model or last event.
- If every relevant `modelUsage` entry has a `cost`, the final thread cost must be `AgentSessionCostKind.EXACT` and render as `$N.NN`.
- If at least one relevant `modelUsage` entry has a `cost` and at least one relevant entry is missing `cost`, the final thread cost must be `AgentSessionCostKind.ESTIMATED` with the sum of known values and render as `~$N.NN`.
- If no relevant `modelUsage` entry has a usable `cost`, the thread cost must be unavailable and no cost label should be shown.
- Junie cost loading must read only `~/.junie/sessions/<sessionId>/events.jsonl`.
- Junie cost loading must not inspect `task-*` subdirectories, `.matterhorn` task state, or any other derived nested storage.
- Junie cost must integrate through the existing `AgentSessionSource.loadThreadCosts()` path.
- `listThreads()` and `listArchivedThreads()` must remain cheap and continue loading only from `index.jsonl`.
- Visible-thread hydration must compute Junie cost only for currently visible rows.
- Archived-thread hydration must compute Junie cost only for currently visible archived rows.
- Once a Junie thread cost is computed, it must be reused until that thread's `updatedAt` changes.
- If `updatedAt` does not change, Agent Workbench must not reread the session log or recompute the cost.
- Archived Junie threads must use the same cost resolution path as active Junie threads.

## Error Handling

- Missing or unreadable `events.jsonl` files degrade to unavailable cost.
- Malformed JSON or unexpected event shapes degrade to unavailable cost.
- Routine parse failures should be logged at a non-intrusive level and must not block thread loading or freeze the UI.

## Testing Strategy

Tests should use sanitized minimal Junie event JSON based on the real observed schema, keeping only fields needed for cost parsing.

Required coverage:

- sums exact cost across multiple `LlmResponseMetadataEvent` records
- sums exact cost across multiple models in one session
- returns `ESTIMATED` when only part of the session has reported cost
- returns unavailable when no event exposes a usable cost
- reads only the root `events.jsonl` and ignores nested task storage
- resolves archived thread cost through the same source-level API
- reuses cached cost until `updatedAt` changes

## Approved Decisions

- Junie uses exact provider-reported cost from logs, not OpenRouter repricing.
- Junie session cost is the sum of all root-session `modelUsage[*].cost` values.
- Partial known totals render as estimated (`~$N.NN`).
- Only the root `events.jsonl` is scanned.
- Archived Junie threads also show cost.
- Recalculation happens only after `updatedAt` changes.
