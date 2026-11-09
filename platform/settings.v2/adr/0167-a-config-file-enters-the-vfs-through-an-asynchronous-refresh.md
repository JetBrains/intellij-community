# 0167. A config file enters the VFS through an asynchronous refresh

Date: 2026-11-09

## Status

Accepted ([IJPL-260980](https://youtrack.jetbrains.com/issue/IJPL-260980)).

## Context

The watcher of the application layer and of the project layer gets a VFS event only for a file that the VFS tracks.
So the watch start makes the config file, or its parent directory, known to the VFS
([ADR 0152](./0152-a-config-file-is-created-on-its-first-write.md)). A write does the same, so an editor and the
watcher see the new content.

The first version did it with a synchronous refresh and lookup. A synchronous refresh from a background thread
waits for a write action, and a write action runs on the EDT. The finish callback of the parent refresh then ran a
second synchronous lookup inline on the EDT. The lookup checks every path segment on the disk.

The vault starts at the application load ([ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md)),
so the watch start now runs at the cold start, when the VFS does not know the config directory and the EDT is busy.
A startup profile showed the lookup on the EDT. The platform asks a plugin to keep the EDT free at startup.

## Decision

**A config file enters the VFS through the asynchronous lookup of the platform, and settings v2 never runs a
synchronous VFS refresh or lookup.** The watch start and the write call `VfsImplUtil.refreshAndFindFileByPath`
with a consumer. It walks the path down on a background thread, refreshes each directory that does not know the
next segment asynchronously, and continues after the refresh. Settings v2 does not wait for the result, and no
settings v2 code runs on the EDT. The API is experimental, so the shared module depends on
`intellij.platform.analysis`, and a change of the API is a change here.

## Consequences

- The startup does not wait for the EDT on behalf of settings v2. The VFS applies the refresh when it is free.
- A VFS event for the config file can come a moment later than before. The watcher reads the file from the disk
  when the event comes, so a reload sees the current content.
- A config file in a directory that the VFS knows but has not listed gets no event until a refresh lists the
  directory. The first version had the same limit.
- The lookup posts its work to a background executor itself, so the caller thread does not matter, and no
  assertion guards it. A synchronous refresh that waits from a background thread leaves no mark on the EDT, so
  only this rule and a review catch a regression.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### Keep the synchronous lookup and start the watcher later

A later start leaves a gap in which an external change of the config file is lost. The watch of a file that
appears after the start is a rule of [ADR 0152](./0152-a-config-file-is-created-on-its-first-write.md).

### Watch the file with a file system watch service instead of the VFS

The IDE already runs one native file watcher, and the VFS delivers its events. A second watcher per config file
costs a thread and a native handle each, and it does not see the changes that the IDE itself makes through the VFS.

### An own walk: refresh the deepest file of the path that the VFS sees

A lookup without a refresh finds the deepest existing file of the path, and one asynchronous refresh of it reports
the next segment. The walk stops there. When the directory of the config file is itself unknown, the file stays
unknown after the refresh, and its later changes fire no event. A second step needs a finish callback or a
re-arm from the watcher. The platform lookup already does every step.
