# 0121. A file context has no directory flag

Date: 2026-09-30

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A file context held a directory flag, and its identity included the flag. The flag let `parentContext` and a
restore from the identity stay string math, with no read lock. Only the backend file layer read it: a directory
stores in its own config ([ADR 0022](./0022-directory-settings-live-in-a-file-inside-the-directory.md)), and a file
stores in the config of its directory ([ADR 0023](./0023-file-level-settings-nest-in-the-directorys-config.md)).

On the frontend of remote development, the thin client file system knows files only. Every `ThinClientVirtualFile`
reports `isDirectory() = false` and has no parent. So a frontend context for a directory got another identity than
the backend context for the same directory. Its own layer would then be missing once layers are mirrored.

## Decision

**A file context holds only its project, anchor root and relative path, and its identity has no directory flag. The
backend file layer finds whether a path is a directory on disk, when it computes the storage context.**

A path that does not exist is its own storage context. [ADR 0122](./0122-a-save-to-a-storage-context-that-does-not-exist-is-dropped.md)
covers the save of such a context.

## Consequences

- The frontend and the backend give a directory the same identity, except the project part. The project part
  differs by design ([ADR 0119](./0119-a-project-context-identity-is-the-location-hash-not-the-project-id.md)).
- `parentContext` and a restore from the identity stay string math.
- `LayerProvider.storageContext` reads the disk, so it is cheap but no longer pure. The storage context of a path
  can change when the path changes on disk.
- A per-file entry and a directory config can no longer be told apart by the identity. The load keeps them apart,
  because it ignores a per-file entry whose name is a directory.
- The identity layout changes, so the snapshot cache `VERSION` goes up. An old cache is ignored once.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md) and the
  [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### Two identities: one for resolution without the flag, one for transport with it

The frontend cannot know the flag, so a transported flag from the frontend is wrong for a directory. The backend
then must not trust it, and the backend is the only side that needs the flag.

### A frontend way to build a directory context from a path

It fixes the frontend, but it keeps a flag that only the backend file layer uses.
