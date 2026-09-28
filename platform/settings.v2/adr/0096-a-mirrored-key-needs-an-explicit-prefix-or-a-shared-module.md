# 0096. A mirrored key needs an explicit prefix or a shared module

Date: 2026-09-07

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0124](./0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md).

## Context

One feature can be two plugins, one per side ([ADR 0095](./0095-the-first-remote-dev-target.md)), with two plugin
ids. A key identity is `pluginId.name`, so one setting gets two identities and mirroring cannot match them.
The key identity rule
([ADR 0124](./0124-a-key-is-identified-by-its-name-and-the-plugin-of-the-class-that-declares-it.md)) allowed an
explicit prefix as an option.

## Decision

**A mirrored key reaches one identity on both sides through an explicit prefix that both plugins declare, or
through a shared module that both sides load, holding the key next to the provider declaration.**

## Consequences

- The explicit prefix carries further: it also covers a feature whose two sides can never share a module.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).
