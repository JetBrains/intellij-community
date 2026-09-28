# 0079. A slot operation targets its context, not the subtree

Date: 2026-08-14

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A slot operation broadcasts to the layers at its context ([ADR 0078](./0078-a-slot-operation-broadcasts-to-every-layer-at-its-context.md)).
It could instead cover a whole
subtree, a directory and everything under it, through a scope context.

## Decision

**The target is the context itself, not its subtree.** A caller migrates a subtree by calling at each context it
can reach. Whether a subtree-wide target is wanted stays open.

## Consequences

- Matches the overview's "applied at a given context across whatever layers hold the slot there".
- Renaming a profile across a project with many directory overrides takes many calls.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
