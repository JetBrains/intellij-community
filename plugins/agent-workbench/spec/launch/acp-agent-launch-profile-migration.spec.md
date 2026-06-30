---
name: ACP Agent Launch Profile Migration
description: Plan for migrating ACP agent selection from generation models to stable launch-target based profiles.
targets:
  - ../../prompt/core/src/AgentPromptModels.kt
  - ../../prompt/ui/src/**/*.kt
  - ../../prompt/ui/testSrc/**/*.kt
  - ../../lib-agent/sessions-core/src/providers/AgentSessionLaunchProfiles.kt
  - ../../lib-agent/sessions-core/src/launch/AgentSessionLaunchPlanner.kt
  - ../../lib-agent/sessions-core/src/launch/AgentSessionOutOfBandLaunch.kt
  - ../../sessions/src/service/AgentSessionLaunchProfileResolverImpl.kt
  - ../../sessions/src/AgentSessionLaunchProfileMenuActions.kt
  - ../../sessions/src/service/AgentSessionLaunchService.kt
  - ../../sessions/src/service/AgentSessionPromptLauncherBridge.kt
  - ../../sessions/src/state/AgentSessionLaunchProfileStateService.kt
  - ../../thread-view/src/AgentThreadViewStartupIntent.kt
  - ../../thread-view/src/AgentThreadViewFileEditorState.kt
  - ../../engine/src/ui/EngineSessionProviderDescriptor.kt
  - ../../engine/src/platform/EngineLaunchAgents.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpEngineLaunchAgentProvider.kt
  - ../../../../../plugins/agent-workbench/acp/src/AcpOutOfBandLaunch.kt
  - ../../../../../plugins/agent-workbench/acp/testSrc/**/*.kt
---

# ACP Agent Launch Profile Migration

Status: Draft
Date: 2026-06-27

## Summary
ACP catalog agents must be launch targets, not generation models. A launch profile selects which agent or
provider to start; generation settings select a model only when the runtime actually exposes models. For
ACP this means top-level launch profiles should be generated from installed and launchable registry entries
using the stable ACP agent id, while ACP model choices remain unavailable until handshake or cached runtime
capability data exists.

This spec supersedes the ACP-specific model-selection approach described in
`engine-acp-launch-profiles.spec.md`. That older spec is still useful as launch-pipeline background, but its
central assumption that an ACP agent is a generation `modelId` is deprecated.

## Goals
- Show terminal launch profiles and installed ACP registry agents together in the New Task launch profile
  surface.
- Persist ACP selection by stable ACP agent id, not display name and not post-handshake model id.
- Make native Agent Workbench UI launch the default for ACP agents.
- Preserve a path for users who prefer terminal launch, with an explicit switchable launch surface.
- Keep existing terminal providers and their generation-model selection behavior unchanged.
- Migrate existing ACP profiles without losing user-created launch profiles.

## Non-goals
- Defining the ACP handshake protocol or the runtime format for ACP-reported models.
- Building a full post-handshake ACP model selector in this migration.
- Changing terminal provider process startup, sandboxing, or command generation.
- Installing missing registry agents on demand from the launch picker.

## Current Problem
The current ACP launch integration reuses generation-model selection as an agent picker. That conflates three
independent axes:

- Launch target: the thing to start, such as a terminal provider or a concrete ACP catalog agent.
- Generation model: a model exposed by a running or known backend.
- Launch surface: native UI/out-of-band launch versus terminal launch.

This creates persistence and UX problems. ACP agent display names can change, ACP models are known only after
handshake, and the current provider-wide out-of-band launcher makes it hard to offer a per-profile terminal
switch later.

## Requirements
- `AgentPromptLaunchProfile` must gain a nullable launch target field, tentatively
  `launchTargetId: String? = null`. For ACP profiles this field stores `AcpAgentId.fullId`. For existing
  terminal providers it remains `null` unless that provider defines its own target concept.
  [@test] ../../prompt/ui/testSrc/AgentPromptLaunchProfileStateTest.kt
