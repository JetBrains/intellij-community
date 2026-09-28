# 0125. A file under no content root gets the project context

Date: 2026-09-30

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Supersedes
[ADR 0027](./0027-a-file-outside-the-project-tree-parents-directly-to-the-project.md).

## Context

[ADR 0027](./0027-a-file-outside-the-project-tree-parents-directly-to-the-project.md) keeps a file outside the
project tree as its own context node, with the project as its parent. It also puts a user node between the project
and the application.

A file context is now built from an anchor root and the relative path of the file under it
([ADR 0121](./0121-a-file-context-has-no-directory-flag.md)). A content root is the only anchor today. A file under
no content root has no anchor, so it has no file context identity. No user context exists.

## Decision

**A file under no content root gets the project context. It has no context node of its own.**

The chain of every context is `file → directory → … → project → application`, or a part of it. It has no user node.

## Consequences

- A library file or an arbitrary file has no file-level settings. It reads and writes at the project.
- Such a file gets no directory layers from where it lives, as in
  [ADR 0027](./0027-a-file-outside-the-project-tree-parents-directly-to-the-project.md).
- A later anchor kind, for example an external-dependency root, can give such a file a file context again.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md).

## Rejected alternatives

### Keep a file node for a file under no content root

The file context would need an identity with no anchor root, for example its absolute url. That is a second
identity form, and it adds file-level settings that no case needs yet.
