# 0150. A dropped save removes every layer of its store

Date: 2026-10-08

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0122](./0122-a-save-to-a-storage-context-that-does-not-exist-is-dropped.md).

## Context

[ADR 0122](./0122-a-save-to-a-storage-context-that-does-not-exist-is-dropped.md) lets a provider drop a save when
its storage context has no place on disk. The vault then removes the dropped layers, but only when its model still
holds the saved data. A write during the save keeps the save pending, and the next save decides again.

A write during the save targets the same storage context. That storage context has no place on disk either, so the
condition only delays the drop to the next save.

## Decision

**On `DROPPED`, the vault removes every layer of that store from its model, also a layer that a write changed
during the save. It clears the pending save of the store and fires the listeners.**

## Consequences

- A write during a dropped save is dropped too, and no further save of the store follows.
- When the directory comes back between the two saves, the second write is lost, although it could have been
  saved. This is accepted.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md).
