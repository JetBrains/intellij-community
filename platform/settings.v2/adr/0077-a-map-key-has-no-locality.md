# 0077. A map key has no locality

Date: 2026-08-14

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

`SettingsMapKey` extends `SettingsKey`, so it inherits a `KeyLocality`. A map key is a storage dimension: it holds
slots keyed by a discriminator, and its slots live in whatever localities its child keys land in.

## Decision

**A map key has no locality of its own. Slot operations ignore the locality it inherits and act across all
localities.** How to express this in the API (a separate map-key type, a marker) is still open.

## Consequences

- The inherited field is meaningless and must not be used for routing.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md) and the
  [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
