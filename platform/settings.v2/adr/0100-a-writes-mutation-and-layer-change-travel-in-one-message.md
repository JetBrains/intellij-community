# 0100. A write's mutation and layer change travel in one message

Date: 2026-09-10

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0089](./0089-a-write-sends-a-mutation-and-a-layer-change.md).

## Context

[ADR 0089](./0089-a-write-sends-a-mutation-and-a-layer-change.md) split a write into a mutation and a layer change,
first as two channels. Two messages for one action can be split around a third write of the same key: the origin
takes `mutation(7)`, then a local write of `9`, then `layerChange(7)`, and settles on 7 though 9 came later.

## Decision

**A write's mutation and its layer change, when it wrote a layer whose origin is the other side, travel in one
message, applied under one lock. The layer change must apply; the mutation is best-effort on the receiver's own
layers, and a failure there is logged and skipped.** A batch is a list of these and crosses as one message.

## Consequences

- One message per action gives one order on the link, with no sequence number or id for this.
- A difference in layer sets never rejects a valid mirror.
- Where the receiver's routing lands on the named layer, its own write is a no-op.
