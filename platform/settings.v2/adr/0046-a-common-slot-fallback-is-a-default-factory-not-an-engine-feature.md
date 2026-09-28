# 0046. A common-slot fallback is a default factory, not an engine feature

Date: 2026-07-16

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Several settings resolve as a per-dimension value falling back to a common one: code style takes the
language-specific value, else the common value, else the default. A map key selects exactly one slot, and a
miss goes straight to the child's default.

## Decision

**A fallback chain is a child key whose `defaultFactory` delegates to the next key.** It works because
`defaultFactory` receives the context, so each link keeps its own app, project and file layering.

## Consequences

- No engine change, and no reserved "common" slot.
- Each feature wires its own chain.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).

## Rejected alternatives

### Slot selection over an ordered list, with a common slot

Bakes a naming convention for the common slot into the engine, for what a factory already expresses.
