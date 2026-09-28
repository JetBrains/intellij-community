# 0067. A dependent slotted write pins its discriminator or shares a batch

Date: 2026-07-25

## Status

Superseded by [ADR 0088](./0088-a-write-names-its-slots.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

With one lock, a sequential dependent write is correct: the value resolves its discriminator from the
just-updated model. A shared discriminator mutated concurrently is not safe: another coroutine can flip it
between "set the discriminator" and "set a value against it", because the lock is released between the two
calls.

## Decision

**Two safe tools: pin the discriminator on the write through `access { }`, or group both writes in one batch. A
naked write against the current discriminator is the documented foot-gun. `access` is not required, because a
batch covers the same need.**

## Consequences

- Correct code has two ways to be correct.
- Truly parallel interdependent writes that use neither stay racy.
