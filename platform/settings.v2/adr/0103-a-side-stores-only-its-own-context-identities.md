# 0103. A side stores only its own context identities

Date: 2026-09-10

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0127](./0127-a-file-context-anchors-on-the-backend-root-url-on-both-sides.md) and
[ADR 0143](./0143-a-receiving-side-puts-its-own-project-into-a-context.md).

## Context

A context identity is a string; a file context holds a root url plus a relative path, and the urls belong to one
machine. The snapshot cache persists context identities.

## Decision

**A vault holds only its own side's identities. Translation happens on the message, in both directions:** the
application identity crosses unchanged, a project identity uses the pair known at connection, and a file keeps its
relative path under the receiving side's own root.

## Consequences

- A cached identity stays valid after a reconnect to a different peer.
- The monolith writes nothing different for the sake of remote dev.
- The resulting behavior is in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).

## Rejected alternatives

### A canonical machine-free identity everywhere

It changes what the monolith writes, for the benefit of remote dev.
