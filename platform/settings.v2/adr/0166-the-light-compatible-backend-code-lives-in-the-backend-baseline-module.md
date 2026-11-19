# 0166. The light-compatible backend code lives in the backend.baseline module

Date: 2026-11-05

## Status

Accepted ([IJPL-260062](https://youtrack.jetbrains.com/issue/IJPL-260062)). Amends
[ADR 0082](./0082-a-backend-module-holds-the-project-scoped-machinery.md).

## Context

[ADR 0082](./0082-a-backend-module-holds-the-project-scoped-machinery.md) put the project-scoped machinery and the
file layer provider into `intellij.platform.settings.v2.backend`. That module depends on `intellij.platform.backend`,
so it loads in the monolith and in a remote-development backend only.

JetBrains Light starts in the light monolith mode. It leaves every backend module out, so it had no context factory,
no project load and no project close flush. The light mode opens a folder with no indexes and no scan of the project
content. The project-scoped machinery needs neither: it reads one known file per scope. The file layer provider needs
the file name index ([ADR 0163](./0163-the-file-layer-finds-the-directory-configs-through-the-file-name-index.md)).

The platform has a module type for this split: a `backend.baseline` module depends on
`intellij.platform.backend.baseline` at run time, and it loads in the monolith and in the light monolith mode.

## Decision

**The project-scoped machinery lives in `intellij.platform.settings.v2.backend.baseline`: the context factory, the
project load activity, the per-project snapshot cache, the roots cache, the project close listener and the context
dump action. The file layer provider stays in `intellij.platform.settings.v2.backend`.** The backend module depends on
the baseline module.

## Consequences

- JetBrains Light reads and writes the application and the project settings. The vault starts there at the
  application load, as everywhere.
- The file layer joins at the move to the smart mode, when the backend module loads
  ([ADR 0164](./0164-file-settings-are-off-in-the-light-mode-until-the-move-to-the-full-mode.md)).
- A new backend class goes to the baseline module unless it needs the indexes, the project model or another
  service that the light mode has not.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md) and the
  [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Keep everything in the backend module and give the light mode its own implementations

A second context factory and a second project load for the light mode would duplicate the code that works there as
it is.

### Move the file layer provider too, with a scan of the project content

The light mode exists to open a folder with no scan. A lazy discovery of the configs is a later step
([IJPL-260652](https://youtrack.jetbrains.com/issue/IJPL-260652)).
