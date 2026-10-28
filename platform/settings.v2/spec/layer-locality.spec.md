---
name: Layer Locality
description: Requirements for the team, local and environmental localities of layers and keys.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/Locality.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/localityFiles.kt
---

# Layer Locality

Status: Draft
Date: 2026-09-28

## Summary

A locality says how far the value of a layer may travel. A team value is committed and shared with the team. A
local value stays out of VCS but follows the user. An environmental value belongs to one machine, such as an
absolute path. Each layer has exactly one locality. Each key declares which localities it may live in.

## Goals

- Keep a user's changes out of the committed configuration by default.
- Keep a machine-bound value on its machine.
- Make a key-locality combination that makes no sense impossible to declare.

## Non-goals

- Mirroring between remote-dev sides. It is not built.

## Requirements

### Localities

- A layer must have exactly one locality: team, local, or environmental.
  See [ADR 0076](../adr/0076-settings-have-three-layer-kinds-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md).
- A key must declare `SHAREABLE` (team or local, the default) or `ENVIRONMENTAL` (environmental only).
  See [ADR 0074](../adr/0074-a-layers-locality-and-a-keys-locality-are-two-enums.md).
- A key must resolve only from layers of the localities it accepts.
  See [ADR 0126](../adr/0126-a-key-reads-only-from-the-layers-of-the-localities-it-accepts.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLocalityTest.kt
- A shareable key must resolve its local layer over its team layer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLocalityTest.kt
- A local layer must win over a team layer, even from a provider that comes later.
  See [ADR 0073](../adr/0073-precedence-is-context-then-locality-then-provider.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLocalityTest.kt
- Each locality of a file-backed layer must persist to its own file. The team file keeps the plain name. The local and
  environmental files insert `local` or `env` before the extension.
  See [ADR 0075](../adr/0075-each-locality-has-its-own-file.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFileLocalityTest.kt
- The application layer must have no local locality. It has only the team and the environmental file.
  See [ADR 0149](../adr/0149-the-application-layer-has-no-local-locality.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt

### Writes

- A plain write of a shareable key must land in the local layer.
  See [ADR 0076](../adr/0076-settings-have-three-layer-kinds-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLocalityTest.kt
- A shareable write must fall back to the team layer of a context when that context offers no local layer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLocalityTest.kt
- At each context, a write must try all its localities in order before it goes to a wider context. So a write can
  land in a team layer at a narrower context, not in a local layer at a wider one.
  See [Settings Writes](settings-writes.spec.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLocalityTest.kt
- A write of an environmental key must land only in an environmental layer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFileLocalityTest.kt
- When `EDIT_TEAM_LAYER` is `true` at the write context, a shareable write must try team first, then local.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsTeamEditingModeTest.kt
- `EDIT_TEAM_LAYER` must be environmental, and only the application and project contexts may hold it.
  See [ADR 0076](../adr/0076-settings-have-three-layer-kinds-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md).
- A map key has no locality of its own. A slot operation ignores it.
  See [ADR 0077](../adr/0077-a-map-key-has-no-locality.md).

## Open Questions / Risks

- A single write cannot choose its locality. So a "move this setting to the team" action cannot exist yet.
  See [ADR 0072](../adr/0072-a-delegated-job-comes-with-a-mechanism.md) and
  [ADR 0076](../adr/0076-settings-have-three-layer-kinds-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md).
- The platform must add the local and environmental files to `.gitignore`, so only the team file goes to VCS.
  It does not do it yet. See [ADR 0075](../adr/0075-each-locality-has-its-own-file.md).
