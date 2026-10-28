---
name: Change Listeners
description: Requirements for observing a change of a setting through a listener on its key.
style: plain-1
targets:
  - ../src/com/intellij/platform/settings/v2/SettingsChangeListener.kt
  - ../src/com/intellij/platform/settings/v2/SettingsKey.kt
  - ../shared/src/com/intellij/platform/settings/v2/impl/SettingsVaultImpl.kt
---

# Change Listeners

Status: Draft
Date: 2026-09-28

## Summary

A consumer observes a setting through a listener on its key. A contextual listener watches the stored value that
the key resolves at one context. A key-only listener watches every change of the key anywhere. A listener receives the key
and a context, but no value: it reads the current value itself.

## Goals

- Give every setting change notification with no code in the setting itself.
- Fire a contextual listener only when its value really changes.
- Keep a burst of changes, such as a branch switch, from flooding the listeners.

## Non-goals

- Listeners on raw layer changes. See [ADR 0026](../adr/0026-layer-change-listeners-are-dropped.md).

## Requirements

- `key.addListener(disposable, context, listener)` must fire when the stored value that the key resolves at that
  context changes. See [ADR 0044](../adr/0044-a-key-is-observable-through-a-listener.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A contextual listener must not fire when the resolved stored value stays the same.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A contextual listener must compare the stored values, not the values that a read returns. So a write of the
  default value fires it when no layer held a value before.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A contextual listener must ignore other keys and changes outside its context chain.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A reload that changes the value of an ancestor must fire a contextual listener at a descendant context, also when
  no layer at the descendant context changed.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsReloadListenerTest.kt
- The vault must deliver a notification only when the context that it carries is ready. A key-only event carries
  the context of the change, and a contextual listener carries its own context. The vault queues a notification at
  a dirty context, and delivers it when the context gets ready. This applies to a reload, a write and a push.
  See [ADR 0161](../adr/0161-a-listener-notification-waits-until-the-context-it-carries-is-ready.md).
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsQueuedNotificationTest.kt
- A queued contextual listener must fire only on a net change of its value. A queued key-only event must fire once
  per pair of a key and a changed context.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsQueuedNotificationTest.kt
- `key.addListener(disposable, listener)` must fire on a change of the key at any context. It carries the context
  where the change happened.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A key-only listener is a hint to read again. The value may be unchanged.
- A listener of a child key must fire on a change of its slot data and on a switch of its discriminator.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A listener of a child key must not fire on a change of another child in the same slot.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A listener at a context that pins a slot must fire on that slot only, not on a sibling slot or a selection switch.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A per-file change must fire a file listener and not a directory listener.
  [@test] ../test/src/com/intellij/platform/settings/v2/PerFileLayerTest.kt
- A listener on a map key must be rejected.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- Disposing the `disposable` must remove the listener. A listener whose notification is queued must not fire after
  the disposal.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsQueuedNotificationTest.kt
- A contextual listener must fire at most once per model change, even when the change touches many layers.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A key-only listener must fire at most once per changed context in one model change. Each call carries one of
  these contexts.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt
- A queued burst of reported changes must fold into one model change.
  [@test] ../test/src/com/intellij/platform/settings/v2/SettingsChangeListenerTest.kt

## Error Handling

- An exception from a listener must be logged. It must not reach the writer or the other listeners.

## Open Questions / Risks

- No listener observes every key at a context.
- A key-only event arrives when its context is ready, but a context under it can still be dirty. A listener that
  reads there must check the readiness itself.
