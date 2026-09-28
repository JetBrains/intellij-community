# 0074. A layer's locality and a key's locality are two enums

Date: 2026-08-13

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md).

## Context

A layer is exactly one locality. A key constrains which localities it accepts. An environmental value, such as
the git path, cannot live in a shared or VCS config at all.

## Decision

**`LayerLocality` is a layer's exclusive identity: `TEAM`, `LOCAL`, `ENVIRONMENTAL`. `KeyLocality` is a key's
routing policy: `ENVIRONMENTAL` (environmental layers only) or `SHAREABLE` (team or local). `KeyLocality` holds the
relation: `accepts(layer)` and `defaultLayer` (`SHAREABLE → LOCAL`, `ENVIRONMENTAL → ENVIRONMENTAL`).** A
provider is tagged with its `LayerLocality`, and its `accepts` consults the key's policy.

## Consequences

- Illegal combinations such as `{TEAM, ENVIRONMENTAL}` cannot be written down.
- An environmental write must route to the machine config; there is no shared fallback.
- Whether a key author gets any say in team versus local is still open; `SHAREABLE` carries no such split.
- The resulting behavior is in the [layer locality spec](../spec/layer-locality.spec.md).

## Rejected alternatives

### A `Set<LayerLocality>` on the key

It admits illegal combinations.
