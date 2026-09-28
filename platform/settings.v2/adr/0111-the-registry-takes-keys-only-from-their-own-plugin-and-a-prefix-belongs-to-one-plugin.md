# 0111. The registry takes keys only from their own plugin, and a prefix belongs to one plugin

Date: 2026-09-24

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0117](./0117-the-marketplace-not-the-runtime-keeps-a-key-prefix-to-one-vendor.md).

## Context

A plugin could promote another plugin's keys, which would make eviction on unload inexact. Two plugins could
declare the same key prefix
([ADR 0124](./0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md))
and collide.

## Decision

**The promoting plugin must own the key, which owner detection gives at construction; the registry refuses others.
A prefix belongs to one plugin in a process: the first declaration wins, and a second plugin declaring it logs an
error and keeps its plugin id.**

## Consequences

- Eviction is exact: a listener on the extension point drops every key of a plugin when its extension goes away.
- A frontend and a backend plugin of one feature still share a prefix, because they never run in one process.
- The resulting behavior is in the [key registry spec](../spec/key-registry.spec.md).
