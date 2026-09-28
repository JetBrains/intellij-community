# 0120. The lambda tests use one wrapper plugin for both sides

Date: 2026-09-30

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A split-mode lambda test runs its lambdas in the class loader of a wrapper plugin. Without the wrapper, a lambda
cannot load a content module such as `intellij.platform.settings.v2`. The lambda test framework offers two setups:

- One wrapper plugin that loads on both sides: `LambdaTestPluginHolder.allModesPlugin`.
- One wrapper for each mode (monolith, backend split, frontend split): `LambdaTestPluginHolder.modeSplitPlugins`.
  Each wrapper then has its own dependencies.

A wrapper that depends on a module of one side does not load on the other side, so every lambda there fails.

## Decision

**The settings v2 lambda tests use one wrapper plugin, `intellij.platform.settings.v2.lambdaTests.plugin`, for both
sides.** A lambda uses only modules that both sides load: the API module, the shared module, and the platform. A test
checks the behavior through the public API.

## Consequences

- One Product DSL entry and one wrapper module: `SettingsV2LambdaTestsPlugin` in
  `platform/buildScripts/src/productLayout/LambdaTestPlugins.kt`.
- A lambda cannot use the internals of one side, such as the backend file layers or the frontend content-roots
  service.
- When a test needs such internals, the tests switch to `modeSplitPlugins`. That adds two wrapper modules and
  changes one call in `SettingsV2LambdaSessionStrategy`. The tests do not change.
- The tests that use this setup are in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).

## Rejected alternatives

### One wrapper for each mode

It allows side-only dependencies, but it needs three wrapper modules and three Product DSL entries. No test needs a
module of one side yet.
