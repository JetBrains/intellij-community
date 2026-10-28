# 0116. The vault drives loading with no timeout, and a provider returns its layers

Date: 2026-09-29. Merged: 2026-10-15.

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0160](./0160-a-reload-installs-the-layers-kind-by-kind.md).

## Context

Providers pushed their data during load, and nothing told the platform when configs were loaded. The vault now
owns the model.

A reload asks every provider for the data of one scope at once. It installs all the data, then drops the stale
layers. The result is the real picture of the scope. While the reload runs, the scope is dirty. Reads still work.
Writes under the scope wait for the readiness gate
([ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md)).

A provider that never answers keeps the scope dirty. Writes under the scope then wait, and later reloads wait for
the reload mutex ([ADR 0071](./0071-reloads-run-one-at-a-time-under-a-global-mutex.md)). This needs a reload, a
write, and a very slow provider at the same time. The case is rare, but it is a real problem.

## Decision

**`LayerProvider.requestData(scope)` is a suspend function that returns the layers it found for the scope. The
vault owns the reload cycle (cold start, project open, VCS change): it calls every provider concurrently, installs
what returns, sweeps, and lifts the readiness gate. `push` is only for spontaneous watcher updates.**

**A reload has no timeout. The vault waits for every provider of the reload to answer.** The reload runs in the
background. A slow reload delays only the writes under its scope. Reads do not wait. The reload still ends when
its caller's scope is cancelled, for example when the project closes.

## Consequences

- The suspend return is the readiness signal, with no bespoke "done" protocol.
- A slow but healthy provider never fails because of an arbitrary limit.
- A provider that never answers blocks the writes under its scope, `readWhenReady`, `awaitReady`, and every later
  reload.
- The arrival-ordered install from the backlog does not change this. Each install still waits for the reloads that
  arrived before it.
- The stuck provider stays an open problem. It needs a fix later, but not a timeout and not per-provider readiness.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### A timeout on each provider

No limit fits every case. A limit that is short enough to help a waiting write fails a slow but healthy provider,
for example the first scan of a large project. A timed-out provider also needs a rule: keep its old layers or
sweep them. Both rules can leave a picture that is not real.

### Install each provider's data when it answers, with readiness per provider

A write cannot know its target layer until all layers of the scope are loaded. A layer that is still loading can
be the target. So the write must wait for every provider anyway. `readWhenReady` is per context, so one slow
provider keeps the whole context not ready. A caller also writes one value after another, so one waiting write
holds back the rest of its writes. The gain is small for the added complexity.

## Earlier decisions

### 0029, 2026-06-19: a contributor is asked once per context, with the whole chain

Layers came from `LayerContributor`s. A contributor was invoked once per context construction, saw the whole chain
through `parentContext`, and returned all its layers at once, each tagged with the exact context it applied to. The
layer set was cached for the lifetime of the context. An editorconfig provider walks directories anyway, and a
scope provider for `src/java/com/` asked per node would return a duplicate layer at every subdirectory. The provider
owned placement, so no duplicates arose, but a contributor walked the chain itself. 0062 replaced it with the reload
cycle that the vault drives.

### 0062, 2026-07-25: the vault drives loading under a timeout

It set up the reload cycle of the decision above, and it called the providers under a timeout. A provider that
timed out was logged, and its last-known entries stayed, so a slow provider did not wipe its data, such as all app
defaults. The current decision dropped the timeout.
