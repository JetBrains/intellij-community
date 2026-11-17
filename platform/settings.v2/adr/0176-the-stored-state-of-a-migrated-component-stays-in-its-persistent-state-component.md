# 0176. The stored state of a migrated component stays in its PersistentStateComponent

Date: 2026-11-17

## Status

Accepted ([IJPL-261930](https://youtrack.jetbrains.com/issue/IJPL-261930)).

## Context

A `PersistentStateComponent` often holds two kinds of values in one state. A setting is a value that the user
configures. Stored state is a value that the IDE produces as a side effect of its work, for example the bounds of a
window, the offset of a popup that the user dragged, or a flag that a notification was shown.

A feature that moves to the keys must decide what to do with the stored state. Non-settings state is out of scope of
this system ([ADR 0054](./0054-non-settings-state-is-out-of-scope-by-design.md)). A key for such a value puts it into
a config that a user edits by hand and that a team can share, and it needs a locality that fits no real case.

## Decision

**A migration to the keys moves only the settings of a component. The stored state stays in the
`PersistentStateComponent`, and the feature reads and writes it there as before.**

## Consequences

- The component stays after the migration. It holds the stored state, and it is the source of the migration.
- A feature can read a setting from a key and a related stored value from the component. A change that touches both
  is not one atomic batch.
- No key is environmental only because its value is stored state.
- The resulting behavior is in the [settings migrations spec](../spec/settings-migrations.spec.md).

## Rejected alternatives

### An environmental key for each stored value

It keeps the value on one machine, but the value still lands in a config for hand edits, and each drag or resize
becomes a write of a setting.

### A new state component only for the stored state

It gives a clean split, but the values need a second migration out of the old state for no gain.
