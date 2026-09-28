# 0043. A write is atomic and needs no Apply

Date: 2026-07-15

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Today's settings dialog buffers changes and commits them on OK or Apply, a dialog-scoped transaction. The new
model is reactive and non-modal.

## Decision

**Each `key[ctx] = v` is a single accepted change that takes effect atomically and persists on its own. There is
no Apply buffer.**

## Consequences

- No transaction buffer to manage in the engine.
- Discrete changes through one sink make undo possible later.
- A UI that wants OK and Cancel has to build it on top.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md).
