# 0160. A reload installs the layers kind by kind

Date: 2026-10-28

## Status

Accepted ([IJPL-259782](https://youtrack.jetbrains.com/issue/IJPL-259782)). Amends
[ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md).

## Context

A reload asks every provider at once, installs all the returned layers, sweeps, and then lifts the readiness gate
([ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md)). One slow provider
keeps every context of the scope dirty. The file layer is slow when a project opens
([IJPL-259782](https://youtrack.jetbrains.com/issue/IJPL-259782)), so the application and the project contexts wait
for it.

[ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md) rejected an install per provider. A write cannot
know its target layer until all layers of its context are loaded. Readiness is per context, so readiness per provider
helps nobody.

A provider now declares the context kinds that it supplies layers at
([ADR 0159](./0159-a-provider-declares-the-context-kinds-it-supplies-layers-at.md)).

## Decision

**A reload installs its layers in buckets, one bucket per context kind. A bucket installs when every provider whose
range includes its kind has answered. A context gets ready when the bucket of its kind and the buckets of every
wider kind have installed.**

- A reload asks a provider as soon as it holds the reload mutex of the widest kind of the provider
  ([ADR 0162](./0162-reloads-take-one-mutex-per-context-kind-from-the-application-down.md)). A reload that no other
  reload blocks holds all the mutexes at once, so it asks every provider at once. The buckets install in the order in
  which their providers answer.
- The vault installs a bucket and sweeps its stale layers under one lock hold. The sweep of a bucket covers only the
  layers of its kind.
- A provider whose range covers several kinds answers once, and its answer gates each of these buckets. Each bucket
  installs only the layers of the answer at its own kind. For example, a provider of `PROJECT` to `FILE` delays both
  the project and the file bucket until it answers.
- A context of kind K under the scope stays dirty until the bucket of K and the buckets of every wider kind install.
  So the application and the project contexts get ready without waiting for the file layer. A file context gets
  ready only when the project and the application buckets have installed too, also when the file bucket was first.
- A held push applies when the context of its store gets ready.

## Consequences

- The objection of [ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md) does not apply.
  Readiness stays exact per context: a write at a context waits for every bucket that can hold its target layer, and
  for no other bucket.
- A write at a directory or a file context still waits for the file layer.
- A reader at a dirty context can see the new layers of a narrow kind with the old layers of a wider kind. A dirty
  context allows such a read, as it allows a read in the middle of any reload.
- A held push applies when the context of its store gets ready, not when the whole reload ends. This changes the
  moment that [ADR 0123](./0123-a-push-during-a-reload-waits-until-the-reload-installs.md) sets, and keeps its
  purpose: the install of the bucket of the store comes first, so it cannot drop a newer push.
- The vault tracks the readiness per scope and kind. A running reload keeps its scope and the kinds that it has not
  installed yet. A context is dirty while a running reload covers it and still has its kind or a wider kind
  pending. The loading-scope set of [ADR 0064](./0064-readiness-is-exposed-per-context.md) therefore holds the pending kinds too.
- A write waits only for a reload of its own context or an ancestor
  ([ADR 0115](./0115-a-write-waits-only-for-a-reload-of-its-own-context-or-an-ancestor.md)). This stays true. The
  buckets only make a context ready earlier.
- A listener notification waits until the context it carries gets ready
  ([ADR 0161](./0161-a-listener-notification-waits-until-the-context-it-carries-is-ready.md)).
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Install the buckets from the application down

The order adds nothing, because the readiness of a context already waits for every wider bucket. It only delays a
narrow bucket behind a slower provider of a wider kind.

### Install all layers at once

This is the current behavior. The slowest provider decides when the application and the project contexts get ready.

### Install the layers of each provider when it answers

[ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md) rejected it. A layer that is still loading can be
the target of a write, so the write must wait for the whole context anyway.
