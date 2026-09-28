# 0033. A write gives no read-your-writes guarantee

Date: 2026-07-01

## Status

Superseded by [ADR 0058](./0058-mutations-apply-as-atomic-declarative-batches-under-one-lock.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A write goes `setValue → layer → the layer's internal model`. The layer persists the value and updates its model,
possibly later, possibly after I/O. Waiting for all of that inside `setValue` would tie every write to the
slowest layer.

## Decision

**A completed `setValue` means the layer accepted the value, not that it is applied. A read right after it may
return the old value. A caller observes the landing through the change flow, not by reading back.** The
prototype shows this honestly instead of hiding it.

## Consequences

- Layers are free to apply and persist asynchronously.
- Read-then-write code and dependent writes can see stale values.
