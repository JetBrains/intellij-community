# 0175. The promoter interface and its extension point live in the API module

Date: 2026-11-17

## Status

Accepted ([IJPL-261930](https://youtrack.jetbrains.com/issue/IJPL-261930)). Amends
[ADR 0114](./0114-the-registry-lives-in-the-shared-module.md).

## Context

[ADR 0114](./0114-the-registry-lives-in-the-shared-module.md) put the key registry and the promotion extension point
into the shared module, next to their only consumer. A feature outside the engine that promotes its keys then needs
a dependency on the shared module and an import from its `impl` package, only for the `SettingsKeyPromoter`
interface. A plugin must not depend on the implementation of the engine to promote its keys.

## Decision

**The `SettingsKeyPromoter` interface and the `com.intellij.settings.v2.keyPromoter` extension point move to the API
module, in the package `com.intellij.platform.settings.v2`. The key registry stays in the shared module.**

## Consequences

- A plugin that declares keys depends only on the API module.
- The extension point lives next to the prefix extension point and the migration extension points of the API module.
- The shared module still consumes the extension point. Frontend and backend use one registry implementation.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).

## Rejected alternatives

### Keep the interface in the shared module

Each adopter would repeat the dependency on the shared module and the import from its `impl` package.
