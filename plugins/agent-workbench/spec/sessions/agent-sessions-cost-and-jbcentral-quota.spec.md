---
name: Agent Sessions Cost and JBCentral Quota
description: Requirements for per-session cost presentation, OpenRouter-backed price snapshots, and JBCentral quota status display.
targets:
  - ../../common/src/session/*.kt
  - ../../sessions-core/src/**/*.kt
  - ../../sessions-toolwindow/src/ui/*.kt
  - ../../sessions-toolwindow/testSrc/*.kt
  - ../../codex/sessions/src/**/*.kt
  - ../../codex/sessions/testSrc/**/*.kt
  - ../../claude/common/src/*.kt
  - ../../claude/sessions/src/**/*.kt
  - ../../claude/sessions/testSrc/**/*.kt
---

# Agent Sessions Cost and JBCentral Quota

Status: Draft
Date: 2026-05-24

## Summary
Agent Workbench must surface two related pieces of spend state without introducing provider-specific pricing tables into the IDE.

The Agent Threads tree must optionally show per-session cost when the provider already exposes exact cost or when the session's token usage can be matched against a persisted OpenRouter price catalog snapshot fetched once at IDE startup. JBCentral must expose a dedicated quota status widget that mirrors the existing Claude quota affordance but is backed by the installed `jbcentral` CLI rather than duplicated auth logic inside the plugin.

## Goals
- Show per-session cost in Agent Threads when a session can be priced exactly or estimated from persisted price data.
- Prefer exact provider-native cost over any estimate.
- Fetch OpenRouter model pricing once at IDE startup and reuse the last successful snapshot as the only fallback price catalog.
- Add a JBCentral quota widget that reports remaining quota and refill timing without reimplementing JBCentral token storage or refresh flows in the IDE.
- Keep the session cost UI and JBCentral quota UI configurable, including startup affordances that mirror the current Claude quota enablement flow.

## Non-goals
- Continuous background price refresh after IDE startup.
- Hard-coded OpenAI, Anthropic, or provider-local tariff tables inside Agent Workbench.
- Best-effort guessed pricing for unmatched or ambiguous model names.
- A separate subagent row model for Claude cost presentation in this change.
- Non-configurable always-on cost or quota surfaces.

## Requirements
- Agent Workbench must maintain a single application-level price catalog service that fetches OpenRouter `/api/v1/models` once during IDE startup, normalizes each model's `id`, `canonical_slug`, `name`, and pricing fields, and persists the last successful snapshot for reuse across restarts.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt

- If the startup fetch fails, cost estimation must fall back to the most recent persisted snapshot; if no persisted snapshot exists, only exact provider-native costs may be shown.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt

- The price catalog service must not perform background polling, periodic refresh, or per-thread network lookups after startup.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt

- Model matching must use a trivial deterministic heuristic built from normalized model identifiers rather than a manually curated alias table. Normalization must lowercase names, replace non-alphanumeric runs with `-`, collapse duplicate separators, and compare both whole-name and trailing-name forms. Prefix fallback may be used only when it produces a single unambiguous match.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeSessionSourceTest.kt

- When a provider exposes exact session cost, Agent Workbench must show that exact amount and must not replace it with an OpenRouter estimate.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeSessionSourceTest.kt

- When exact cost is unavailable but normalized usage and an unambiguous OpenRouter model match exist, Agent Workbench must show an estimated session cost derived from the persisted snapshot.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeSessionsStoreTest.kt

- When exact cost is unavailable and the model match is missing or ambiguous, Agent Workbench must treat the session cost as unavailable and must not display a guessed amount.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeSessionSourceTest.kt

- Codex rollout parsing must capture the last observed model identifier and the last observed cumulative token totals for the thread. Repeated `token_count` events with unchanged totals must not multiply the session cost.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt
  [@test] ../../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Claude session usage aggregation must sum `message.usage` across assistant messages in the main session transcript and any `subagents/*.jsonl` transcripts that belong to the same session so that the displayed cost reflects the full session visible to the user.
  [@test] ../../claude/sessions/testSrc/ClaudeSessionsStoreTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeSessionSourceTest.kt

- Claude synthetic zero-usage assistant messages and similar placeholder records must not change aggregated usage or cost.
  [@test] ../../claude/sessions/testSrc/ClaudeSessionsStoreTest.kt

- Agent Threads cost presentation must remain opt-in behind a dedicated registry key or equivalent feature flag.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt

- The session cost feature and the JBCentral quota widget must each expose persisted enablement state so users can turn them on and off independently.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeQuotaHintStateServiceTest.kt

