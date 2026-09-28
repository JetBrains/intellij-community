# 0119. A project context identity is the location hash, not the project id

Date: 2026-09-29

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0143](./0143-a-receiving-side-puts-its-own-project-into-a-context.md).

## Context

A project context identity holds `Project.locationHash`. Each side of remote development computes it on its own,
so the frontend and the backend get different identities for one project. The platform `ProjectId` is the same on
both sides, so it looks like a better identity.

`ProjectId` is random: the backend creates it when the project registers, and the frontend receives it. It changes
on every open. The snapshot cache stores its entries by context identity and loads them at the next start
([ADR 0083](./0083-the-binary-snapshot-cache-is-a-pure-accelerator.md)).

No context identity crosses the two sides yet. Each side has its own vault and reads locally
([ADR 0084](./0084-each-side-has-its-own-vault-and-every-read-is-local.md)).

## Decision

**A project context identity stays `Project.locationHash`. `ProjectId` does not go into a context identity.**

## Consequences

- A cached entry of a project stays valid after a restart.
- The two sides keep different project identities, as
  [ADR 0103](./0103-a-side-stores-only-its-own-context-identities.md) wants.
- Mirroring will need the identity pair of
  [ADR 0107](./0107-the-connection-carries-a-live-table-of-content-roots.md). `ProjectId` can link the two
  projects to find the pair, because it is the same on both sides during a session.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md).

## Rejected alternatives

### `ProjectId` as the project identity

Both sides would get one identity with no translation. But every restart would give the project a new identity,
so the snapshot cache would never match.
