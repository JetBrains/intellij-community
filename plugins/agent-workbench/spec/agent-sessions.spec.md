---
name: Agent Threads Tool Window
description: Requirements for multi-provider session aggregation and tree behavior in Agent Threads.
targets:
  - ../plugin/resources/META-INF/plugin.xml
  - ../plugin-content.yaml
  - ../sessions/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../chat/src/*.kt
  - ../sessions/testSrc/*.kt
---

# Agent Threads Tool Window

Status: Draft
Date: 2026-02-16

## Summary
Define the Agent Threads tool window as a provider-agnostic, project-scoped session browser. Threads from supported providers are aggregated per project/worktree, rendered in one tree, and opened through a shared chat routing flow.

## Goals
- Keep project/worktree grouping deterministic across open and recent projects.
- Aggregate provider results without dropping successful data when one provider fails.
- Keep tree interactions predictable for load, warning, error, and paging states.
- Preserve dedicated-frame vs current-project routing for thread and sub-agent opens.

## Non-goals
- Thread transcript rendering or in-tree compose actions.
- Search/filter UX beyond tree speed search.
- Archived-thread browsing or unarchive actions.

## Requirements
- Project registry must merge currently open projects and recent projects, excluding the dedicated frame project.
- Git worktrees must be represented under parent projects when detected.
- Default session sources must include Codex and Claude providers.
- Thread identity must include provider + session id to avoid collisions across providers.
- Provider results for a project/worktree load must be merged and sorted by `updatedAt` descending.
- If at least one provider succeeds, successful threads must be shown and failed providers must surface provider-local warning rows.
- If all providers fail for a project/worktree load, show blocking project/worktree error state and suppress provider warning rows for that load.
- Unknown provider totals must propagate via `hasUnknownThreadCount` and drive unknown-count `More…` rendering.
- Sessions tree UI state must persist by normalized path:
  - collapsed project/worktree state,
  - per-path visible thread count,
  - open-path thread preview cache.
- Refresh bootstrap must immediately seed open project/worktree nodes from cached previews when available and mark those paths loaded until live provider results arrive.
- Refresh bootstrap must restore persisted visible thread counts above default for known project/worktree paths.
- Refresh bootstrap must retain preview cache only for currently open project/worktree paths and prune stale closed-path entries.
- Final merged refresh results must update preview cache for a path only when that path does not end in blocking error.
- Auto-open default project expansion must skip paths persisted as collapsed.
- User collapse/expand interactions must update persisted collapsed state.
- Cached preview entries must preserve provider identity; missing legacy provider value must default to Codex for backward compatibility.
- `More` and programmatic visibility expansion (`ensureThreadVisible`) must persist visible-count increments in tree UI state.
- On-demand project/worktree loading must deduplicate concurrent requests for the same path.
- Concurrent refresh requests must be deduplicated while a refresh is already running.
- Project primary click must open/focus the project; closed projects must expose `Open` in context menu.
- Thread/sub-agent opens must route according to `agent.workbench.chat.open.in.dedicated.frame`.
- Resume command must be provider-specific:
  - Codex: `codex resume <sessionId>`
  - Claude: `claude --resume <sessionId>`
- New-session action behavior (provider options, Codex/Claude command mapping, and Full Auto semantics) is defined in `spec/actions/new-thread.spec.md` and must be used by both project and worktree rows.
- Codex thread discovery must default to rollout session files; app-server thread discovery remains an explicit compatibility override path.
- Codex thread title normalization and filtering rules are defined in `spec/agent-sessions-codex-rollout-source.spec.md` and must be used for Codex thread rows.
- Branch mismatch between thread origin and current worktree branch must show a warning confirmation before opening chat.

[@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceConcurrencyIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt
[@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt

## User Experience
- Project rows are always expandable and may show worktree children.
- Thread rows show title, provider marker, and short relative time.
- Provider warning rows are non-blocking and shown inline in the same project/worktree section.
- Blocking errors show retry action inline.
- `More...`/`More…` behavior follows `spec/agent-sessions-thread-visibility.spec.md`.

## Data & Backend
- Open projects use long-lived provider sessions where available.
- Closed project/worktree loads may use short-lived provider calls scoped to path.
- Provider sources may have different pagination/count capabilities; aggregation layer normalizes into a single state model.
- Do not force global CLI home overrides from sessions service; provider clients own their process environment rules.

## Error Handling
- Missing provider CLI/tooling must resolve to provider-specific user-facing messages.
- Unexpected provider failures must resolve to generic provider-unavailable warnings when partial data exists.
- Refresh/load failures must preserve already loaded thread data when possible.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsService*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`

## Open Questions / Risks
- Thread/sub-agent provider coverage can expand; additional provider-specific UX may be needed.
- Worktree discovery quality depends on Git metadata availability.

## References
- `spec/agent-sessions-thread-visibility.spec.md`
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/actions/new-thread.spec.md`
- `spec/agent-sessions-codex-rollout-source.spec.md`
