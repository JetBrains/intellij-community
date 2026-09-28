---
name: Key Registry
description: Requirements for the registry that resolves a key identity to its key, and for key promotion.
style: plain-1
targets:
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsKeyRegistry.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
  - ../shared/resources/intellij.platform.settings.v2.shared.xml
---

# Key Registry

Status: Draft
Date: 2026-09-28

## Summary

The key registry maps a key identity to the key behind it. A plugin promotes its keys through the
`settings.v2.keyPromoter` extension. A key is otherwise known only after a write of it, so an identity that comes
from outside, such as from another remote-dev side, could resolve to nothing.

## Goals

- Resolve any promoted key by its identity.
- Make sure that one identity names one key.
- Report a key that its plugin forgot to promote.

## Non-goals

- Enumerating keys for a JSON schema.

## Requirements

### Promotion

- A promoted key must resolve by its identity. An unknown identity resolves to nothing.
  See [ADR 0105](../adr/0105-keys-are-found-through-a-key-factory-ep-and-a-registry-service.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- The same key promoted twice must be one entry.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A different key with a taken identity must be refused and logged.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A promoter must register the keys of its own plugin only.
  See [ADR 0111](../adr/0111-the-registry-takes-keys-only-from-their-own-plugin-and-a-prefix-belongs-to-one-plugin.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- An unloaded promoter must take its keys along. The other promoters of that plugin keep theirs.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A promoter that throws must be logged. All its keys stay unknown, also the keys it passed before it threw.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A promoter that throws a cancellation of its own must be logged without its stack trace. All its keys stay
  unknown. Only a cancellation of the registry itself propagates.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A promoter that fails in any way must not stop the other promoters.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- Discovery must start when the registry is created, in the background. The registry is ready when every
  promoter ran. See [ADR 0109](../adr/0109-promoting-keys-is-the-discovery-and-it-runs-in-the-background.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- The registry must hold its keys strongly. See [ADR 0112](../adr/0112-the-registry-holds-keys-strongly.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt

### Writes

- A write must wait for the registry to be ready, then check its keys.
  See [ADR 0109](../adr/0109-promoting-keys-is-the-discovery-and-it-runs-in-the-background.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A write of a key that nobody promoted must register the key, log an error, and apply.
  See [ADR 0110](../adr/0110-a-write-of-an-unknown-key-registers-it.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A write of a key of an unloading module must log an error, apply, and register nothing.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A write of a key whose identity another key holds must fail with `IllegalStateException`.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- A read must not consult the registry.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsKeyRegistryTest.kt
- In a unit test the write check must be off. `withRegistryChecks` turns it on for a block.
  See [ADR 0113](../adr/0113-a-test-opts-into-registry-checks.md).

## Open Questions / Risks

- A promoter that unloads makes the registry rebuild from the promoters that stay. A key that registered through a
  write, and that no promoter holds, goes too. So its next write logs the error again. This is accepted: an unload
  is rare, and the key is still not promoted.
- The registry is meant for remote-dev mutations, which are not built yet.
