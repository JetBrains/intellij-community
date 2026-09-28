# 0003. The settings API is format-neutral

Date: 2026-05-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0001](./0001-json-is-the-default-settings-format.md).

## Context

[ADR 0001](./0001-json-is-the-default-settings-format.md) chose JSON as the settings format. Storage backends
differ, though: a human-readable file, a binary cache, a remote daemon. A format baked into the API would tie
every backend to it.

## Decision

**The settings API exposes typed operations on keys and knows no format. JSON is only the format of the
default storage backend.**

## Consequences

- JSON, YAML, binary and remote backends can be swapped with no API change.
- [ADR 0001](./0001-json-is-the-default-settings-format.md) now describes the default backend, not the API.
- A serializer never sees the storage format (see the Serializer design).
- The resulting behavior is in the [setting values spec](../spec/setting-values.spec.md).
