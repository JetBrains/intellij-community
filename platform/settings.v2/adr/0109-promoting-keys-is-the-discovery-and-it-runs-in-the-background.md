# 0109. Promoting keys is the discovery, and it runs in the background

Date: 2026-09-24

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0105](./0105-keys-are-found-through-a-key-factory-ep-and-a-registry-service.md).

## Context

The registry calls every key factory once ([ADR 0105](./0105-keys-are-found-through-a-key-factory-ep-and-a-registry-service.md)). An earlier
idea, a
`SettingsKeyPromoter` EP, let authors promote stable keys for schema generation. Calling every extension
synchronously costs time, and starting from an application event fails in a test application, which fires none:
the first write would then wait forever.

## Decision

**One extension point: its method takes the registry and registers the keys of the declaring plugin; promotion is
the discovery. The service starts discovery when it is created, calling every extension from a coroutine of its own
scope. The registry holds a ready flag and a method that suspends until it is set, and the write check goes through
it.**

## Consequences

- Discovery costs nothing at start.
- A write can arrive before discovery ends and simply waits; a write already suspends.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).
