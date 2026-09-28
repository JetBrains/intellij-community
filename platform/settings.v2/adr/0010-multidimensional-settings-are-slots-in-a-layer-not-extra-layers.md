# 0010. Multidimensional settings are slots in a layer, not extra layers

Date: 2026-05-29

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Some settings vary along an axis that is not the context chain: inspections per profile, code style per
language. The same key has different values depending on the selected profile. One suggestion (@konstantin.nisht) was to model a dimension
as a layer selected by context.

## Decision

**A dimension is slotted storage inside a regular layer. A `SettingsMapKey` holds one slot per value of its
discriminator key, child keys live in the selected slot, and a `SettingsAccessContext` can override the
discriminator for one access.** The layer set comes from the underlying context and is built once; an access
context is cheap to create and throw away.

## Consequences

- Layers do not multiply: app, project, directory and every other layer each hold their own slots.
- The discriminator resolves through the layers like any other key, so a directory can select its own profile.
- Reading an arbitrary slot needs no change of the user's selection.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md) and the
  [settings contexts spec](../spec/settings-contexts.spec.md).

## Rejected alternatives

### A layer per dimension value

Every existing layer would be duplicated per profile, per language and so on. The combinations explode.
