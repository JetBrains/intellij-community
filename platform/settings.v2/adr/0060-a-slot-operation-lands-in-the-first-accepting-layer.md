# 0060. A slot operation lands in the first accepting layer

Date: 2026-07-25

## Status

Superseded by [ADR 0078](./0078-a-slot-operation-broadcasts-to-every-layer-at-its-context.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

With slot consistency left to the API user ([ADR 0055](./0055-keeping-slots-consistent-is-the-api-users-job.md)), the cross-context
broadcast went away. A
slot operation still needs a target layer.

## Decision

**`copySlot` and `dropSlot` are one-entry batches routed like a plain write: they land in the first accepting
layer. There is no broadcast.** `renameSlot` is copy plus drop in one batch.

## Consequences

- One routing path for writes and slot operations.
- A slot held in several layers at the same context changes in one of them only.
