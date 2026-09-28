# 0025. Values crossing the API are isolated by `Serializer.copy`

Date: 2026-06-18

## Status

Superseded by [ADR 0030](./0030-a-value-is-deserialized-fresh-per-read-so-serializer-copy-goes.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A value returned from `getValue` may be a mutable object. A consumer that mutates it could corrupt the value a
layer holds, and a caller that keeps a reference after `setValue` could change what is stored. Kotlin and Java
cannot guarantee immutability for an arbitrary type.

## Decision

**Isolate instead of enforce. `Serializer` gains `copy(value): T`, by default `deserialize(serialize(value))`,
identity for immutable types. The platform copies on every `getValue` and every `setValue`.** Built-in scalars
copy as a no-op.

## Consequences

- A consumer cannot reach a layer's instance, and a stored value cannot drift after it was set.
- Every read and write of a mutable type pays for a copy.
- A contributor leaking its own model through a side API is outside the contract.

## Rejected alternatives

### Enforce immutability

The language cannot. An `@Immutable` annotation with an inspection remains a possible hygiene nudge.
