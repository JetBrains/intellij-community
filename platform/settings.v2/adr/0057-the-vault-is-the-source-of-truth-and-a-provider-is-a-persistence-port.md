# 0057. The vault is the source of truth, and a provider is a persistence port

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0136](./0136-a-write-swaps-an-immutable-model-and-a-read-takes-no-lock.md).

## Context

In the prototype the file was the source of truth, and the vault was an eventually consistent cache fed by
asynchronous provider pushes. Each provider ran its own debounced read-apply-write-push channel. That gave no
read-your-writes, stale-discriminator races between independent channels, several overlapping conflict points (external reload, the
provider's own write, our write echoing back through the watcher), and no readiness or
eviction protocol.

## Decision

**The vault owns the authoritative in-memory model, `(context identity, provider) → MapValue`, and all mutation
logic. All reads resolve against it. A provider is a persistence port: it returns its layers on load, declares
which writes it accepts, and serializes the slice the vault hands it.** A provider watching its own store pushes
later spontaneous changes.

## Consequences

- Mutation rules live in one place, not in every provider.
- The provider's read-modify-write disappears.
- Internal storage can use plain maps, since one lock serializes every access.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).
