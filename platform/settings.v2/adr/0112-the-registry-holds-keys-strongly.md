# 0112. The registry holds keys strongly

Date: 2026-09-24

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

An unloaded plugin's keys must leave the registry. A weak reference looks tempting, because the key becomes
unreachable on its own. But on a plugin reload before a garbage collection, the dead key still holds the identity,
and the new instance is rejected as a duplicate. A soft reference is worse: memory pressure clears it while the
plugin is alive.

## Decision

**The registry holds keys strongly, and the extension point listener is what drops them.**

## Consequences

- A plugin reload never collides with its own dead keys.
- A write with an evicted key registers nothing, because its plugin is gone; a read still works, since it needs no
  registry.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).

## Rejected alternatives

### Weak or soft references

Weak fails on a reload before GC; soft drops live keys under memory pressure.
