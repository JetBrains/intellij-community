# 0171. Migrations run in parallel, in no order, and a write does not wait for them

Date: 2026-10-30

## Status

Accepted ([IJPL-261033](https://youtrack.jetbrains.com/issue/IJPL-261033)).

## Context

After a reload, the platform runs the migrations that the migration ledger does not list
([ADR 0170](./0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md)).
The migrations of different owners have nothing in common.

A write waits until its context is ready
([ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md)). A migration writes the keys
itself.

## Decision

**The platform launches each migration in its own coroutine. The migrations run in parallel and in no order. They do
not delay the readiness of a context, so a write does not wait for them. The platform only promises to launch a
migration, and to record it when it ends with success.**

## Consequences

- A slow migration does not delay the others.
- A read between the reload and the end of a migration sees the values before the migration. The listeners fire when
  the migration writes.
- A user write in that window can be overwritten by the migration. This is accepted.
- The design assumes that a migration is cheap and fast. So the platform keeps no record of the migrations that run
  now. This is accepted for now, with two effects:
  - Two checks close together can launch the same migration twice at the same time. Both runs write the same
    values.
  - A reload does not cancel a migration that runs. So when the config changes during a run, for example at a
    branch switch, the migration still writes the values that it read from the old config. The platform then
    records it as run for the new config, and it does not run there again.
- Two steps that depend on each other must be one migration. For example, the import of a
  `PersistentStateComponent` and a later type change of the same key.
- An ordinal number could order the migrations of one owner. It is not decided whether it is worth it.
- The resulting behavior is in the [Settings Migrations spec](../spec/settings-migrations.spec.md).

## Rejected alternatives

### A context stays not ready until its migrations end

The migrations write themselves, so their writes would wait for their own end.

### An order between migrations

An order couples the migrations of different owners. A source that needs several steps keeps them in one migration.
