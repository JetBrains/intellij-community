# 0098. A replica layer has a provider of its own

Date: 2026-09-09

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md).

## Context

A copy was first described as engine-managed, with no provider
([ADR 0085](./0085-mirroring-is-symmetric-a-layer-has-an-origin-and-copies.md)). A copy layer still needs a place in
the stack, and a way to be seeded and dropped.

## Decision

**A replica is an ordinary provider registered on the side that holds it, so extension order places it. The
provider interface splits: a light part (`requestData` asks the peer, `availableLocalities` answers statically,
`accepts` delegates to one object shared with the origin), and the full part adds `writeData` and
`storageContext`. The role, owning or replicating, lives on the provider, not in the layer data.** A replica
provider states which layer it replicates, and the platform moves the layer and its changes.

## Consequences

- A connection and a reconnection reuse the existing reload path.
- Routing works before any data arrives, because localities are declared.
- Both sides answer `accepts` the same way, so one mutation cannot route to two layers.
- A key's `layerFilter` must accept the replica class next to the origin class.

## Rejected alternatives

### A role flag in the pushed data

It must be set right on every push, and it can contradict itself.
