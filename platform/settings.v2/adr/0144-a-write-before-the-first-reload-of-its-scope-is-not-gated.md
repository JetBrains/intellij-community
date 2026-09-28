# 0144. A write before the first reload of its scope is not gated

Date: 2026-10-06

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0031](./0031-a-startup-snapshot-speeds-up-reads-and-is-never-a-write-target.md).

## Context

[ADR 0031](./0031-a-startup-snapshot-speeds-up-reads-and-is-never-a-write-target.md) says a write goes to the real
layer, which must be loaded first. The readiness gate
([ADR 0063](./0063-a-write-waits-for-its-scope-to-be-ready-and-a-read-never-waits.md)) makes a write wait only
while a reload runs for its context or an ancestor.

So a write before the first reload of the application or a project does not wait:

- The vault starts the application reload in its init, in a launched coroutine, after it loads the snapshot cache.
- A project reload starts in a startup activity, after the cache load of the project. Other startup activities run
  at the same time.

A write in that window saves a layer that the vault never loaded from disk, over the real file. No real scenario
that writes in that window is known.

## Decision

**A write before the first reload of its scope is not gated. The vault accepts the window.**

## Consequences

- An early write can replace the content of a config file with the cached or written keys only.
- The vault keeps no state about which scopes were loaded, so no write can wait for a load that never comes.
- The decision is revisited when a real case shows up, or when a lazy load of directory configs needs the same
  mechanism.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### Gate a write until the first reload of its scope ends

A waitable "application loaded" flag, and a waitable set of loaded projects with the default project in it from the
start. The first reload of a scope sets it, also a failed or a cancelled one. A project leaves the set after it
closes. The application context checks the flag, and a project context checks the set.

It closes the window, but it adds state that can hang every write of a scope whose load never comes. The window is
small and no real case is known, so the cost is not worth it now.
