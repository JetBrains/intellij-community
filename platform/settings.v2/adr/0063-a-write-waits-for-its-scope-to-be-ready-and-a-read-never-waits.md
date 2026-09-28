# 0063. A write waits for its scope to be ready, and a read never waits

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0115](./0115-a-write-waits-only-for-a-reload-of-its-own-context-or-an-ancestor.md).

## Context

On a branch switch from A to B, if a mutation could win over the incoming reload, persisting the stale
near-empty branch-A model plus one mutation would overwrite branch B's rich config. Gating reads too would freeze
the UI during a load.

## Decision

**A mutation at a context suspends until the context is ready: no reloading scope is the context itself, an
ancestor, or a descendant. Reads are never gated.** Reloads are per scope, so a reload in project A does not
block project B; an app-level write waits for everything, since app is everyone's ancestor.

## Consequences

- The reload always wins, and a mutation applies on top of freshly loaded config. There is no
  mutation-versus-reload reconcile rule.
- Reads during a reload can see old data briefly.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).
