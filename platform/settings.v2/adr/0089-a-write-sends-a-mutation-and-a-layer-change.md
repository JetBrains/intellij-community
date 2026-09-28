# 0089. A write sends a mutation and a layer change

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0100](./0100-a-writes-mutation-and-layer-change-travel-in-one-message.md).

## Context

A remote write must keep two things true. The same key at the same context must answer the same on both sides
([ADR 0087](./0087-both-sides-aim-at-the-same-value-for-the-same-key-and-context.md)), and a copy must stay
faithful to its origin ([ADR 0085](./0085-mirroring-is-symmetric-a-layer-has-an-origin-and-copies.md)). The two
sides can hold different layer sets.

## Decision

**A write produces two things. The mutation (key, value, pinned context) is applied by both sides, each routing it
over its own layers, and a received mutation does not travel on. The layer change travels between an origin and
its copies.** The mutation names no layer and no locality.

## Consequences

- The mutation covers layers the sender does not have; the layer change keeps copies faithful.
- A side lacking a layer gets a different routing result, which is accepted.
