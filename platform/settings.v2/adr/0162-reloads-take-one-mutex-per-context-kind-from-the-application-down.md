# 0162. Reloads take one mutex per context kind, from the application down

Date: 2026-10-28

## Status

Accepted ([IJPL-259782](https://youtrack.jetbrains.com/issue/IJPL-259782)). Supersedes
[ADR 0071](./0071-reloads-run-one-at-a-time-under-a-global-mutex.md).

## Context

Reloads run one at a time under one global mutex
([ADR 0071](./0071-reloads-run-one-at-a-time-under-a-global-mutex.md)). A reload now installs its layers kind by kind
([ADR 0160](./0160-a-reload-installs-the-layers-kind-by-kind.md)), and the file bucket can
wait for indexing. Under one global mutex, every later reload waits for that indexing too: another project open, a
VCS update, or an application reload.

## Decision

**The vault keeps one reload mutex per context kind. A reload takes them from `APPLICATION` down to `FILE`, hand over
hand: it takes the mutex of the next kind before it releases the mutex of the current kind.**

- A reload takes each next mutex as soon as it can. It does not wait until the bucket of the current kind installs.
- A reload asks a provider as soon as it holds the mutex of the widest kind of the provider. A provider of `PROJECT`
  to `FILE` is asked under the project mutex. A reload that no other reload blocks holds all the mutexes at once, so
  it asks every provider at once.
- A reload installs the bucket of a kind while it holds the mutex of that kind. It releases that mutex after the
  install, when it already holds the next mutex. So the file layers of the provider above install under the file
  mutex.

## Consequences

- A later reload can install its application and project buckets while an earlier reload waits for its file
  bucket.
- The installs of one kind keep their order. A later reload cannot overtake an earlier one at any kind, because it
  takes the mutexes in the same order.
- All reloads take the mutexes in one fixed order, so they cannot deadlock.
- The asks of each provider happen in reload order, as its installs do. So a later reload never installs data that
  it read before an earlier reload read it. This keeps the guarantee of
  [ADR 0071](./0071-reloads-run-one-at-a-time-under-a-global-mutex.md).
- A later reload asks the providers of a kind only after the earlier reload has installed that kind.
- A provider that never answers blocks only the kinds of its range in the later reloads. This narrows the open
  problem of [ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md). It
  does not solve it.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Keep one global mutex

The file bucket then blocks every later reload until indexing ends.

### Hold the mutex of a kind only for the install

The asks of two reloads then run at the same time. The earlier reload can read the disk later, install first, and
then the later reload installs its older data over it.

### One mutex per scope

Two reloads of different scopes, such as the application and a project, cover the same contexts. A mutex per scope
does not order their installs.
