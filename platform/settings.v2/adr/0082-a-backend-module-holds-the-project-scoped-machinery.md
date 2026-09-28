# 0082. A backend module holds the project-scoped machinery

Date: 2026-09-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Remote dev splits the IDE into a frontend and a backend, and a coordinator daemon may follow. The engine had an
api module and a shared implementation module only.

## Decision

**An `intellij.platform.settings.v2.backend` module sits next to the api and shared modules. It holds the
project-scoped machinery (the per-project binary cache, the project load activity, the project-close listener)
and the file layer provider. All layer providers are meant to end up there; a frontend gets its own providers or
the data over RPC.** The vault, contexts, provider base classes and the cache engine stay shared.

## Consequences

- The split has a seam, though what moves across it is not finished.
- Whether the app-level cache stays shared, and where the VFS-touching parts land, are open.
