# 0139. A slot appears on the first write of a child key

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0034](./0034-a-settings-storage-is-a-live-handle-and-a-slot-appears-on-its-first-write.md).

## Context

[ADR 0034](./0034-a-settings-storage-is-a-live-handle-and-a-slot-appears-on-its-first-write.md) makes a
`SettingsStorage` a live handle on a slot. A read of a missing slot creates nothing, and the first write through
the handle creates the slot. The handle is the one live value.

That idea was dropped. The vault holds an immutable model
([ADR 0136](./0136-a-write-swaps-an-immutable-model-and-a-read-takes-no-lock.md)), and a write of a child key takes
its slots from the context pins ([ADR 0088](./0088-a-write-names-its-slots.md)). No `SettingsStorage` type exists.

## Decision

**A child key reads and writes through its map key, with no storage handle. A read of a missing slot creates
nothing. A write of a child key creates every missing map on its pinned address.**

## Consequences

- A read of a missing slot gives the defaults of the children, as in
  [ADR 0034](./0034-a-settings-storage-is-a-live-handle-and-a-slot-appears-on-its-first-write.md).
- A slot exists only because a write put it into a real layer, so no slot is detached or unsaved.
- Every value is an immutable snapshot. No value is live.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
