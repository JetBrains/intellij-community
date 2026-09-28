# 0156. A plugin contributes a default of a platform key through the default factory of the key

Date: 2026-10-19

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A plugin can ship something that selects a slot of a platform key, such as a keymap or a color scheme. When a user
selects it, the slots of that keymap hold no value in any layer, so a read falls back to the default of the key. The
plugin must deliver the values for its slots.

The engine has no rule to merge the contributions of two plugins to one key. Only the owner of the key knows whether
a later value replaces an earlier one or a list grows. The application defaults resource is a maintenance helper of
the platform, not a channel for plugins
([ADR 0147](./0147-the-application-defaults-ship-with-the-ide-and-are-only-read.md)).

A context-dependent default is a factory, and a data-driven default from one source, such as an enterprise or a
product, is a layer at the widest position
([ADR 0017](./0017-a-context-dependent-default-is-a-factory-and-a-data-driven-default-is-a-layer.md)). Many sources,
one per plugin, are not a layer: they need a merge rule, and only the owner of each key has one.

## Decision

**The owner of a platform key decides how plugins contribute its defaults, usually with an extension point. The
default factory of the key looks the contribution up. To learn the slot path of the read, the factory calls
`context.accessFor(key)` with its own key and reads each pinned discriminator.**

- The engine does not pin the resolved discriminators before it calls the default. The factory resolves them again
  only when it needs them.
- A model change between the read and the factory can give the factory a newer slot. This is accepted.

## Consequences

- The platform has no merge rule for defaults. The owner of each key chooses the meaning of a contribution.
- A read that finds a value pays nothing. Only a factory that needs the slot path resolves it.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).

## Rejected alternatives

### A defaults file per plugin, merged by the engine

A later file overwrites an earlier one on the same key, and a list that several plugins fill cannot merge.

### A defaults layer per plugin

It adds a layer for every plugin to every read, and it still needs a rule for the order.

### The read pins every resolved discriminator before it calls the default

The read cannot know in advance that it falls back. It would have to carry the pins through every level of the
walk. When no layer holds the storage of a map key, the read never resolves its discriminator, so the engine would
have to resolve those discriminators only to pin them. The factory can do the same work only when it needs it.
