---
name: Multidimensional Settings
description: Requirements for map keys, slots, discriminators, slotted writes and slot operations.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/SettingsMapKey.kt
  - ../src/com/intellij/platform/settings/v2/SettingsKey.kt
  - ../src/com/intellij/platform/settings/v2/SettingsAccessContext.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/mapValue.kt
---

# Multidimensional Settings

Status: Draft
Date: 2026-09-28

## Summary

Some settings vary along an axis that is not the context chain, such as inspections per profile or code style per
language. A map key holds such a setting as slots inside ordinary layers. A discriminator key selects the slot, and
a child key lives inside the selected slot. Map keys can nest, for example profile inside language.

## Goals

- Model a dimension with no extra layers.
- Read or write a slot other than the selected one.
- Copy, drop and rename a whole slot.

## Non-goals

- Keeping slots consistent across contexts. That is the job of the feature that owns them.
  See [ADR 0055](../adr/0055-keeping-slots-consistent-is-the-api-users-job.md).

## Requirements

### Reads

- A child key must resolve in the slot that the discriminator selects, else take its default.
  See [ADR 0010](../adr/0010-multidimensional-settings-are-slots-in-a-layer-not-extra-layers.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMultiDimTest.kt
- The discriminator value must come from the context pins, else from the layers like any key.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMultiDimTest.kt
- A child of nested map keys must resolve through each map key in turn.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedDimTest.kt
- A child key must follow the context chain and the provider order like a plain key.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNestedPrecedenceTest.kt
- The discriminator and the slot value may sit in different layers, for example a directory and a file.
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
- A read of a missing slot must create nothing.
  See [ADR 0139](../adr/0139-a-slot-appears-on-the-first-write-of-a-child-key.md).
- A storage or a slot that is not a map must hide that layer from the child. A wider layer can then supply the
  value. With no such layer, the child takes its default.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMalformedMapKeyTest.kt
- A child value of the wrong type or shape must make the child take its default, as for a plain key. The next
  layer is not asked. See [ADR 0047](../adr/0047-a-wrongly-typed-value-falls-back-to-the-default.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMalformedMapKeyTest.kt

### Access contexts

- `accessFor(key)` must pin a discriminator for every map key of the storage chain of the key, outer first. It must
  keep a discriminator that the context already pins. It must resolve an inner discriminator in the slot that the
  outer pins select.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContributedDefaultTest.kt

### Writes

- A write of a child key must take every slot from the context pins. It resolves no discriminator.
  See [ADR 0088](../adr/0088-a-write-names-its-slots.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsPinnedSlotWriteTest.kt
- A write with any slot of its chain unpinned must fail with `UnpinnedDiscriminatorException`.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsPinnedSlotWriteTest.kt
- A write must land in the pinned slot, even after the discriminator moved.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsPinnedSlotWriteTest.kt
- A write must create the slot and every missing enclosing map on demand.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSlottedWriteTest.kt
- A batch entry must use the slots its own context pins, not the ones an earlier entry selects.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt
- A map key must not be written directly. Every write path fails such a write with `IllegalArgumentException` and
  stores nothing. A batch with such an entry fails as a whole.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMapKeyDirectWriteTest.kt

### Slot operations

- `copySlot`, `dropSlot` and `renameSlot` must act on the layers at their context only, with no walk up the chain.
  See [ADR 0079](../adr/0079-a-slot-operation-targets-its-context-not-the-subtree.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSlotBroadcastAcrossLayersTest.kt
- A slot operation must change every layer at that context that holds the slot: every provider and locality.
  See [ADR 0077](../adr/0077-a-map-key-has-no-locality.md) and
  [ADR 0078](../adr/0078-a-slot-operation-broadcasts-to-every-layer-at-its-context.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSlotBroadcastTest.kt
- A slot operation must skip a layer whose provider rejects the map key or offers no write in its locality at the
  context. See [ADR 0129](../adr/0129-a-layer-filters-keys-and-offers-localities-per-context.md).
- A slot operation must leave a layer without the slot unchanged.
- A copy must keep the source slot and overwrite an existing target slot.
  See [ADR 0053](../adr/0053-copying-onto-an-existing-slot-overwrites-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSlotOperationsTest.kt
- A drop must remove the slot. The storage entry goes with its last slot.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSlotOperationsTest.kt
- A rename must be a copy and a drop, applied as one atomic change.
  See [ADR 0052](../adr/0052-renaming-a-slot-is-a-copy-plus-a-drop.md) and
  [ADR 0132](../adr/0132-the-vault-applies-a-slot-rename-as-one-batch.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSlotOperationsTest.kt
- A slot operation must move the raw slot data, so it works while the child keys are not loaded.
  See [ADR 0050](../adr/0050-a-slot-operation-moves-raw-sub-maps-through-a-typed-owner-scoped-api.md).
- A slot operation at a file context must act on that file's entry in the directory config.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsPerFileSlotOperationsTest.kt
- A slot operation must not fix a stored discriminator that still names the old slot.
  See [ADR 0051](../adr/0051-slots-have-no-reference-integrity-and-no-vcs-journal.md).

## Open Questions / Risks

- A slot operation matches its map key at the top level of a layer. A map key nested in another slot has no
  addressed path for a slot operation yet.
- A pin is read at one moment, so a concurrent write can split a multi-level address.
- A feature should use a stable id as the discriminator value, not a label. See
  [ADR 0056](../adr/0056-a-discriminator-value-is-a-stable-id-not-a-label.md).
