# 0137. A user context is postponed

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Older ADRs put a user node into the context chain, above or below the application
([ADR 0027](./0027-a-file-outside-the-project-tree-parents-directly-to-the-project.md), and the earlier scope model
in [ADR 0138](./0138-named-scopes-are-postponed-and-their-place-in-the-chain-is-open.md)). A user context would hold
the settings of a person, across every IDE and every version they use. No user context exists, and the chain ends
at the application ([ADR 0125](./0125-a-file-under-no-content-root-gets-the-project-context.md)).

## Decision

**A user context is postponed. It is not built until a real need for it appears.**

## Consequences

- The application context stays the root of every chain.
- It is not clear that a user context is needed.
- A user context must keep its settings consistent across every IDE and every version. That cost may be more than
  the feature is worth.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md).
