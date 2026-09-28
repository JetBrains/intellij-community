# 0101. A replica provider shares a module with its origin

Date: 2026-09-10

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md).

## Context

A replica provider and its origin provider must answer `accepts` the same way
([ADR 0098](./0098-a-replica-layer-has-a-provider-of-its-own.md)), and they need one id that names the pair. A
lambda has no fingerprint, so nothing would catch two diverging verdicts.

## Decision

**A replica provider and its origin provider live in one module, sharing the `accepts` object and the pair id.**

## Consequences

- One mutation cannot route to two different layers.
- Two plugins that cannot share a module cannot pair an origin and a replica.
