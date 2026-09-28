# 0107. The connection carries a live table of content roots

Date: 2026-09-15

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0143](./0143-a-receiving-side-puts-its-own-project-into-a-context.md).

## Context

The frontend maps a file to the root that holds it, which gives the root half of a file context identity. It also
decides whether a file is inside the project tree, which decides the shape of the context chain. A wrong answer
builds a different chain and resolves a different layer set.

## Decision

**The connection carries one live table: the project identity pair, plus an entry per content root pairing the two
sides' urls. The backend answers with a flow, so the frontend gets the current roots and every later state. The
roots are collapsed to the topmost ones.**

## Consequences

- A root added after the connection reaches the other side.
- A project with a content root per source set anchors a context on the outermost root.
- The frontend matches a file by path and keeps the backend url as the anchor, so both sides name a file context
  the same way.
- The resulting behavior is in the [frontend file contexts spec](../spec/frontend-file-contexts.spec.md).
