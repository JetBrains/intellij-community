---
name: Settings Migrations
description: Requirements for how the platform declares, runs and records the migrations of settings data.
style: plain-1
targets:
  - ../resources/intellij.platform.settings.v2.xml
  - ../src/com/intellij/platform/settings/v2/SettingsMigration.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsMigrationLedger.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsMigrationRunner.kt
---

# Settings Migrations

Status: Draft
Date: 2026-10-30

## Summary

A migration moves settings from an older shape to a newer one. For example, it imports the values of a
`PersistentStateComponent`, changes the value type of a key, or splits or merges keys. A plugin declares a migration as
an extension with an id. After a reload, the platform runs each migration that the migration ledger of the scope does
not list, and records each migration that ends with success.

## Goals

- Run a migration of the application or of a project only while no ledger of that scope lists it.
- Load no migration class for a migration that already ran.
- Keep the base free of any knowledge about the source of a migration.

## Non-goals

- How long the owners keep their migrations and the old `PersistentStateComponent` classes. This is a product
  decision.
- Declarative helpers for simple migrations. They can come later, on top of this base. See
  [ADR 0169](../adr/0169-a-migration-is-a-suspend-function-of-its-scope-context-and-the-platform-does-not-know-its-source.md).
- Migrations of other kinds, for example a migration per directory.

## Requirements

### Declaration

- A plugin must declare a migration as an extension of the application migrations or of the project migrations. The
  extension must have an `id` attribute and an implementation class. See
  [ADR 0168](../adr/0168-a-migration-is-an-extension-with-an-id-that-the-platform-creates-only-to-run-it.md).
- The `id` must be unique within its plugin. Two plugins may declare the same `id`, and each migration must run.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- A migration with no `id` must never run. The platform must log an error with the plugin of the migration.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- The platform must not create the instance of a migration that the migration ledger lists. See
  [ADR 0168](../adr/0168-a-migration-is-an-extension-with-an-id-that-the-platform-creates-only-to-run-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt

### Run

- After a reload installs the layers of the application kind, the platform must run each application migration that
  the migration ledger of the application context does not list. See
  [ADR 0170](../adr/0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md)
  and [ADR 0160](../adr/0160-a-reload-installs-the-layers-kind-by-kind.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- After a reload installs the layers of the project kind, the platform must run each project migration that the
  migration ledger of the project context does not list.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- A migration must receive the scope context of its run: the application context or the context of the project. The
  context of a project is a `ProjectAware`, which gives the project. See
  [ADR 0169](../adr/0169-a-migration-is-a-suspend-function-of-its-scope-context-and-the-platform-does-not-know-its-source.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- Each migration must run in its own coroutine. The migrations must run in parallel and in no order. See
  [ADR 0171](../adr/0171-migrations-run-in-parallel-in-no-order-and-a-write-does-not-wait-for-them.md).
- A running migration must not delay the readiness of a context. A write must not wait for a migration. See
  [ADR 0171](../adr/0171-migrations-run-in-parallel-in-no-order-and-a-write-does-not-wait-for-them.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- When a migration ends with success, the platform must add its ledger entry and the time of the run to the
  migration ledger.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- When a migration throws, the platform must log the error with the plugin of the migration, and must not add the
  migration to the ledger. The other migrations must go on.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- When a migration is cancelled, the platform must not add it to the ledger, and must log no error.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- When the extensions of a migration point change, the platform must run the check again, with no reload. See
  [ADR 0170](../adr/0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- A check must wait until its scope context is ready.
- A reload of the application scope must also run the project migrations of each open project.
- The migrations of a project must stop when the project closes.
- A migration that no ledger lists after a reload must run again. For example, a user removes its entry, or a branch
  switch brings a config with another ledger.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt

### Migration ledger

- The migration ledger of a scope must be one platform key at the scope context: `settings.appMigrations` at the
  application, and `settings.projectMigrations` at a project. Each key must resolve only at a context of its own
  kind. Its value must hold a `completed` map from `<pluginId>:<migrationId>` to the ISO-8601 time of its successful
  run. See
  [ADR 0170](../adr/0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md).
- The check "did this migration run" must merge the ledgers of all localities of the scope context. An id in any of
  them must count as run. See
  [ADR 0172](../adr/0172-a-check-merges-the-migration-ledgers-of-all-localities-and-an-update-writes-the-closest-one.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMigrationTest.kt
- An update must read the closest ledger, local over team, add the id, and write it back by the usual write rules. It
  must not change the ledger of another locality. So the first update of a locality copies the entries of the
  closest ledger into it. See
  [ADR 0172](../adr/0172-a-check-merges-the-migration-ledgers-of-all-localities-and-an-update-writes-the-closest-one.md).

## Open Questions / Risks

- Migrations of environmental keys. A team migration writes their values only on the machine of devops, but the team
  ledger lists the migration. An environmental ledger is one idea, but it adds complexity. See
  [ADR 0172](../adr/0172-a-check-merges-the-migration-ledgers-of-all-localities-and-an-update-writes-the-closest-one.md).
- Remote development. A first idea: the migrations run on both sides and write only into the local, not mirrored,
  layers, with a ledger per side. The migrations run on each side with no such rule.
- A migration that runs does not stop when its plugin unloads. See
  [ADR 0168](../adr/0168-a-migration-is-an-extension-with-an-id-that-the-platform-creates-only-to-run-it.md).
- A migration that moves to another plugin gets a new ledger entry, so it runs again.
- An ordinal number to order the migrations of one owner.
- How Settings Sync treats the application ledger in `app.settings.json`.
- A user write between a reload and the end of a migration can be overwritten by the migration.
- A migration is assumed to be cheap and fast. Two checks close together can launch the same migration twice at the
  same time. A reload does not cancel a migration that runs. So when the config changes during a run, the migration
  still writes the values that it read from the old config, and it is recorded as run for the new config. See
  [ADR 0171](../adr/0171-migrations-run-in-parallel-in-no-order-and-a-write-does-not-wait-for-them.md).
