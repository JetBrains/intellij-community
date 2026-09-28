# 0123. A push during a reload waits until the reload installs

Date: 2026-09-30

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0066](./0066-pushes-during-a-reload-are-not-suppressed.md).

## Context

[ADR 0066](./0066-pushes-during-a-reload-are-not-suppressed.md) applies a push at once, also while a reload runs.
A reload asks the providers for their data, then installs the returned layers and drops the stale ones.

This order can lose a change:

1. The reload asks a provider, and the provider reads its store.
2. The store changes, for example a new config file after a branch switch. The provider pushes the new layer.
3. The vault applies the push.
4. The reload installs its older result. The pushed layer is not in it, so the reload drops it as stale.

Nothing pushes again, so the layer stays missing until the next reload. A test hit this order: a seed pushed
during the reload that followed a provider mask was lost in 8 of 10 runs.

## Decision

**The vault holds back a push whose store is under the scope of a running reload. It applies the held pushes in
arrival order after that reload installs.**

- A push for a store outside the scope of every running reload applies at once, as before.
- The vault applies the held pushes also when the reload fails or is cancelled.
- The pending-save rule of [ADR 0061](./0061-the-vault-persists-on-a-debounced-save-signal.md) applies when the
  vault applies a held push, not when the push arrives.

## Consequences

- A change that arrives during a reload is no longer lost. A push is never older than the reload read it overlaps,
  so applying it after the install gives the latest state.
- A push that repeats what the reload read changes nothing, and fires no listener.
- A reader sees such a change only after the reload ends. The context is dirty during the reload anyway, so a
  ready read does not see less.
- A push no longer makes a context ready during a reload of its scope.
- The vault keeps the held pushes in memory for the length of a reload.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Apply a push at once, as [ADR 0066](./0066-pushes-during-a-reload-are-not-suppressed.md) does

The reload install can drop the pushed layer, as the context shows.

### Drop a push during a reload

The reload can read the store before the change, so the change is lost.

### Let the reload not drop a layer that a push installed during the reload

It keeps a pushed layer, but the reload install can still overwrite a pushed layer with older data from its read.
