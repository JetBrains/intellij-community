# 0087. Both sides aim at the same value for the same key and context

Date: 2026-09-03

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

"Consistent" can mean two things for two vaults. Same layers with the same values is unreachable: a machine-bound
layer stays where it is, and a side can own a layer the other never needs.

## Decision

**The goal is: the same key read at the same context gives the same value on both sides, whatever the layers
under it look like.** The mirroring rules aim at this.

## Consequences

- The two sides may hold different layer sets.
- The goal holds only while a key and a context mean the same on both sides. A slotted key's address includes
  discriminators, a chain of them, and every link must agree.
- A discriminator that differs by design, such as the operating system, must never address a mirrored set.
