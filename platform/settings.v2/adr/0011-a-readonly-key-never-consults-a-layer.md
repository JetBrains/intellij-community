# 0011. A readonly key never consults a layer

Date: 2026-05-29

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Some values are derived at run time and must not be overridden: the remote-dev side, the operating system, the
process id. They still benefit from being keys, so other settings can depend on them, for example as a
discriminator.

## Decision

**A key declared `readonly = true` rejects every write, and its resolution skips the layers entirely: its
value is its default, constant or factory-made.**

## Consequences

- No layer, file or enterprise policy can override a runtime fact.
- Resolution of such a key is cheap.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).
