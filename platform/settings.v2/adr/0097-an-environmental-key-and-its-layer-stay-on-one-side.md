# 0097. An environmental key and its layer stay on one side

Date: 2026-09-09

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

An environmental key describes the machine that runs it, such as a tool path. In remote dev the two sides can be
different machines, so they must not hold the same value.

## Decision

**An environmental mutation never crosses: each side applies its own. An environmental layer has no copy: it sends
no change, takes none and echoes nothing.** Both sides decide the same way, because locality is part of the
storage shape that must match.

## Consequences

- An environmental key writes only into environmental layers, so the two rules meet.
- A backend environmental file in the project tree is visible on the frontend, and an edit there does nothing.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).
