# 0048. Undo is out of scope

Date: 2026-07-16

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Writes are atomic, discrete changes through one sink ([ADR 0043](./0043-a-write-is-atomic-and-needs-no-apply.md)), so the engine can track
them for undo. The
platform undo API needs a `FileEditor` and a `VirtualFile`, which a settings screen does not have, and one stack
against a stack per page is a UI choice.

## Decision

**Undo and redo are a UI concern, out of scope of this task (per Alexandr Trushev). The engine only makes them
possible.**

## Consequences

- The constraints (editor-bound undo API, stack scope) stay recorded for whoever picks it up.
