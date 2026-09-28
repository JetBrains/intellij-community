# 0132. The vault applies a slot rename as one batch

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0052](./0052-renaming-a-slot-is-a-copy-plus-a-drop.md).

## Context

[ADR 0052](./0052-renaming-a-slot-is-a-copy-plus-a-drop.md) makes a slot rename a copy plus a drop. A layer
applies them, may override the rename, and may leave a duplicate slot when the two steps are not atomic.

The vault is now the source of truth, and a provider is only a persistence port
([ADR 0057](./0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md)). The vault applies a
batch of mutations under one lock
([ADR 0058](./0058-mutations-apply-as-atomic-declarative-batches-under-one-lock.md)), and a slot operation goes to
every layer at its context that holds the slot
([ADR 0078](./0078-a-slot-operation-broadcasts-to-every-layer-at-its-context.md)). A provider knows nothing about
slot operations.

## Decision

**A slot rename is a copy and a drop that the vault applies to its model as one batch. A layer provider takes no
part in it and cannot override it.**

## Consequences

- A rename is atomic in the model. No reader sees the copy without the drop, and no duplicate slot stays behind.
- A provider only saves the new data of each storage context that the rename changed.
- A rename that touches several storage contexts, for example the team and the local file, saves each one on its
  own. On disk it is not one atomic step.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
