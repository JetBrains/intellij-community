---
name: Settings Migrations
description: Requirements for how the platform declares, runs and records the migrations of settings data.
style: plain-1
targets:
  - ../resources/intellij.platform.settings.v2.xml
---

# Settings Migrations

Status: Draft
Date: 2026-10-30

## Summary

A migration moves settings from an older shape to a newer one. For example, it imports the values of a
`PersistentStateComponent`, changes the value type of a key, or splits or merges keys. A plugin declares a migration as
an extension with an id. After a reload, the platform runs each migration that the migration ledger of the scope does
not list, and records each migration that ends with success. Nothing of this is built yet.

## Goals

- Run each migration once per application and once per project.
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
- A migration with no `id` must never run.
- The platform must not create the instance of a migration that the migration ledger lists. See
  [ADR 0168](../adr/0168-a-migration-is-an-extension-with-an-id-that-the-platform-creates-only-to-run-it.md).

### Run

- After a reload installs the layers of the application kind, the platform must run each application migration that
  the migration ledger of the application context does not list. See
  [ADR 0170](../adr/0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md)
  and [ADR 0160](../adr/0160-a-reload-installs-the-layers-kind-by-kind.md).
- After a reload installs the layers of the project kind, the platform must run each project migration that the
  migration ledger of the project context does not list.
- A migration must receive the scope context of its run: the application context or the context of the project. See
  [ADR 0169](../adr/0169-a-migration-is-a-suspend-function-of-its-scope-context-and-the-platform-does-not-know-its-source.md).
- Each migration must run in its own coroutine. The migrations must run in parallel and in no order. See
  [ADR 0171](../adr/0171-migrations-run-in-parallel-in-no-order-and-a-write-does-not-wait-for-them.md).
- A running migration must not delay the readiness of a context. A write must not wait for a migration. See
  [ADR 0171](../adr/0171-migrations-run-in-parallel-in-no-order-and-a-write-does-not-wait-for-them.md).
- When a migration ends with success, the platform must add its id and the time of the run to the migration ledger.
- When a migration throws, the platform must log the error with the plugin of the migration, and must not add the
  migration to the ledger. The other migrations must go on.
- When a migration is cancelled, the platform must not add it to the ledger, and must log no error.
- When an extension of a migration point appears and no reload runs, the platform must run the check again. See
  [ADR 0170](../adr/0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md).
- A migration whose entry a user removes from the ledger must run again after the next reload.

### Migration ledger

- The migration ledger must be one platform key at the scope context. Its value must map the id of a migration to the
  time of its successful run. See
  [ADR 0170](../adr/0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md).
- The check "did this migration run" must merge the ledgers of all localities of the scope context. An id in any of
  them must count as run. See
  [ADR 0172](../adr/0172-a-check-merges-the-migration-ledgers-of-all-localities-and-an-update-writes-the-closest-one.md).
- An update must read the closest ledger, local over team, add the id, and write it back by the usual write rules. It
  must not change the ledger of another locality. See
  [ADR 0172](../adr/0172-a-check-merges-the-migration-ledgers-of-all-localities-and-an-update-writes-the-closest-one.md).

## Open Questions / Risks

- Whether a migration also needs the kind of the layers that the reload installed, besides the scope context.
- Migrations of environmental keys. A team migration writes their values only on the machine of devops, but the team
  ledger lists the migration. An environmental ledger is one idea, but it adds complexity. See
  [ADR 0172](../adr/0172-a-check-merges-the-migration-ledgers-of-all-localities-and-an-update-writes-the-closest-one.md).
- Remote development. A first idea: the migrations run on both sides and write only into the local, not mirrored,
  layers, with a ledger per side. See
  [ADR 0173](../adr/0173-migration-support-is-built-before-remote-development-is-done.md).
- A rule for the ids, so that two plugins do not declare the same id.
- An ordinal number to order the migrations of one owner.
- How Settings Sync treats the application ledger in `app.settings.json`.
- A user write between a reload and the end of a migration can be overwritten by the migration.
