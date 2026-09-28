# 0105. Keys are found through a key factory EP and a registry service

Date: 2026-09-10

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0109](./0109-promoting-keys-is-the-discovery-and-it-runs-in-the-background.md).

## Context

A mutation carries a key identity, and the receiving side needs the key object for the serializer, the filters,
the validator and the locality routing. A key registers when its declaring class initializes, which is lazy, so a
key nobody touched cannot be found. Scanning or a generated index would be fragile or heavy.

## Decision

**A `SettingsKeyFactory` extension point and a `SettingsKeyRegistry` service: the registry calls every factory once,
and each factory registers the keys it knows.** The registry is a service, so it initializes on first use, off the
startup path. It absorbs the collision check and follows the extension point as plugins load and unload.

## Consequences

- An identity resolves with no scanning and no generated index.
- A factory loads its declaring classes; a cost to optimize later, for a simple contract now.
- A key absent on the receiving side is logged and skipped; the layer change still carries the value.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).
