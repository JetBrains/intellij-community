# 0039. A key declares the context kinds it is defined at

Date: 2026-07-14

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0128](./0128-a-key-filters-contexts-with-a-predicate.md).

## Context

Some settings belong to a granularity, not to a storage: UI and appearance settings make sense at application
level only, and must not be overridden per project or per file. A `layerFilter`
([ADR 0019](./0019-a-key-and-a-layer-each-filter-the-other.md)) confines a key to where it is stored, which is a
different constraint.

## Decision

**A key declares the context kinds it is defined at (app, project, directory, file, scope). Resolution skips the
disallowed nodes of the chain.** Context filtering is the domain constraint; layer filtering stays the storage
constraint.

## Consequences

- An app-only key read from a file context resolves at the application.
- A write to a disallowed context is not settled yet: an error, or a redirect to the nearest allowed context.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md).
