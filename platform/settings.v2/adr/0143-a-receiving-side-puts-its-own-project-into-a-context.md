# 0143. A receiving side puts its own project into a context

Date: 2026-10-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0103](./0103-a-side-stores-only-its-own-context-identities.md),
[ADR 0107](./0107-the-connection-carries-a-live-table-of-content-roots.md) and
[ADR 0119](./0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md).

## Context

A project context identity holds `Project.locationHash`, and each side of remote development computes its own
([ADR 0119](./0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md)). Several ADRs plan to pair
the two hashes:

- [ADR 0103](./0103-a-side-stores-only-its-own-context-identities.md) translates a project identity with the pair
  known at the connection.
- [ADR 0107](./0107-the-connection-carries-a-live-table-of-content-roots.md) sends a live table with the pair of
  project identities, and a pair of urls for each content root.
- [ADR 0119](./0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md) says mirroring will
  need that pair.

The content roots RPC carries only the backend root urls. The platform already names a project across the sides by
its `ProjectId`, and each side finds its own `Project` from it.

## Decision

**No side sends its project identity to the other. A side translates only data that it receives and that holds a
project, mostly a context. It puts its own project identity in place of the project part.** The receiving side finds
its project from the `ProjectId` that the platform gives with the call.

- The content roots RPC carries only the backend root urls, collapsed to the topmost ones. It carries no url
  pair, because a file context anchors on the backend url on both sides
  ([ADR 0127](./0127-a-file-context-anchors-on-the-backend-root-url-on-both-sides.md)).
- No connection keeps a table of identity pairs.

## Consequences

- The backend location hash never reaches the frontend, and the other way round.
- A context that crosses the sides keeps its root and its relative path, and gets the project of the receiving side.
- The translation is built when the first data that holds a context crosses the sides. Nothing crosses yet.
- The resulting behavior is in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).
