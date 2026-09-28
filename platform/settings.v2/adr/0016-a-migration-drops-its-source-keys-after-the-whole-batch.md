# 0016. A migration drops its source keys after the whole batch

Date: 2026-06-11

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

With no marker ([ADR 0015](./0015-a-project-level-migration-keeps-no-already-migrated-marker.md)), a migration
needs another way to stop firing once it has done its work. Two applicable migrations can share a source key.

## Decision

**A migration that replaces its sources lists them in `keysToDrop`. After the applicable batch finishes, the
platform drops the union of all listed keys.** Once the sources are gone, `isApplicable` returns false on the
next open, so the migration stops firing on its own. A derivation that keeps its sources leaves the list empty.

## Consequences

- Migrations terminate without a marker.
- Deferring the drop to the end of the batch keeps two migrations with a shared source from starving each other.
- Dropping a key that something else still needs is the migration author's responsibility.
