# Hard Cutover: Agent Sessions UI to Swing Async Tree

Status: Implemented
Date: 2026-02-28

## Scope
Full replacement of sessions tool-window UI from Compose/Jewel to IntelliJ Swing tree APIs (`StructureTreeModel` + `AsyncTreeModel` + `Tree`) with no compatibility layer and no feature flag.

## Implementation Status
- Spec contracts updated for Swing behavior and test ownership.
  Status: Done
  Evidence: `spec/agent-sessions.spec.md`, `spec/agent-sessions-testing.spec.md`, `spec/actions/new-thread.spec.md`

- Swing tool-window panel and controller path in place.
  Status: Done
  Evidence: `sessions/src/AgentSessionsToolWindow.kt`, `sessions/src/AgentSessionsToolWindowFactory.kt`

- Tree domain snapshot logic extracted from UI framework types.
  Status: Done
  Evidence: `sessions/src/SessionTree.kt` (`SessionTreeSnapshot`, IDs/nodes, visibility helpers)

- Native async tree stack used.
  Status: Done
  Evidence: `StructureTreeModel`, `AsyncTreeModel`, `Tree`, `TreeUtil.installActions`, `TreeUIHelper.installTreeSpeedSearch`

- Expansion/collapse and on-demand loading synchronized with persisted UI state.
  Status: Done
  Evidence: `TreeExpansionListener` + `AgentSessionsTreeUiStateService` integration in `AgentSessionsToolWindow.kt`

- Activation/selection policy aligned to IntelliJ conventions.
  Status: Done
  Evidence:
  - single-click actions only for `More...` rows,
  - Enter/double-click open project/worktree/thread/sub-agent,
  - for openable parent rows, double-click open/focus takes precedence over expansion,
  - context-menu selection retarget/preserve policy.

- Row actions and context menus implemented in Swing.
  Status: Done
  Evidence:
  - thread archive context menu with multi-select count,
  - hover/selection new-session affordances for project/worktree rows,
  - quick-create last provider + action popup with Standard/YOLO sections and CLI enablement.

- Top-area states migrated to Swing components.
  Status: Done
  Evidence: global empty/loading text + Swing `ClaudeQuotaHintPanel` in `AgentSessionsToolWindow.kt`

- Compose code/dependencies removed from sessions module.
  Status: Done
  Evidence:
  - sessions module has no Compose source files/imports,
  - `intellij.agent.workbench.sessions.iml` and `sessions/BUILD.bazel` contain no Compose dependencies,
  - plugin descriptor points directly to Swing factory.

## Rationale for Not Using Compose
- The sessions tool window now follows IntelliJ-native tree interaction conventions directly (selection, activation, popup behavior).
- Removing the dual UI stack reduces maintenance cost and eliminates framework-bridging logic.
- Swing async tree integrates with platform tree tooling (speed search, expansion behavior, action infrastructure) without compatibility glue.

## Validation Targets
- Swing interaction/rendering/state/new-session/factory coverage:
  - `sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt`
  - `sessions/testSrc/AgentSessionsSwingTreeInteractionTest.kt`
  - `sessions/testSrc/AgentSessionsSwingTreeStatePersistenceTest.kt`
  - `sessions/testSrc/AgentSessionsSwingNewSessionActionsTest.kt`
  - `sessions/testSrc/AgentSessionsSwingQuotaHintTest.kt`
  - `sessions/testSrc/AgentSessionsToolWindowFactorySwingTest.kt`
