# 0042. A key carries a validator that rejects an invalid write

Date: 2026-07-15

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A UI needs to check user input before it writes, and show why a value is wrong. The engine must not store a
value the key considers invalid. Config files are hand-edited, so an invalid value can still reach storage.

## Decision

**A key carries a validator. It returns a reason, or `null` when the value is valid, and never throws. A UI
calls it live on candidate input; a write calls the same check and rejects an invalid value with an
exception.**

## Consequences

- One check serves the UI and the write path.
- A hand-edited file can still hold a value the validator would reject; the validator does not describe stored
  data.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).
