---
name: Agent Launch Surface Switching
description: Design notes for representing terminal and ACP UI as switchable launch surfaces for Agent Workbench sessions.
targets:
  - ../../prompt/core/src/AgentPromptModels.kt
  - ../../prompt/ui/src/**/*.kt
  - ../../lib-agent/sessions-core/src/launch/AgentSessionSurfaces.kt
  - ../../lib-agent/sessions-core/src/launch/AgentSessionLaunchPlanner.kt
  - ../../lib-agent/sessions-core/src/launch/AgentSessionOutOfBandLaunch.kt
  - ../../lib-agent/sessions-core/src/providers/AgentSessionLaunchProfiles.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../chat/src/AgentChatCustomContent.kt
  - ../../chat/src/AgentChatStartupIntent.kt
  - ../../chat/src/AgentChatFileEditorState.kt
  - ../../chat/src/AgentChatTabModel.kt
  - ../../chat/src/AgentChatVirtualFile.kt
  - ../../chat/src/AgentChatTabsStateService.kt
  - ../../engine/src/ui/EngineSessionProviderDescriptor.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpOutOfBandLaunch.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpLaunchProfileContributor.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpLauncherService.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpSessionManager.kt
---

# Agent Launch Surface Switching

Status: Draft
Date: 2026-06-29

## Summary

Agent Workbench should model terminal sessions and native ACP UI sessions as different launch surfaces, not as
different launch modes, generation models, or provider identities. The same conceptual agent, such as Codex or
Claude Code, may be available through more than one surface: a managed terminal wrapper and an ACP-backed native
UI. Users should be able to choose the surface when starting a task, and eventually open a compatible existing
thread in another surface when the provider can bridge that state safely.

The key design rule is to keep four independent axes separate:

- Provider: the integration family, such as `codex`, `claude`, or `acp`.
- Launch target: the concrete thing to start, such as an ACP catalog agent id.
- Launch mode: permission or safety behavior, such as standard versus YOLO.
- Launch surface: where the interaction is hosted, such as terminal or ACP UI.

## Current State

- Terminal providers (`codex`, `claude`, and similar providers) build an `AgentSessionTerminalLaunchSpec` and
  render inside the embedded terminal in Agent Chat.
- ACP uses `AgentSessionOutOfBandLaunch` to skip terminal dispatch. `AcpOutOfBandLaunch` prepares an ACP session
  and sends the initial prompt directly to the runtime.
- `AgentChatCustomContentProvider` is currently selected only by provider. This means `provider=acp` always uses
  custom UI, while `provider=codex` and `provider=claude` always use terminal content.
- Launch profile migration work already separates `launchTargetId` from generation settings. That is the right
  foundation, but it still needs an explicit launch-surface axis.

## Decision

Introduce an explicit launch surface id field:

```kotlin
val surfaceId: String?

const val AGENT_SESSION_SURFACE_TERMINAL = "terminal"
const val AGENT_SESSION_SURFACE_ACP = "acp"
```

The field should be carried through launch profiles, prompt launch requests, launch intents, chat startup intents,
tab runtime snapshots, editor state, and out-of-band launch context. It is intentionally string-backed, not an enum,
so providers can add future surfaces without changing the shared core API. Existing terminal providers default to
`"terminal"`. ACP launch profiles default to `"acp"` unless the user explicitly selects terminal launch.

## Requirements

- `AgentPromptLaunchProfile` must store a nullable `surfaceId`. Null means provider default for backward
  compatibility.
- `AgentSessionLaunchIntent` and `AgentSessionOutOfBandLaunchContext` must carry the resolved `surfaceId`.
- `AgentSessionOutOfBandLaunch.handles(context)` must be context-aware. Provider-wide matching is too broad
  because the same provider/target may later support both terminal and ACP UI surfaces.
- `AgentChatCustomContentProvider` lookup must be context-aware instead of provider-only. It needs enough data to
  decide whether a tab should render native content or attach a terminal.
- `AgentChatStartupIntent`, `AgentChatTabRuntime`, and `AgentChatFileEditorState` must persist `surfaceId` as tab
  metadata. On restore, `launchProfileId` is authoritative: the current effective profile settings resolve the
  provider, launch target, launch mode, surface, and generation settings. Persisted `surfaceId` is only a fallback
  cache when no profile can be resolved.
- A launch profile must not store an ACP agent id in `generationSettings.modelId`. ACP agent identity belongs in
  `launchTargetId`; model selection can be added later from ACP handshake capabilities.

## Launch Flow

New-session launch should resolve in this order:

1. Resolve profile and normalize `provider`, `launchTargetId`, `launchMode`, `surfaceId`, and generation
   settings.
2. Build a launch plan from the resolved intent.
3. If `surfaceId == "acp"`, let `AgentSessionOutOfBandLaunch.forContext(context)` handle startup and suppress
   terminal prompt dispatch.
4. If `surfaceId == "terminal"`, attach the embedded terminal and use the provider's terminal launch spec.
5. Persist the resolved `surfaceId` in the chat tab runtime state as fallback/cache metadata.

`AgentSessionLaunchMode` must not be reused for this. It describes permission and safety behavior, not UI/runtime
hosting.

## Custom Content Resolution

The current API:

```kotlin
AgentChatCustomContent.find(provider)
```

should become context-based, for example:

```kotlin
data class AgentChatContentContext(
  val provider: AgentSessionProvider,
  val threadId: String,
  val threadIdentity: String,
  val launchTargetId: String?,
  val surfaceId: String?,
)

AgentChatCustomContent.find(context)
```

