# 0004. Caches stay out of the settings API

Date: 2026-05-25

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0054](./0054-non-settings-state-is-out-of-scope-by-design.md).

## Context

Today `workspace.xml` and many `PersistentStateComponent`s mix preferences with caches, such as a library
cache. The two are different things. A cache is derivable, versioned, and can be dropped at any time. A
setting carries user intent and must survive. Consultations (@daddy-bear) asked to make storing a cache in
settings structurally impossible.

## Decision

**This design covers settings only. Caches get a separate API, in a separate workstream.** How to stop
caches from landing in settings (inspections, separate APIs) is an implementation concern of that work.

## Consequences

- The settings API can promise what settings need and nothing more.
- A cache needs somewhere else to live, which the settings work does not provide.
