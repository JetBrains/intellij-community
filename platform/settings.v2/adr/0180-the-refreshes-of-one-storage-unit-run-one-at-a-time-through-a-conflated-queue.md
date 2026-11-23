# 0180. The refreshes of one storage unit run one at a time, through a conflated queue

Date: 2026-11-20

## Status

Accepted ([IJPL-262279](https://youtrack.jetbrains.com/issue/IJPL-262279)).

## Context

A provider that sees a change of its store reads the store again and pushes the layers to the vault
([ADR 0057](./0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md)). Each change started
its own refresh, and two refreshes of one storage unit had no order. A refresh that read an older state of a config
could push after a refresh that read a newer state. The vault then kept the old values until the next reload.

A save of the IDE followed by a delete of the same config showed it: the save gives a "create" event, the delete
gives a "delete" event, and the two refreshes ran at the same time.

## Decision

**Each storage unit has one conflated queue of refresh signals and one coroutine that takes them one by one. A
refresh reads the store and pushes its layers before the next refresh of that unit starts.**

- A signal carries no data. A refresh always reads the current state of the store.
- The queue is conflated: the signals that arrive during a refresh collapse into one more refresh.
- A refresh that fails is logged, and the queue takes the next signal.
- The vault uses the same shape for the saves of a storage unit.

## Consequences

- The pushes of one storage unit reach the vault in the order of the reads. Each change starts a read after it, so
  the last push holds the newest state of the store.
- A burst of changes of one config, such as a branch switch, gives fewer reads.
- The refreshes of different storage units do not wait for each other.
- A storage unit that had a change keeps its queue and its idle coroutine as long as its service lives.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### One lock per service around the read and the push

It gives the same order with one field. But the refreshes of every config of the service then wait for each other,
and a burst of changes of one config still reads the config once per change.
