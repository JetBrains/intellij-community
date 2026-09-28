# 0044. A key is observable through a listener

Date: 2026-07-16

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0008](./0008-a-bulk-change-listener-names-its-keys.md).

## Context

[ADR 0008](./0008-a-bulk-change-listener-names-its-keys.md) designed `Flow`-based listeners per key and per named
key set. The prototype's push-based engine already folds provider pushes through an internal change stream, and
many features need to react to a setting (repaint on a color change, re-run inspections on a profile change).
A branch switch changes many values at once.

## Decision

**A key is observable: `key.addListener(disposable, [context]) { key, context -> … }`. A contextual listener fires
only when the effective value at its context changes. A key-only listener is a value-less "may have changed"
ping. Bursts are coalesced to one delivery per watch.** A slotted key reacts to its slot's data and to a
discriminator switch. A listener on a map key itself is rejected; observe its child keys.

## Consequences

- One engine capability replaces per-setting change notification.
- A branch switch does not storm listeners.
- A contextual listener deduplicates on the raw stored value.
- The resulting behavior is in the [change listeners spec](../spec/change-listeners.spec.md).
