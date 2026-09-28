# 0023. File-level settings nest in the directory's config

Date: 2026-06-17

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0130](./0130-per-file-settings-follow-their-file-on-a-rename-or-a-move.md).

## Context

A single file can have its own settings. They need a place on disk that survives renames as well as possible.
Options: one global path-keyed file, a sidecar file per configured file, or a section in the directory's config.

## Decision

**Per-file settings live in the containing directory's config, under a section keyed by file name.** The IDE
watches the file system and updates or drops an entry on rename, move or delete. A sidecar file per configured
file is an opt-in alternative, not the default.

## Consequences

- Path keys are directory-local, and the entries move with the directory.
- Only a rename within the directory needs tracking, and VFS events cover it while the IDE runs.
- A change made while the IDE is not running leaves a harmless orphan entry, cleaned up later.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### One global path-keyed file

Every rename anywhere touches it.

### A sidecar file per configured file

It does not follow its target's rename, and it adds clutter.
