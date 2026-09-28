# 0015. A project-level migration keeps no "already migrated" marker

Date: 2026-06-11

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The obvious way to avoid running a migration twice is to record that it ran. At project level that is
unreliable: a user can switch branches outside the IDE and reopen the project with old config that needs
migrating again. A marker would make the platform miss it.

## Decision

**Migrations are attempted on every project open and on VCS actions (checkout, pull, merge, rebase). Only
app-level state may keep a marker. `isApplicable` (default `true`) keeps the re-check cheap:** the platform runs
only the migrations that report applicable, silently or with a prompt, controlled by a setting.

## Consequences

- Old config that comes back through VCS is migrated again.
- Every migration must write a cheap, correct `isApplicable`, typically "source present" or "target absent".
- A derivation that keeps its sources is attempted on every open and does nothing, by design.

## Rejected alternatives

### A per-project marker

Branch switches outside the IDE make it lie.
