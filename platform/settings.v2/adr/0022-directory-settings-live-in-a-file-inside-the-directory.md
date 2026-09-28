# 0022. Directory settings live in a file inside the directory

Date: 2026-06-17

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Directory-level settings need a place on disk. A central file keyed by directory path would break on every
rename or move, and need path tracking.

## Decision

**Directory settings live in a config file inside that directory, committed, or `.local` and ignored.** This is
the editorconfig model.

## Consequences

- Renaming, moving or deleting a directory carries or drops its settings as a unit under VCS, with local diffs.
- No path tracking.
- Config files spread through the project tree.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).

## Rejected alternatives

### One central path-keyed file

Every directory rename needs a rewrite of the central file, and a rename outside the IDE leaves it wrong.
