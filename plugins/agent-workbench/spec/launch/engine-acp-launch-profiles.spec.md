---
name: Engine ACP Out-of-Band Launch Profiles
description: Requirements for surfacing ACP agents as Engine launch-profile generation models and launching them without a terminal through the shared session launch pipeline.
targets:
  - ../../lib-agent/sessions-core/src/launch/AgentSessionOutOfBandLaunch.kt
  - ../../lib-agent/sessions-core/resources/intellij.agent.workbench.sessions.core.xml
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../engine/src/ui/EngineSessionProviderDescriptor.kt
  - ../../engine/src/platform/EngineLaunchAgents.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpOutOfBandLaunch.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpEngineLaunchAgentProvider.kt
  - ../../../../../plugins/agent-workbench/acp/resources/intellij.agent.workbench.acp.xml
---

# Engine ACP Out-of-Band Launch Profiles

Status: Draft
Date: 2026-06-26

## Summary
The Engine provider (`AgentSessionProvider` id `acp`) exposes ACP agents in the shared
New-Task launch profiles and launches them **out of band** — preparing an ACP session and sending the
initial prompt directly, with no embedded terminal. This reuses the existing prompt-launch machinery
(`AgentSessionLaunchService.createNewSession`, generation-model selection, initial-message planning)
rather than forking a parallel launch path. The mechanism is generic: a new
`AgentSessionOutOfBandLaunch` extension point lets any provider opt out of terminal dispatch and deliver
a launch through its own runtime, with ACP being the first consumer.

## Goals
- Surface ACP catalog agents as Engine launch-profile **generation models**, so a user picks an ACP
  agent from the same New-Task profile UI used for terminal providers.
- Launch the selected ACP agent without a terminal: prepare its ACP session against the same thread id
  the chat tab opens with, then send the initial prompt.
- Keep terminal providers unchanged: the out-of-band path is engaged only for providers that register an
  `AgentSessionOutOfBandLaunch` and is otherwise inert.

## Non-goals
- ACP session lifecycle, transport, streaming-event mapping, and permission flow (owned by the `acp`
  module and `community/platform/acp`; this spec only covers launch wiring).
- The Engine event-sourced core (event store, projection, reducer).
- Resume/reconnect of an existing ACP thread; this spec covers new-session launch only.

## Requirements
- A provider may register `AgentSessionOutOfBandLaunch` on EP
  `com.intellij.agent.workbench.sessionOutOfBandLaunch`. `forProvider(provider)` returns the first
  extension whose `handles(provider)` is true, or `null` when none match (and short-circuits when the EP
  has no extensions).
- When a matching out-of-band launcher exists for the requested provider,
  `AgentSessionLaunchService.createNewSession` must skip terminal initial-message dispatch by passing
  `AgentInitialMessageDispatchPlan.EMPTY`, and instead deliver the launch by composing
  `AgentSessionOutOfBandLaunch.launch(...)` into the `openedChatHandler` so it runs against the opened
  project once the chat tab is created. When no launcher matches, behavior is unchanged (terminal
  dispatch and the original `openedChatHandler` are preserved).
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
- The out-of-band launcher receives the preallocated thread id, the normalized source path, the selected
  generation `modelId`, and the composed initial prompt, so the runtime prepares and prompts against the
  same thread id the chat tab opened with.
- `EngineSessionProviderDescriptor` sets `supportsPromptLaunch = true` and
  `supportsGenerationModelSelection = true`, and preallocates a concrete Engine thread id
  (`acp:<short-uuid>`) in `buildNewSessionLaunchSpec` so the chat tab and the out-of-band launcher agree
  on the thread id.
- `EngineSessionProviderDescriptor.listAvailableGenerationModels(project)` maps the agents reported by
  `EngineLaunchAgentProvider.availableAgents(project)` to `AgentPromptGenerationModel` entries; with no
  project or no contributed agents it returns an empty list.
- `EngineLaunchAgentProvider` aggregates agents over EP
  `com.intellij.agent.workbench.engine.launchAgentProvider` and returns an empty list when the EP has no
  extensions, keeping the Engine provider functional without the `acp` module present.
- `AcpEngineLaunchAgentProvider` contributes one `EngineLaunchAgent` per `AcpAgentsCatalog` entry, keyed
  by the entry display name.
- `AcpOutOfBandLaunch.handles` matches only the `acp` provider; `launch` resolves the catalog
  entry whose display name equals the selected `modelId`, prepares the ACP session for the thread id, and
  sends the prompt only when it is non-blank. An unresolved `modelId` is a no-op.
  [@test] ../../../../../plugins/agent-workbench/acp/testSrc/AcpThreadEventMapperTest.kt

## User Experience
- ACP agents appear in the New-Task launch-profile generation-model picker under the Engine provider,
  labeled by their catalog display name.
- Selecting an ACP agent and launching opens a chat tab rendering Engine custom content (no terminal),
  with the chosen agent prepared and the initial prompt sent.

## Data & Backend
- Provider id: `acp` (`AgentSessionProvider.from("acp")`).
- Preallocated thread id format: `acp:` + first 8 chars of a random UUID.
- Generation `modelId` carried through the launch equals the ACP catalog entry display name.
- EP contracts:
  - `com.intellij.agent.workbench.sessionOutOfBandLaunch` -> `AgentSessionOutOfBandLaunch` (sessions-core).
  - `com.intellij.agent.workbench.engine.launchAgentProvider` -> `EngineLaunchAgentProvider` (engine).
  - `acp` registers `AcpOutOfBandLaunch` and `AcpEngineLaunchAgentProvider` on the two EPs above.

## Error Handling
- Missing/unknown generation model (no catalog entry for `modelId`): launch is a silent no-op; no terminal
  is opened.
- `acp` module absent (e.g. a product build without AI Assistant): neither EP has extensions, so the
  Engine provider lists no generation models and registers no out-of-band launcher; the shared pipeline
  falls back to its default (terminal) behavior.

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.engine.tests --test com.intellij.agent.workbench.engine.platform.EngineEventStoreTest`
- `./tests.cmd --module intellij.agent.workbench.acp.tests --test com.intellij.agent.workbench.acp.AcpThreadEventMapperTest`
- End-to-end launch verification requires a run configuration with AI Assistant present so
  `intellij.ml.llm.agents.acp` resolves and `AcpAgentsCatalog` is populated.

## Open Questions / Risks
- No dedicated test yet asserts the out-of-band branch end to end (it depends on a populated ACP catalog
  and an opened chat tab). The branch is covered indirectly by `AgentSessionLaunchServiceTest`'s existing
  prompt-launch coverage; a focused test for the EMPTY-dispatch + composed-`openedChatHandler` path should
  be added.
- `modelId` keys on catalog display name rather than a stable agent id; renaming a catalog entry would
  break a persisted profile selection. A stable agent id key is preferable once available.

## References
- `../actions/new-thread.spec.md`
- `../actions/global-prompt-generation-controls.spec.md`
- `../sessions/agent-sessions.spec.md`
