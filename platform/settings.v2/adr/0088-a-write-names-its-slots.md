# 0088. A write names its slots

Date: 2026-09-04

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0067](./0067-a-dependent-slotted-write-pins-its-discriminator-or-shares-a-batch.md).

## Context

A slotted write resolved its own address: it read the discriminator through the layers, then spliced the value into
the selected slot. That is a read-modify-write against a value another writer can move in between, and the damage
is permanent: the value lands in the stale slot, and nothing removes it.
[ADR 0067](./0067-a-dependent-slotted-write-pins-its-discriminator-or-shares-a-batch.md) only documented the
foot-gun. In remote dev, the same race hits every write, because a write and its echo resolve against two states.

## Decision

**A write resolves nothing: it takes every slot from the context, and fails with `UnpinnedDiscriminatorException`
when the context does not pin one. The check walks the whole declared storage chain. A read still resolves
discriminators.** `context.accessFor(key)` pins everything one key needs, outermost first;
`context.access(a, b)` pins a named set. A batch entry carries its own address.

## Consequences

- The address cannot drift, on this side, on the peer, or later.
- The pinned context is the address: side-independent and serializable, so a remote mutation carries it.
- An earlier batch entry that moves a discriminator no longer redirects a later one, and the batch need not hold
  the lock across the two.
- A pin is a snapshot, so a concurrent write can in principle split a multi-link address.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md) and the
  [settings contexts spec](../spec/settings-contexts.spec.md).
