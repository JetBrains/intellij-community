# 0054. Non-settings state is out of scope by design

Date: 2026-07-24

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0004](./0004-caches-stay-out-of-the-settings-api.md).

## Context

[ADR 0004](./0004-caches-stay-out-of-the-settings-api.md) kept caches out of the settings API. The engine's
contract turned out deliberately relaxed: a write persists on a debounce with no durability guarantee,
reconciliation with external changes is eventual, and a mid-edit file falls back instead of failing.

## Decision

**Caches, derived data and high-frequency mutable state stay out of this system by design. They keep using
storage built for strong consistency, transactional durability or write-heavy throughput.**

## Consequences

- The relaxed contract is the right trade for user settings, and it stays relaxed.
- A feature that stores non-settings state here gets no guarantee it relies on.
