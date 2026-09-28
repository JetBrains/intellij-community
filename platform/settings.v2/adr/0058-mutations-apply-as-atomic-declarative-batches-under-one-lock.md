# 0058. Mutations apply as atomic declarative batches under one lock

Date: 2026-07-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0033](./0033-a-write-gives-no-read-your-writes-guarantee.md).

## Context

With the vault as the source of truth ([ADR 0057](./0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md)),
mutations need one
primitive. Some writes belong together, such as a discriminator at the app layer and a value at the project
layer. A lambda block would run caller code under the lock and let `key[ctx] = v` inside it silently run as a
separate mutation.

## Decision

**Every mutation applies to the model under one `Mutex`, and the public operations suspend until it is applied:
read-your-writes. The single primitive is "apply this batch atomically": a declarative list of entries (`set`,
`copySlot`, `dropSlot`, `renameSlot`) collected by a fluent builder and run by an explicit terminal.** A plain
`key[ctx] = v` is a one-entry batch. Each entry may override the batch's context. The vault checks the whole batch
first, then applies it, so a rejected entry fails the batch before anything changes.

## Consequences

- A write followed by a read returns the written value.
- All-or-nothing with no rollback, and no caller code under the lock.
- A batch is data: loggable, validatable, and able to cross an RPC boundary.
- Forgetting the terminal does nothing.
- A value computed from a read inside the batch is not supported.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md).

## Rejected alternatives

### A lambda block

Caller code under the lock, and a nested `key[ctx] = v` escapes the batch.
