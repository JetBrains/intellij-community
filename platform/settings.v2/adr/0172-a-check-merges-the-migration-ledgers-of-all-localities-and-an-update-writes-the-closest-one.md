# 0172. A check merges the migration ledgers of all localities, and an update writes the closest one

Date: 2026-10-30

## Status

Accepted ([IJPL-261033](https://youtrack.jetbrains.com/issue/IJPL-261033)).

## Context

The migration ledger of a scope is one platform key
([ADR 0170](./0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md)).
Its value is an object that maps the id of a migration to the time of its run. Like any key, it can have a value in
several localities.

- An unhinted write goes to the local layer. The team-first mode sends it to the team layer
  ([ADR 0076](./0076-settings-have-three-localities-a-write-goes-to-local-by-default-and-team-editing-is-a-per-machine-mode.md)).
- A local value wins over a team value
  ([ADR 0073](./0073-precedence-is-context-then-locality-then-provider.md)). A value replaces the whole object.

So with a plain read, a local ledger hides a team ledger. A teammate with a local ledger then does not see the
migrations that the team ran, and runs them again into the local layer.

## Decision

**A check "did this migration run" merges the ledgers of all localities of the scope context. An id in any of them
counts as run. An update reads the closest ledger, local over team, adds the id, and writes it back by the usual
rules. The ledger of the other locality stays as it is.**

- Usually the ledger and the migrated values go to the local layer.
- A team migration is a job for devops in the team-first mode:
  1. Check out the project into a new directory.
  2. Switch to the team-first mode.
  3. Shut down the IDE.
  4. Remove all local configs in the project directory, so that they do not hide the team values. The
     environmental configs stay.
  5. Start the IDE and open the project. All migrations run and save into the team files.
  6. Commit and push.

## Consequences

- An update is a normal read and a normal write. So the first update of a locality copies the entries of the closest
  ledger into it. For example, the first local update copies the entries of the team ledger into the local ledger.
  A later removal of an entry from the team ledger then has no effect on that machine.
- In the team-first mode, an update starts from the local ledger and writes its entries into the team ledger. The
  team can then get entries for migrations that the teammates never ran. A correct team migration is the
  responsibility of devops. A single-person project has no teammates, so there is no harm there.
- A team migration on two branches gives two times for one id, so the team file conflicts. This case is rare.
- An environmental key is always written to the environmental file. In a team migration, its values stay on the
  machine of devops, but the team ledger lists the migration. The teammates never get these values. This is open.
- The application layer has no local locality
  ([ADR 0149](./0149-the-application-layer-has-no-local-locality.md)), so the application ledger is in
  `app.settings.json`. For now this file is local to the machine. How Settings Sync treats it is not known.
- The resulting behavior is in the [Settings Migrations spec](../spec/settings-migrations.spec.md).

## Rejected alternatives

### A plain read of the ledger

The check reads the ledger like any key, so it gets only the closest ledger. A local ledger then hides the team
ledger, and the check does not see the migrations that the team ran. The case that rules it out:

1. The team runs a migration into the team layer. The team ledger lists it.
2. The team then changes the value of a migrated key, and commits it.
3. A teammate pulls. The teammate has a local ledger, which does not list the migration.
4. The migration runs again on the machine of the teammate. It writes the old migrated value into the local layer.
5. The local value wins over the team value. So the teammate lost the change of the team, with no notice.

### A map key with a slot per migration

A map key keeps its slots across all localities
([ADR 0077](./0077-a-map-key-has-no-locality.md)). The merge on a check gives the same result with one plain key.

### A forced write into the team layer

The usual write rules and the team-first mode already cover the team migration.

### A marker per option

Each option would need its own marker.
