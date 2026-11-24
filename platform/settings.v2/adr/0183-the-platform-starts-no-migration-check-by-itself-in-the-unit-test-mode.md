# 0183. The platform starts no migration check by itself in the unit-test mode

Date: 2026-11-24

## Status

Accepted ([IJPL-262723](https://youtrack.jetbrains.com/issue/IJPL-262723)).

## Context

The platform starts a migration check after a reload installs the layers of a kind, and after a change of the
migration extensions
([ADR 0170](./0170-the-platform-runs-each-migration-once-per-scope-and-the-migration-ledger-records-each-success.md)).
A check runs each migration that no ledger lists, and records it in the ledger.

In a unit test, the tests of one run share one application. A test clears the configs that it owns and reloads them.
The ledger is then empty, so the next check runs each production migration again and writes its ledger entry into
those configs. The write creates a config file where a test expects none. It replaces a config that a test wrote by
hand. It adds a run between the reloads that a test counts. The first production application migration of the core
plugin broke 11 tests of the engine this way. Each new production migration has the same effect, in every test
module that loads its plugin.

## Decision

**In the unit-test mode the platform starts no migration check by itself, and no switch turns it on.** A reload and
a change of the migration extensions start no check there. The check itself does not change: it reads the ledgers,
runs each migration that no ledger lists, and records each success.

- A unit test of a migration calls the migration.
- A unit test of the engine starts a check through a test-only entry.
- A test that starts a real application proves that a reload and a change of the extensions start a check.

## Consequences

- A production migration does not touch the configs of a unit test, in any test module.
- A unit test cannot prove that the platform starts a check. That proof needs a test with a real application, which
  is slower.
- A unit test that expects a migration to run by itself gets the defaults of the keys. It must call the migration.
- The resulting behavior is in the [settings migrations spec](../spec/settings-migrations.spec.md).

## Rejected alternatives

### A switch that a test turns on

The application is shared, so the switch is global. A test that turns it on also turns it on for each test that runs
at the same time, and the production migrations run in those tests again.

### The engine tests remove the production migrations

A test fixture of the engine tests can remove the registered migrations. It protects only the tests of one module.
Each other test module that loads a migration still runs it.

### The tests accept a foreign migration

A test would wait for the end of the migrations before it writes its configs. Each test of the engine, and each
later one, would need this step, and a miss shows only as an unstable test.

### A clean application for each test

The test application is one per process. A clean application needs a process of its own for each test class.