- Launch profile resolution must preserve the selected target. `AgentSessionResolvedLaunchProfile`, prompt
  launch requests, session launch intents, prepared launch state, and any out-of-band launch context must carry
  `launchTargetId`; no overload may resolve a profile and then keep only provider, mode, and generation settings.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
- Launch requests and launch intents must carry the selected launch target from prompt UI into the session
  launch pipeline. ACP launch code must resolve the ACP catalog entry from `launchTargetId`, not from
  `generationSettings.modelId`.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
- Thread View startup/restoration metadata must retain the launch target when a pending new ACP thread is reopened.
  `launchProfileId` is not enough because a built-in profile can disappear, be renamed, or be regenerated while
  the stable target remains the identity needed to finish launch.
  [@test] ../../thread-view/testSrc/AgentThreadViewOpenTopLevelDispatchTest.kt
- `AgentPromptGenerationSettings.modelId` must keep its current meaning: a generation model id. ACP agent ids
  must not be written to this field by new code. ACP-specific model ids may be written there only after a
  real ACP model list is available from handshake or an explicit cache of handshake capabilities.
  [@test] ../../prompt/ui/testSrc/AgentPromptLaunchProfileStateTest.kt
- `EngineSessionProviderDescriptor` must stop exposing ACP catalog agents through
  `listAvailableGenerationModels`. Until ACP runtime model capabilities are available, the Engine/ACP provider
  must not show pre-launch model selection just to surface ACP agents.
  [@test] ../../prompt/ui/testSrc/AgentPromptProviderSelectorTest.kt
- Built-in launch-profile generation must include launchable ACP registry/local catalog entries next to the
  provider-derived terminal built-ins. ACP entries that are not installed or cannot resolve their binary must
  be omitted from the built-in profile list.
  [@test] ../../../../../plugins/agent-workbench/acp/testSrc/AcpLaunchableAgentsTest.kt
- Built-in profile generation must be centralized. Palette UI, Manage Profiles, New Thread menu actions,
  scratch prompt actions, and `AgentSessionLaunchProfileResolverImpl` must all use the same effective built-in
  profile source; adding ACP target profiles in only one UI path is not acceptable.
  [@test] ../../prompt/ui/testSrc/actions/AgentWorkbenchManageLaunchProfilesActionTest.kt
- ACP built-in profiles must be contributed through a project-aware extension/contributor API, not by adding an
  ACP dependency to shared sessions-core or prompt UI code. The same contributor should be usable when a
  `Project` is available for accurate availability and when only app-level fallback resolution is available.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
- A built-in ACP profile must be derived, not persisted as a user profile by default. If the catalog entry
  disappears or becomes unavailable, the built-in profile disappears. If the user customizes it, the saved user
  profile keeps the same `launchTargetId` and can be shown as unavailable until the agent is available again.
  [@test] ../../prompt/ui/testSrc/actions/AgentWorkbenchManageLaunchProfilesActionTest.kt
- Built-in ACP profile ids must be stable and deterministic. Use a prefix such as
  `builtin:acp-agent:<encoded-acp-agent-id>:<launch-mode>`, where the encoded part is reversible and safe for
  delimiters. The display name comes from the catalog entry and may change without changing persisted target
  identity.
  [@test] ../../prompt/ui/testSrc/AgentPromptLaunchProfileNameGeneratorTest.kt
- Legacy ACP profiles must be accepted during migration. If `providerId == acp`, `launchTargetId == null`, and
  `generationSettings.modelId != null`, the resolver should treat the old value as a legacy ACP agent key,
  resolve by stable id first and display name second, copy the result to `launchTargetId`, and clear
  `generationSettings.modelId` in the normalized profile.
  [@test] ../../prompt/ui/testSrc/AgentPromptLaunchProfileStateTest.kt
