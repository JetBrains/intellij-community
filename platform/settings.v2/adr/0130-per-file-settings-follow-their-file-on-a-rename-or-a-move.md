# 0130. Per-file settings follow their file on a rename or a move

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0023](./0023-file-level-settings-nest-in-the-directorys-config.md).

## Context

[ADR 0023](./0023-file-level-settings-nest-in-the-directorys-config.md) keeps the settings of a file in the config of
its directory, keyed by the file name. It says the IDE moves the entry on a rename or a move and drops it on a
delete. It also offers a sidecar file per configured file as an opt-in alternative.

The file layer watches only its config files. Nothing moves an entry on a rename or a move, and nothing builds a
sidecar. A load ignores the per-file entry of a missing file. A save writes the per-file entries that the vault
holds, and the vault keeps the slice of a deleted file until the next load of its directory config.

## Decision

**A watcher moves the per-file entry of a file when the file is renamed or moved, so its settings follow it. A
delete needs no watcher: the next load ignores the entry of the missing file, and the first save after that load
drops it. There is no sidecar file.**

- A rename within a directory moves the entry within that directory config.
- A move to another directory moves the entry into the config of the new directory.
- The watcher is not built yet.

## Consequences

- Until the watcher exists, a renamed or moved file loses its settings. Its old entry stays until the next load of
  the old directory config, for example at the next start. The first save after that load drops it.
- Until that load, a new file with the name of a deleted one gets the settings of the deleted file.
- A move touches two configs, so both need a save.
- A rename or a move while the IDE is not running still loses the settings of the file.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### A sidecar file per configured file

It adds a file next to each configured file, and it does not follow its file on a rename either.
