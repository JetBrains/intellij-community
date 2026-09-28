# 0018. The editing aspect is part of a context's identity

Date: 2026-06-17

## Status

Superseded by [ADR 0141](./0141-a-context-has-no-editing-aspect.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Some contributors add layers only while the user edits settings, in the settings UI or in Search Everywhere.
ReSharper's layers are the example. The layer set is built per context and cached, so whatever switches it must
be known when the context is built.

## Decision

**A context carries a `userEditing` aspect, and it is part of the context's identity, not a late modifier. Only
the platform creates an editing context, when it opens an editing session; ordinary API users do not get one.**

## Consequences

- An editing context has its own cached layer set with the extra layers; normal program flow never pays for them.
- In-context editing (option editors in Search Everywhere) sees foreign layers through the same flow.
