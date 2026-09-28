# 0031. A startup snapshot speeds up reads and is never a write target

Date: 2026-06-20

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0144](./0144-a-write-before-the-first-reload-of-its-scope-is-not-gated.md). Amended by
[ADR 0083](./0083-the-binary-snapshot-cache-is-a-pure-accelerator.md).

## Context

A binary snapshot of last session's settings would make startup fast. The workspace model had a race with one:
the snapshot loaded, code mutated it before the real configs arrived, and the real configs then clobbered those
changes.

## Decision

**A snapshot only accelerates reads. It is never a write target: a write goes to the real layer, which must be
loaded first.**

## Consequences

- Nothing modifies the snapshot, so nothing gets clobbered.
- A write can wait for its layer to load.
- Reads served from the snapshot can be stale until the real load reconciles and fires listeners.
- The resulting behavior is in the [snapshot cache spec](../spec/snapshot-cache.spec.md).
