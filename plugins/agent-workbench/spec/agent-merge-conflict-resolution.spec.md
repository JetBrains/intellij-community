---
name: Agent Merge Conflict Resolution
description: Requirements for agent-assisted merge conflict resolution via the generic non-iterative VCS merge action contributor API, covering Changes, the Git conflicted-file editor banner, and one-shot modal merge dialog handoff into a pinned standard Agent Workbench thread.
targets:
  - ../../vcs-merge/src/AgentMergeResolveActionProvider.kt
  - ../../vcs-merge/src/AgentResolveConflictsAction.kt
  - ../../vcs-merge/testSrc/AgentResolveConflictsActionTest.kt
  - ../../vcs-merge/src/AgentVcsMergeSessionService.kt
  - ../../sessions/src/AgentSessionProviderMenuActions.kt
  - ../../vcs-merge/resources/intellij.agent.workbench.vcs.merge.xml
  - ../../vcs-merge/resources/messages/AgentVcsMergeBundle.properties
  - ../../sessions/src/state/AgentSessionUiPreferencesStateService.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../git4idea/src/git4idea/conflicts/MergeConflictResolveUtil.kt
  - ../../../../platform/vcs-impl/resources/META-INF/VcsExtensionPoints.xml
  - ../../../../platform/vcs-impl/shared/src/com/intellij/openapi/vcs/changes/ui/ChangesBrowserConflictsNode.kt
  - ../../../../platform/vcs-impl/shared/src/com/intellij/openapi/vcs/merge/MergeResolveActionProvider.kt
  - ../../../../platform/vcs-impl/shared/src/com/intellij/openapi/vcs/merge/MergeResolveActionSupport.kt
  - ../../../../platform/vcs-impl/shared/src/com/intellij/openapi/vcs/merge/MergeResolveWithAgentContext.kt
  - ../../../../platform/vcs-impl/src/com/intellij/openapi/vcs/merge/MultipleFileMergeDialog.kt
  - ../../../../platform/vcs-impl/src/com/intellij/openapi/vcs/merge/flow/OneShotMergeFlowDelegate.kt
  - ../../../../platform/vcs-impl/testSrc/com/intellij/openapi/vcs/merge/flow/OneShotMergeFlowDelegateTest.kt
  - ../../../../platform/vcs-impl/shared/testSrc/com/intellij/openapi/vcs/changes/ui/ChangesBrowserConflictsNodeTest.kt
  - ../../git4idea/tests/git4idea/conflicts/GitMergeConflictEditorNotificationProviderTest.kt
  - ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
---

# Agent Merge Conflict Resolution

Status: Draft
Date: 2026-04-05

## Summary
Define agent-assisted merge conflict resolution as a handoff from existing non-iterative VCS conflict surfaces into a pinned standard Agent Workbench thread backed by a plugin-owned merge session.

The Changes conflicts surface, the Git conflicted-file editor banner, and the one-shot modal merge dialog are launch surfaces for the same backend workflow. These surfaces consume a generic VCS merge action contributor EP and pass a lightweight shared merge context through the standard IntelliJ action update and perform pipeline. The modal dialog is not a second workspace: it stays open until handoff succeeds, then closes and yields to the pinned thread. Review and follow-up happen in normal IDE surfaces such as editors, Changes, and VCS state, not in a separate modeless merge dialog.

Agent Workbench owns the only concrete contributed action today. Platform and `git4idea` stay decoupled from agent action ids and agent services; merge-provider resolution and merge-specific customizer derivation happen inside the agent plugin for non-iterative launch surfaces. Registry-gated iterative merge affordances may reuse the same backend, but they do not define the primary UX contract for this feature.

## Goals
- Let users delegate merge resolution from existing conflict surfaces without introducing a second visible merge workspace.
- Keep platform and `git4idea` decoupled from agent-specific action ids and service implementations.
- Keep merge application inside IDE-controlled merge models and VCS conflict callbacks.
- Let the agent ask clarifying questions in a normal Agent Workbench thread.
- Make repeated launches for the same active conflict set converge on a single backend merge session.

## Non-goals
- Introducing a modeless merge dialog as the primary review or apply surface.
- Running merge handoff through plan mode or the global prompt-entry flow.
- Replacing single-file merge-viewer actions.
- Supporting binary-file conflict resolution.
- Treating registry-gated iterative merge UI as the primary subsystem contract.
- Embedding `MergeProvider` or `MergeDialogCustomizer` in the shared non-iterative action context.

## Requirements
- The platform must define a generic extension point `com.intellij.openapi.vcs.merge.resolveActionProvider` for non-iterative merge actions, and non-iterative merge surfaces must consume contributed actions through that EP rather than referencing agent-specific action ids.

