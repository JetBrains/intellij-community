# 0041. A pervasive setting migrates through a parallel v2 API, then deprecation

Date: 2026-07-14

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Some settings, code style first among them, are read and written directly all over the codebase and in
third-party plugins. There is no single choke point to swap. For them, migration is not about moving data.

## Decision

**A pervasively accessed setting gets a second, v2-based way to work with it. The old accessors are deprecated,
then removed over time. v2 runs in parallel with v1 while this happens, and simple, well-encapsulated settings
move first.**

## Consequences

- Each such setting is substantial, per-case work.
- The overall migration is long-lived.
