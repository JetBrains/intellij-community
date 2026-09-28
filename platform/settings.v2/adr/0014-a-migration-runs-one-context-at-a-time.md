# 0014. A migration runs one context at a time

Date: 2026-06-11

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A migration imports settings from a `PersistentStateComponent` or derives a new key from existing ones. Its
source values can live in different layers, contributed by different sources. The first idea was to walk all
of a project's layers as one structure. But layer order is per origin: two directory configs in sibling subtrees
never share a resolution stack, so there is no single project-wide order to walk.

## Decision

**The unit of migration is the context. `MigrationContextProvider` gives a migration its own scope context (`context()`) and a context for
every layer holding any listed key (`forEachContext(keys)`).** Contributors
report those contexts through `provideContexts`, as wide as possible; the platform unions them and dedups by
origin, so contexts are value-equal by origin.

## Consequences

- Each yielded context resolves effective values across its whole stack, so cross-key, cross-layer sources are
  visible at once.
- A shared origin migrates once.
- Contributors must implement `provideContexts`.

## Rejected alternatives

### Walk the project's layers as one structure

There is no project-wide layer order.
