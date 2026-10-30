# 0170. The platform runs each migration once per scope, and the migration ledger records each success

Date: 2026-10-30

## Status

Accepted ([IJPL-261033](https://youtrack.jetbrains.com/issue/IJPL-261033)). Supersedes
[ADR 0015](./0015-a-project-level-migration-keeps-no-already-migrated-marker.md) and
[ADR 0016](./0016-a-migration-drops-its-source-keys-after-the-whole-batch.md). Amends
[ADR 0040](./0040-data-migration-runs-on-the-layer-load-lifecycle.md).

## Context

[ADR 0015](./0015-a-project-level-migration-keeps-no-already-migrated-marker.md) keeps no marker at the project level.
It tries each migration on each project open, and a cheap `isApplicable` filters them.
[ADR 0016](./0016-a-migration-drops-its-source-keys-after-the-whole-batch.md) drops the sources of a migration, so
that `isApplicable` returns false later. The reason for no marker is a branch switch: the old config can come back
through VCS.

Dmitry Jemerov objected: a call of hundreds of migrations on each start costs too much. Each call loads a class
([ADR 0168](./0168-a-migration-is-an-extension-with-an-id-that-the-platform-creates-only-to-run-it.md)). A check
of `isApplicable` for a `PersistentStateComponent` also reads its state. He also noted that `.idea` is usually not in
VCS, so the branch switch case is rare.

[ADR 0040](./0040-data-migration-runs-on-the-layer-load-lifecycle.md) runs a migration on the layer-load lifecycle,
and it leaves the registration, the order and the idempotency open.

## Decision

**After a reload installs the layers of the kind of a migration, the platform runs each migration whose id is not in
the migration ledger of the scope. When a migration ends with success, the platform adds its id and the time of the
run to the ledger.**

- The migration ledger is a platform key. It maps the identity of a migration to the time of its successful run.
  The identity is the id of its plugin and its own id, as `<pluginId>:<migrationId>`.
- There are two ledger keys: one for the application migrations, and one for the project migrations. Each key
  resolves only at a context of its own kind. With one key, a read at a project context goes up the chain to the
  application. An id in the application ledger then counts as run for a project migration with the same id.
- A migration that throws is not added. The platform logs the error with the plugin of the migration, and goes on.
- The platform listens to the migration extension points. When their extensions change, the platform runs the
  check again. Each check waits until its scope context is ready, so a check never reads a ledger in the middle of a
  reload.
- A user can remove the entry of a migration from the ledger by hand. The migration then runs again after the next
  reload.

## Consequences

- A migration does not run while a ledger of its scope lists it. Usually this is once per application, and once per
  project.
- A migration can run again. A branch switch can bring a config whose ledger does not list it, and a user can remove
  its entry. So a migration must give a correct result on a repeated run.
- A plugin that is enabled later gets its migrations run, with no reload.
- A failed migration runs again after each reload, until it ends with success.
- No source must be dropped, so the platform never writes the old storage.
- The opposite case also exists. When the ledger is not in VCS, a branch switch that brings back an old config does
  not run the migration again.
  This is accepted. A later product feature can offer to remove the old settings from VCS.
- The resulting behavior is in the [Settings Migrations spec](../spec/settings-migrations.spec.md).

## Rejected alternatives

### A check on each open, as [ADR 0015](./0015-a-project-level-migration-keeps-no-already-migrated-marker.md) says

It loads each migration class on each open, and its cost grows with each release.

### The source is the marker, as [ADR 0016](./0016-a-migration-drops-its-source-keys-after-the-whole-batch.md) says

For a `PersistentStateComponent`, the platform must remove the old state from its storage with no class. A team with
mixed IDE versions then loses the values in the older IDE.

### An implicit marker

A migration counts as done when a key of its group has an explicit value at the level of the source. For this, a
migration must write even the default values, and a written default never sees a later change of the default. A reset
to the default removes the value, so the migration runs again and brings back the old value.

### A migration on the first read or write of a key

Dmitry Jemerov wanted no migration until code uses the settings. A read cannot wait for it, because the old state can
live on a slow environment. A write must first migrate the other keys of the same source. Alexandr Evstigneev dropped
the idea in favor of the ledger.
