# 0142. Migration waits until remote development is done

Date: 2026-10-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Several ADRs plan how a migration of settings data runs:
[ADR 0014](./0014-a-migration-runs-one-context-at-a-time.md),
[ADR 0015](./0015-a-project-level-migration-keeps-no-already-migrated-marker.md),
[ADR 0016](./0016-a-migration-drops-its-source-keys-after-the-whole-batch.md) and
[ADR 0040](./0040-data-migration-runs-on-the-layer-load-lifecycle.md). None of them is built, and no spec holds
them.

## Decision

**Migration support is postponed until the remote development part of Settings v2 is done.** The migration ADRs
stay the plan.

## Consequences

- Settings v2 ships with no migration support until then.
- No spec holds a migration rule yet. A spec comes with the implementation.
- The migration ADRs may change when the work starts.
- The open questions of the [layer loading spec](../spec/layer-loading.spec.md) link this ADR.
