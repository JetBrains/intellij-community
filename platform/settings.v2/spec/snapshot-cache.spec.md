---
name: Snapshot Cache
description: Requirements for the binary cache that seeds the settings model at startup and on project open.
style: plain-1
targets:
  - ../shared/src/com/intellij/platform/settings/v2/impl/cache/SettingsSnapshotCache.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/cache/SettingsCacheStorage.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/cache/SettingsCacheSerializer.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsCacheInvalidator.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsAppLifecycleListener.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/ProjectSettingsCacheService.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/ProjectSettingsLoadActivity.kt
  - ../backend/src/com/intellij/platform/settings/v2/impl/SettingsProjectListener.kt
---

# Snapshot Cache

Status: Draft
Date: 2026-09-28

## Summary

The snapshot cache is a binary copy of the settings model from the last session. At startup and at project open,
the vault seeds its model from the cache, so the first reads get last session's values. The real load runs anyway
and replaces them. So the cache only makes the start faster, and a bad cache never makes a value wrong for long.

## Goals

- Serve settings values before the providers finish loading.
- Never let a cache wedge the start.

## Non-goals

- A durable store. A lost cache costs speed only.
  See [ADR 0083](../adr/0083-the-binary-snapshot-cache-is-a-pure-accelerator.md).

## Requirements

- There must be one application cache and one cache per project.
  See [ADR 0083](../adr/0083-the-binary-snapshot-cache-is-a-pure-accelerator.md).
- A layer whose context names a project must go to that project's cache. Every other layer goes to the application
  cache.
- The application cache must live in the system directory. A project cache lives in the project data directory,
  never in `.idea`.
- The vault must load the application cache before the startup reload, and a project cache before the project reload.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSnapshotCacheTest.kt
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/SnapshotCacheRestartTest.kt
- A load must not replace a layer that is already in the model.
- A cached layer whose provider or context cannot be resolved must be skipped.
- A load must not mark any store as pending a save.
  See [ADR 0031](../adr/0031-a-startup-snapshot-speeds-up-reads-and-is-never-a-write-target.md).
- A saved cache must hold a value written just before the save.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSnapshotCacheTest.kt
- A cache must save after a long quiet period following a model change, and at application or project close.
  [@test] ../../../../remote-dev/settings/v2/lambdaTests/src/com/intellij/platform/settings/v2/lambdaTests/SnapshotCacheRestartTest.kt
- A cache must skip the write when its layers did not change.
- The dump order must depend on the content only, not on the order of insertion.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsSnapshotCacheTest.kt
- A cache must hold each layer with its context identity, its provider, its locality and its value.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsCacheSerializerTest.kt
- A cache write must be atomic.
- "Invalidate Caches" must make every cache be ignored and deleted at its next load.
- The cache must be off in unit tests.

## Error Handling

- A cache of another format version is ignored.
- A corrupt cache is logged and ignored.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsCacheSerializerTest.kt
- A cache that cannot be read or written is logged. The IDE loads from the real sources.

## Open Questions / Risks

- A remote-dev frontend has no real load of its mirrored layers, so a cache there would serve stale values for long.
