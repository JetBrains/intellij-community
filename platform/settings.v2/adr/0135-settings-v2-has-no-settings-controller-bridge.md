# 0135. Settings v2 has no Settings Controller bridge

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0002](./0002-settings-controller-bridges-psc-and-the-layered-api.md). Amends
[ADR 0006](./0006-existing-settings-move-to-the-new-api-one-at-a-time-by-their-owners.md).

## Context

[ADR 0002](./0002-settings-controller-bridges-psc-and-the-layered-api.md) makes Settings Controller a bridge. The
layers of Settings v2 would join its chain, so the existing `PersistentStateComponent` code would get the merged
values with no change.
[ADR 0006](./0006-existing-settings-move-to-the-new-api-one-at-a-time-by-their-owners.md) moves the features
one at a time, by their owners, and says this task delivers the engine and the bridge.

Nothing of this bridge is built, and Settings v2 does not interact with Settings Controller.

## Decision

**Settings v2 has no bridge to Settings Controller. This task delivers the engine only.** The features still move
one at a time, as [ADR 0006](./0006-existing-settings-move-to-the-new-api-one-at-a-time-by-their-owners.md) decides.

- A feature that has not moved keeps its `PersistentStateComponent` and its own storage.
- A feature that has moved reads and writes its settings through keys.

## Consequences

- Settings v2 does not depend on Settings Controller, and Settings Controller does not depend on Settings v2.
- The values of Settings v2 do not reach a `PersistentStateComponent`. A feature gets the layers only when it moves.
- Both systems exist side by side for a long time, each for its own features.

## Rejected alternatives

### Settings Controller as a bridge, as [ADR 0002](./0002-settings-controller-bridges-psc-and-the-layered-api.md) says

It needs a merge of the layers into each state blob of a `PersistentStateComponent`. The effort is not worth it,
because each feature moves to keys anyway.
