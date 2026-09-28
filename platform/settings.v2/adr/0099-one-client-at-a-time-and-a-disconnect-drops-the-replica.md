# 0099. One client at a time, and a disconnect drops the replica

Date: 2026-09-09

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A replica whose origin is a frontend exists only while that frontend is connected. Several clients would need a
client dimension in contexts and per-client resolution.

## Decision

**The model assumes one client at a time, or none: one origin per layer, no client dimension, no per-client
resolution. On a disconnection the platform drops the replica layer, and a reconnection adds it back.** The replica
provider re-supplies nothing, so the ordinary reload sweep removes it.

## Consequences

- Before the first connection and after a disconnection the backend holds the same state; the values live on the
  client's disk, where their origin is.
- A blip drops and re-adds the layer, and listeners fire twice. Accepted: blips are rare, and a delay before the
  drop would be an arbitrary number.
