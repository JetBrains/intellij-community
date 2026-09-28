# 0151. A non-persistent file resolves through its delegate, then its parent

Date: 2026-10-08

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A file context needs a persistent file under a content root. A light or in-memory file is not persistent, so its
context comes from the nearest persistent ancestor, or from the project when it has none
([ADR 0125](./0125-a-file-under-no-content-root-gets-the-project-context.md)).

Some non-persistent files stand for a real file. A `VirtualFileDelegate` names the file it delegates to. A
`VirtualFileWindow` is a file of injected code, and its delegate is the host file. Their own parent, if any, says
nothing about where their settings belong.

## Decision

**To find the file for a context, the platform repeats these steps until it reaches a persistent file:**

1. A `VirtualFileDelegate` gives its delegate.
2. A `VirtualFileWindow` gives its delegate.
3. Any other non-persistent file gives its parent.

When a step gives nothing, the file gets the project context.

## Consequences

- Injected code reads the settings of its host file.
- A delegating file reads the settings of the file it stands for, not of its own parent.
- A light file with a persistent parent reads the settings of that parent directory.
- The backend and the frontend use the same steps, because both build a file context from the same helper.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md).
