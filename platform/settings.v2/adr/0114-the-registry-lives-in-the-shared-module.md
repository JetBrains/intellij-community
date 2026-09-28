# 0114. The registry lives in the shared module

Date: 2026-09-24

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The engine has api, shared and backend modules ([ADR 0082](./0082-a-backend-module-holds-the-project-scoped-machinery.md)). Both sides of
the wire need to resolve
key identities.

## Decision

**The key registry and the promotion extension point live in the shared module. The prefix extension point stays in
the api module, where the key reads it.**

## Consequences

- Frontend and backend use one registry implementation.
- The promotion EP sits next to its only consumer.
