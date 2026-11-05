# 0165. A test writes a key of the test keys module inside the IDE under test

Date: 2026-11-05

## Status

Accepted ([IJPL-260062](https://youtrack.jetbrains.com/issue/IJPL-260062)).

## Context

An integration test starts a real IDE and writes a setting inside it, over a lambda or over the driver. The key of
that setting must live in the IDE, because its owner is the plugin of the class that declares it
([ADR 0124](./0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md)). A write of a
key that no promoter registered logs an error.

The lambda tests kept their key in their own module, with a promoter of their own. JetBrains Light cannot load that
module: it has no lambda framework. A driver test of the split mode will have the same need.

The platform keys are no substitute. `EDIT_TEAM_LAYER` accepts only the application and the project context, and
`LANGUAGE` is a run-time discriminator with no stored value.

## Decision

**The probe keys of the integration tests live in `intellij.platform.settings.v2.testKeys`, a content module with no
test framework dependency. The generated plugin `com.intellij.platform.settings.v2.testKeys.plugin` wraps it, and the
lambda tests plugin includes it too.** A JetBrains Light driver test adds the plugin to the product. A lambda test gets
the module through its wrapper plugin. The module also holds the helpers that a driver call needs and the production
API does not give. One of them builds a file context from a thread with no read lock.

## Consequences

- One key set serves the lambda tests, the JetBrains Light tests and a future split driver test.
- A test needs no production key as a probe, so a production key keeps its own semantics.
- The module ships with no product. A test adds it, as the lambda framework plugin is added. The plugin module is a
  test module, because the product build publishes every compatible plugin with a production descriptor. The dev build
  of a driver test packs a test module only when the test turns the test output of the build on, as the lambda test
  framework does.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).

## Rejected alternatives

### A key in each test module

The lambda tests had one. A module that only a lambda wrapper loads cannot serve a driver test, and each test kind
would repeat the key and the promoter.

### A production key as the probe

The two platform keys do not fit. The context says why. A probe key in a production module would ship a test artifact.
