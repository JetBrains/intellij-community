# 0086. A flush on a side with copies also drains the peer

Date: 2026-09-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A copy has no store, so only the origin persists a mirrored layer
([ADR 0085](./0085-mirroring-is-symmetric-a-layer-has-an-origin-and-copies.md)). A flush
([ADR 0080](./0080-flush-drains-a-scope-from-the-vault.md)) promises that pending writes are on disk.

## Decision

**On a side that holds copies, `flush(scopeContext)` drains its own origin layers and asks each peer to drain the
matching scope for the layers the peer owns. It returns only when both are done.**

## Consequences

- A write made on the frontend is on the backend's disk after a flush returns.
- A flush depends on the link.
