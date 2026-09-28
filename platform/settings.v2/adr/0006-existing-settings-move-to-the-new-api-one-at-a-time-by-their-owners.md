# 0006. Existing settings move to the new API one at a time, by their owners

Date: 2026-05-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0135](./0135-settings-v2-has-no-settings-controller-bridge.md).

## Context

There are thousands of `PersistentStateComponent`s across the platform and plugins. Some subsystems (file
types, inspections, code style) carry their own resolution machinery. Migrating all of them is a volume of
work far beyond defining the new API, and a big-bang cutover would break plugins.

## Decision

**Adoption is incremental and opt-in, per setting. Each plugin or IDE team moves its own settings at its own
pace. This task delivers the engine and the bridge, not the migration.** We may move a few representative
settings ourselves to prove the design on real cases.

## Consequences

- No cutover date and no forced migration.
- v1 and v2 live side by side for a long time (see
  [ADR 0002](./0002-settings-controller-bridges-psc-and-the-layered-api.md)).
- Each complex subsystem is its own effort, on top of the engine.
