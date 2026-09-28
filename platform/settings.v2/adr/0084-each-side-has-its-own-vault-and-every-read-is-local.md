# 0084. Each side has its own vault, and every read is local

Date: 2026-09-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The monolith engine is one vault in one JVM. Remote dev splits the IDE into a frontend and a backend, often on
different machines. Reads are synchronous and hot ([ADR 0032](./0032-reads-are-synchronous-and-writes-suspend.md)).

## Decision

**Each side runs its own vault: a full engine with the same model, resolution and listeners, not a cache and not a
proxy. Every read resolves in the vault of the side that asks. It never crosses the wire, never blocks on the
link, and works while the link is down.** The wire carries layer state and mutations, never a resolution request.

## Consequences

- Reads stay synchronous and fast on both sides.
- The two vaults hold different layer sets, so the design must say what "consistent" means.
- The resulting behavior is in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).
