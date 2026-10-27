# 0158. A failure of the storage context mapping of a provider propagates

Date: 2026-10-27

## Status

Accepted ([IJPL-259731](https://youtrack.jetbrains.com/issue/IJPL-259731)).

## Context

The vault asks each `LayerProvider` for the storage context of a context. It uses the answer to widen a reload
scope ([ADR 0115](./0115-a-write-waits-only-for-a-reload-of-its-own-context-or-an-ancestor.md)) and to find the
store of a write. The vault does not catch a failure of these calls.

A failure of `requestData` is different. The vault logs it and skips the provider, and the other providers still
load. A missing layer is a safe result, because the layers of the provider stay as they are.

Qodana reported the unguarded calls and asked to isolate each provider. Any method can throw. A catch is useful
only when the caller has a correct answer to use instead.

## Decision

**The vault does not catch a failure of `LayerProvider.storageContext`. The failure propagates to the caller of
the operation.**

The vault has no correct storage context to use instead. The context itself or a guess can be a different store
than the store of the provider.

## Consequences

- A provider that throws from `storageContext` fails each reload and write that asks it. The error is visible at
  the operation, and it is not hidden in a log.
- No write goes to a wrong store because of a guessed storage context.
- The vault isolates `requestData` and does not isolate `storageContext`. This difference is intentional.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Log the failure and continue without the mapping of the provider

The operation then uses the context itself, and that is a guess. When the provider maps the context to a wider
store, a reload covers less than the store, and a write goes to a different store.
