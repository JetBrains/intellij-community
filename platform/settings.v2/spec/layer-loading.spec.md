---
name: Layer Loading
description: Requirements for how the vault loads and reloads layers, and how a caller waits for the load.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/SettingsVault.kt
  - ../src/com/intellij/platform/settings/v2/LayerProvider.kt
  - ../src/com/intellij/platform/settings/v2/SettingsContext.kt
  - ../src/com/intellij/platform/settings/v2/SettingsApi.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/providers/BaseLayerService.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/ProjectSettingsLoadActivity.kt
---

# Layer Loading

Status: Draft
Date: 2026-09-30

## Summary

The vault is the one source of truth for settings in memory. It loads the layers by asking each provider for its
data of a scope, such as the application or a project. A provider can also report a change it sees in its own store.
While a scope reloads, its contexts are dirty: a read still works, but a write waits until its context is ready.

## Goals

- Load every layer of a scope in one step, and drop the layers that no longer exist.
- Never let a write land on data that is half loaded.
- Let a read-then-decide flow wait for the load.

## Non-goals

- The startup cache. The [Snapshot Cache](snapshot-cache.spec.md) spec owns it.

## Requirements

### Reload

- The vault must reload the application scope at startup, and a project scope when the project opens.
- `reload(scope)` must ask every provider for its data of the scope. See
  [ADR 0116](../adr/0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md). It asks a
  provider as soon as it holds the mutex of the widest kind of the provider, so a reload that no other reload blocks
  asks every provider at once. See
  [ADR 0162](../adr/0162-reloads-take-one-mutex-per-context-kind-from-the-application-down.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadMutexTest.kt
- A provider must declare the range of context kinds that it supplies layers at: its widest kind and its narrowest
  kind. The narrowest kind can be open. A provider that declares nothing covers every kind. See
  [ADR 0159](../adr/0159-a-provider-declares-the-context-kinds-it-supplies-layers-at.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerKindRangeTest.kt
- The vault must log an error for a layer that a provider returns outside its range of kinds, and drop that layer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerKindRangeTest.kt
- A provider must return only the layers of the contexts that the scope covers: the scope and its descendants. A
  reload must not install a layer outside its scope again.
- A reload must install the returned layers kind by kind. The bucket of a kind installs when every provider whose
  range includes the kind has answered. The buckets install in the order in which their providers answer.
  See [ADR 0160](../adr/0160-a-reload-installs-the-layers-kind-by-kind.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKindByKindReloadTest.kt
- Each bucket must install its layers, then drop its stale layers, under one lock.
  See [ADR 0065](../adr/0065-a-reload-evicts-stale-layers-by-mark-sweep.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKindByKindReloadTest.kt
- A reload must not install a returned layer while a save is pending for its store. The vault keeps the held
  layer, as it does for a reported change. See
  [ADR 0061](../adr/0061-the-vault-persists-on-a-debounced-save-signal.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
- A stale layer is one under the scope that a returning provider did not supply again.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
- A reload that drops a stale layer must fire the listeners of the keys that the layer held. A contextual listener
  fires only when its resolved value changes. A key-only listener fires at the context of the dropped layer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadListenerTest.kt
- A reload must not drop a layer that has a pending save.
- A provider that fails must be logged, and its layers must stay. The vault does not know why the load failed, so
  it does not treat the files as missing.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
- `reload` must return only when the loaded data is in the model.
- A reload must have no timeout. It waits for every provider to answer.
  See [ADR 0116](../adr/0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md).
- A reload must take one mutex per context kind, from `APPLICATION` down to `FILE`. It takes the mutex of the next
  kind before it releases the mutex of the current kind, and it takes each next mutex as soon as it can. It asks a
  provider under the mutex of the widest kind of the provider, and installs a bucket under the mutex of its kind. So the asks and the installs of one kind keep
  their order. See [ADR 0162](../adr/0162-reloads-take-one-mutex-per-context-kind-from-the-application-down.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadMutexTest.kt
- A reload must end when the scope of its caller is cancelled. The context then stops being dirty.
  See [ADR 0116](../adr/0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadMutexTest.kt
- A provider that registers later must load the application scope on its own.
- A provider that unloads must take its layers along. The listeners of the keys that those layers held must fire.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsProviderUnloadTest.kt

### Reported changes

- A provider can push a layer that changed in its store. An empty layer removes the entry.
  See [ADR 0057](../adr/0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md).
- The vault must hold back a push while the context of its store is dirty. It must apply the held pushes in
  arrival order when the context of the store gets ready, also when the reload fails or is cancelled.
  See [ADR 0123](../adr/0123-a-push-during-a-reload-waits-until-the-reload-installs.md) and
  [ADR 0160](../adr/0160-a-reload-installs-the-layers-kind-by-kind.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKindByKindReloadTest.kt
- A push for a store whose context is ready must apply at once.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadSweepTest.kt
- The vault must log an error for a pushed layer outside the range of kinds of its provider, and drop that push.
  See [ADR 0159](../adr/0159-a-provider-declares-the-context-kinds-it-supplies-layers-at.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerKindRangeTest.kt
- The vault must drop a push while a save is pending for that store. For a held push, the vault checks this when
  it applies the push, not when the push arrives.
  See [ADR 0061](../adr/0061-the-vault-persists-on-a-debounced-save-signal.md) and
  [ADR 0066](../adr/0066-pushes-during-a-reload-are-not-suppressed.md).
- A push equal to the held layer must change nothing and fire no listener.
- A provider must push nothing when its store is malformed, for example a file in the middle of an edit, so the
  vault keeps the layer it holds. A reload drops that layer, as for a missing store. See
  [ADR 0145](../adr/0145-a-reload-treats-a-malformed-config-as-missing.md).

### Readiness

- A context must be dirty while a reload runs for it or for an ancestor, until that reload installs the bucket of
  the kind of the context and the buckets of every wider kind. A reloading descendant does not count. See [ADR 0160](../adr/0160-a-reload-installs-the-layers-kind-by-kind.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKindByKindReloadTest.kt
- `isReady()` must be the opposite of `isDirty()`. `awaitReady()` must suspend until the context is ready.
  See [ADR 0064](../adr/0064-readiness-is-exposed-per-context.md).
- `readWhenReady(key)` must wait for readiness, then read.
  See [ADR 0131](../adr/0131-a-read-that-waits-for-readiness-is-a-separate-suspend-call.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReadinessGateTest.kt
- A write must suspend until every context it writes to is ready.
  See [ADR 0063](../adr/0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReadinessGateTest.kt
- A write must not wait for a reload of a descendant context.
  [ADR 0115](../adr/0115-a-write-waits-only-for-a-reload-of-its-own-context-or-an-ancestor.md)
- The vault must widen a reload scope to the widest storage context that a provider maps it to.
  [ADR 0115](../adr/0115-a-write-waits-only-for-a-reload-of-its-own-context-or-an-ancestor.md)
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReadinessGateTest.kt
- A plain read must never wait for readiness.
  See [ADR 0063](../adr/0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md).
- A reload of one project must not make the contexts of another project dirty.

## Error Handling

- A provider that throws during a load is logged and skipped. The other providers still load.
- A provider that throws from `storageContext` fails the operation that asks it. The vault does not catch it.
  See [ADR 0158](../adr/0158-a-failure-of-the-storage-context-mapping-of-a-provider-propagates.md).

## Open Questions / Risks

- A write before the first reload of the application or a project is not gated. It can save a layer that was
  never loaded over the real file. See [ADR 0144](../adr/0144-a-write-before-the-first-reload-of-its-scope-is-not-gated.md).
- A provider that never answers blocks the reload, every write under its scope at a kind of its range, and the
  same kinds of every later reload. See [ADR 0162](../adr/0162-reloads-take-one-mutex-per-context-kind-from-the-application-down.md). The case
  is rare, but it is a real problem that needs a fix later. [ADR 0116](../adr/0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md)
  rejects a timeout and per-provider readiness as the fix.
- The reload on a VCS change is not built yet.
  [ADR 0116](../adr/0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md) plans it, and
  [ADR 0063](../adr/0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md) relies on it to protect
  a branch switch. Until then, a branch switch arrives as pushes, and the readiness gate does not protect it. The
  vault drops a push while a save is pending for that store. A write just before the switch can then save the old
  model over the file of the new branch.
- A newer reload could cancel the asks of an older overlapping reload, instead of the reload mutexes. See
  [IJPL-259868](https://youtrack.jetbrains.com/issue/IJPL-259868).
- A slow provider with a wide range of kinds delays every context that it covers.
- Migration of settings data is not built. It waits until remote development is done. See
  [ADR 0142](../adr/0142-migration-waits-until-remote-development-is-done.md).
