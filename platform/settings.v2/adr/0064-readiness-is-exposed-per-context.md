# 0064. Readiness is exposed per context

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The readiness gate suspends writes, not reads ([ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md)). A
consumer that reads and
then writes, such as a migration or a startup activity, can compute its write from defaults it read during the
load; suspending the write cannot fix its input. A single global ready flag would make project B wait for
project A.

## Decision

**Readiness is public and per context: `ctx.isReady()` is a snapshot, and `suspend ctx.awaitReady()` waits for the
next moment the context is ready. Both are default methods on `SettingsContext` that delegate to the vault.** The
vault keeps the loading-scope set as a `StateFlow` and derives both from it.

## Consequences

- Read-then-decide flows can wait for the load instead of reading defaults.
- Readiness re-arms on each reload, so a consumer can only await the next ready state, not pin it.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).
