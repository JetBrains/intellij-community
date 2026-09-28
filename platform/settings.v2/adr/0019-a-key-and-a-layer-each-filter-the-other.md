# 0019. A key and a layer each filter the other

Date: 2026-06-17

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md).

## Context

Some settings belong only at certain layers: UI scale, theme and font size make no sense per project. A
machine-specific tool path must not land in shared storage. Some layers, the other way round, hold only their own
subsystem's keys. Consultations (@nik) asked how to keep a key out of layers where it makes no sense.

## Decision

**A key may declare a `layerFilter` over the layer descriptor, and a layer may declare a `keyFilter` over keys.
A layer is a candidate for a key only when both agree.** A rejected layer is skipped on read as if it held no
value, and a write goes to the first writable layer that passes. There is no fixed taxonomy of levels; a filter
checks well-known layer ids or any descriptor property.

## Consequences

- Writing `UI_SCALE` from a file context still lands at the application layer instead of failing.
- A self-contained subsystem can keep its keys in its own layers by restricting its own side only.
- A filter is code, so nothing can check it statically.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).
