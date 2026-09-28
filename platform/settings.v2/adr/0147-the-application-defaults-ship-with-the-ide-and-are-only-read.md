# 0147. The application defaults ship with the IDE and are only read

Date: 2026-10-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A default supplied as data is a layer at the widest position
([ADR 0017](./0017-a-context-dependent-default-is-a-factory-and-a-data-driven-default-is-a-layer.md)). The IDE ships
its other defaults, such as the color schemes and the keymaps, as resources inside its jars. It reads them through
the class loader, and the config directory holds only the copies and changes of the user.

## Decision

**The application defaults ship with the IDE as one resource of the platform: `settings/app.settings.default.json`
in the `intellij.platform.settings.v2.shared` module. The defaults layer only reads it, through the class loader.**

- The resource is a maintenance helper. A key already has a default in its code. The resource lets a maintainer
  configure the IDE, take the application config, and ship it as the defaults, without code changes.
- A product that needs other defaults replaces or amends the resource when it builds. The IDE still reads one
  resource.
- A plugin does not ship defaults through this resource. It contributes a default of a platform key through the
  default factory of that key
  ([ADR 0156](./0156-a-plugin-contributes-a-default-of-a-platform-key-through-the-default-factory-of-the-key.md)).

## Consequences

- No defaults file appears in the config directory of a user, and a user cannot edit or delete the defaults.
- A change to the defaults comes with a new IDE build.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### A file in the config `options` directory

The config directory belongs to the user. A user could edit or delete the defaults there.

### An extension per plugin, like `bundledColorScheme`

Each plugin would ship its own defaults resource, and the layer would merge them. The engine has no rule to merge
the contributions of two plugins to one key
([ADR 0156](./0156-a-plugin-contributes-a-default-of-a-platform-key-through-the-default-factory-of-the-key.md)).