- `MergeResolveActionSupport` must build action events with `PROJECT`, `CONTEXT_COMPONENT`, and `MergeResolveWithAgentContext.KEY`, and it must drive contributed actions through normal `ActionUtil.updateAction` and `ActionUtil.performAction` calls.
  [@test] ../../../../platform/vcs-impl/shared/testSrc/com/intellij/openapi/vcs/changes/ui/ChangesBrowserConflictsNodeTest.kt
  [@test] ../../../../platform/vcs-impl/testSrc/com/intellij/openapi/vcs/merge/flow/OneShotMergeFlowDelegateTest.kt
  [@test] ../../git4idea/tests/git4idea/conflicts/GitMergeConflictEditorNotificationProviderTest.kt

- `MergeResolveWithAgentContext` for non-iterative launch surfaces must remain lightweight and contain only the project, conflicted files, and optional launch-handoff callbacks. It must not carry `MergeProvider` or `MergeDialogCustomizer`.

- Contributed non-iterative merge actions must be ordered by `MergeResolveActionProvider.order` across all launch surfaces.
  [@test] ../../../../platform/vcs-impl/shared/testSrc/com/intellij/openapi/vcs/changes/ui/ChangesBrowserConflictsNodeTest.kt
  [@test] ../../../../platform/vcs-impl/testSrc/com/intellij/openapi/vcs/merge/flow/OneShotMergeFlowDelegateTest.kt
  [@test] ../../git4idea/tests/git4idea/conflicts/GitMergeConflictEditorNotificationProviderTest.kt

- The Changes conflicts surface must expose `Resolve with Agent` as a contributed conflict-node action, honor normal action update presentation for visibility and enabled state, and render disabled state with tooltip text.
  [@test] ../../../../platform/vcs-impl/shared/testSrc/com/intellij/openapi/vcs/changes/ui/ChangesBrowserConflictsNodeTest.kt

- Invoking the Changes conflicts action must go through `ActionUtil.performAction` rather than bypassing the standard action pipeline.
  [@test] ../../../../platform/vcs-impl/shared/testSrc/com/intellij/openapi/vcs/changes/ui/ChangesBrowserConflictsNodeTest.kt

- The one-shot modal merge dialog must expose visible contributed merge actions in the right-side action column, provide shared merge action context containing project, unresolved files, a dialog-close callback, and a launch-validity check, and allow contributed actions to render custom dialog components through the standard IntelliJ custom-component action contract.
  [@test] ../../../../platform/vcs-impl/testSrc/com/intellij/openapi/vcs/merge/flow/OneShotMergeFlowDelegateTest.kt
  [@test] ../../vcs-merge/testSrc/AgentResolveConflictsActionTest.kt

- The Git conflicted-file editor banner must keep `Resolve conflicts…` as the first link and append `Resolve with Agent` as a second link when the agent action is available and enabled for that single-file merge context.
- The Git conflicted-file banner must build the same shared merge action context shape used by other non-modal launch surfaces and invoke the action through the standard action pipeline.
- The existing Git “resolve in progress” banner remains manual-window-only and must not gain agent-session state in v1.
  [@test] ../../git4idea/tests/git4idea/conflicts/GitMergeConflictEditorNotificationProviderTest.kt

- For non-iterative launch surfaces, the agent plugin must resolve the merge provider from the conflicted files via `ProjectLevelVcsManager` and derive merge titles through `mergeProvider.createDefaultMergeDialogCustomizer()` rather than depending on merge-specific objects in the shared action context.
- If no merge provider can be resolved from the conflicted files, the contributed action must be unavailable for that context.

- Modal merge dialogs must close immediately after the user activates `Resolve with Agent` with a concrete provider choice, before the Agent thread is launched or focused.
- If provider selection fails before launch begins, the source merge UI must remain usable.
- If launch or preparation fails after a modal dialog closes, the user should get normal project-level error messaging and reopen merge from standard VCS entry points if needed.

- Merge handoff must open a standard Agent Workbench thread through `AgentSessionLaunchService.createNewSession(...)`; it must not use plan mode or global prompt-entry routing.
- Active merge-thread tabs must be pinned while their merge session is active. When the merge session finishes or is disposed, the pin-only lifecycle must be released and the transcript may remain open as a normal closable tab.

- Launches that are intentionally distinct must carry distinct `singleFlightDiscriminator` values so prompt-launch deduplication does not collapse separate merge sessions.
  [@test] ../../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt

- `Resolve with Agent` must store the last used merge provider and launch mode in shared Agent Workbench UI preferences, independently from general new-thread preferences.
- If exactly one enabled provider entry is available, the action may launch directly and should use that provider's icon.
- If multiple enabled provider entries are available in the one-shot modal merge dialog, `Resolve with Agent` must render as a dialog-native compound button with a primary action and selector affordance.
- If multiple enabled provider entries are available and a last used merge provider plus launch mode exists, the primary action must launch directly with that provider entry while the selector continues to expose the full provider list.
- If multiple enabled provider entries are available and no merge-specific preference exists, primary activation must require an explicit provider choice and must reuse the same provider action list and sectioning model used by `New Thread`, including Standard and YOLO sections.
- Provider selection UI must open from the invoking control rather than a centered chooser.
- If no provider is available, the action must be disabled and explain how to configure Agent Workbench.
  [@test] ../../../../platform/vcs-impl/shared/testSrc/com/intellij/openapi/vcs/changes/ui/ChangesBrowserConflictsNodeTest.kt
  [@test] ../../vcs-merge/testSrc/AgentResolveConflictsActionTest.kt