- Native UI launch must be the default ACP surface. Terminal launch must be modeled as a launch surface, not as
  a generation model and not as a launch mode. If the implementation cannot add the surface selector in the
  first change, it must keep the data model open for `DEFAULT`, `UI`, and `TERMINAL` values and avoid adding
  provider-wide assumptions that would block it.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
- `AgentSessionOutOfBandLaunch.forProvider(provider)` is too coarse for the final design. The launch decision
  should become context-based, using at least provider id, launch target id, and launch surface. Until that API
  is changed, ACP out-of-band launch must be treated as an implementation bridge, not as the product model.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
- Out-of-band launch API must not keep a parameter named or documented as `modelId` for ACP target identity.
  Replace it with a launch context object or an explicit `launchTargetId` before removing the legacy fallback.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt
- New-session single-flight keys must include launch target or launch profile identity for targeted providers.
  Two ACP launches for different agents in the same project and mode must not be deduplicated as the same
  create-session action.
  [@test] ../../sessions/testSrc/AgentSessionLaunchServiceTest.kt

## User Experience
- New Task shows one top-level launch profile list. Existing terminal profiles stay visible, and installed ACP
  agents from the registry appear as peer entries.
- Selecting an ACP registry profile opens an Agent Workbench thread view thread and starts the ACP agent through the
  native UI path by default, without showing an embedded terminal.
- Advanced users should be able to switch an ACP profile or launch action to terminal launch when that surface
  is implemented. This switch should be explicit and reversible.
- ACP model selection should not appear before the ACP runtime has reported models. After handshake, model
  selection may appear inside the running thread or in a later launch profile editor if cached capabilities are
  available.
- Unavailable built-in ACP agents are hidden. Saved user profiles for unavailable ACP targets may remain visible
  as disabled entries with localized recovery text.

## Data & Backend
Example generated built-in ACP profile:

```kotlin
AgentPromptLaunchProfile(
  id = "builtin:acp-agent:<encoded acp.registry.mistral-vibe>:standard",
  name = "Mistral Vibe",
  kind = AgentPromptLaunchProfileKind.BUILT_IN,
  providerId = AgentSessionProvider.from("acp"),
  launchMode = AgentSessionLaunchMode.STANDARD,
  launchTargetId = "acp.registry.mistral-vibe",
  generationSettings = AgentPromptGenerationSettings(modelId = null),
)
```

Launch profile resolution should have four normalization layers:

- Provider defaults: existing provider-built profiles and generation-setting sanitization.
- Target defaults: ACP built-ins generated from currently launchable catalog entries.
- Surface defaults: native UI for ACP when no explicit surface is stored.
- Compatibility: legacy ACP `modelId` values converted into `launchTargetId` when possible.

ACP launch should use `launchTargetId` to resolve a launchable catalog entry, then use ACP handshake data to
populate runtime capabilities. Handshake-discovered ACP models are runtime capabilities and must not be required
to construct the initial launch profile.

The profile contributor boundary should replace or supersede `EngineLaunchAgentProvider` for ACP launch-picker
population. Keeping `EngineLaunchAgentProvider` as a generation-model adapter after ACP target profiles exist
would preserve the original modeling error.

## Migration Plan
- Phase 0: Land this spec and mark `engine-acp-launch-profiles.spec.md` as superseded for the agent-as-model
  part during the first implementation change.
- Phase 1: Add `launchTargetId` to launch profile data, serialization, normalization, resolved-profile objects,
  prompt launch requests, launch intents, prepared launch state, and thread view startup metadata. Add compatibility
  conversion for legacy ACP profiles.
- Phase 2: Centralize built-in profile generation and add ACP profiles from installed and launchable catalog
  entries. Keep terminal-provider built-ins unchanged.
- Phase 3: Update Engine/ACP generation-model exposure and out-of-band launch. ACP launch resolves by
  `launchTargetId`; Engine stops listing ACP agents as generation models; new code stops writing ACP agent ids
  to `generationSettings.modelId`.
