# 0148. There is no project defaults layer

Date: 2026-10-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Early plans expected a "project defaults" layer: settings that every new project starts with, apart from the
default project of the platform. No such layer exists.

Every project context has the application as its parent. So the application layer and the application defaults
layer already supply a value to every project that sets none of its own
([ADR 0147](./0147-the-application-defaults-ship-with-the-ide-and-are-only-read.md)).

## Decision

**There is no project defaults layer. A project that sets no value reads the application layers.**

## Consequences

- The layer stack stays at application defaults, application, project and file layers.
- A value that every project shares goes into an application layer.
- The resulting behavior is in the [file layers spec](../spec/file-layers.spec.md).
