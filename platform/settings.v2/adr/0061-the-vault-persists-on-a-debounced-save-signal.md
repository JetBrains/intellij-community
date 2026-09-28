# 0061. The vault persists on a debounced save signal

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The vault applies mutations to its model at once ([ADR 0058](./0058-mutations-apply-as-atomic-declarative-batches-under-one-lock.md)).
Persisting each one
synchronously would put file I/O under the lock and rewrite a file per write. A payload sent to the saver would
go stale if later mutations landed during the debounce. Our own write also comes back as a watcher echo.

## Decision

**On apply, the vault sends a payload-less save signal to a conflated, debounced channel per
`(context, provider)`. When it fires, the vault reads the current model slice under the lock and the provider's
`writeData` serializes it, off the lock. While a save is pending, the vault ignores that provider's spontaneous
pushes for it.**

## Consequences

- A burst of writes collapses into one serialize, and the transform logic lives only in the vault.
- A save writes current truth, including mutations that landed during the debounce.
- Our own watcher echo is dropped.
- An external edit racing an IDE write of the same file is lost. Accepted.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).
