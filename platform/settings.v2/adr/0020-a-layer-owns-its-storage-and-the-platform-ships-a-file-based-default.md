# 0020. A layer owns its storage, and the platform ships a file-based default

Date: 2026-06-17

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0133](./0133-a-provider-persists-what-the-vault-holds.md).

## Context

Layers come from very different sources: a config file, an enterprise daemon, a devcontainer, an in-memory
value provider, a remote service. The question was whether the platform owns storage behind a `StorageAdapter`
concept, or each contributor owns it.

## Decision

**Where a layer's data lives is the layer's own responsibility (`LayerDescriptor.getValue` / `setValue`). The
platform owns storage for its built-in layers, and ships a file-based default: a config-backed contributor that
turns a file into a layer, given the file and its anchors.** There is no `StorageAdapter` concept.

## Consequences

- The common case needs no custom I/O.
- Unusual sources (daemon, remote, in-memory) plug in with no platform change.
- The platform does not know how a contributed layer stores data.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).
