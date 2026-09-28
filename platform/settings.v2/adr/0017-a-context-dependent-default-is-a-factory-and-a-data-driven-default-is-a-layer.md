# 0017. A context-dependent default is a factory, and a data-driven default is a layer

Date: 2026-06-17

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Some defaults are not constants. A shortcut can depend on the operating system. An enterprise or a product can
ship its own defaults. The question was whether this needs a dedicated `AppDefaults` mechanism.

## Decision

**A default computed by code is a `defaultFactory: (SettingsContext) -> T` on the key, and the key stays
writable so layers can still override it. A default supplied as data (enterprise, product-shipped) is an
ordinary dynamic layer at the widest position.** No separate defaults mechanism.

## Consequences

- Both cases reuse what the model already has.
- A factory receives the context, so it can read other settings through it.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md) and the
  [file layers spec](../spec/file-layers.spec.md).
