# 0008. A bulk change listener names its keys

Date: 2026-05-27

## Status

Superseded by [ADR 0044](./0044-a-key-is-observable-through-a-listener.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

`PersistentStateComponent` is not reactive: consumers poll `getState`, and notification comes through a zoo of
message-bus topics, dispatchers and flows. The new API needs change notification at setting granularity. A
change a consumer cares about is a change of the **effective** value at its context, which a write to any
layer can cause by shadowing or unshadowing a value. So every layer write makes the platform recompute the
effective values that someone listens to.

## Decision

**Listeners observe effective-value changes through `Flow`, per key or for a named set of keys in a context.
Observing every key in a context is not supported.**

## Consequences

- The platform recomputes only the keys someone named, not every key on every write.
- A consumer interested in "anything under this context" has no API for it.
