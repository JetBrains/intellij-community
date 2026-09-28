# 0127. A file context anchors on the backend root url on both sides

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0103](./0103-a-side-stores-only-its-own-context-identities.md).

## Context

[ADR 0103](./0103-a-side-stores-only-its-own-context-identities.md) keeps only its own side's identities in a vault.
A message translates them. In that design, a file keeps its relative path under the receiving side's own root.

The frontend gets the content roots from the backend, with the backend urls
([ADR 0107](./0107-the-connection-carries-a-live-table-of-content-roots.md)). It matches a file to a root by path,
because the two sides use other protocols. It then anchors the file context on the backend root url it received.

## Decision

**A file context anchors on the backend root url, on the backend and on the frontend.** The frontend uses the url
that it got from the backend. It has no root url of its own in a file context.

## Consequences

- A file context has the same root and the same relative path on both sides. Only its project part differs
  ([ADR 0119](./0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md)).
- A translation of a file context changes only its project part.
- A frontend vault holds backend urls in its file contexts, so it does not hold only its own side's identities.
- A cached frontend file context belongs to one backend. After a connection to another backend, it does not match.
  The file layers come from the backend anyway.
- The resulting behavior is in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).

## Rejected alternatives

### Anchor on the frontend root url and translate it on each message

Each side would name a file context in its own way, and every message would need a translation of the root. The
frontend has no use for its own url in a file context.
