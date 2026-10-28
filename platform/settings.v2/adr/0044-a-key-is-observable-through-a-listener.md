# 0044. A key is observable through a listener

Date: 2026-07-16

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0008](./0008-a-bulk-change-listener-names-its-keys.md).

## Context

[ADR 0008](./0008-a-bulk-change-listener-names-its-keys.md) designed `Flow`-based listeners per key and per named
key set. The prototype's push-based engine already folds provider pushes through an internal change stream, and
many features need to react to a setting (repaint on a color change, re-run inspections on a profile change).
A branch switch changes many values at once. A consumer such as Settings Sync observes the writes of its keys
anywhere, and it needs to know where a change happened.

## Decision

**A key is observable: `key.addListener(disposable, [context]) { key, context -> … }`. A contextual listener fires
only when the effective value at its context changes. A key-only listener is a value-less "may have changed at or
under this context" ping, with the context of the changed layer. The vault coalesces the notifications per model
change: a contextual listener fires at most once, and a key-only listener fires at most once per changed context.**

- A burst of reported changes folds into one model change.
- Several layers that change at one context, such as its team and local layers, give one key-only call.
- A slotted key reacts to its slot's data and to a discriminator switch.
- A listener on a map key itself is rejected. Observe its child keys.

## Consequences

- One engine capability replaces per-setting change notification.
- A branch switch does not storm listeners.
- A key-only listener learns every context where its key changed.
- A contextual listener deduplicates on the raw stored value.
- The resulting behavior is in the [change listeners spec](../spec/change-listeners.spec.md).
