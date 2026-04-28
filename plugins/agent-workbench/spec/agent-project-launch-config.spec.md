---
name: Agent Workbench Project Launch Config
description: Requirements for project-local launch augmentation via `.agent-workbench.yaml`.
targets:
  - ../sessions-launch-config/backend/src/*.kt
  - ../sessions-launch-config/backend/resources/*.xml
  - ../sessions-launch-config/backend/testSrc/*.kt
  - ../sessions-core/src/launch/*.kt
  - ../sessions-core/resources/intellij.agent.workbench.sessions.core.xml
  - ../sessions/src/service/AgentSessionLaunchService.kt
  - ../sessions/src/service/AgentSessionChatOpenPayload.kt
  - ../chat/src/AgentChatEditorService.kt
  - ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  - ../sessions/testSrc/AgentSessionChatOpenPayloadTest.kt
  - ../chat/testSrc/AgentChatEditorServiceTest.kt
---

# Agent Workbench Project Launch Config

Status: Draft
Date: 2026-03-13

## Summary
Define the project-local launch augmentation contract for Agent Workbench sessions. This spec owns how `.agent-workbench.yaml` contributes `PATH` entries and command shims for all providers, with optional provider-specific overrides, without changing canonical provider command mapping.

Shared provider command mapping remains owned by `spec/agent-core-contracts.spec.md`.

## Goals
- Allow a repository to augment Agent Workbench launches without changing provider bridge code.
- Keep create, resume, prompt bootstrap, and rebind flows consistent by applying one augmentation path.
- Keep launch augmentation environment-aware for local, WSL, and other EEL-backed execution contexts.

## Non-goals
- Redefining canonical provider commands. Provider bridges (not project launch config) resolve provider executables to absolute paths via the shared `TerminalAgentResolver` — see `spec/agent-core-contracts.spec.md`.
- Defining provider-specific prompt construction rules.
- Providing live UI editing or validation for project launch config.

## Requirements
- Project launch config file location is the normalized project root file `.agent-workbench.yaml`.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Config schema supports shared top-level launch augmentation keys plus optional provider override blocks under `providers.<providerId>`.
  Supported launch augmentation keys at both levels are:
  - `pathPrepend`: list of path strings
  - `commandShims`: object mapping command name to target path string
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Shared top-level launch augmentation applies to every provider automatically. Provider override blocks must affect only their matching provider id.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Unknown or empty provider sections may be ignored. If the combined shared root config and matching provider override yield no effective `pathPrepend` or `commandShims`, launch resolution must remain unchanged.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Invalid config shapes or entries must fail soft:
  - malformed YAML,
  - non-object `providers`,
  - non-object `providers.<providerId>`,
  - non-string map values,
  - non-list `pathPrepend`,
  - invalid path strings.
  Such cases must be logged and must preserve any remaining valid launch augmentation instead of blocking session open.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Launch augmentation must apply to every flow that resolves an `AgentSessionTerminalLaunchSpec` through shared launch-spec resolution:
  - create-new-session launch,
  - resume launch for opening an existing thread or sub-agent,
  - prompt startup launch-spec overrides built from augmented base launch specs,
  - chat-tab rebind flows that resolve resume specs.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../sessions/testSrc/AgentSessionChatOpenPayloadTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- Canonical provider command mapping is provider-owned (`spec/agent-core-contracts.spec.md`). Project launch config must augment `PATH` lookup and shim lookup only; it must not redefine canonical provider commands. Provider bridges may pre-resolve the provider executable token to an absolute path via the shared `TerminalAgentResolver`; project launch config must not perform its own pre-resolution and must not depend on the resolved value being a bare command name.
  [@test] ../sessions/testSrc/AgentSessionPromptLauncherBridgeTest.kt
  [@test] ../sessions/testSrc/AgentSessionChatOpenPayloadTest.kt

- `PATH` merge order is:
  - generated shim directory first when effective shims exist,
  - shared root `pathPrepend` entries next in listed order,
  - provider override `pathPrepend` entries next in listed order,
  - base launch-spec `PATH` if present,
  - otherwise target login-shell `PATH`.
  The effective `PATH` key name must respect target OS conventions (`PATH` on Posix, `Path` on Windows when no base key is present).
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Command shims must validate before use:
  - shim command names must be non-blank and must not contain path separators,
  - shim targets must exist and be regular files,
  - Posix shim targets must be executable.
  Invalid shim entries must be ignored with logging; valid entries may still augment launch.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Effective command shims must be materialized under IDE system directory state and exposed by prepending the shim directory to `PATH` instead of replacing provider commands.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Command shim merge precedence is:
  - shared root `commandShims` provides defaults,
  - provider override `commandShims` wins over shared root `commandShims` on command-name conflicts.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

- Any path injected into target launch environment or written into generated shim script content must use target-native EEL path serialization. Host-formatted NIO path strings are not a valid wire format for WSL or other remote-like execution environments.
  [@test] ../sessions-launch-config/backend/testSrc/AgentWorkbenchProjectLaunchConfigTest.kt

## User Experience
- This feature is project-local and file-driven; no dedicated UI is required in v1.
- Users should experience launch augmentation transparently: canonical session commands stay unchanged while target environment lookup resolves through injected `PATH` entries and command shims.
- Repositories should be able to declare shared tools such as Bun once at the root of the config file and add provider-specific overrides only when needed.

## Data & Backend
- Provider ids used in `providers.<providerId>` must match stable Agent Workbench provider ids.
- Shared root launch augmentation is provider-agnostic and applies before provider-specific overrides are considered.
- Relative configured paths resolve against the normalized project root.
- Absolute configured paths are allowed and normalize before use.
- Shared launch-spec resolution remains the single composition point between provider bridges and project-local augmentation.

## Error Handling
- Any unexpected augmentation failure must log and return the original launch spec.
- Invalid config must degrade to no-op augmentation rather than partial hard failure.
- Missing or invalid shim targets must not prevent valid `pathPrepend` augmentation from applying.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.launch.config.backend.AgentWorkbenchProjectLaunchConfigTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionPromptLauncherBridgeTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionChatOpenPayloadTest'`

## Open Questions / Risks
- Current implementation caches parsed project launch config for the service lifetime and does not specify live reload semantics for `.agent-workbench.yaml` edits during the same IDE session.
- If additional providers are added, examples and provider-id coverage in this spec should be extended without moving canonical command mapping ownership out of `agent-core-contracts.spec.md`.
- Provider-bridge absolute-path resolution (via `TerminalAgentResolver`) runs before launch-spec augmentation. A `commandShims` entry whose key matches the provider's own command (`claude`/`codex`) is therefore bypassed for the launched provider process — the bridge already passed the absolute resolved path to the shell. Repositories that need to redirect the provider binary itself must place the redirection target on `PATH` (so `TerminalAgentResolver` finds it) rather than relying on `commandShims` for the provider command. `commandShims` for sub-tools that the provider invokes internally remain effective.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-sessions.spec.md`
