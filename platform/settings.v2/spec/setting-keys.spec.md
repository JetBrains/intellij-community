---
name: Setting Keys
description: Requirements for declaring a setting key, its identity, its default, and its read-only and validation rules.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/SettingsKey.kt
  - ../src/com/intellij/platform/settings/v2/SettingsKeyPrefixBean.kt
  - ../src/com/intellij/platform/settings/v2/SettingsApi.kt
  - ../resources/intellij.platform.settings.v2.xml
---

# Setting Keys

Status: Draft
Date: 2026-09-28

## Summary

A setting key is a typed name for one setting. Code reads and writes a setting only through its key, never through
a string. A key carries its name, its default value, the serializer of its value, and the plugin that owns it. The
key identity is the name that the key has in storage. See [Setting Values](setting-values.spec.md) for serializers.

## Goals

- Give every setting one addressable identity. The key prefix keeps the identities of two vendors apart. The
  marketplace, not the runtime, keeps a prefix to one vendor. See
  [ADR 0117](../adr/0117-the-marketplace-not-the-runtime-keeps-a-key-prefix-to-one-vendor.md).
- Make the owner of a key follow from the code that declares it.
- Keep a key usable when no layer holds a value for it.

## Non-goals

- Enumerating all keys. The [Key Registry](key-registry.spec.md) spec owns that.
- Where a value comes from. The [Layer Resolution](layer-resolution.spec.md) spec owns that.

## Requirements

### Identity

- The owner of a key must be the plugin of the class that declares it. A platform class gives no owner.
  See [ADR 0124](../adr/0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt
- A key that a platform class declares must have no owner, also when a plugin class is the first to use the key.
  See [ADR 0124](../adr/0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt
- A key identity must be `prefix.name`, or the bare name when the key has no prefix.
  See [ADR 0124](../adr/0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyPrefixTest.kt
- The prefix must be the one the owner declares in the `settings.v2.keyPrefix` extension, else the owner plugin id.
  See [ADR 0124](../adr/0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyPrefixTest.kt
- A prefix that another plugin declares must not change the identity of a key.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyPrefixTest.kt
- Two plugins may declare the same prefix. Each must use it, and the runtime must report nothing.
  See [ADR 0096](../adr/0096-a-mirrored-key-needs-an-explicit-prefix-or-a-shared-module.md) and
  [ADR 0117](../adr/0117-the-marketplace-not-the-runtime-keeps-a-key-prefix-to-one-vendor.md).
- A key must resolve its prefix at its first use, not when it is constructed.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyPrefixTest.kt
- A key name must not start with `@`. A prefix that starts with `@` must be rejected at first use.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyPrefixTest.kt
- A second key with the same owner and name must fail when it is constructed.
  See [ADR 0124](../adr/0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt
- A key of a plugin that was unloaded and loaded again must not count as a duplicate. A second key with the same
  owner and name from a live class loader must still fail. See
  [ADR 0112](../adr/0112-the-registry-holds-keys-strongly.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt
- Two keys are equal when they have the same name and the same owner.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt
- A test can set the owner of a key before its first use. Outside a unit test this must fail. No test covers the
  second part, because the tests run in unit test mode.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt
- A key knows when the module that declares it is unloading. A platform key never is.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt

### Default

- A read must return the default when no layer holds a value for the key.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsResolutionTest.kt
- A key can take a default factory instead of a value. The factory receives the context of the read.
  See [ADR 0017](../adr/0017-a-context-dependent-default-is-a-factory-and-a-data-driven-default-is-a-layer.md) and
  [ADR 0046](../adr/0046-a-common-slot-fallback-is-a-default-factory-not-an-engine-feature.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsFallbackChainTest.kt
- A default factory of a slotted key learns the slot path of the read with `accessFor`. A plugin contributes a
  default of a platform key this way. See [ADR 0156](../adr/0156-a-plugin-contributes-a-default-of-a-platform-key-through-the-default-factory-of-the-key.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsContributedDefaultTest.kt
- A key declared with no default must have a nullable type, and its default is `null`.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsNullDefaultTest.kt

### Read-only keys

- A read of a read-only key must return its default and consult no layer.
  See [ADR 0011](../adr/0011-a-readonly-key-never-consults-a-layer.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsResolutionTest.kt
- A write of a read-only key must fail with `ReadOnlySettingsException`.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsBulkMutationTest.kt

### Validation

- A key can carry a validator. It returns `null` for a valid value, or a reason for an invalid one.
  See [ADR 0042](../adr/0042-a-key-carries-a-validator-that-rejects-an-invalid-write.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsValidationTest.kt
- `validate` must return the validator result and never throw, so a UI can check input live.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsValidationTest.kt
- A validator that throws must count as a rejection. The engine logs the error.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsValidationTest.kt
- A write of an invalid value must fail with `SettingsValidationException` and store nothing.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsValidationTest.kt
- The engine must not validate the default.

### Access forms

- `key[context]` reads and `key[context] = value` writes. `context[key]` does the same from the context side.
- `getValue` reads at the application, a project, or a file. `setValue` writes at the application or a project.

### Extension

- Only the platform may extend `SettingsKey`. `SettingsMapKey` is its only subclass. Both keep the plural name.
  See [ADR 0153](../adr/0153-all-v2-types-and-the-key-classes-use-the-plural-settings-name.md).

## Error Handling

- A name or a prefix that starts with `@` fails with `IllegalArgumentException`.
- A duplicate owner and name fails with `IllegalArgumentException` at the second construction.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyOwnerTest.kt

## Open Questions / Risks

- The marketplace does not yet keep a prefix to one vendor. A plugin from a custom repository or a local disk is
  never checked. See [ADR 0117](../adr/0117-the-marketplace-not-the-runtime-keeps-a-key-prefix-to-one-vendor.md).
