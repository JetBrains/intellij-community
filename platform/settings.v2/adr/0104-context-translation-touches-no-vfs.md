# 0104. Context translation touches no VFS

Date: 2026-09-10

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Contexts are translated on the message ([ADR 0103](./0103-a-side-stores-only-its-own-context-identities.md)). The frontend file system
mirror is lazy, so
a context can name a file that side has not materialized yet.

## Decision

**Context translation works on strings only. It never asks for a `VirtualFile`, and the walk up and down the
context chain compares strings.**

## Consequences

- Translation never fails or forces a fetch for a file the frontend has not seen.
- A context can exist on one side only, with no live file behind it, as in the monolith.
- The resulting behavior is in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).
