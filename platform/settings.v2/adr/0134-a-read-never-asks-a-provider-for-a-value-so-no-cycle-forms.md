# 0134. A read never asks a provider for a value, so no cycle forms

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0009](./0009-a-cycle-drops-the-offending-layer-instead-of-failing.md).

## Context

[ADR 0009](./0009-a-cycle-drops-the-offending-layer-instead-of-failing.md) breaks a cycle in the layer machinery. A
contributor that read settings while it built its layer, or a layer `getValue` that read settings while it
resolved, could re-enter the machinery. The platform tracked the calls and dropped the layer that re-entered.

A read now takes its value from the in-memory model of the vault
([ADR 0133](./0133-a-provider-persists-what-the-vault-holds.md)). A provider loads its layers when the vault asks
for them, apart from any read.

## Decision

**A read never asks a provider for a value, so a read cannot re-enter a provider. The platform tracks no
calls and breaks no cycle.**

- A provider that reads a setting while it loads reads the model, as any other caller does.
- On a read, the vault calls only the key filter of a provider. It must be synchronous and pure, and must not read
  settings ([ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md)).

## Consequences

- No layer is dropped from a stack because of a cycle, so the effective value never changes silently.
- A provider that reads a setting while it loads can see a value from before the load. The readiness gate covers a
  flow that must wait ([ADR 0131](./0131-a-read-that-waits-for-readiness-is-a-separate-suspend-call.md)).
- A key filter that reads settings breaks its contract. The platform does not guard against it.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md).
