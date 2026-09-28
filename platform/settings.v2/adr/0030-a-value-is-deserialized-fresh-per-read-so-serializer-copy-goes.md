# 0030. A value is deserialized fresh per read, so `Serializer.copy` goes

Date: 2026-06-20

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0025](./0025-values-crossing-the-api-are-isolated-by-serializer-copy.md).

## Context

[ADR 0025](./0025-values-crossing-the-api-are-isolated-by-serializer-copy.md) isolated mutable values with a
copy on every read and write. The data-flow discussion made the canonical in-memory state an immutable
`SettingsValue` tree. With that, a write serializes into an independent immutable tree, and a read deserializes
a fresh `T`.

## Decision

**The boundary itself isolates: a write serializes, a read deserializes a fresh value. `Serializer` has no
`copy`.**

## Consequences

- Nothing mutable is shared, so there is nothing to defend.
- Every read pays a deserialization; no `T` is cached.
- A hot path that wants a cached `T` makes its type immutable and caches it itself.
- The resulting behavior is in the [setting values spec](../spec/setting-values.spec.md).
