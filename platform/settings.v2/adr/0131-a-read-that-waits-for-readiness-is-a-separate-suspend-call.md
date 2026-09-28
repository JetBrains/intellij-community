# 0131. A read that waits for readiness is a separate suspend call

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0032](./0032-reads-are-synchronous-and-writes-suspend.md).

## Context

[ADR 0032](./0032-reads-are-synchronous-and-writes-suspend.md) makes a read a synchronous call against the
in-memory model. It says there is no separate sync and suspend read, so no caller picks between a stale and a
fresh value.

[ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md) adds readiness: a context is
not ready while a reload runs for it. A read-then-decide flow, for example a migration, an import or a startup
activity, must not decide from a model in the middle of a load.

## Decision

**A plain read returns what the model holds now, and never waits. `readWhenReady(key)` suspends until the context
is ready, then does the plain read.**

- `readWhenReady` reads nothing fresher than the plain read. It only waits for the load to end.
- The plain read stays the one read for normal code.

## Consequences

- Normal code keeps one synchronous read and never picks between two reads.
- A read-then-decide flow uses `readWhenReady` from a coroutine.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### A plain read that waits for readiness

The hot path would block or suspend during every load, and most code cannot suspend.
