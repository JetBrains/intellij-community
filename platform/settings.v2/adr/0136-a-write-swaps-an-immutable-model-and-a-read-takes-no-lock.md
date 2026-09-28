# 0136. A write swaps an immutable model, and a read takes no lock

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0057](./0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md).

## Context

[ADR 0057](./0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md) makes the vault model
the source of truth. It says the internal storage can use plain maps, because one lock serializes every access.

A read is the hot path. It is synchronous ([ADR 0032](./0032-reads-are-synchronous-and-writes-suspend.md)), so it
cannot wait for the suspend mutex that a write takes.

## Decision

**The vault model is an immutable snapshot. A write builds the next snapshot and swaps it in under one mutex. A
read takes the current snapshot through a volatile reference, with no lock.**

## Consequences

- A read never waits, also while a write or a reload runs.
- A reader sees the model before a batch or after it, never a part of it.
- Writes still run one at a time, so a batch keeps its order
  ([ADR 0058](./0058-mutations-apply-as-atomic-declarative-batches-under-one-lock.md)).
- Each write copies the parts of the model that it changes.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md).
