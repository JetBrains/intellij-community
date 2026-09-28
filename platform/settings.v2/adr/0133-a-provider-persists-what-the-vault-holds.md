# 0133. A provider persists what the vault holds

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0020](./0020-a-layer-owns-its-storage-and-the-platform-ships-a-file-based-default.md).

## Context

[ADR 0020](./0020-a-layer-owns-its-storage-and-the-platform-ships-a-file-based-default.md) makes a layer own where
its data lives, through `LayerDescriptor.getValue` and `setValue`. The platform ships a file-based layer, built from
a file and its anchors. There is no storage adapter.

The vault now holds every value
([ADR 0057](./0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md)). It asks a provider
for its layers ([ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md)). Layers order by
context, locality and provider, not by anchors
([ADR 0073](./0073-precedence-is-context-then-locality-then-provider.md)).

## Decision

**A layer provider owns how its data persists, but not the values. It returns or pushes its layer data to the
vault, and saves the data that the vault gives it.** There is no `LayerDescriptor`, no `getValue` or `setValue`, and
no anchor.

- The platform still ships the file-based layers, and there is still no storage adapter.
- A provider of another source, for example a daemon or a remote service, plugs in the same way.

## Consequences

- A read takes its value from the vault, never from a provider.
- The platform still does not know how a provider stores its data.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).
