# 0092. A per-layer sequence number orders layer states

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A whole layer and a layer change for the same layer can cross on the link. A copy must not install a stale layer
over a newer change.

## Decision

**The origin stamps a per-layer sequence number, and a copy drops anything not newer than what it holds.** Order
across layers is not tracked, because each layer resolves on its own.

## Consequences

- Each layer is an independent single-owner stream.
- No global ordering on the link.