- The plugin must own the active merge-session lifecycle. There is no second visible modeless merge workspace for this feature.
- Session preparation must build merge models with `MergeConflictIterativeDataHolder`, resolve auto-resolvable conflicts before agent launch, and keep IDE merge APIs as the only conflict-apply path.
- If all selected conflicts resolve during preparation, the service must finalize them, update VCS conflict state, show a lightweight success notification, and skip agent-thread launch.

- The initial merge prompt must frame merge resolution as a normal Agent Workbench thread: normal IDE tools, git or file workflow, and installed skills are allowed, and success is defined by conflicted files leaving VCS conflict state.
- The initial merge prompt must also instruct the agent to stage resolved files and continue an in-progress Git merge, rebase, or cherry-pick when needed.
- Merge sessions must finalize files that leave VCS conflict state through normal workflow, including ordinary editing plus VCS actions such as staging a resolved Git file.

- The existing blocking `AbstractVcsHelper.showMergeDialog(...)` contract must remain intact for legacy and manual callers. Agent merge resolution is an additive async/plugin entrypoint.

## User Experience
- Changes renders contributed merge actions inline with the built-in `Resolve` link, using standard enabled or disabled action presentation. Disabled contributed actions remain visible and surface their descriptions in the tooltip.
- The Git conflicted-file editor banner keeps `Resolve conflicts…` as its primary manual link and, when eligible, shows each enabled contributed merge action as an additional link in provider order. Disabled contributed actions are omitted there.
- The one-shot modal merge dialog shows one visible control per contributed merge action beside `Accept Yours`, `Accept Theirs`, and `Merge...`. Standard contributed actions render as ordinary dialog buttons; contributed custom components may render compound dialog controls. `Resolve with Agent` uses a dialog-native button-with-selector pattern when multiple provider entries are available, while preserving provider order, icon, enabled state, and description tooltip.
- After the dialog closes, focus moves to a pinned standard Agent Workbench thread instead of a second merge dialog.
- The agent may ask clarifying questions in that pinned thread, and the user replies there.
- Review after handoff happens in normal IDE editors, Changes, and VCS surfaces.
- If preparation resolves everything automatically, the user gets a lightweight success notification and no agent thread is opened.

## Data & Backend
- The backend session key must be derived from the active conflicted file set so duplicate launches for the same active merge set converge on one session in the project.
- The merge session owns unresolved-file tracking, merge-model preparation, file finalization, dirty-scope updates, and pin cleanup.
- The generic non-iterative action layer owns only action presentation and event wiring; merge-specific object lookup for non-iterative launches stays in the agent plugin.
- The merge prompt plus prompt context are responsible for supplying conflicted file lists and merge metadata; the thread itself remains a normal Agent Workbench chat/editor surface.
- Unsupported binary files may remain in the active merge set for manual resolution. Text conflicts may still launch and are expected to be resolved through normal workflow.

## Error Handling
- Preparation or launch failures must surface `Resolve with Agent Failed` messaging. If the dialog already closed, the error is shown in normal project UI.
- Unsupported merge payloads must fail handoff cleanly without corrupting already-prepared merge state.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.vcs.merge.tests --test com.intellij.agent.workbench.vcs.merge.AgentResolveConflictsActionTest`
- `./tests.cmd --module intellij.platform.vcs.impl.tests --test com.intellij.openapi.vcs.merge.flow.OneShotMergeFlowDelegateTest`
- `./tests.cmd --module intellij.platform.vcs.impl.shared.tests --test com.intellij.openapi.vcs.changes.ui.ChangesBrowserConflictsNodeTest`
- `./tests.cmd --module intellij.vcs.git.tests --test git4idea.conflicts.GitMergeConflictEditorNotificationProviderTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest`

## Open Questions / Risks
- Direct service-level coverage is still missing for auto-resolve-only completion, stale-session tool rejection, and the no-merge-provider path in `AgentResolveConflictsAction`.
- If registry-gated iterative merge affordances later add richer status UI or ownership rules, this spec must be extended explicitly rather than treated as implied behavior.
- Session identity currently keys off the conflicted file set; if future UX needs separate sessions for the same file set under different policies, the contract must change deliberately.
- If future consumers need more than project, files, and handoff callbacks in the shared non-iterative context, that contract must be revised carefully to avoid pulling `intellij.platform.vcs` APIs back into `vcs-impl/shared`.

## References
- `./agent-core-contracts.spec.md`
- `./agent-sessions.spec.md`
- `./actions/new-thread.spec.md`
