# 0169. A migration is a suspend function of its scope context, and the platform does not know its source

Date: 2026-10-30

## Status

Accepted ([IJPL-261033](https://youtrack.jetbrains.com/issue/IJPL-261033)). Supersedes
[ADR 0014](./0014-a-migration-runs-one-context-at-a-time.md).

## Context

A migration moves settings from an older shape to a newer one. The source can be a `PersistentStateComponent`, a key
with an old value type, or keys to split or merge. So the base must not know about one kind of source.

An application migration needs no context. A project migration needs the project.

[ADR 0014](./0014-a-migration-runs-one-context-at-a-time.md) gives a migration a `MigrationContextProvider`. It yields
a context for each layer that holds a listed key, through `provideContexts` of each contributor. None of it is
built.

Dmitry Jemerov wants a declarative mechanism for the simple cases, so that the project does not get thousands of
lines of migration code.

## Decision

**A migration is a suspend function. It receives the scope context of its run: the application context or the
context of the project. The platform does not know what the migration reads or writes.**

- A migration reads its source and writes the keys through the usual API.
- A migration works at the level of its source. A `PersistentStateComponent` of a project migrates at the context
  of the project, not at the context of a file.
- Declarative helpers can come later, on top of this base. They are not part of it.

## Consequences

- One base covers the import from a `PersistentStateComponent`, a type change, a split, and a merge.
- The platform cannot check what a migration writes. A rule such as "one migration writes keys of one locality"
  belongs to a future migration skill.
- An owner moves the options of one source in one migration, not one migration per option.
- Until the declarative helpers exist, each migration is code.
- The resulting behavior is in the [Settings Migrations spec](../spec/settings-migrations.spec.md).

## Rejected alternatives

### A path to the old value on each key

Dmitry Jemerov proposed an XPath-like path to the old storage on `SettingsKey`. Dmitry Batkovich proposed a
`SettingDescriptor` of Settings Controller in the old format. Both tie the base to `PersistentStateComponent`. The
second also makes settings v2 depend on Settings Controller, and
[ADR 0135](./0135-settings-v2-has-no-settings-controller-bridge.md) rules this out.

### A settings group that names its source

Dmitry Jemerov proposed a `SettingsGroup` that holds the keys of one `PersistentStateComponent` and names it. It is
declarative, but only for this kind of source. A helper on top of this base can give the same result.

### One context at a time, as [ADR 0014](./0014-a-migration-runs-one-context-at-a-time.md) says

The migrations of an application and of a project work at the level of their source. They do not need a context for
each layer. A migration per directory is a later kind.
