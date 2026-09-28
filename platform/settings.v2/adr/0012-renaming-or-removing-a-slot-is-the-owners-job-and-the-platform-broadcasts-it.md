# 0012. Renaming or removing a slot is the owner's job, and the platform broadcasts it

Date: 2026-06-01

## Status

Superseded by [ADR 0055](./0055-keeping-slots-consistent-is-the-api-users-job.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A slot is keyed by a discriminator value, such as a profile name. When the user renames or deletes the profile,
its slot data has to follow, in every layer that holds it. Many of those layers, such as directory overrides
outside the given context, are not visible through that context's layer stack. The options were owner
responsibility, a typed `SettingsRef` with platform-handled cascade, or platform garbage collection of orphaned
slots.

## Decision

**Keeping slots consistent is the owner's responsibility. The platform exposes `SettingsMapKey.renameSlot` and
`removeSlot`, and broadcasts each call to every registered `LayerContributor` with the context, the map key and
the old and new slot names. Each contributor updates its own storage.** No `SettingsRef`, no garbage
collection, no platform-side referential integrity.

## Consequences

- The platform stays out of cross-layer consistency, which has no generic answer.
- Every contributor holding slotted data must implement the hooks.
- A stored discriminator that still names the old slot is not fixed by the platform.

## Rejected alternatives

### Typed references with platform cascade

Needs the platform to know every place a discriminator value is stored, which it cannot.

### Walking the layer stack

The stack of one context does not reach layers outside it.
