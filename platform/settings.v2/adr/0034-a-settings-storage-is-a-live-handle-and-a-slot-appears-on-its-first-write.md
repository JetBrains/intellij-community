# 0034. A settings storage is a live handle, and a slot appears on its first write

Date: 2026-07-05

## Status

Superseded by [ADR 0139](./0139-a-slot-appears-on-the-first-write-of-a-child-key.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A map key resolves to a nested `SettingsStorage`, the selected slot. If reading a missing slot created a default
storage, code could write into it and the slot would never be saved: a detached default.

## Decision

**`SettingsStorage` is the get-and-set-by-key abstraction scoped to a context, not a data wrapper. Getting a
nested storage returns a live handle bound to `(context, map key, discriminator value)` and does not create a
slot. The first write through the handle creates the slot, in the first writable layer.** Storages nest
recursively; the backing form is a `MapValue`.

## Consequences

- Reading a missing slot falls through to the children's defaults, and creates nothing.
- A slot exists only because a write put it into a real layer, so nothing is detached or unsaved.
- The storage is the one "value" that is live rather than an immutable snapshot.
