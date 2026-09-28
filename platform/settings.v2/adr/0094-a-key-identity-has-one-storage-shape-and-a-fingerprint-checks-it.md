# 0094. A key identity has one storage shape, and a fingerprint checks it

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A mutation carries a key identity, and the receiving side needs the real key behind it. If the same identity
described two different keys, the value would break in silence: at the top level on one side and inside a slot on
the other, so a layer change carries a path the peer never reads. Some behaviour legitimately differs per side,
such as a machine-dependent default.

## Decision

**What must match across sides is the storage shape: the serializer, `locality` and `readonly`, and the storage
chain with each map key and discriminator. The default, the validator and the key, layer and context filters may
differ. The storage shape is a deterministic fingerprint, checked in each mutation and in a build check that
compares the frontend and backend dumps.**

## Consequences

- What hashes is exactly what must match.
- A mismatch in a mutation logs an error and applies nothing.
- The build check catches a mismatch before a user writes a setting, if a test instantiates every holder.
