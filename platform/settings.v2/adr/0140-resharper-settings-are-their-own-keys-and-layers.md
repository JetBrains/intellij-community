# 0140. ReSharper settings are their own keys and layers

Date: 2026-10-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0024](./0024-resharper-settings-join-as-inert-layers-only-while-editing.md).

## Context

[ADR 0024](./0024-resharper-settings-join-as-inert-layers-only-while-editing.md) lets ReSharper register no keys.
It adds inert layers that refuse every key, carry an opaque ReSharper handle, and anchor against the IDEA layers.
The layers join only in an editing context, so that one settings UI shows both stacks.

Anchors are gone ([ADR 0073](./0073-precedence-is-context-then-locality-then-provider.md)). A layer provider now
filters keys, and a key filters layer providers
([ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md)). Nothing of
[ADR 0024](./0024-resharper-settings-join-as-inert-layers-only-while-editing.md) is built.

## Decision

**ReSharper settings are their own keys and their own layers, which work only with each other.** A ReSharper key
accepts only the ReSharper layer providers, and a ReSharper layer provider accepts only the ReSharper keys. A
platform key ignores a ReSharper layer, and a ReSharper key ignores a platform layer.

## Consequences

- ReSharper settings resolve and persist in normal program flow, like any other settings.
- They do not depend on a settings UI or on an editing context.
- The two filters of [ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md) are enough. The
  platform needs no special case for ReSharper.
- It is not built.
