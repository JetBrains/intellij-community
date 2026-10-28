# 0163. The file layer finds the directory configs through the file name index

Date: 2026-10-28

## Status

Accepted ([IJPL-259782](https://youtrack.jetbrains.com/issue/IJPL-259782)).

## Context

The file layer finds the directory configs by a walk over the whole project content through the VFS. The walk loads
the children of every content directory into the VFS. When a project opens, the walk runs before the initial VFS
refresh. That refresh then lists each directory in full, instead of a cheap partial check. In the performance test,
the initial refresh went from 0.4 s to 10.4 s ([IJPL-259782](https://youtrack.jetbrains.com/issue/IJPL-259782)).

The file layer spec already listed the walk as a risk for a large project.

## Decision

**The file layer finds the directory configs by their file names in the file name index. It waits until the index is
ready, and then it looks the configs up.**

A reload of the project or the application scope waits for the index only in the file bucket
([ADR 0160](./0160-a-reload-installs-the-layers-kind-by-kind.md)). So the application and
the project contexts get ready without the file layer.

## Consequences

- The file layer walks no directory, so the initial VFS refresh stays partial.
- The directory configs load only after indexing. Until then, a read at a directory or a file context gives the
  snapshot cache value or the value of an ancestor.
- A branch switch that starts indexing delays the file bucket of its reload. The config watcher still reports a
  single changed config at once.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### Keep the walk and start it after the initial VFS refresh

The metric recovers, but the walk stays. Every reload of the project or the application scope still walks the whole
project.

### Walk only under the scope of a reload

The reloads that matter are of the project and the application scopes. For them, the scope is the whole project.
