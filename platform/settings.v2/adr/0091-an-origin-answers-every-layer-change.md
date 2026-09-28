# 0091. An origin answers every layer change

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The origin owns the write rules: a read-only enforced layer, a key filter, a locality restriction, a validator. So
it can refuse or alter a copy-side change ([ADR 0090](./0090-a-copy-applies-a-change-at-once-and-the-origin-echoes-it.md)). If it stayed
silent on a refusal,
the copy would keep its speculative value forever.

## Decision

**The origin answers every layer change, accepted or not. The copy takes the answer. Unconfirmed changes on the
copy have a cap and a timeout.**

## Consequences

- A refused change is reverted on the copy.
- A lost answer cannot make the unconfirmed set grow without bound.
