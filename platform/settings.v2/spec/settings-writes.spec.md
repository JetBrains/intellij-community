---
name: Settings Writes
description: Requirements for where a write lands, atomic batches, and when a write reaches storage.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/SettingsVault.kt
  - ../src/com/intellij/platform/settings/v2/SettingsMutationBuilder.kt
  - ../src/com/intellij/platform/settings/v2/SettingsApi.kt
  - ../src/com/intellij/platform/settings/v2/LayerProvider.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsAppLifecycleListener.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/SettingsProjectListener.kt
---

# Settings Writes

Status: Draft
Date: 2026-09-28

## Summary

A write stores a value for a key at a context. The vault applies the write to its in-memory model at once, so a
read right after it sees the new value. The vault then saves the layer through its provider after a short delay.
A batch applies several writes and slot operations as one atomic change.

## Goals

- Make a write visible to the next read at once.
- Let several related writes apply together or not at all.
- Save a burst of writes to one store only once.

## Non-goals

- Slot writes and slot operations in detail. The [Multidimensional Settings](multidimensional-settings.spec.md)
  spec owns them.
- Which locality a write picks. The [Layer Locality](layer-locality.spec.md) spec owns that.

## Requirements

### Where a write lands

- A valid mutation at a context of a project that is not open must be dropped with a warning. The other mutations
  of its batch still apply. An invalid mutation fails the whole batch, at any context. The context can be the
  project or a file of it. A closed project and the default project are such projects. A read at such a context
  resolves through the application layers. See [ADR 0155](../adr/0155-a-mutation-at-a-context-of-a-project-that-is-not-open-is-dropped.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNotOpenProjectTest.kt
- A write must start at its context and walk up the chain until a layer accepts it.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsWriteFallthroughTest.kt
- At each context the write must try the localities in preference order, then the providers from the latest one.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMultiProviderTest.kt
- A layer takes a write when its provider offers the locality at that context. This is the only write check on the
  context. An empty set of localities means that the provider takes no write there. See [ADR 0129](../adr/0129-a-layer-filters-keys-and-offers-localities-per-context.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt
- A write must skip the contexts that the context filter of the key rejects. See [ADR 0128](../adr/0128-a-key-filters-contexts-with-a-predicate.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyFilterTest.kt
- A write must skip the providers that the layer filter of the key rejects.
  See [ADR 0019](../adr/0019-a-key-and-a-layer-each-filter-the-other.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyFilterTest.kt
- A write must skip the providers whose key filter rejects the key.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerKeyFilterTest.kt
- A write that a file layer refuses must climb to a wider context. Then it affects the siblings too.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsWriteFallthroughTest.kt
- A write that no layer in the whole chain takes must fail with `IllegalStateException`. The message names the key
  and the context. It does not name a layer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerWriteLocalitiesTest.kt
- A write must suspend until it is applied. A read after it must return the written value.
  See [ADR 0032](../adr/0032-reads-are-synchronous-and-writes-suspend.md) and
  [ADR 0058](../adr/0058-mutations-apply-as-atomic-declarative-batches-under-one-lock.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsResolutionTest.kt
- A write must wait until its context is ready. See [Layer Loading](layer-loading.spec.md) and
  [ADR 0063](../adr/0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReadinessGateTest.kt

### Batches

- `context.mutate()` must start a batch. `apply()` commits it. A batch that is never applied writes nothing.
  See [ADR 0058](../adr/0058-mutations-apply-as-atomic-declarative-batches-under-one-lock.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- An entry must use the current context of the batch, or its own context when it passes one.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- `context()` must change the context for the entries that follow, and not for earlier ones.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- A batch must check every write before it applies any. One rejected entry must fail the batch.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- A batch must apply its entries in order, under one lock, with no other write between them.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- A batch can hold writes and slot operations at different contexts.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- A read during a batch must see the model before the batch or after it, never a part of it. See
  [ADR 0136](../adr/0136-a-write-swaps-an-immutable-model-and-a-read-takes-no-lock.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt

### Persistence

- The vault must save a changed store after a short quiet period. A burst of writes saves once.
  See [ADR 0061](../adr/0061-the-vault-persists-on-a-debounced-save-signal.md).
- The vault must hand the provider the current layers of the store. The provider only serializes them.
  See [ADR 0057](../adr/0057-the-vault-is-the-source-of-truth-and-a-provider-is-a-persistence-port.md) and
  [ADR 0133](../adr/0133-a-provider-persists-what-the-vault-holds.md).
- While a save is pending, the vault must ignore changes that the provider reports for that store.
  See [ADR 0061](../adr/0061-the-vault-persists-on-a-debounced-save-signal.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsPendingSaveTest.kt
- A failed save must be logged. The next write to that store saves again.
- A provider must report whether it saved or dropped a store. On a drop, the vault must remove the dropped layers
  from its model and fire the listeners. See
  [ADR 0122](../adr/0122-a-save-to-a-storage-context-that-does-not-exist-is-dropped.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/FileLayerProviderTest.kt
- A drop must remove every layer of the store, also one that a write changed during the save, and clear the
  pending save of the store. See [ADR 0150](../adr/0150-a-dropped-save-removes-every-layer-of-its-store.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsDroppedSaveTest.kt
- `flush(scope)` must save every pending store that the scope covers, and return when they are on disk.
  See [ADR 0080](../adr/0080-flush-drains-a-scope-from-the-vault.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFlushTest.kt
- A flush of a project must also save the file configs under that project.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFlushTest.kt
- The platform must flush the application scope before the application closes.
- The platform must flush a project before the project closes.

## Error Handling

- A write of a read-only key fails with `ReadOnlySettingsException`.
- A write of an invalid value fails with `SettingsValidationException`.
- A write of a slotted key with no pinned slot fails with `UnpinnedDiscriminatorException`.
- A write of a key whose identity another key holds fails. See [Key Registry](key-registry.spec.md).

## Open Questions / Risks

- A mutation while a project opens, before the platform lists the project as open, is dropped. See
  [ADR 0155](../adr/0155-a-mutation-at-a-context-of-a-project-that-is-not-open-is-dropped.md).
- No test proves the flush before the application closes. The debounced save usually writes the file first, so a
  test passes without the flush.
- A write that climbs past a refusing layer silently widens its scope. It is not settled whether it should fail
  instead.
- An external edit of a config file while a save is pending is lost.
- A caller cannot learn when its own write reaches the disk, other than through `flush`.
- A write that reads a value and computes from it inside a batch is not supported.
