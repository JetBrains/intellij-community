# 0081. A context carries no validity check

Date: 2026-09-02

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

`SettingsContext` had `isValid()`: is the project still open, does the file still exist. Only tests used it. A
context is a string snapshot: a project location hash, or a root url plus a relative path, nothing live. The
check needed the VFS, the one VFS use left in an otherwise VFS-free type, and it blocked the frontend and backend
split.

## Decision

**`isValid()` is removed. A context is a snapshot, and "valid" is a question about the outer world, not about the
context.** If a real need appears, such as a UI that greys out a dead context, it comes back as a separate service
with frontend and backend implementations, not as a method on the context.

## Consequences

- A context is VFS-free and leak-free.
- A stale context is harmless: a read finds whatever layers exist or the default, and a write lands in a layer
  that may no longer matter. Nothing corrupts.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md).
