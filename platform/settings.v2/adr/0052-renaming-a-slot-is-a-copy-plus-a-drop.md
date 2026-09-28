# 0052. Renaming a slot is a copy plus a drop

Date: 2026-07-22

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0132](./0132-the-vault-applies-a-slot-rename-as-one-batch.md).

## Context

A slot rename could be its own primitive, implemented by every layer, or a composition of copy and drop.

## Decision

**`renameSlot` is a copy followed by a drop, composed by default, not a primitive.** A layer whose copy and drop
share one write path applies both in one persist, so the rename is atomic there; a layer may override for a
stronger guarantee.

## Consequences

- No per-layer rename implementation; a slot-less layer rejects it through `copySlot`.
- Where the two are not atomic, a failure between them leaves a duplicate slot, which is recoverable, not a
  corrupt one.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
