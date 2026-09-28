# 0083. The binary snapshot cache is a pure accelerator

Date: 2026-09-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0031](./0031-a-startup-snapshot-speeds-up-reads-and-is-never-a-write-target.md).

## Context

[ADR 0031](./0031-a-startup-snapshot-speeds-up-reads-and-is-never-a-write-target.md) let a snapshot speed up
reads but never take writes. The data-flow notes had considered an authoritative cache, with staleness detection
and corruption recovery from it. The vault is now the one canonical model, and readiness already gates writes
during a load.

## Decision

**The binary cache only seeds the model at boot. The normal provider load runs anyway and reconciles, so the real
configs always win.** An app cache loads at startup and a per-project cache on project open, split by whether a
layer's context names a project. The dump is `(context identity, provider id, locality, value)` in kotlinx
serialization over ProtoBuf, with a format version. A version mismatch, a corrupt or missing file is logged and
ignored. One app-level marker file invalidates both. Saving runs on a long debounce and on close and shutdown.

## Consequences

- No staleness detection and no up-to-date check to build.
- A bad cache never wedges boot.
- The same types can carry the frontend and backend exchange later.
- The resulting behavior is in the [snapshot cache spec](../spec/snapshot-cache.spec.md).