- Phase 4: Introduce explicit launch surface support for ACP, with native UI as default and terminal as an
  opt-in surface. Replace provider-wide out-of-band selection with context-based launch selection.
- Phase 5: After one compatibility window, remove legacy ACP `modelId` fallback or keep it only in persisted
  profile migration code.

## Error Handling
- Missing or not installed ACP registry entries must not be emitted as built-in profiles.
- A saved user profile that points to an unavailable ACP target must not attempt lazy binary resolution during
  list rendering. It should fail before launch with a localized unavailable-agent message or be disabled in UI.
- If legacy `modelId` cannot be resolved as either ACP stable id or display name, preserve the profile as
  invalid/unavailable rather than silently launching a different agent.
- Failure to fetch post-handshake ACP models must not invalidate the selected launch target. It only disables or
  delays runtime model selection.

## Testing / Local Run
- Add serialization and migration coverage to launch-profile state tests:
  `./tests.cmd --module intellij.agent.workbench.prompt.ui.tests --test com.intellij.agent.workbench.prompt.ui.AgentPromptLaunchProfileStateTest`
- Add launch-context coverage to session launch tests:
  `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLaunchServiceTest`
- Keep ACP availability coverage in:
  `./tests.cmd --module intellij.agent.workbench.acp.tests --test com.intellij.agent.workbench.acp.AcpLaunchableAgentsTest`
- Add an integration-style test or manual run configuration that verifies the New Task picker shows terminal
  profiles and installed ACP registry profiles side by side.

## Architectural Validation
- Separation of concerns: launch target, generation model, launch mode, and launch surface become separate
  axes. This matches the domain and removes the current accidental dependency on `modelId`.
- Stable persistence: ACP profile identity uses `AcpAgentId.fullId`, which is stable across display-name changes
  and independent of handshake timing.
- Runtime correctness: ACP models appear only after handshake, so launch profiles no longer need information
  that cannot be known before launch.
- UX fit: native UI launch becomes the default for ACP without removing terminal launch as a deliberate expert
  choice.
- Failure containment: unavailable registry agents are filtered before profile display, preventing the launch
  failure mode where a visible profile points to a non-installed lazy binary.
- Backward compatibility: legacy profiles are normalized in one place, avoiding scattered fallbacks in UI and
  launch code.
- Extensibility: the target field can support future providers with multiple launchable targets without
  manufacturing provider ids or misusing generation models.

Rejected alternatives:

- Keep ACP agents as generation models. Rejected because it stores agent identity in the wrong field and cannot
  represent post-handshake ACP models cleanly.
- Create one provider id per ACP registry agent. Rejected because provider ids describe integration families,
  while ACP agent ids describe launch targets; splitting providers would duplicate settings and provider logic.
- Materialize every registry ACP entry as a user profile. Rejected because availability is dynamic and would
  create stale persisted state for catalog changes.
- Use `launchMode` for UI versus terminal. Rejected because launch mode describes permission/safety behavior;
  UI versus terminal is an execution surface.

## Open Questions / Risks
- Exact field name: `launchTargetId` is descriptive, but `agentId` or `runtimeTargetId` may read better in UI
  code. The chosen name must not imply ACP-only semantics.
- Adding `launchTargetId` changes serialized model/API shape. Deserialization must keep a default `null` value,
  and implementation must account for API dumps or reduce visibility before landing code.
- Launch surface can be implemented in the first migration or deferred, but Phase 1 must avoid APIs that make
  provider-wide out-of-band launch permanent.
- ACP model capability caching is intentionally out of scope. If added later, it needs cache invalidation and
  clear distinction from the stable launch target.
- Profile id encoding must be specified before implementation so delimiters in external ACP ids cannot break
  parsing or future migrations.

## References
- `engine-acp-launch-profiles.spec.md`
- `../actions/new-thread.spec.md`
- `../actions/global-prompt-generation-controls.spec.md`
- `../sessions/agent-sessions.spec.md`
