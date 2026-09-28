# 0128. A key filters contexts with a predicate

Date: 2026-10-01

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0039](./0039-a-key-declares-the-context-kinds-it-is-defined-at.md).

## Context

[ADR 0039](./0039-a-key-declares-the-context-kinds-it-is-defined-at.md) lets a key declare the context kinds it is
defined at: app, project, directory, file and scope. The platform would check the list. The ADR leaves a write to a
disallowed context not settled.

The contexts now are the application, a project and a file. A file context also stands for a directory
([ADR 0121](./0121-a-file-context-has-no-directory-flag.md)). No scope context exists.

## Decision

**A key filters contexts with a predicate on the context. By default the predicate accepts every context. A read
and a write skip every context that the predicate rejects.**

- `SettingsContextKind` names the application, project and file kinds, so a predicate can check the kind without an
  `instanceof` check.
- A read of a rejected context resolves at the nearest accepted context up the chain. A write goes there too.
- The platform will give ready-made predicates, for example application only and project only, so that a key
  author can reuse them. They are not built yet.

## Consequences

- An application-only key read or written at a file context resolves and lands at the application.
- A predicate can express any rule that a list of kinds can, and more.
- A predicate is code, so nothing can check it statically.
- The resulting behavior is in the [layer resolution spec](../spec/layer-resolution.spec.md) and the
  [settings writes spec](../spec/settings-writes.spec.md).

## Rejected alternatives

### A list of context kinds that the platform checks

The key returns the kinds it accepts, and the platform keeps the filter logic. Ready-made predicates give the same
reuse, and a predicate also covers the cases that a list of kinds cannot express.
