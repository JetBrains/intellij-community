# 0078. A slot operation broadcasts to every layer at its context

Date: 2026-08-14

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0060](./0060-a-slot-operation-lands-in-the-first-accepting-layer.md).

## Context

[ADR 0060](./0060-a-slot-operation-lands-in-the-first-accepting-layer.md) routed a slot operation like a plain
write, into the single narrowest accepting layer. A slot is not a regular value: at one context it can live in
the team, local and environmental layers of several providers at once, and moving it in one of them leaves the
others behind.

## Decision

**A slot operation matches its context directly, without walking up the chain, and rewrites every
`(provider, locality)` layer at that context that holds the slot, best-effort.** Read-only and inapplicable layers
are skipped, and a layer without the slot is untouched. Plain writes still route to one layer; both apply under
one vault lock, so a batch and a rename stay atomic.

## Consequences

- A rename moves the slot everywhere it lives at that context.
- Layers the broadcast cannot reach (other branches, closed projects, other machines) remain the API user's job.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
