# 0093. A mirrored context mirrors every locality

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Who decides which layers mirror is open: the provider, the peer, the key or a product policy. A plain write goes to
the local layer ([ADR 0076](./0076-settings-have-three-layer-kinds-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md)). If a
context mirrored `team` and not `local`, a side would have nothing narrow to route a plain write to.

## Decision

**Whatever decides mirroring, a mirrored context mirrors every mirrorable locality, never a subset.**
Environmental layers never mirror.

## Consequences

- A plain write on either side never falls through to the team layer and reaches the whole team by accident.