Then ACP UI is installed only when the resolved surface says so. Terminal launch for an ACP-capable target remains
possible without fighting the provider-wide custom-content registration.

## Claude and Codex Wrappers

Claude Code and Codex should appear to users as agent choices with a surface switch, but the internal launch
recipes should stay explicit.

Suggested mapping:

- Codex terminal: `provider=codex`, `surfaceId="terminal"`.
- Codex ACP UI: current behavior remains `provider=acp`, `launchTargetId=<codex-acp-agent-id>`, `surfaceId="acp"`.
- Claude terminal: `provider=claude`, `surfaceId="terminal"`.
- Claude ACP UI: current behavior remains `provider=acp`, `launchTargetId=<claude-acp-agent-id>`, `surfaceId="acp"`.

The UI can group these as one visible agent with a segmented surface selector. The model should not fake that by
creating one provider id per ACP registry agent or by storing the ACP agent id as a generation model.

Future provider unification may allow `provider=codex` to expose both `surfaceId="terminal"` and `surfaceId="acp"`,
but this migration must be explicit. This milestone does not remap existing ACP profiles away from `provider=acp`.

## Existing Thread Switching

There are two different features here and they should not be conflated.

Launch-time surface selection is the first milestone. It starts a new task on the chosen surface.

Opening an existing thread in another surface is harder and should require an explicit provider bridge. A terminal
PTY is an unstructured byte stream; an ACP UI thread expects structured runtime events, permissions, auth state,
and session identity. There is no safe generic conversion from an arbitrary live terminal process into an ACP UI
session.

Supported cases can be added per provider:

- Resume ACP UI from ACP runtime binding: safe when the thread projection stores `runtimeKind=Acp`, agent id, and
  enough session metadata for `AcpSessionManager` to rehydrate ownership.
- Open terminal for an ACP-backed thread: possible only if the ACP agent exposes a terminal resume command or the
  wrapper can start a terminal view against the same backend session id.
- Convert terminal Codex/Claude to ACP UI: generally not safe unless the provider exposes the same durable thread
  id through both runtimes and ACP can attach to that existing conversation.

The UI should distinguish these outcomes:

- Switch view: reuses the same running/durable backend session.
- Open companion view: opens another view on the same thread but does not migrate runtime ownership.
- Start new session with same context: starts a new task with copied prompt/context when no bridge exists.

## Container Sessions

Container mode is orthogonal to launch surface but affects support. The existing container design relies on a host
CLI process and MCP/tool routing into the container. Claude Code can be constrained with disabled built-in tools;
Codex terminal mode is not equivalent because built-in tools may bypass MCP.

For ACP UI, container support should be decided per ACP agent/wrapper:

- If the ACP process runs on host and all filesystem/tool operations go through IDE/MCP services, container mode can
  follow the existing host-runner model.
- If the ACP process itself must run in the workspace/container, auth and binary distribution become part of the
  ACP launch target contract.
- Launch surface must not imply container compatibility. Add a separate capability flag when needed.

## UX Notes

- The New Task surface should present agent choice and launch surface as separate controls.
- The default for ACP registry agents should be ACP UI.
- The terminal option should remain visible for wrappers where terminal behavior is valuable for auth, debugging, or
  expert workflows.
- Existing-thread actions should use conservative wording. If a true state-preserving switch is unavailable, the UI
  should not label the action as a switch.

## Migration Plan

1. Add string-backed `surfaceId` constants/helpers and thread them through launch profile state, resolved profiles, prompt requests,
   launch intents, startup intents, tab runtime snapshots, and editor state.
2. Make `AgentSessionOutOfBandLaunch` context-aware and gate ACP out-of-band launch on `surfaceId == "acp"`.
3. Make `AgentChatCustomContentProvider` context-aware and install ACP UI only for `surfaceId == "acp"` tabs.
4. Generate ACP launch profiles from stable ACP agent ids with `surfaceId="acp"` by default.
5. Add terminal-surface profiles or profile variants for wrappers that support both surfaces.
6. Add provider-specific bridge APIs for existing-thread surface actions only after the launch-time model is stable.

## Testing

- Launch profile serialization keeps `surfaceId` and defaults missing values safely.
- Launch planner preserves `surfaceId` through `AgentSessionLaunchIntent` and out-of-band context.
- ACP UI launch suppresses terminal dispatch only when `surfaceId == "acp"`.
- Terminal launch for an ACP-capable target does not install custom content or call `AcpOutOfBandLaunch`.
- Restored chat tabs resolve current effective profile settings from persisted `launchProfileId`; persisted `surfaceId`
  is only fallback/cache metadata.
- Existing terminal providers keep their current behavior when `surfaceId` is absent.

## Rejected Alternatives

- Use launch mode for terminal versus ACP UI. Rejected because launch mode is about permissions/safety.
- Use generation model for ACP agent selection. Rejected because ACP agents are launch targets, not models.
- Create one provider id per ACP wrapper. Rejected because provider ids describe integration families, while ACP
  registry entries are launch targets.
- Make `AgentChatCustomContentProvider` provider-wide and add exceptions. Rejected because exceptions would spread
  surface decisions across editor startup, restore, and launch code.

## Open Questions

- What is the minimum provider bridge needed for a real existing-thread switch?
- For Codex, can ACP attach to a thread created by terminal Codex using the same durable thread id, or are these
  separate backends in practice?
- For Claude, is there an ACP wrapper with enough resume/auth capability to support the same thread across surfaces?
- Should container support be expressed as a launch target capability matrix: terminal host-runner, ACP host-runner,
  ACP container-runner?
