---
name: Agent Sessions Sleep Prevention
description: Requirements for preventing idle system sleep while Agent Workbench has active processing or review work.
targets:
  - ../sessions/src/**/*.kt
  - ../sessions/resources/intellij.agent.workbench.sessions.xml
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../sessions/testSrc/*.kt
---

# Agent Sessions Sleep Prevention

Status: Draft
Date: 2026-03-08

## Summary
Define plugin-local system sleep prevention for Agent Workbench. When any loaded session thread is actively working, the IDE must prevent idle system sleep on macOS and Windows. Linux is out of scope for v1.

Sleep prevention must acquire immediately when work starts and release with a 30-second debounce after work stops. The debounce is release-only and exists to avoid sleep-block flapping during brief session refresh gaps or status churn.
IDE Power Save Mode is a hard override: while it is enabled, sleep prevention must stay released.

## Goals
- Keep macOS and Windows machines awake while Agent Workbench shows active agent work.
- Use one app-level aggregate across all projects and worktrees instead of per-thread or per-tab blockers.
- Reuse normalized `AgentThreadActivity` instead of provider-specific raw status rules.
- Keep the feature enabled by default but user-controllable.

## Non-goals
- Linux sleep inhibition or any external-process/D-Bus integration.
- Preventing display sleep, screen lock, or explicit user-initiated sleep.
- Inferring activity from pending chat tabs before a thread appears in sessions state.
- Adding new backend activity states or provider-specific wake-lock logic.

## Requirements
- Advanced setting key `agent.workbench.prevent.system.sleep.while.working` must exist, default to `true`, and be exposed in Advanced Settings under the existing Agent Workbench group.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Sessions gear menu must expose `AgentWorkbenchSessions.TogglePreventSleepWhileWorking` and update the same advanced setting.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- The setting and gear action must use localized user-visible text from `AgentSessionsBundle.properties`.
  [@test] ../sessions/testSrc/AgentSessionsGearActionsTest.kt

- Sleep prevention must be driven by one app-level service that observes Agent Workbench sessions state and owns at most one blocker lease at a time.
  Background projects and worktrees count; behavior must not be limited to the selected tree row, selected tab, or focused project.
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

- Aggregate working state must be `true` if and only if any loaded thread in any project or worktree has normalized activity `PROCESSING` or `REVIEWING`.
  Threads with `READY` or `UNREAD` must not keep the blocker held.
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

- When the setting is enabled and aggregate working state becomes active, the service must acquire sleep prevention immediately with no debounce.
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

- When aggregate working state becomes inactive, the service must start a 30-second release debounce instead of releasing immediately.
  If aggregate working state becomes active again before the debounce expires, the pending release must be canceled and the blocker must remain held.
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

- While IDE Power Save Mode is enabled, sleep prevention must remain released even if the setting is enabled and qualifying work is active.
  Turning Power Save Mode on must cancel any pending debounce and release immediately.
  Turning Power Save Mode off must acquire immediately if the setting is enabled and qualifying work is still active.
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

- Turning the setting off must release sleep prevention immediately and cancel any pending debounce.
  Turning the setting back on while aggregate working state is already active must acquire immediately.
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

- Service disposal must release sleep prevention immediately and cancel any pending debounce.
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

- Native sleep inhibition must remain plugin-local and must be hidden behind a small internal abstraction so the rest of the sessions module depends only on acquire/release semantics.
  [@test] ../sessions/testSrc/AgentSleepInhibitorTest.kt

- macOS implementation must use `NSProcessInfo.beginActivity...` with options that disable idle system sleep while the blocker is held.
  `MacUtil.wakeUpNeo()` must not be used for this feature because it explicitly allows idle system sleep.
  [@test] ../sessions/testSrc/AgentSleepInhibitorTest.kt

- Windows implementation must use `SetThreadExecutionState` to request continuous system-required execution while the blocker is held and must clear that request on release.
  Windows-specific thread affinity must be guaranteed by a service-owned dedicated single-thread coroutine dispatcher so acquire and release always run on the same OS thread.
  `Dispatchers.IO.limitedParallelism(1)` and bounded application-pool executors are not sufficient for this requirement.
  [@test] ../sessions/testSrc/AgentSleepInhibitorTest.kt

- Linux and unsupported environments must use a no-op implementation in v1.
  The feature must not spawn external helper processes and must not add a D-Bus dependency in this iteration.
  [@test] ../sessions/testSrc/AgentSleepInhibitorTest.kt

- JNA-backed implementations must be guarded by native availability checks such as `JnaLoader.isLoaded()`.
  If native access is unavailable or an inhibitor call fails, the feature must fail open: log the failure and continue normal sessions behavior without user-facing error UI.
  [@test] ../sessions/testSrc/AgentSleepInhibitorTest.kt
  [@test] ../sessions/testSrc/AgentSessionSleepPreventionServiceTest.kt

## User Experience
- The feature is on by default.
- The Sessions gear menu exposes a toggle labeled `Prevent System Sleep While Agent Is Working`.
- Advanced Settings exposes the same behavior under the Agent Workbench group.
- Toggling the setting affects subsequent acquire/release behavior immediately.
- Power Save Mode temporarily suppresses the runtime effect without changing the stored toggle value.
- No new tool-window badge, notification, or status text is required for this feature.

## Data & Backend
- Activity aggregation must consume normalized `AgentThreadActivity` values already present in sessions state.
- The service may observe `AgentSessionReadService.stateFlow()` or the underlying app-level sessions state store, but behavior must stay event-driven; polling loops are not allowed.
- The service must also observe IDE Power Save Mode changes and treat Power Save Mode as a hard override on blocker ownership.
- The 30-second debounce applies only to releasing an already-held blocker. Acquisition is always immediate.

## Error Handling
- Native inhibitor errors must not block session loading, refresh, chat opening, or tool-window rendering.
- Repeated inactive state updates during a pending release debounce must not schedule duplicate release timers.
- Multiple active threads must not cause duplicate acquires, and one thread becoming inactive must not release the blocker while other active threads remain.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsGearActionsTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionSleepPreventionServiceTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSleepInhibitorTest'`

## Open Questions / Risks
- If the IntelliJ Platform later gains a supported cross-platform sleep-inhibition API, the plugin-local inhibitor abstraction may be re-backed by that API without changing the user-visible contract in this spec.

## References
- `spec/agent-sessions.spec.md`
- `community/plugins/agent-workbench/common/src/AgentThreadActivity.kt`
