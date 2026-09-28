# 0146. An unload on one side removes the mirrored layers on the other

Date: 2026-10-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

In remote development, a layer can live on one side and be mirrored on the other. A replica provider on the
receiving side holds the mirrored layer ([ADR 0098](./0098-a-replica-layer-has-a-provider-of-its-own.md)). It
lives in one module with its origin provider
([ADR 0101](./0101-a-replica-provider-shares-a-module-with-its-origin.md)), but each side loads and unloads plugins
on its own. So the origin provider can unload while the replica provider stays loaded on the other side.

A provider that unloads takes its layers along, and the listeners of their keys fire
([layer loading spec](../spec/layer-loading.spec.md)).

## Decision

**When a layer provider unloads on one side, the other side must remove the layers that mirror it. This holds
whether the provider on the other side unloads too or stays loaded.**

## Consequences

- The other side never serves values from a layer whose origin is gone.
- The listeners of the removed keys fire on the other side too, as for a local unload.
- A replica provider that stays loaded keeps nothing of the unloaded origin. It gets layers again when the origin
  loads again.
- Mirroring is not built, so neither is this rule. No spec holds it yet.
