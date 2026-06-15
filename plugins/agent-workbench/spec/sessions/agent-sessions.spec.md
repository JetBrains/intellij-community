---
name: Agent Threads Tool Window
description: Requirements for the Agent Threads project browser, row model, actions, and owned session UI surface.
targets:
  - ../../claude/sessions/src/**/*.kt
  - ../../codex/sessions/src/**/*.kt
  - ../../junie/sessions/src/**/*.kt
  - ../../pi/sessions/src/**/*.kt
  - ../../terminal/sessions/src/**/*.kt
  - ../../sessions/src/**/*.kt
  - ../../sessions-toolwindow/src/**/*.kt
  - ../../sessions-actions/src/**/*.kt
  - ../../sessions/resources/messages/AgentSessionsBundle.properties
  - ../../sessions-toolwindow/testSrc/*.kt
  - ../../sessions/testSrc/*.kt
  - ../../sessions-actions/testSrc/*.kt
  - ../../pi/sessions/testSrc/*.kt
  - ../../terminal/sessions/testSrc/*.kt
---

# Agent Threads Tool Window

Status: Draft
Date: 2026-06-15

## Summary

Agent Threads is a project-scoped Swing tree for browsing agent threads across providers. This spec owns the visible tool-window product
behavior; refresh mechanics and detailed tree rendering/interaction contracts live in focused specs.

## Requirements

- The tool window must use the native Swing async tree path (`StructureTreeModel`, `AsyncTreeModel`, `Tree`) with no Compose/Jewel
  compatibility UI.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsToolWindowFactorySwingTest.kt

- Project catalog rows must merge open projects and recent projects, exclude the dedicated Agent frame project, and group Git worktrees
  under their parent project when detected.
  [@test] ../../sessions/testSrc/AgentSessionProjectCatalogTest.kt
  [@test] ../../sessions/testSrc/GitWorktreeDiscoveryTest.kt

- Project rows may show branch metadata only for standalone non-default branches. Worktree-backed project rows stay branchless because
  worktree child rows carry branch state.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeCellRendererTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreeModelDiffTest.kt

- Default providers registered for session loading are Codex, Claude, Junie, Pi, and Terminal.
  [@test] ../../sessions/testSrc/AgentSessionProvidersTest.kt

- Tree rows must expose provider-aware thread icons, normalized activity badges, relative activity time, provider warnings, blocking errors,
  empty rows, and `More` rows according to `agent-sessions-tree.spec.md`.

- Session loading, warm snapshots, source update events, and provider result merging must follow `agent-sessions-refresh.spec.md`.

- Thread/sub-agent open routing must follow `../frame/agent-dedicated-frame.spec.md`; shared identity/action contracts must follow
  `../core/agent-core-contracts.spec.md`.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- New-thread affordances for project/worktree rows and toolbars must follow `../actions/new-thread.spec.md`.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsMainToolbarNewThreadActionsTest.kt

- Archive, unarchive, and rename actions must update the session view through provider-backed refresh paths; unsupported provider
  capabilities must hide or disable the corresponding action without blocking supported targets.
  [@test] ../../sessions/testSrc/AgentSessionArchiveServiceIntegrationTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRenameServiceTest.kt
  [@test] ../../sessions-actions/testSrc/AgentSessionsEditorTabActionsTest.kt

- Successful user thread renames must update the normalized title in sessions state and the shared thread presentation model immediately.
  Later provider refreshes may replace the title only when the provider reports a newer authoritative title for the same concrete thread.
  Archive and unarchive must preserve the session title carried by sessions state.
  [@test] ../../sessions/testSrc/AgentSessionRenameServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt
  [@test] ../../sessions/testSrc/AgentArchivedSessionsServiceTest.kt

- The tool window must offer an archived-only thread view alongside the default active view. Opening an archived row unarchives it and
  refreshes both views; archived rows can also be explicitly unarchived. Active activity counters and warm snapshots stay driven by the
  active view, and archived filtering is limited to runtime `All`, `Today`, `Last 7 days`, and `Last 30 days` presets.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreePopupActionsTest.kt
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceTest.kt
  [@test] ../../claude/sessions/testSrc/ClaudeSessionSourceTest.kt
  [@test] ../../junie/sessions/testSrc/JunieSessionSourceTest.kt

- Claude quota hint visibility and acknowledgement must be gated by eligibility, acknowledgement state, and widget availability.
  [@test] ../../claude/sessions/testSrc/AgentSessionsSwingQuotaHintTest.kt
  [@test] ../../claude/sessions/testSrc/AgentSessionsClaudeQuotaWidgetActionRegistrationTest.kt

## User Experience

- Open projects are visually emphasized; closed recent projects are readable but de-emphasized.
- Default visibility includes all open projects and up to three closed recent projects, with additional closed projects behind `More`.
- Single-click selects normal rows. Opening/focusing happens through Enter, double-click, or explicit actions.
- Active view keeps the title header focused on thread activity counters. Archived view replaces those counters with a direct
  return-to-active icon, an archived context label, and a separate range selector.

## Testing / Local Run

- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test "com.intellij.agent.workbench.sessions.toolwindow.AgentSessions*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test "com.intellij.agent.workbench.sessions.AgentSession*IntegrationTest"`
- `./tests.cmd --module intellij.agent.workbench.sessions.actions.tests --test "com.intellij.agent.workbench.sessions.AgentSessions*Test"`

## References

- `agent-sessions-tree.spec.md`
- `agent-sessions-refresh.spec.md`
- `../core/agent-core-contracts.spec.md`
- `../chat/agent-chat-editor.spec.md`
- `../actions/new-thread.spec.md`
