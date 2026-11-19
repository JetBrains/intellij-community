---
name: Setting Values
description: Requirements for the storage-neutral value model and the serializers that convert typed values to it.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/SettingsValue.kt
  - ../src/com/intellij/platform/settings/v2/Serializer.kt
  - ../src/com/intellij/platform/settings/v2/SettingsKey.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/KotlinxSerializer.kt
---

# Setting Values

Status: Draft
Date: 2026-09-28

## Summary

A setting value is stored as a tree of simple immutable values: string, int, long, double, boolean, null, list,
and map. A serializer converts a typed value to this tree and back. A storage backend reads and writes the tree in
its own format, so neither the key nor the serializer knows the format.

## Goals

- Store any setting value in one small closed set of value types.
- Let a key declare a scalar, a nullable value, a list, a map, or an object with little code.
- Keep one bad stored value from breaking the other settings.

## Non-goals

- The on-disk format. The [File Layers](file-layers.spec.md) spec owns the JSON form.

## Requirements

### Value model

- A stored value must be exactly one of: null, string, int, long, double, boolean, list, or map.
  See [ADR 0003](../adr/0003-the-settings-api-is-format-neutral.md).
- A map value is keyed by string. A list value keeps its order.
- Every value must be immutable.
- A public builder of a list or map value must copy the collection it gets.
  [@test] ../test/src/com/intellij/platform/settings/v2/LayerDataTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsListSerializerTest.kt
- An explicit null must stay distinct from an absent value.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNullDefaultTest.kt
- A stored explicit null must read as null, also for a key with a non-null default. This applies to a direct read,
  `readWhenReady`, a read of a slotted key, and `values`.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNullDefaultTest.kt

### Serializers

- A key with a scalar default of type `Int`, `Long`, `Double`, `Boolean` or `String` infers its serializer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsResolutionTest.kt
- A key with any other default type must pass a serializer. Otherwise its construction fails.
- `nullable()` wraps a serializer so that `null` round-trips as the null value.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNullDefaultTest.kt
- `listOf()` serializes a list element by element, and nullable elements round-trip.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsListSerializerTest.kt
- `mapOf()` serializes a string-keyed map value by value, and nullable values round-trip.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMapSerializerTest.kt
- `kotlinxSerializer()` must be public in the shared module, so a plugin can build a serializer for a
  `@Serializable` type. See [ADR 0118](../adr/0118-the-kotlinx-serializer-is-a-public-utility-of-the-shared-module.md).
- A `@Serializable` object round-trips through the kotlinx bridge, with nulls in fields, lists and maps.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsObjectSerializerTest.kt
- An object value must store as nested maps and lists, with no type tags or wrapper objects.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsObjectSerializerTest.kt
- A read must deserialize the stored value every time, and the engine caches no read value. So a caller may change
  a mutable value it read without changing the stored value or the result of another read. Two reads may share an
  immutable instance, for example a `String`.
  See [ADR 0030](../adr/0030-a-value-is-deserialized-fresh-per-read-so-serializer-copy-goes.md).

### Malformed values

- A stored value of the wrong type or shape must make its key resolve to the default.
  See [ADR 0047](../adr/0047-a-wrongly-typed-value-falls-back-to-the-default.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsJsonRobustnessTest.kt
- A stored object value that does not match its `@Serializable` class must make its key resolve to the default.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsJsonRobustnessTest.kt
- The other keys of the same layer must still resolve their stored values.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsJsonRobustnessTest.kt
- The engine must log a warning for a malformed value.

## Error Handling

- A serializer signals a type mismatch with `ClassCastException`. The engine catches only that exception.
- The kotlinx bridge rethrows a decode failure of kotlinx as `ClassCastException`.

## Open Questions / Risks

- None.
