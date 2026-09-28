# 0115. A write waits only for a reload of its own context or an ancestor

Date: 2026-09-28

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md).

## Context

[ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md) makes a write wait while a
reload runs for the context itself, an ancestor, or a descendant. The gate prevents one failure: the vault persists
a stale model over a store that a reload is about to replace.

The vault works with a provider per storage context. It loads, installs, and persists the layers of one storage
context as a unit, and it gives no guarantee across storage contexts. A write at a context changes only the layers
of that context and its ancestors. A reload of a descendant replaces only the layers of the descendant subtree.

## Decision

**A write waits while a reload runs for its own context or an ancestor. A reload of a descendant does not make the
write wait.**

A reload scope covers whole storage contexts. One store can back several contexts: a directory config also holds
the overrides of the files directly in that directory. The vault widens a reload scope to the widest storage context
that a provider maps it to. A reload of a file then covers its directory. The vault makes this decision, not the
caller and not the provider.

## Consequences

- An application-level write does not wait for the reload of a project or a directory.
- A write and a descendant reload touch different storage contexts, so a stale model cannot overwrite the reload.
- A caller can reload any context. It does not need to know how the providers group their stores.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Wait for a descendant reload too

This is the rule of [ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md). It protects nothing, because a descendant reload never replaces a layer that the write
changes. It makes an application-level write wait for every project and directory reload.
