# 0065. A reload evicts stale layers by mark-sweep

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

After a branch switch, a config that existed in the old branch may not exist in the new one. Its entries must
leave the model. The file layer tracked vanished configs with its own bookkeeping.

## Decision

**Each vault entry carries a ready flag. A reload clears the flags of its scope, sets them on install, and sweeps
what stays unflagged, only for providers that returned.** An entry with a pending save is not swept.

## Consequences

- The file layer can drop its bulk-vanish tracking.
- A single config deleted while the IDE runs is still handled by the watcher pushing an empty layer.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).
