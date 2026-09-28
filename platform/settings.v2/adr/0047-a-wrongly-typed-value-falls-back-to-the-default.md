# 0047. A wrongly typed value falls back to the default

Date: 2026-07-16

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Config files are hand-edited, so a value can have the wrong type or shape. A read deserialized it and threw,
failing the read.

## Decision

**A deserialization mismatch for one key is logged as a warning, and that key resolves to its default. Other
keys in the same file are unaffected.** Malformed slotted structure also falls back.

## Consequences

- One bad entry cannot poison a file.
- A bad value is silently replaced by the default, except for the log.
- The resulting behavior is in the [setting values spec](../spec/setting-values.spec.md) and the
  [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