- When enabled, thread rows must render exact cost as `$N.NN`, estimated cost as `~$N.NN`, and unavailable cost as no cost label.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt

- Cost presentation must update through the existing thread refresh flow; it must not introduce an additional tree-specific polling loop.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeSessionSourceTest.kt

- JBCentral quota must be surfaced through a dedicated status bar widget that follows the Claude quota widget interaction model: lazy activation, explicit refresh on click, and tooltip-based detail display.
  [@test] ../../claude/sessions/testSrc/ClaudeQuotaStatusBarWidgetTest.kt

- JBCentral quota enablement must be offered through a startup hint flow equivalent in behavior to the current Claude quota hint flow: opening the relevant Agent Workbench surface may mark the hint as eligible, the hint must be dismissible, and enabling the widget from the hint must acknowledge the prompt and persist enablement.
  [@test] ../../claude/sessions/testSrc/ClaudeQuotaHintStateServiceTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeQuotaStatusBarWidgetTest.kt

- The JBCentral quota implementation must shell out to the installed `jbcentral quota` workflow instead of duplicating Central CLI token loading, encrypted storage, or refresh logic inside Agent Workbench.
  [@test] ../../claude/sessions/testSrc/ClaudeQuotaServiceE2eTest.kt

- The JBCentral quota widget must resolve the CLI path from explicit configuration first, then `PATH`, then known local fallback locations.
  [@test] ../../claude/sessions/testSrc/ClaudeQuotaServiceE2eTest.kt

- If the JBCentral CLI is unavailable, unauthenticated, or returns malformed quota data, the widget must hide itself or show a non-intrusive error tooltip instead of occupying persistent space with stale data.
  [@test] ../../claude/sessions/testSrc/ClaudeQuotaStatusBarWidgetTest.kt

## User Experience
- User-visible strings for cost labels, tooltips, and JBCentral quota states must live in `.properties` bundles.
- The Agent Threads tree should reserve cost space in the existing right-side metadata lane, alongside time metadata, so titles continue to clip predictably.
- Exact and estimated costs must be visually distinguishable without adding extra icons.
- The JBCentral widget should use a single remaining-quota bar and a tooltip that includes license name, used amount, remaining amount, maximum quota, and refill date when available.
- Startup prompts for cost/quota affordances should follow the same tone and dismissal model as existing Claude quota prompting instead of introducing a separate UX pattern.

## Data & Backend
- The persisted OpenRouter snapshot should store only the normalized pricing data needed for offline matching and calculation, not the entire raw response body.
- Session usage should be represented in a provider-agnostic structure containing model identifier, input tokens, output tokens, cache-read tokens, cache-write tokens, request count, and optional native exact cost.
- Cost calculation should remain provider-agnostic: exact native cost first, OpenRouter snapshot estimate second, unavailable otherwise.
- The JBCentral quota service may parse CLI output for v1, but the service boundary should allow a future direct API implementation without changing widget code.

## Error Handling
- Failed OpenRouter fetch at startup should be logged once and degrade to the last persisted snapshot without retry spam.
- Corrupt persisted snapshots should be discarded and treated as absent.
- Ambiguous model matches should resolve to unavailable cost instead of picking an arbitrary candidate.
- CLI invocation failures for JBCentral quota should not surface modal errors or notifications during routine IDE usage.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRolloutIntegrationTest`
- `./tests.cmd --module intellij.agent.workbench.claude.sessions.tests --test com.intellij.agent.workbench.claude.sessions.ClaudeSessionsStoreTest`
- `./tests.cmd --module intellij.agent.workbench.claude.sessions.tests --test com.intellij.agent.workbench.claude.sessions.ClaudeSessionSourceTest`
- `./tests.cmd --module intellij.agent.workbench.claude.sessions.tests --test com.intellij.agent.workbench.claude.sessions.ClaudeQuotaStatusBarWidgetTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsSwingTreeCellRendererTest`

## Open Questions / Risks
- Claude transcript aggregation across subagents assumes that session-linked `subagents/*.jsonl` files should contribute to the parent session cost; if future Claude UX exposes subagents as separate first-class rows, this rollup policy may need to split.
- OpenRouter field availability may change over time; the snapshot normalizer should ignore unknown fields and tolerate missing optional pricing dimensions.
- JBCentral CLI text output is less stable than a structured machine endpoint, so the CLI-backed quota client should remain behind a narrow abstraction boundary.

## References
- `agent-sessions.spec.md`
- `agent-sessions-tree.spec.md`
- `agent-sessions-codex-rollout-source.spec.md`
- `agent-sessions-codex-rollout-hints.spec.md`
