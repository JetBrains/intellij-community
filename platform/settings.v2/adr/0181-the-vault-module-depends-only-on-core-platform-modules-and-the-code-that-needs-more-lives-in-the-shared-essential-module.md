# 0181. The vault module depends only on core platform modules, and the code that needs more lives in the shared essential module

Date: 2026-11-23

## Status

Accepted ([IJPL-262587](https://youtrack.jetbrains.com/issue/IJPL-262587)).

## Context

A module of the `core.platform` module set must be able to depend on the settings engine. A product that has only
`core.platform` must load the engine. So the vault module, `intellij.platform.settings.v2.shared`, can depend only
on modules of `core.platform` and of the boot jars. `SettingsV2CoreDependenciesTest` checks this rule.

Three parts of the vault module needed modules outside `core.platform`:

- The context dump action shows a dialog of `intellij.platform.ide`, which is in `core.ide`.
- `SettingsContentRootsApi` takes a `ProjectId` of `intellij.platform.project`, which is in `core.lang`.
- The save at the application close ran under `runWithModalProgressBlocking` of `intellij.platform.ide.progress`,
  which is in `core.lang`.

The engine does not use the dump action or the RPC API. Only the split modules call the API. The shutdown save is
different: each product that runs the engine must save the last writes at the close.

[ADR 0179](./0179-one-context-factory-serves-every-product-mode-and-an-extension-point-gives-it-the-content-roots.md)
put the dump action into the vault module. Its decision is about the context factory, and that part stays.

## Decision

**The vault module depends only on modules of `core.platform` and of the boot jars. Its code that needs another
module lives in `intellij.platform.settings.v2.shared.essential`, a module of the `essential` set.**

- The dump action and `SettingsContentRootsApi` move to the essential module. They keep their packages. The split
  modules depend on the essential module.
- The modal progress of the shutdown save moves to the essential module. The shutdown save itself stays in the
  vault module.

## Consequences

- A product with only `core.platform` loads the vault module.
- The shutdown save has two forms, because its modal progress is optional now. `SettingsAppLifecycleListener` of
  the vault module asks for the optional application service `ShutdownSaver`. The interface is in the vault module.
  Its only implementation, `ModalShutdownSaver`, is in the essential module. This mirrors `ContextFactory` and its
  implementation in the vault module.
- The save body is one function of the vault module, `saveAppSettingsAndCache`. A saver decides only how the body
  runs. `ModalShutdownSaver` runs it under a modal progress that the user cannot cancel, as before.
- With no saver, the listener runs the body itself with a blocking call and shows no progress. So a product with
  only `core.platform` saves the last writes at the close. Its EDT does not process events during the save.
- The essential module loads on both sides of a split session, as the vault module does. So the dump action of
  each side still shows in the client.
- The vault module can be a module of `core.platform`.
  [ADR 0182](./0182-the-api-module-is-embedded-in-core-platform-and-the-vault-module-is-a-content-module-of-the-same-set.md)
  places it there.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md) and in the
  [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).

## Rejected alternatives

### Move `intellij.platform.ide`, `intellij.platform.ide.progress` and `intellij.platform.project` into `core.platform`

Other teams own these modules, and they bring many more dependencies. The move is not realistic for the settings
engine.

### Move the shutdown listener out of the vault module

A product with only `core.platform` then has no listener. It loses the last writes at the close, and nothing tells
the user.

### Without a saver, run the save in a background coroutine and wait for it

The EDT is blocked in both forms. The background form adds a scope, a wait and its failure handling, and it gains
nothing.
