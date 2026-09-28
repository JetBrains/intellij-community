# 0032. Reads are synchronous, and writes suspend

Date: 2026-07-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0131](./0131-a-read-that-waits-for-readiness-is-a-separate-suspend-call.md).

## Context

Reads are the hot path: settings are read everywhere, often from code that cannot suspend. Writes are rare and
may involve I/O. The data-flow notes had considered two reads, a fast possibly stale sync read and a fresh
suspending one.

## Decision

**Reads are fast, synchronous calls resolved against the in-memory model. Writes are `suspend`. There is no
separate sync and suspend read.** Agreed in the discussion with Daniil Ovchinnikov.

## Consequences

- Any code can read a setting, with no coroutine.
- A write needs a coroutine.
- One read API, so no caller picks between stale and fresh.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).
