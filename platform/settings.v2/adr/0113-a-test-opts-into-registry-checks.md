# 0113. A test opts into registry checks

Date: 2026-09-24

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A test declares its keys in its own classes, and nothing promotes them. With the registry check on, every write in
the suite would report an unknown key ([ADR 0110](./0110-a-write-of-an-unknown-key-registers-it.md)).

## Decision

**In unit test mode the vault skips the registry check. A test that wants it calls `withRegistryChecks`, which sets
a private flag, runs the block and restores the flag.** Same shape as the owner hook.

## Consequences

- The suite stays quiet, and registry behaviour is still testable.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).
