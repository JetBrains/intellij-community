# 0182. The API module is embedded in core.platform, and the vault module is a content module of the same set

Date: 2026-11-23

## Status

Accepted ([IJPL-262587](https://youtrack.jetbrains.com/issue/IJPL-262587)). Amends
[ADR 0154](./0154-a-core-module-reaches-the-settings-through-its-own-implementation-module-not-a-shared-helper-module.md).

## Context

A module of the `core.platform` module set must read a settings key. `intellij.platform.editor.ui`, an embedded
module, is such a module: it holds the UI settings. Some products have `core.platform` but not the `essential` module
set, for example CodeServer and Draft.

The settings modules were content modules of `essential`. So a core module could not depend on them, and a product
without `essential` had no settings engine.
[ADR 0154](./0154-a-core-module-reaches-the-settings-through-its-own-implementation-module-not-a-shared-helper-module.md)
works around the first part. A core module declares a service interface, and an implementation module of each
adopter implements it with the settings API.

An embedded module loads in the core class loader. The core class loader cannot see a content module that has its own
class loader. So an embedded module can depend only on embedded modules and on the boot jars.

The API module depends only on embedded modules and on the boot jars. The vault module depends only on modules of
`core.platform` and of the boot jars
([ADR 0181](./0181-the-vault-module-depends-only-on-core-platform-modules-and-the-code-that-needs-more-lives-in-the-shared-essential-module.md)).
`SettingsV2CoreDependenciesTest` checks that both modules depend only on modules of `core.platform` and of the boot
jars.

## Decision

**The API module, `intellij.platform.settings.v2`, is an embedded module of `core.platform`. The vault module,
`intellij.platform.settings.v2.shared`, is a content module of `core.platform`. Both leave the `essential` module set.
The other settings modules stay in `essential`.**

- A module of `core.platform` reads a key through the API directly. It needs no implementation module of its own.
- A module that the API module depends on, such as `intellij.platform.core`, cannot depend on the API module. Such a
  module still reaches the settings through its own implementation module, as
  [ADR 0154](./0154-a-core-module-reaches-the-settings-through-its-own-implementation-module-not-a-shared-helper-module.md)
  says.
- The vault module stays a content module, because no embedded code uses its classes. The API module reaches the
  vault through services, such as `SettingsVault` and `ContextFactory`, and through extension points.

## Consequences

- Every product with `core.platform` loads the engine, also CodeServer and Draft. The `essential` set nests
  `core.platform`, so a product with `essential` loads the same settings modules.
- A product without the shared essential module has no shutdown saver. Its vault runs the save at the close itself
  ([ADR 0181](./0181-the-vault-module-depends-only-on-core-platform-modules-and-the-code-that-needs-more-lives-in-the-shared-essential-module.md)).
- Both modules load in every product mode. So a split session and a light mode load the same settings modules on
  each side.
- The move does not change the identity of a key. A key of a class of the platform (core) plugin has no owner and
  the bare name, in an embedded module and in a content module alike
  ([ADR 0124](./0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md)).
- The API module must keep its dependencies embedded. Else the embedded dependency check of the plugin model tool
  fails.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Keep the layout, and give each core module its own implementation module in `essential`

This is the way of
[ADR 0154](./0154-a-core-module-reaches-the-settings-through-its-own-implementation-module-not-a-shared-helper-module.md).
Each core consumer needs one more module. A product without `essential` still has no engine, so the feature there
has no settings at all.

### Embed only the API module, and return the defaults when no vault is loaded

A product without `essential` then reads only the defaults, and a write has no store. This changes the behavior of the
engine for every caller, and a caller cannot see that its writes are lost.
