---
name: Layer Resolution
description: Requirements for how a key resolves its value over the layers of a context chain.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/LayerProvider.kt
  - ../src/com/intellij/platform/settings/v2/LayerData.kt
  - ../src/com/intellij/platform/settings/v2/SettingsVault.kt
  - ../src/com/intellij/platform/settings/v2/SettingsKey.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
  - ../shared/resources/intellij.platform.settings.v2.shared.xml
---

# Layer Resolution

Status: Draft
Date: 2026-09-28

## Summary

A layer is one set of stored values at one context. A layer provider is an extension that supplies layers, such as
the application file or a directory config. The vault holds every layer in memory. A read walks the context chain
from the narrowest context, and the first layer that holds the key wins. With no such layer, the key default wins.

## Goals

- Resolve a key the same way for every setting, whatever its source.
- Let a plugin add a layer with no change to the engine.
- Let a key confine itself to some contexts or some layers.

## Non-goals

- The three localities in detail. The [Layer Locality](layer-locality.spec.md) spec owns them.
- How layers load. The [Layer Loading](layer-loading.spec.md) spec owns that.

## Requirements

### Precedence

- A read must be synchronous and must not take a lock.
  See [ADR 0032](../adr/0032-reads-are-synchronous-and-writes-suspend.md) and
  [ADR 0136](../adr/0136-a-write-swaps-an-immutable-model-and-a-read-takes-no-lock.md).
- A read must see the model before a batch or after it, never a part of the batch.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- A narrower context must win over a wider one, whatever the locality or the provider.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMultiLayerContextTest.kt
- Within one context, a narrower locality must win: environmental, then local, then team.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLocalityTest.kt
- Within one context and locality, the provider that comes later in the extension order must win.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMultiProviderTest.kt
- A value that only a wider or earlier layer holds must still resolve.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsCrossContextTest.kt
- The same key written at the application, a project and a file must read back per context.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerScopeWriteTest.kt
- See [ADR 0073](../adr/0073-precedence-is-context-then-locality-then-provider.md) for this order.

### Providers

- Providers must be ordered by the `id` and `order` attributes of the `settings.v2.layerProvider` extension.
- The application defaults provider must be first, and the application provider second. Any other order is an error.
- A provider must hold no mutable state. Its data belongs to a service.
- A provider must declare which keys it holds, and which localities take a write at a context. See
  [ADR 0129](../adr/0129-a-layer-filters-keys-and-offers-localities-per-context.md).

### Key filters

- A context filter must hide the contexts it rejects from a read of the key.
  See [ADR 0039](../adr/0039-a-key-declares-the-context-kinds-it-is-defined-at.md) and
  [ADR 0128](../adr/0128-a-key-filters-contexts-with-a-predicate.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyFilterTest.kt
- A layer filter must hide the providers it rejects, even when they outrank an accepted one.
  See [ADR 0019](../adr/0019-a-key-and-a-layer-each-filter-the-other.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyFilterTest.kt
- The key filter of a provider must hide its layers from a read of a key that it rejects, even when they outrank
  another layer. A wider layer or the default then wins. See [ADR 0129](../adr/0129-a-layer-filters-keys-and-offers-localities-per-context.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLayerKeyFilterTest.kt
- A key and a provider must filter each other in the same way on read and on write. See [ADR 0019](../adr/0019-a-key-and-a-layer-each-filter-the-other.md).
- A map key has no filters. The filters of the child key apply through its storage.

### All contributions

- `values(context)` must return every stored value along the chain, widest first.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMergeValuesTest.kt
- `values` must never contain the default. A read-only key gives an empty list.
- `values` on a child key must span the chain and skip a layer that lacks the slot.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMergeValuesTest.kt
- The engine must not merge values. A consumer merges by a fold over `values`.
  See [ADR 0045](../adr/0045-merging-map-values-is-a-consumer-fold-over-values.md).

## Open Questions / Risks

- A narrower layer cannot remove an entry that a wider layer contributes to a merged map. Empty, absent and an
  explicit clear are not told apart.
- A read may see values that a reload is about to replace. See
  [Layer Loading](layer-loading.spec.md).
