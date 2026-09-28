# 0013. Flush is a full barrier over every contributor

Date: 2026-06-03

## Status

Superseded by [ADR 0080](./0080-flush-drains-a-scope-from-the-vault.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A contributor may buffer writes in memory (batching, debouncing, caching). Some events can overwrite or
invalidate stored state: project close, branch switch, pull, IDE shutdown. Buffered state must reach durable
storage before them.

## Decision

**A `flush` hook on `LayerContributor`, triggered through `SettingsSystem.flush()` / `flushSync()` by
external code, calls every contributor. The call is a barrier: when it returns, all pending state is stored.
A flush is always full; a partial flush is not supported.** `flushSync` defaults to `runBlocking { flush() }`.

## Consequences

- One simple contract for project lifecycle, VCS plugins and shutdown.
- A flush does more work than needed when only part of the data matters, such as a renamed directory.
