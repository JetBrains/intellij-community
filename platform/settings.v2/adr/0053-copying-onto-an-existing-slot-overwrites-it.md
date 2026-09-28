# 0053. Copying onto an existing slot overwrites it

Date: 2026-07-22

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

`copySlot` can target a slot that already has data. A pre-check at submission would race mutations still queued
in the debounced flow, and once a transform is applied there is no clean way to fail late.

## Decision

**A copy onto a non-empty slot silently overwrites it. Guarding against that is the caller's responsibility.**
Nested slots move as the whole sub-map.

## Consequences

- No false promise of a guard the engine cannot keep.
- A careless copy loses the target's data.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
