# 0071. Reloads run one at a time under a global mutex

Date: 2026-08-12

## Status

Superseded by [ADR 0162](./0162-reloads-take-one-mutex-per-context-kind-from-the-application-down.md)
([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

A reload's payload is frozen when it gathers and applied when it installs. Two overlapping reloads, such as an app
scope and a project scope, can finish in the wrong order: a slow reload that started earlier installs its stale
snapshot over a fresher one. Rare, but hard to reproduce.

## Decision

**One global mutex serializes the whole reload, gather and install. Reloads never overlap.**

## Consequences

- Whichever reload runs second gathers current disk, so nothing stale is installed.
- A slow gather blocks other reloads until it finishes. Reads are not blocked. Writes under the reloading scope wait
  for the readiness gate ([ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md)).
- A concurrent gather with installs in arrival order stays a possible later improvement.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).
