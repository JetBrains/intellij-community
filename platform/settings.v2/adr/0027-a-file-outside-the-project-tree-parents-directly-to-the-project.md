# 0027. A file outside the project tree parents directly to the project

Date: 2026-06-19

## Status

Superseded by [ADR 0125](./0125-a-file-under-no-content-root-gets-the-project-context.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A file context walks its directories up to the project. A library file or an arbitrary file has no path to the
project through its directories, and its on-disk location says nothing about which settings should apply.

## Decision

**A file context always carries a project. A file inside the project tree walks its directories up to the
project. A file outside it parents directly to the project, with no directory traversal.**

## Consequences

- The chain always reaches `project → user → app`.
- An outside file gets no directory layers, no editorconfig and no per-directory config from where it lives.
- It is still its own context node, so file-level settings work on it.
