---
name: Agent Threads Tool Window
description: Requirements for provider-agnostic session aggregation, project/worktree loading, and tree lifecycle behavior in Agent Threads.
targets:
  - ../plugin/resources/META-INF/plugin.xml
  - ../plugin-content.yaml
  - ../sessions/src/*.kt
  - ../sessions/src/providers/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../sessions/testSrc/*.kt
  - ../chat/src/*.kt
---

# Agent Threads Tool Window

Status: Draft
Date: 2026-02-23

## Summary
Define Agent Threads as a provider-agnostic, project-scoped thread browser. This spec owns aggregation, loading, deduplication, cache bootstrap, and tree lifecycle behavior. Shared cross-feature contracts are defined in `spec/agent-core-contracts.spec.md`.

## Goals
- Keep project/worktree grouping deterministic across open and recent projects.
- Merge provider results without dropping successful data when one provider fails.
- Keep refresh/on-demand behavior predictable under concurrency.
- Preserve stable tree behavior across refresh and UI recreation.

## Non-goals
- Thread transcript rendering and compose UX.
- Backend-specific rollout parsing rules (owned by Codex rollout spec).
- Shared command/action contracts (owned by core contracts spec).

## Requirements
- Project registry must merge open projects and recent projects, excluding the dedicated-frame project.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Git worktrees must be represented under parent projects when detected.
  [@test] ../sessions/testSrc/GitWorktreeDiscoveryTest.kt
  [@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Default session-source registration must include Codex and Claude provider bridges.
  [@test] ../sessions/testSrc/AgentSessionProviderBridgesTest.kt

- Provider results for a path load must be merged and sorted by `updatedAt` descending.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- If at least one provider succeeds, successful threads must be shown and failed providers must surface provider-local warning rows.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

- If all providers fail for a path load, a blocking path error must be shown and provider warning rows for that load must be suppressed.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Unknown provider totals must propagate through `hasUnknownThreadCount` to tree state.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- Refresh bootstrap must seed open project/worktree paths from preview cache immediately and keep those paths marked loaded until live provider results arrive.
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- Refresh bootstrap visibility-restoration behavior must follow `spec/agent-sessions-thread-visibility.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- Refresh bootstrap must retain preview cache only for currently open project/worktree paths and prune stale closed-path entries.
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- Final refresh results must update preview cache only for paths that are not in blocking error state.
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- Auto-open default project expansion must skip paths persisted as collapsed.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

- User collapse/expand interactions must update persisted collapsed state.
  [@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt

- Cached preview entries missing legacy provider value must default provider to Codex for backward compatibility.
  [@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt

- On-demand loading must deduplicate concurrent requests for the same normalized path.
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

- Refresh requests must be coalesced while processing is in progress; catalog-sync requests must not be dropped, and any queued full refresh must take precedence.
  [@test] ../sessions/testSrc/AgentSessionsServiceConcurrencyIntegrationTest.kt

- Project open/close lifecycle updates must run catalog sync and load threads only for newly opened paths; already open paths must not be reloaded by lifecycle updates.
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- Session-source update observation and refresh scheduling must be event-driven; periodic polling loops are not allowed.
  [@test] ../sessions/testSrc/AgentSessionsLoadingCoordinatorTest.kt

- Project primary click must open/focus the project; closed projects must expose `Open` in context menu.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt

- Thread and sub-agent open routing must follow mode policy defined in `spec/agent-dedicated-frame.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Tree row open policy must be pointer-gesture aware: plain primary click opens, multi-selection gestures (`Cmd/Ctrl+click`, `Shift+click`) do not open, and context-menu gestures (`secondary click`, macOS `Ctrl+click`) do not open.
  [@test] ../sessions/testSrc/SessionTreePointerEventActionsTest.kt

- Context-menu selection policy for thread rows must preserve existing multi-selection when right-clicking an already selected row; right-click on an unselected row must retarget selection to the clicked row.
  [@test] ../sessions/testSrc/SessionTreePointerEventActionsTest.kt

- Session-driven thread title updates must refresh open chat tab metadata and editor-tab presentation.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../chat/testSrc/AgentChatTabSelectionServiceTest.kt

- Codex thread discovery must default to rollout source; app-server discovery remains an explicit compatibility override defined in `spec/agent-sessions-codex-rollout-source.spec.md`.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Branch mismatch between thread origin and current worktree branch must show warning confirmation before opening chat.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Shared command mapping, editor-tab popup actions, archive gating, and visibility primitives must follow `spec/agent-core-contracts.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

- Batch archive must archive all targets whose providers support archive, while unsupported targets are skipped without blocking successful targets.
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

- Unarchive flow for previously archived Codex targets must restore thread visibility on refresh without requiring tool-window recreation.
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

## User Experience
- Project rows are always expandable and may show worktree children.
- Open project rows must be visually emphasized via stronger title weight.
- Closed project rows must remain readable but visually de-emphasized relative to open rows.
- Default project visibility must include all open projects and up to 3 closed recent projects; additional closed projects appear behind `More`.
- Thread rows show provider marker and relative activity time.
- Thread-row archive context menu should apply to current multi-selection when invoked from a selected thread and show `Archive Selected (N)` when `N > 1`.
- Selection gestures (`Cmd/Ctrl+click`, `Shift+click`) update selection without opening the clicked thread/sub-agent row.
- Context-menu gestures (`secondary click`, macOS `Ctrl+click`) never open rows.
- Provider warnings are inline and non-blocking when partial data exists.
- Blocking errors provide inline retry affordance.

## Data & Backend
- Open projects may use long-lived provider sessions where available.
- Closed project/worktree loads may use path-scoped short-lived provider calls.
- Aggregation normalizes provider differences (paging/count capability) into one state model.
- Sessions service must not impose global CLI home overrides; provider clients own process environment rules.

## Error Handling
- Missing provider tooling must produce provider-specific messages.
- Unexpected provider failures must map to provider-unavailable warnings when partial data exists.
- Load failures should preserve previously loaded thread data where safe.
- Batch archive/unarchive failures should isolate to failing targets and preserve successful target state updates.
- Chat metadata cleanup failures during archive must be logged and must not block successful thread removal/refresh.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsService*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`

## Open Questions / Risks
- New providers may require additional provider-specific warning or context-row UX.
- Worktree discovery quality depends on Git metadata completeness.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-sessions-thread-visibility.spec.md`
- `spec/agent-dedicated-frame.spec.md`
- `spec/agent-chat-editor.spec.md`
- `spec/actions/new-thread.spec.md`
- `spec/agent-sessions-codex-rollout-source.spec.md`
