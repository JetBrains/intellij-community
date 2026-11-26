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

- A key with a scalar default of type `Int`, `Long`, `Float`, `Double`, `Boolean` or `String` infers its serializer.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsResolutionTest.kt
- A key with no default infers its serializer from its nullable static type, for the same scalar types.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNullDefaultTest.kt
- The float serializer must store a float as the double of its shortest decimal form, so `1.1f` is stored as `1.1`.
  When that double does not narrow back to the same float, it must store the exact double of the float.
  See [ADR 00F5FN](../adr/00F5FN-the-api-gives-a-float-serializer-and-a-number-key-reads-a-narrower-stored-number.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A float key must read back every float that it stored.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A float key must read a stored double, int or long value as the nearest float.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A float key must read a stored infinity or NaN as the same float.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A double key must read a stored int or long value as a double.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A long key must read a stored int value as a long. A config file gives the smallest kind of a number, so a small
  long comes back as an int.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsLongSerializerTest.kt
- The float serializer composes with `nullable()`, `listOf()` and `mapOf()`.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A key with an enum entry as its default infers the enum serializer, also for an entry with a body.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsEnumSerializerTest.kt
- The enum serializer must store an entry by its constant name, as a string, with no conversion of the name. Two
  constants that differ only by the case or by an underscore stay distinct. See
  [ADR 0178](../adr/0178-an-enum-is-stored-by-its-constant-name-with-no-conversion.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsEnumSerializerTest.kt
- `enumSerializer<E>()` gives the enum serializer for explicit use, and it composes with `nullable()`, `listOf()` and
  `mapOf()`. `enumSerializer(enumClass)` gives the same serializer where the enum is known only as a class, for
  example in Java code.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsEnumSerializerTest.kt
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
- A deserializer must do its best to restore a stored value. When it cannot, it must throw `IllegalArgumentException`
  with a message that says what is wrong. This applies to every kind of incorrect value: an unexpected type or shape,
  an unknown name, or anything else.
- Every built-in serializer must check the type of the stored value. A wrong scalar, a wrong element of a list, a
  wrong value of a map, and a wrong value of a nullable serializer must each give `IllegalArgumentException`, so the
  key resolves to its default with a warning.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMalformedValueTest.kt
- A stored value that is not a number must make a float key or a double key resolve to its default with a warning.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A finite stored number out of the range of a float must make a float key resolve to its default with a warning.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFloatSerializerTest.kt
- A stored enum name that no constant has, and a stored value that is not a string, must make an enum key resolve to
  its default with a warning.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsEnumSerializerTest.kt
- A custom serializer that throws `IllegalArgumentException` must make its key resolve to the default with a warning.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsMalformedValueTest.kt

## Error Handling

- A serializer signals an incorrect stored value with `IllegalArgumentException`. The engine catches only that
  exception and treats it as a malformed value.
- The kotlinx bridge lets a decode failure of kotlinx propagate. Its `SerializationException` is an
  `IllegalArgumentException`.

## Open Questions / Risks

- A rename of an enum constant breaks the values stored under the old name. A mechanism that lets a constant declare
  its exact stored value would keep them. See
  [ADR 0178](../adr/0178-an-enum-is-stored-by-its-constant-name-with-no-conversion.md).
