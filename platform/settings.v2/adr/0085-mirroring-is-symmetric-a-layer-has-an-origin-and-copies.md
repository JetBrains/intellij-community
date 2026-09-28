# 0085. Mirroring is symmetric: a layer has an origin and copies

Date: 2026-09-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

With a vault on each side ([ADR 0084](./0084-each-side-has-its-own-vault-and-every-read-is-local.md)), some layers
must appear on both. The obvious design has a frontend rule and a backend rule. But the frontend owns some layers
too, such as its own machine layer, which the backend may need.

## Decision

**Mirroring is per layer, with two roles. The origin is the source of truth: a provider populates it, and only the
origin side persists it. A copy is a writable mirror in the other side's vault, managed by the engine, with no
store.** A side is the origin of some layers and holds copies of others at once, so both sides run the same code.

## Consequences

- No side is special in the engine.
- The usual picture (backend origin for team and local, frontend copy, environmental not mirrored) is an
  assignment of roles, not a rule.
- Who decides which layers mirror is open.
