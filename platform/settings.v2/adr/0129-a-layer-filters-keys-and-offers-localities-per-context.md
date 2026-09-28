# 0129. A layer filters keys and offers localities per context

Date: 2026-10-01. Merged: 2026-10-14.

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0019](./0019-a-key-and-a-layer-each-filter-the-other.md),
[ADR 0074](./0074-a-layers-locality-and-a-keys-locality-are-two-enums.md),
[ADR 0098](./0098-a-replica-layer-has-a-provider-of-its-own.md) and
[ADR 0101](./0101-a-replica-provider-shares-a-module-with-its-origin.md).

## Context

[ADR 0019](./0019-a-key-and-a-layer-each-filter-the-other.md) gives a key a layer filter and a layer a key filter.
A layer is a candidate for a key only when both agree, on read and on write.

The code has the key side: a context filter
([ADR 0128](./0128-a-key-filters-contexts-with-a-predicate.md)) and a layer filter. The layer side has no key filter.
It has `accepts(context, key)`, which only a write consults. So a key can read a value from a layer that it can
never write, against the rule of
[ADR 0126](./0126-a-key-reads-only-from-the-layers-of-the-localities-it-accepts.md).

No implementation of `accepts` looks at the key. Each one refuses a read-only layer or a context kind that the layer
does not store. `availableLocalities(context)` already says which localities a provider can store at a context.

The vault applies a mutation under its lock
([ADR 0058](./0058-mutations-apply-as-atomic-declarative-batches-under-one-lock.md)). Probing a layer through its
write would persist while probing, and nothing slow may run under the lock. So `accepts` is a synchronous check
with no I/O.

## Decision

**A layer provider filters keys with a key filter, which a read and a write both consult. It accepts every key by
default. `availableLocalities(context)` is the only write check on the context, and `accepts` goes away.**

- An empty set of localities means that the provider takes no write at that context. A read-only layer and a
  context kind that the layer does not store both give an empty set.
- A read needs no context check on the provider side. A provider holds no layer at a context that it does not
  serve.
- The key filter and `availableLocalities` are synchronous and do no I/O. The vault calls them under its lock, so a
  write routes by an explicit check, not by a probe that persists.
- A check on the pair of a context and a key comes when a layer needs one. No layer needs it today.

## Consequences

- A key and a layer filter each other in the same way on read and on write. A key never reads a value from a layer
  that refuses the key.
- The filters are symmetric: a key filters contexts and layers, and a layer filters keys and offers localities per
  context.
- A failed write no longer tells which layer refused it, or why. The error says only that no layer took the write.
- The vault checks the locality policy of the key against the localities that a provider offers. The provider
  does not consult the policy, as [ADR 0074](./0074-a-layers-locality-and-a-keys-locality-are-two-enums.md) says.
- A replica provider and its origin share the key filter and the localities, in place of one `accepts` object
  ([ADR 0098](./0098-a-replica-layer-has-a-provider-of-its-own.md),
  [ADR 0101](./0101-a-replica-provider-shares-a-module-with-its-origin.md)).
- The ReSharper keys and layers of [ADR 0140](./0140-resharper-settings-are-their-own-keys-and-layers.md) see only
  each other through the two filters.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).

## Rejected alternatives

### Filter reads with `accepts`

`accepts` answers whether a write can land, not whether a key belongs. The read-only defaults layer refuses every
write, so a read through `accepts` would hide every default.

### Keep `accepts` next to the key filter

It repeats what `availableLocalities` says about the context, and no layer uses its key argument.

## Earlier decisions

### 0036, 2026-07-11: a layer write suspends and returns an acceptance result

`LayerDescriptor.setValue` was `suspend`, the accept point of the write path, and returned a minimal result:
accepted, or failed with a reason. The key-level `setValue` propagated it. A write meant that the layer accepted
the value ([ADR 0033](./0033-a-write-gives-no-read-your-writes-guarantee.md)), and a layer could be offline or
refuse a key. The platform could fall through to the next writable layer or surface the error. 0059 replaced it,
because a probe through the write persists under the vault lock.

### 0059, 2026-07-25: a provider answers `accepts` synchronously

`LayerProvider` exposed a synchronous `accepts(context, key): WriteResult.Failed?` with no I/O. The vault validated
the write, walked up the context chain skipping filter rejects, and targeted the first `(context, provider)` whose
`accepts` passed. Routing became an explicit check, not a side-effecting probe. 0129 replaced `accepts` with the key
filter and `availableLocalities`.
