---
name: Agent Threads Tool Window
description: Requirements for the Swing Async Tree implementation of Agent Threads, including aggregation, loading, activation, and tree lifecycle behavior.
targets:
  - ../sessions/src/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../sessions/intellij.agent.workbench.sessions.iml
  - ../sessions/BUILD.bazel
  - ../sessions/testSrc/*.kt
  - ../chat/src/*.kt
---

# Agent Threads Tool Window

Status: Draft
Date: 2026-02-24

## Summary
Define Agent Threads as a provider-agnostic, project-scoped browser implemented with native IntelliJ Swing tree APIs (`StructureTreeModel` + `AsyncTreeModel` + `Tree`).

This spec owns:
- session aggregation and loading lifecycle,
- tree snapshot/rendering rules,
- interaction policy (selection vs activation),
- new-session affordances,
- quota hint visibility behavior.

Shared contracts remain in `spec/agent-core-contracts.spec.md`.

## Goals
- Keep project/worktree grouping deterministic across open and recent projects.
- Merge provider results without dropping successful data when one provider fails.
- Keep refresh/on-demand behavior predictable under concurrency.
- Follow IntelliJ tree conventions: single-click selects, activation happens on Enter or double-click.
- Use one UI path only (Swing async tree), with no Compose compatibility layer.

## Non-goals
- Thread transcript rendering.
- Reintroducing Compose/Jewel UI in the sessions module.
- Feature-flagged dual-path UI rollout for sessions.
- Backend-specific rollout parsing rules (owned by Codex rollout spec).
- Shared command/action contracts (owned by core contracts spec).

## Architecture Decision
- The sessions tool window must use IntelliJ-native Swing async tree infrastructure.
  Rationale: this aligns interaction semantics with platform conventions, removes duplicate UI stacks in the plugin, and reduces maintenance/testing overhead.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowFactorySwingTest.kt

## Requirements
- Project registry must merge open projects and recent projects, excluding the dedicated-frame project.
  [@test] ../sessions/testSrc/AgentSessionsProjectCatalogTest.kt

- Git worktrees must be represented under parent projects when detected.
  [@test] ../sessions/testSrc/GitWorktreeDiscoveryTest.kt

- Default session-source registration must include Codex and Claude provider bridges.
  [@test] ../sessions/testSrc/AgentSessionProviderBridgesTest.kt

- Provider results for a path load must be merged and sorted by `updatedAt` descending.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- If at least one provider succeeds, successful threads must be shown and failed providers must surface provider-local warning rows.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt

- If all providers fail for a path load, a blocking path error must be shown and provider warning rows for that load must be suppressed.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt

- Unknown provider totals must propagate through `hasUnknownThreadCount` to tree state.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt
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
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeStatePersistenceTest.kt

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

- Tree rendering must preserve precedence and exclusivity rules:
  - error row suppresses warning/empty rows for that path,
  - warning rows suppress empty row,
  - `More` rows preserve exact/unknown count semantics.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Activation policy must follow IntelliJ tree conventions:
  - single-click selects rows,
  - single-click actions are reserved for `More...` rows,
  - Enter/double-click open project/worktree/thread/sub-agent rows.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Context-menu selection policy must preserve multi-selection when right-clicking an already selected row and retarget selection when right-clicking an unselected row.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Thread and sub-agent open routing must follow mode policy defined in `spec/agent-dedicated-frame.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Tree/chat tab selection synchronization must resolve project/worktree/thread/sub-agent identities to stable tree IDs.
  [@test] ../sessions/testSrc/SessionTreeSelectionSyncTest.kt

- New-session row affordances for project/worktree rows must be hover-or-selection based and must offer:
  - quick create with last used provider when standard mode is supported,
  - provider popup entries split into Standard and YOLO sections.
  [@test] ../sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt

- Session-driven thread title updates must refresh open chat tab metadata and editor-tab presentation.
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt
  [@test] ../chat/testSrc/AgentChatTabSelectionServiceTest.kt

- Codex thread discovery must default to app-server source; rollout discovery remains an explicit compatibility override defined in `spec/agent-sessions-codex-rollout-source.spec.md`.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Branch mismatch between thread origin and current worktree branch must show warning confirmation before opening chat.
  [@test] ../sessions/testSrc/AgentSessionsOpenModeRoutingTest.kt

- Shared command mapping, editor-tab popup actions, archive gating, and visibility primitives must follow `spec/agent-core-contracts.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt
  [@test] ../sessions/testSrc/AgentSessionsEditorTabActionsTest.kt
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

- Batch archive must archive all targets whose providers support archive, while unsupported targets are skipped without blocking successful targets.
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

- Unarchive flow for previously archived Codex targets must restore thread visibility on refresh without requiring tool-window recreation.
  [@test] ../sessions/testSrc/AgentSessionsServiceArchiveIntegrationTest.kt

- Tool window factory must create Swing panel content and register tool-window gear actions.
  [@test] ../sessions/testSrc/AgentSessionsToolWindowFactorySwingTest.kt
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Claude quota hint visibility and acknowledgement must follow eligibility/ack/widget-enabled gating rules.
  [@test] ../sessions/testSrc/AgentSessionsSwingQuotaHintTest.kt
  [@test] ../sessions/testSrc/AgentSessionsClaudeQuotaWidgetActionRegistrationTest.kt

## User Experience
- Project rows are always expandable and may show worktree children.
- Open project rows must be visually emphasized via stronger title weight.
- Closed project rows must remain readable but visually de-emphasized relative to open rows.
- Default project visibility must include all open projects and up to 3 closed recent projects; additional closed projects appear behind `More`.
- Thread rows use a provider-aware leading icon; non-`READY` activities add an overlay badge, and rows show a right-aligned relative activity time.
- Thread-row archive context menu applies to current multi-selection when invoked from a selected thread and shows `Archive Selected (N)` when `N > 1`.
- Single-click on normal rows selects only; open happens on Enter or double-click.
- Project/worktree rows expose new-session affordances when hovered/selected.
- Provider warnings are inline and non-blocking when partial data exists.
- Blocking errors are inline and non-openable.

## Data & Backend
- Open projects may use long-lived provider sessions where available.
- Closed project/worktree loads may use path-scoped short-lived provider calls.
- Aggregation normalizes provider differences (paging/count capability) into one state model.
- Sessions service must not impose global CLI home overrides; provider clients own process environment rules.
- UI-layer migration to Swing does not change backend/service contracts.

## Error Handling
- Missing provider tooling must produce provider-specific messages.
- Unexpected provider failures must map to provider-unavailable warnings when partial data exists.
- Load failures should preserve previously loaded thread data where safe.
- Batch archive/unarchive failures should isolate to failing targets and preserve successful target state updates.
- Chat metadata cleanup failures during archive must be logged and must not block successful thread removal/refresh.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsService*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwing*Test'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowFactorySwingTest'`

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
