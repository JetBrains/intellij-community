# 0168. A migration is an extension with an id that the platform creates only to run it

Date: 2026-10-30

## Status

Accepted ([IJPL-261033](https://youtrack.jetbrains.com/issue/IJPL-261033)).

## Context

Hundreds or thousands of `PersistentStateComponent`s exist, in the platform and in third-party plugins. They move to
settings v2 in batches over many releases, so the set of migrations only grows. Dmitry Jemerov pointed out that the
load of a migration class alone makes the startup slower, also when the migration does nothing. With hundreds of
migrations, the platform must not load their classes on each start.

The extension API can list the extensions of a point without their instances.
`ExtensionPointName.filterableLazySequence()` gives a `LazyExtension` for each extension. It holds the `id` attribute
of the `<extension>` element, and it creates the instance only on request. The project converters
(`ConverterProvider`) use this pattern: they compare the ids with the ids of the applied converters, and they create
only the missing ones.

## Decision

**A migration is an extension of one of two extension points: the application migrations or the project migrations.
Each extension declares an `id`, unique within its plugin, and an implementation class. The platform lists the extensions with
`filterableLazySequence()`, compares their ids, and creates an instance only for a migration that it runs.**

- An application migration runs for the application scope. A project migration runs for each project scope.
- Other kinds of migration, for example a migration per directory, come later.

## Consequences

- The check "which migrations to run" reads only the plugin descriptors, which are in memory. It loads no migration
  class.
- The plugin id and the migration id together are the identity of a migration. A new id makes the migration run
  again.
- Two plugins can declare the same id with no clash. A plugin keeps its ids unique by itself.
- A migration that moves to another plugin gets a new identity, so it runs again.
- A plugin that is disabled declares no migrations. Its migrations become visible when the plugin is enabled.
- A migration that runs does not stop when its plugin unloads. The run belongs to the platform, not to the plugin,
  so it goes on with the classes of the unloaded plugin. This is accepted for now.
- The resulting behavior is in the [Settings Migrations spec](../spec/settings-migrations.spec.md).

## Rejected alternatives

### A listener on each load that always runs

Alexandr Evstigneev proposed that the platform call each migration always, and the migration decides whether it has
work to do. Each call loads the class of the migration, so the startup gets slower with each new migration.

### An eager extension point

A plain `extensions` call creates every instance, so it loads every class. It has the same cost.
