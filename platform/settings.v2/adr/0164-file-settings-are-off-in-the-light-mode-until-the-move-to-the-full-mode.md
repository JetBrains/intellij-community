# 0164. File settings are off in the light mode until the move to the full mode

Date: 2026-11-05

## Status

Accepted ([IJPL-260062](https://youtrack.jetbrains.com/issue/IJPL-260062)).

## Context

The file layer finds the directory configs through the file name index
([ADR 0163](./0163-the-file-layer-finds-the-directory-configs-through-the-file-name-index.md)). The light mode has
no indexes, and it must not walk the project content: a light session opens a folder with no scan. So the file layer
cannot find the configs there in the way it does in the full mode.

The application and the project layers need no scan. They read one known file each, and they live in modules that
the light mode loads ([IJPL-260062](https://youtrack.jetbrains.com/issue/IJPL-260062)). The file layer lives in
the backend module, which the light mode leaves out
([ADR 0166](./0166-the-light-compatible-backend-code-lives-in-the-backend-baseline-module.md)).

## Decision

**In the light mode, only the application and the project settings work. The file layer stays in the backend module,
so the light mode has no file layer. A write at a file context lands in the project layer, as a write that no
narrower layer takes. After the move to the full mode, the file layer loads, and a write at a file context lands in
the directory config.**

A lazy discovery of the directory configs for the light mode is a later step
([IJPL-260652](https://youtrack.jetbrains.com/issue/IJPL-260652)). It would load a config on the first use of a file
context under its directory.

## Consequences

- A light session reads and writes the application and the project settings. A file context resolves to the project
  value.
- A value written at a file context in the light mode is a project value. It stays the project value after the move,
  and a file write after the move shadows it in the directory config.
- A directory config that exists on disk is invisible in the light mode until the move.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### A lazy discovery of the directory configs in the light mode now

It is the right end state, but it is a feature of its own. It needs a lookup of the configs on the path from a file
to its root, with no index and no scan. It also needs a reconciliation with the index-based load after the move. It
goes to [IJPL-260652](https://youtrack.jetbrains.com/issue/IJPL-260652).

### A scan of the project content in the light mode

A light session must open a folder with no scan. The scan is what the light mode exists to avoid.
