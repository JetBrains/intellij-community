# 0050. A slot operation moves raw sub-maps through a typed, owner-scoped API

Date: 2026-07-20

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A slot is the sub-map a `SettingsMapKey` stores under one discriminator value. Slots appear automatically; copy,
drop and rename cover the rest of their life. The plugin that declares a slot's child keys may not be loaded
when the slot moves.

## Decision

**Slot operations move raw sub-maps by string identity (map key id and discriminator value), never through
typed child keys. The public surface is typed and scoped to the owner: `mapKey.copySlot(ctx, from, to)`,
`dropSlot(ctx, value)`, `renameSlot(ctx, from, to)`.** Holding the map key is the gate. There is no public "write
anything by string" API; that power stays inside the engine.

## Consequences

- Slot operations work when the child-key plugin is not loaded.
- A caller passes only discriminator values within its own map key.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
